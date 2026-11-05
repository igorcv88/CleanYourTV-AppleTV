package com.gathin.tvhome;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PathMeasure;
import android.graphics.Typeface;
import android.graphics.Shader;
import android.os.SystemClock;
import android.view.KeyEvent;
import android.view.View;

/**
 * Glass Current: the thin lit progress ribbon above the dock. A level stroke (3.2dp), brighter
 * over the elapsed part, with the elapsed time at its left end and the remaining time at its right end; the
 * stroke runs between the two labels. A separate, low-amplitude accent animates above the straight
 * progress line, tinted from its cover artwork; motion is decorative, not audio/FFT analysis.
 *
 * Focusable only when the session supports seeking: then Left/Right seek 15 s per press and are consumed;
 * Up/Down pass through. Unfocused, it never touches D-pad keys.
 */
final class GlassCurrentView extends View {
    interface SeekListener {
        void onSeekBy(long deltaMs);
    }

    private final float density;
    private final Paint lit = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint glow = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint rest = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint knob = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint accentWave = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint label = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path path = new Path();
    private final Path waveform = new Path();
    private final Path seg = new Path();
    private final PathMeasure measure = new PathMeasure();
    private final float[] pos = new float[2];
    private float fraction = -1f;
    private String elapsed = "", remaining = "";
    private boolean seekable;
    private SeekListener seekListener;
    private int pathW = -1;
    private float pathStart, pathEnd;
    private int accent = 0xFFA9D7F4;
    private boolean playing;
    private boolean animateWave = true;
    private int lastGradientWidth = -1;

    GlassCurrentView(Context ctx) {
        super(ctx);
        density = ctx.getResources().getDisplayMetrics().density;
        float stroke = 3.2f * density;
        for (Paint p : new Paint[]{lit, glow, rest}) {
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeCap(Paint.Cap.ROUND);
            p.setStrokeWidth(stroke);
        }
        lit.setColor(0xF2FFFFFF);
        glow.setColor(0x33FFFFFF);
        glow.setStrokeWidth(stroke * 3.2f);
        rest.setColor(0x47FFFFFF);
        knob.setColor(0xFFFFFFFF);
        accentWave.setColor(0x70A9D7F4);
        accentWave.setStyle(Paint.Style.STROKE);
        accentWave.setStrokeWidth(Math.max(1f, 1.4f * density));
        accentWave.setStrokeCap(Paint.Cap.ROUND);
        label.setColor(0xD9F5F6F8);
        label.setTextSize(13f * ctx.getResources().getDisplayMetrics().scaledDensity);
        Typeface inter = ctx.getResources().getFont(R.font.inter_medium);
        label.setTypeface(inter);
        label.setFontFeatureSettings("tnum");
        setFocusable(false);
        setWillNotDraw(false);
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
    }

    void setSeekListener(SeekListener l) {
        seekListener = l;
    }

    void setMotionEnabled(boolean enabled) {
        animateWave = enabled;
        invalidate();
    }

    void setPlaying(boolean value) {
        if (playing == value) return;
        playing = value;
        pathW = -1;
        invalidate();
    }

    /** A bright but restrained palette from the album's pixels, no heavyweight Palette dependency. */
    void setArtwork(Bitmap artwork) {
        int color = 0xFFA9D7F4;
        if (artwork != null && artwork.getConfig() != Bitmap.Config.HARDWARE) {
            try {
                float hueX = 0f, hueY = 0f, weight = 0f;
                float[] hsv = new float[3];
                for (int y = 1; y <= 6; y++) for (int x = 1; x <= 6; x++) {
                    int pixel = artwork.getPixel(x * (artwork.getWidth() - 1) / 7,
                            y * (artwork.getHeight() - 1) / 7);
                    Color.colorToHSV(pixel, hsv);
                    float w = hsv[1] * hsv[2];
                    float rad = (float) Math.toRadians(hsv[0]);
                    hueX += Math.cos(rad) * w;
                    hueY += Math.sin(rad) * w;
                    weight += w;
                }
                if (weight > 1f) {
                    float h = (float) Math.toDegrees(Math.atan2(hueY, hueX));
                    color = Color.HSVToColor(new float[]{(h + 360f) % 360f, 0.72f, 1f});
                }
            } catch (RuntimeException ignored) { /* artwork may be revoked between session updates */ }
        }
        if (accent != color) {
            accent = color;
            glow.setColor((accent & 0x00FFFFFF) | 0x46000000);
            accentWave.setColor((accent & 0x00FFFFFF) | 0x70000000);
            lastGradientWidth = -1;
            invalidate();
        }
    }

