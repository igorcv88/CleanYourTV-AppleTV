package com.gathin.tvhome;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.BitmapDrawable;
import android.os.Handler;
import android.os.Looper;
import android.widget.FrameLayout;
import android.widget.ImageView;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Rotating Scenic collections: bundled photos offline, optional full-resolution Commons downloads on disk. */
final class ScenicWallpaperPlayer {
    static final String MODE = "gallery", GROUP = "wallpaper_group", FIXED = "wallpaper_fixed";
    static final String MINUTES = "wallpaper_minutes", LAST = "wallpaper_last", CHANGED = "wallpaper_changed";
    static final int[] ROTATION_MINUTES = {5, 10, 20, 30, 60};

    private final Context context;
    private final SharedPreferences prefs;
    private final FrameLayout root;
    private final ImageView image, incoming;
    private final ExecutorService decoder = Executors.newSingleThreadExecutor();
    private final Handler main = new Handler(Looper.getMainLooper());
    private final Random random = new Random();
    private final int width, height;
    private String current = "";
    private boolean active, released, loading;

    ScenicWallpaperPlayer(Context context, FrameLayout root, int belowIndex) {
        this.context = context;
        this.root = root;
        this.prefs = context.getSharedPreferences(ScenicHome.PREFS, Context.MODE_PRIVATE);
        width = context.getResources().getDisplayMetrics().widthPixels;
        height = context.getResources().getDisplayMetrics().heightPixels;
        image = new ImageView(context);
        incoming = new ImageView(context);
        image.setScaleType(ImageView.ScaleType.CENTER_CROP);
        incoming.setScaleType(ImageView.ScaleType.CENTER_CROP);
        image.setImportantForAccessibility(ImageView.IMPORTANT_FOR_ACCESSIBILITY_NO);
        incoming.setImportantForAccessibility(ImageView.IMPORTANT_FOR_ACCESSIBILITY_NO);
        FrameLayout.LayoutParams full = new FrameLayout.LayoutParams(-1, -1);
        root.addView(image, belowIndex, full);
        root.addView(incoming, belowIndex + 1, new FrameLayout.LayoutParams(-1, -1));
    }

    static boolean selected(Context c) {
        return MODE.equals(c.getSharedPreferences(ScenicHome.PREFS, Context.MODE_PRIVATE)
                .getString(ScenicHome.WALLPAPER_KEY, "auto"));
    }

    static List<ScenicWallpaperCatalog.Item> items(Context context, String group) {
        List<ScenicWallpaperCatalog.Item> result = new ArrayList<>();
        for (ScenicWallpaperCatalog.Group g : ScenicWallpaperCatalog.allGroups()) {
            if ("all".equals(group) || g.id.equals(group)) result.addAll(g.builtIn);
        }
        File folder = directory(context);
        for (ScenicWallpaperCatalog.Group g : ScenicWallpaperCatalog.allGroups()) {
            if (!"all".equals(group) && !g.id.equals(group)) continue;
            File catalog = new File(folder, g.id + ".json");
            try {
                if (!catalog.isFile() || catalog.length() > 128 * 1024) continue;
                byte[] data = new byte[(int) catalog.length()];
                try (java.io.FileInputStream in = new java.io.FileInputStream(catalog)) {
                    int n = 0, read;
                    while (n < data.length && (read = in.read(data, n, data.length - n)) > 0) n += read;
                    if (n != data.length) continue;
                }
                JSONArray records = new JSONArray(new String(data, java.nio.charset.StandardCharsets.UTF_8));
                for (int i = 0; i < Math.min(100, records.length()); i++) {
                    JSONObject j = records.optJSONObject(i);
                    if (j == null) continue;
                    String filename = j.optString("file", "");
                    if (!filename.matches("[a-z]+_[0-9]+\\.jpg")) continue;
                    File file = new File(folder, filename);
                    if (file.isFile() && file.length() > 0) {
                        result.add(new ScenicWallpaperCatalog.Item(g.id + "/" + filename, g.id,
                                j.optString("name", "4K photo"), null, file.getAbsolutePath(),
                                j.optString("credit", ""), j.optString("source", "")));
                    }
                }
            } catch (Exception ignored) { /* Corrupt catalog never prevents the offline collection. */ }
        }
        return result;
    }

