package com.gathin.tvhome;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.media.MediaMetadata;
import android.media.session.MediaController;
import android.media.session.MediaSessionManager;
import android.media.session.PlaybackState;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.provider.Settings;
import android.util.Log;

import java.io.InputStream;
import java.io.ByteArrayOutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * The real music session for Glass Current: Android's active media sessions, read through MediaSessionManager
 * once the owner granted notification access to {@link MediaAccessService}. Nothing is simulated: no session,
 * no access or nothing playing means {@link Listener#onNowPlaying} gets null.
 *
 * Runs only between {@link #start} and {@link #stop} (Home's onResume/onPause): the session listener and the
 * controller callback are released when Home is not in front. Metadata and state changes are coalesced
 * ({@link #COALESCE_MS}); artwork is decoded and blurred off the UI thread, only when the track's art changes.
 */
final class NowPlaying {
    private static final String TAG = "TvHome.NowPlaying";
    static final long COALESCE_MS = 120;

    /** An immutable view of the session at one moment. */
    static final class Track {
        String pkg, title, artist, album, artKey;
        int state;
        long position, updateTime, duration, actions;
        float speed;
        /** Display artwork (<= 512 px), and a small blurred copy for the scene; both may be null. */
        Bitmap art, blurredArt;
        /** This track's art is still being decoded: keep the current scene rather than flashing the wallpaper. */
        boolean artPending;

        long positionNow() {
            return MediaTime.position(state, position, updateTime, speed, SystemClock.elapsedRealtime(), duration);
        }
    }

    interface Listener {
        /** null: nothing to show (no access, no session, stopped). */
        void onNowPlaying(Track track);
    }

    private final Context ctx;
    private final Listener listener;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private final ComponentName service;
    private MediaSessionManager msm;
    private MediaController controller;
    private boolean started;
    private String lastArtKey;
    private Bitmap lastArt, lastBlurred;
    private int artGeneration;
    private boolean artLoading;

    NowPlaying(Context ctx, Listener listener) {
        this.ctx = ctx.getApplicationContext();
        this.listener = listener;
        this.service = new ComponentName(this.ctx, MediaAccessService.class);
    }

    /** Whether notification access is granted to this launcher. */
    static boolean hasAccess(Context ctx) {
        String flat = Settings.Secure.getString(ctx.getContentResolver(), "enabled_notification_listeners");
        if (flat == null) return false;
        String mine = new ComponentName(ctx, MediaAccessService.class).flattenToString();
        String mineShort = new ComponentName(ctx, MediaAccessService.class).flattenToShortString();
        for (String s : flat.split(":")) if (s.equals(mine) || s.equals(mineShort)) return true;
        return false;
    }

    /** The system screen for granting access, or null where the TV has none (then adb is the only way). */
    static Intent accessSettingsIntent(Context ctx) {
        Intent i = new Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        return i.resolveActivity(ctx.getPackageManager()) != null ? i : null;
    }

    /** The adb command that grants access, shown where the TV has no settings screen for it. */
    static String adbGrantCommand(Context ctx) {
        return "adb shell cmd notification allow_listener "
                + new ComponentName(ctx, MediaAccessService.class).flattenToShortString();
    }

    private final MediaSessionManager.OnActiveSessionsChangedListener sessions = this::pick;

    private final MediaController.Callback callback = new MediaController.Callback() {
        @Override public void onPlaybackStateChanged(PlaybackState state) { post(); }
        @Override public void onMetadataChanged(MediaMetadata metadata) { post(); }
        @Override public void onSessionDestroyed() {
            if (controller != null) controller.unregisterCallback(this);
            controller = null;
            refreshSessions();
        }
    };

    private final Runnable publish = this::publish;

    void start() {
        if (started) return;
        started = true;
        if (!hasAccess(ctx)) {
            listener.onNowPlaying(null);
            return;
        }
        msm = ctx.getSystemService(MediaSessionManager.class);
        if (msm == null) {
            listener.onNowPlaying(null);
            return;
        }
        try {
            msm.addOnActiveSessionsChangedListener(sessions, service, main);
            refreshSessions();
        } catch (SecurityException e) {
            Log.w(TAG, "no media session access", e);
            listener.onNowPlaying(null);
        }
    }

    void stop() {
        if (!started) return;
        started = false;
        main.removeCallbacks(publish);
        if (msm != null) {
            try {
                msm.removeOnActiveSessionsChangedListener(sessions);
            } catch (RuntimeException ignored) {
            }
        }
        if (controller != null) controller.unregisterCallback(callback);
        controller = null;
    }

    void release() {
        stop();
        worker.shutdownNow();
    }

    private void refreshSessions() {
        if (!started || msm == null) return;
        try {
            pick(msm.getActiveSessions(service));
        } catch (SecurityException e) {
            pick(null);
        }
    }

    /** Prefer a playing session, else a paused one with a title; the first wins among equals (most recent). */
    private void pick(List<MediaController> list) {
        MediaController best = null;
        int bestRank = 0;
        if (list != null) {
            for (MediaController c : list) {
                PlaybackState s = c.getPlaybackState();
                MediaMetadata m = c.getMetadata();
                int st = s != null ? s.getState() : 0;
                String title = m != null ? m.getString(MediaMetadata.METADATA_KEY_TITLE) : null;
                int rank = !MediaTime.showable(st, title) ? 0 : MediaTime.advancing(st) || st == MediaTime.STATE_BUFFERING ? 2 : 1;
                if (rank > bestRank) {
                    best = c;
                    bestRank = rank;
                }
            }
        }
        if (best == null && list != null && !list.isEmpty()) best = list.get(0);
        if (controller != null && best != null && controller.getSessionToken().equals(best.getSessionToken())) {
            post();
            return;
        }
        if (controller != null) controller.unregisterCallback(callback);
        controller = best;
        if (controller != null) controller.registerCallback(callback, main);
        post();
    }

    private void post() {
        main.removeCallbacks(publish);
        main.postDelayed(publish, COALESCE_MS);
    }

    private void publish() {
        if (!started) return;
        MediaController c = controller;
        if (c == null) {
            listener.onNowPlaying(null);
            return;
        }
        PlaybackState s = c.getPlaybackState();
        MediaMetadata m = c.getMetadata();
        Track t = new Track();
        t.pkg = c.getPackageName();
        t.state = s != null ? s.getState() : 0;
        t.position = s != null ? s.getPosition() : MediaTime.UNKNOWN;
        t.updateTime = s != null ? s.getLastPositionUpdateTime() : 0;
        t.speed = s != null ? s.getPlaybackSpeed() : 0f;
        t.actions = s != null ? s.getActions() : 0;
        if (m != null) {
            t.title = text(m, MediaMetadata.METADATA_KEY_TITLE, MediaMetadata.METADATA_KEY_DISPLAY_TITLE);
            t.artist = text(m, MediaMetadata.METADATA_KEY_ARTIST, MediaMetadata.METADATA_KEY_ALBUM_ARTIST);
            if (t.artist == null) t.artist = text(m, MediaMetadata.METADATA_KEY_DISPLAY_SUBTITLE, null);
            t.album = text(m, MediaMetadata.METADATA_KEY_ALBUM, null);
            t.duration = m.getLong(MediaMetadata.METADATA_KEY_DURATION);
        }
        if (!MediaTime.showable(t.state, t.title)) {
            listener.onNowPlaying(null);
            return;
        }
        Bitmap bmp = m == null ? null : firstBitmap(m);
        List<String> artUris = m == null ? new ArrayList<>() : artUris(m);
        t.artKey = t.pkg + '|' + t.title + '|' + t.album + '|' + (bmp != null
                ? bmp.getGenerationId() + "x" + bmp.getWidth() : artUris.toString());
        if (t.artKey.equals(lastArtKey)) {
            t.art = lastArt;
            t.blurredArt = lastBlurred;
            t.artPending = lastArt == null && artLoading;
            listener.onNowPlaying(t);
            return;
        }
        // New art: show the track now, the artwork when it is ready. A newer change wins over a slow decode.
        lastArtKey = t.artKey;
        lastArt = null;
        lastBlurred = null;
        artLoading = bmp != null || !artUris.isEmpty();
        t.artPending = artLoading;
        final int gen = ++artGeneration;
        listener.onNowPlaying(t);
        if (!artLoading) return;
        final String key = t.artKey;
        worker.execute(() -> {
            Bitmap src = bmp;
            // A content:// URI may be private to Spotify; try every alternative that
            // the same media session supplies, including its HTTPS cover-art CDN URI.
            if (src == null) for (String candidate : artUris) {
                src = load(candidate);
                if (src != null) break;
            }
            Bitmap shown = src == null ? null : scaleDown(src, 512);
            Bitmap blur = src == null ? null : Blur.blurred(src, 48, 3);
            main.post(() -> {
                if (gen != artGeneration || !key.equals(lastArtKey)) return;
                // Decoded, or failed for good: either way no longer pending (a failure falls back to no art).
                artLoading = false;
                lastArt = shown;
                lastBlurred = blur;
                publish();
            });
        });
    }

    private static String text(MediaMetadata m, String key, String fallbackKey) {
        CharSequence v = m.getText(key);
        if ((v == null || v.toString().trim().isEmpty()) && fallbackKey != null) v = m.getText(fallbackKey);
        return v == null || v.toString().trim().isEmpty() ? null : v.toString().trim();
    }

    private static Bitmap firstBitmap(MediaMetadata m) {
        for (String k : new String[]{MediaMetadata.METADATA_KEY_ALBUM_ART, MediaMetadata.METADATA_KEY_ART,
                MediaMetadata.METADATA_KEY_DISPLAY_ICON}) {
            Bitmap b = m.getBitmap(k);
            if (b != null && !b.isRecycled() && b.getWidth() > 0) return b;
        }
        return null;
    }

    private static List<String> artUris(MediaMetadata m) {
        List<String> result = new ArrayList<>();
        String[] keys = {MediaMetadata.METADATA_KEY_ALBUM_ART_URI, MediaMetadata.METADATA_KEY_ART_URI,
                MediaMetadata.METADATA_KEY_DISPLAY_ICON_URI};
        for (String k : keys) {
            String v = m.getString(k);
            if (v != null && !v.isEmpty() && !result.contains(v)) result.add(v);
        }
        return result;
    }

    /** Try provider artwork first; official music apps also provide HTTPS album-art CDN URLs. */
    private Bitmap load(String uri) {
        try {
            Uri u = Uri.parse(uri);
            String scheme = u.getScheme();
            if ("https".equalsIgnoreCase(scheme)) return loadHttps(u);
            if (!"content".equals(scheme) && !"android.resource".equals(scheme) && !"file".equals(scheme)) return null;
            BitmapFactory.Options o = new BitmapFactory.Options();
            o.inJustDecodeBounds = true;
            try (InputStream in = ctx.getContentResolver().openInputStream(u)) {
                BitmapFactory.decodeStream(in, null, o);
            }
            int sample = 1;
            while (Math.max(o.outWidth, o.outHeight) / (sample * 2) >= 512) sample *= 2;
            BitmapFactory.Options d = new BitmapFactory.Options();
            d.inSampleSize = sample;
            try (InputStream in = ctx.getContentResolver().openInputStream(u)) {
                return BitmapFactory.decodeStream(in, null, d);
            }
        } catch (Exception e) {
            return null;
        }
    }

    /** Bounded download, only for the HTTPS URI supplied by the active media session.
     * No arbitrary HTTP redirects, local-network URL fetching, extra SDK or main-thread I/O. */
    private static Bitmap loadHttps(Uri uri) {
        HttpURLConnection connection = null;
        try {
            URL url = new URL(uri.toString());
            if (!"https".equalsIgnoreCase(url.getProtocol()) || url.getHost().isEmpty()) return null;
            connection = (HttpURLConnection) url.openConnection();
            connection.setInstanceFollowRedirects(false);
            connection.setConnectTimeout(5000);
            connection.setReadTimeout(6000);
            connection.setRequestProperty("Accept", "image/*");
            if (connection.getResponseCode() != HttpURLConnection.HTTP_OK) return null;
            if (connection.getContentLength() > 4 * 1024 * 1024) return null;
            byte[] data;
            try (InputStream in = connection.getInputStream(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
                byte[] buf = new byte[8192];
                for (int n; (n = in.read(buf)) != -1;) {
                    if (out.size() + n > 4 * 1024 * 1024) return null;
                    out.write(buf, 0, n);
                }
                data = out.toByteArray();
            }
            BitmapFactory.Options probe = new BitmapFactory.Options();
            probe.inJustDecodeBounds = true;
            BitmapFactory.decodeByteArray(data, 0, data.length, probe);
            if (probe.outWidth < 1 || probe.outHeight < 1) return null;
            BitmapFactory.Options opts = new BitmapFactory.Options();
            opts.inSampleSize = 1;
            while (Math.max(probe.outWidth, probe.outHeight) / (opts.inSampleSize * 2) >= 512)
                opts.inSampleSize *= 2;
            return BitmapFactory.decodeByteArray(data, 0, data.length, opts);
        } catch (Exception ignored) {
            return null;
        } finally {
            if (connection != null) connection.disconnect();
        }
    }

    private static Bitmap scaleDown(Bitmap src, int max) {
        int w = src.getWidth(), h = src.getHeight();
        if (Math.max(w, h) <= max) return src;
        float s = max / (float) Math.max(w, h);
        return Bitmap.createScaledBitmap(src, Math.round(w * s), Math.round(h * s), true);
    }

    // ---- transport: only what the session's actions allow (the buttons are hidden otherwise) ----

    void togglePlay() {
        MediaController c = controller;
        if (c == null) return;
        PlaybackState s = c.getPlaybackState();
        int st = s != null ? s.getState() : 0;
        long actions = s != null ? s.getActions() : 0;
        switch (MediaTime.toggleCommand(actions, st)) {
            case MediaTime.TOGGLE_PAUSE: c.getTransportControls().pause(); break;
            case MediaTime.TOGGLE_PLAY: c.getTransportControls().play(); break;
            case MediaTime.TOGGLE_MEDIA_KEY:
                // The session offers only the toggle: send it the media key, as a remote's button would.
                long now = SystemClock.uptimeMillis();
                c.dispatchMediaButtonEvent(new android.view.KeyEvent(now, now, android.view.KeyEvent.ACTION_DOWN,
                        android.view.KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE, 0));
                c.dispatchMediaButtonEvent(new android.view.KeyEvent(now, now, android.view.KeyEvent.ACTION_UP,
                        android.view.KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE, 0));
                break;
            default:
        }
    }

    void next() {
        if (controller != null) controller.getTransportControls().skipToNext();
    }

    void previous() {
        if (controller != null) controller.getTransportControls().skipToPrevious();
    }

    void seekTo(long ms) {
        if (controller != null) controller.getTransportControls().seekTo(ms);
    }

    /** Opens the app that owns the session (its launch intent), for "Open app" in the listening view. */
    Intent appIntent() {
        MediaController c = controller;
        if (c == null) return null;
        if (c.getSessionActivity() != null) return null;   // handled by sendSessionActivity
        Intent i = ctx.getPackageManager().getLeanbackLaunchIntentForPackage(c.getPackageName());
        if (i == null) i = ctx.getPackageManager().getLaunchIntentForPackage(c.getPackageName());
        if (i != null) i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        return i;
    }

    boolean sendSessionActivity() {
        MediaController c = controller;
        if (c == null || c.getSessionActivity() == null) return false;
        try {
            c.getSessionActivity().send();
            return true;
        } catch (Exception e) {
            return false;
        }
    }
}