    /** fraction -1 = unknown duration (subdued line, no remaining time). */
    void setProgress(float fraction, String elapsed, String remaining) {
        boolean changed = Math.abs(fraction - this.fraction) > 0.0005f || !elapsed.equals(this.elapsed)
                || !remaining.equals(this.remaining);
        this.fraction = fraction;
        this.elapsed = elapsed;
        this.remaining = remaining;
        if (changed) invalidate();
    }

    void setSeekable(boolean seekable) {
        if (this.seekable == seekable) return;
        this.seekable = seekable;
        setFocusable(seekable);
        if (!seekable && isFocused()) clearFocus();
    }

    boolean isSeekable() {
        return seekable;
    }

    @Override
    protected void onFocusChanged(boolean gainFocus, int direction, android.graphics.Rect previous) {
        super.onFocusChanged(gainFocus, direction, previous);
        animate().cancel();
        // Never scale the entire view: doing so stretched the timestamp glyphs and bent the track.
        setScaleY(1f);
        invalidate();
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (seekable && isFocused() && seekListener != null
                && (keyCode == KeyEvent.KEYCODE_DPAD_LEFT || keyCode == KeyEvent.KEYCODE_DPAD_RIGHT)) {
            seekListener.onSeekBy(keyCode == KeyEvent.KEYCODE_DPAD_RIGHT ? MediaTime.SEEK_STEP_MS : -MediaTime.SEEK_STEP_MS);
            return true;
        }
        return super.onKeyDown(keyCode, event);
    }

    /** The seek path is always horizontal. Only the separate visual accent is animated. */
    private void buildPath(int w, int h) {
        float gap = 14f * density;
        float labelW = Math.max(label.measureText("0:00:00"), 40f * density);
        pathStart = labelW + gap;
        pathEnd = w - labelW - gap;
        float cy = h / 2f;
        path.reset();
        path.moveTo(pathStart, cy);
        path.lineTo(pathEnd, cy);
        measure.setPath(path, false);
        pathW = w;
        if (lastGradientWidth != w) {
            float[] hsv = new float[3];
            Color.colorToHSV(accent, hsv);
            hsv[0] = (hsv[0] + 28f) % 360f;
            int neighbor = Color.HSVToColor(hsv);
            Shader gradient = new LinearGradient(pathStart, 0f, pathEnd, 0f,
                    new int[]{0xFFFFFFFF, accent, neighbor, 0xE8FFFFFF},
                    new float[]{0f, 0.38f, 0.76f, 1f}, Shader.TileMode.CLAMP);
            lit.setShader(gradient);
            lastGradientWidth = w;
        }
    }

    /** Cover-coloured animation; amplitude does not claim to follow Spotify's actual sound. */
    private void drawAccent(Canvas c, int h) {
        if (!playing || !animateWave) return;
        float phase = (SystemClock.uptimeMillis() % 2600L) * (float) (2 * Math.PI / 2600L);
        float centre = h / 2f - 7f * density;
        waveform.reset();
        for (int i = 0; i <= 48; i++) {
            float t = i / 48f;
            float x = pathStart + (pathEnd - pathStart) * t;
            float envelope = (float) Math.sin(Math.PI * t);
            float y = centre - 2.5f * density * envelope * (float) Math.sin(8f * Math.PI * t - phase);
            if (i == 0) waveform.moveTo(x, y); else waveform.lineTo(x, y);
        }
        c.drawPath(waveform, accentWave);
    }

    @Override
    protected void onDraw(Canvas c) {
        int w = getWidth(), h = getHeight();
        if (w <= 0 || h <= 0) return;
        if (pathW != w) buildPath(w, h);
        boolean focused = isFocused();
        label.setAlpha(focused ? 255 : 210);
        float base = h / 2f - (label.descent() + label.ascent()) / 2f;
        c.drawText(elapsed, pathStart - 14f * density - label.measureText(elapsed), base, label);
        if (!remaining.isEmpty()) c.drawText(remaining, pathEnd + 14f * density, base, label);
        float len = measure.getLength();
        if (fraction < 0f) {
            c.drawPath(path, rest);
            drawAccent(c, h);
            if (playing && animateWave && isShown()) postInvalidateDelayed(83);
            return;
        }
        float at = len * fraction;
        seg.reset();
        measure.getSegment(at, len, seg, true);
        c.drawPath(seg, rest);
        seg.reset();
        measure.getSegment(0, at, seg, true);
        c.drawPath(seg, glow);
        c.drawPath(seg, lit);
        if (focused) {
            measure.getPosTan(at, pos, null);
            c.drawCircle(pos[0], pos[1], 6f * density, knob);
        }
        drawAccent(c, h);
        // ~12 Hz while Home is visible. No playback motion in reduced-motion mode.
        if (playing && animateWave && isShown()) postInvalidateDelayed(83);
    }
}