    static File directory(Context c) {
        File external = c.getExternalFilesDir("scenic_wallpapers");
        return external != null ? external : new File(c.getFilesDir(), "scenic_wallpapers");
    }

    void start() {
        active = true;
        tick.run();
    }

    void pause() {
        active = false;
        main.removeCallbacks(tick);
    }

    void close() {
        pause();
        released = true;
        decoder.shutdownNow();
    }

    private final Runnable tick = new Runnable() {
        @Override public void run() {
            if (!active || released) return;
            int minutes = prefs.getInt(MINUTES, 10);
            long interval = Math.max(1, minutes) * 60_000L;
            long changed = prefs.getLong(CHANGED, 0);
            boolean due = current.isEmpty() || System.currentTimeMillis() - changed >= interval;
            if (due && !loading) {
                String group = prefs.getString(GROUP, "all");
                String fixed = prefs.getString(FIXED, "");
                List<ScenicWallpaperCatalog.Item> available = items(context, group);
                ScenicWallpaperCatalog.Item selected = null;
                if (!current.isEmpty()) {
                    for (ScenicWallpaperCatalog.Item item : available) {
                        if (item.id.equals(current) && (!due || !fixed.isEmpty())) selected = item;
                    }
                }
                if (selected == null) selected = ScenicWallpaperCatalog.pick(available, current, fixed, random);
                if (selected != null && !selected.id.equals(current)) load(selected);
                else if (selected != null) prefs.edit().putLong(CHANGED, System.currentTimeMillis()).apply();
            }
            main.postDelayed(this, 10_000);
        }
    };

    private void load(ScenicWallpaperCatalog.Item item) {
        loading = true;
        decoder.execute(() -> {
            Bitmap next = decode(item);
            Bitmap sample = next != null ? Blur.blurred(next, 96, 4) : null;
            main.post(() -> {
                loading = false;
                if (released || !active) return;
                if (next == null) {
                    // A damaged download or asset is skipped instead of trapping Home on a blank screen.
                    prefs.edit().putLong(CHANGED, System.currentTimeMillis()).apply();
                    return;
                }
                current = item.id;
                prefs.edit().putString(LAST, item.id).putLong(CHANGED, System.currentTimeMillis()).apply();
                incoming.animate().cancel();
                incoming.setImageDrawable(new BitmapDrawable(context.getResources(), next));
                incoming.setAlpha(0f);
                incoming.animate().alpha(1f).setDuration(750).withEndAction(() -> {
                    if (released) return;
                    image.setImageDrawable(incoming.getDrawable());
                    incoming.setImageDrawable(null);
                    incoming.setAlpha(0f);
                }).start();
                if (sample != null) Scene.setStill(sample);
            });
        });
    }

    private Bitmap decode(ScenicWallpaperCatalog.Item item) {
        try {
            BitmapFactory.Options o = new BitmapFactory.Options();
            o.inJustDecodeBounds = true;
            if (item.asset != null) {
                try (InputStream in = context.getAssets().open(item.asset)) { BitmapFactory.decodeStream(in, null, o); }
            } else BitmapFactory.decodeFile(item.file, o);
            if (o.outWidth < 320 || o.outHeight < 180) return null;
            o.inSampleSize = WallpaperSize.sampleSize(o.outWidth, o.outHeight, width, height);
            if (!WallpaperSize.accepted(o.outWidth / o.inSampleSize, o.outHeight / o.inSampleSize)) return null;
            o.inPreferredConfig = Bitmap.Config.RGB_565; // Photos on an Android 11 TV: fewer GPU allocations.
            o.inJustDecodeBounds = false;
            if (item.asset != null) {
                try (InputStream in = context.getAssets().open(item.asset)) { return BitmapFactory.decodeStream(in, null, o); }
            }
            return BitmapFactory.decodeFile(item.file, o);
        } catch (Exception | OutOfMemoryError ignored) { return null; }
    }
}
