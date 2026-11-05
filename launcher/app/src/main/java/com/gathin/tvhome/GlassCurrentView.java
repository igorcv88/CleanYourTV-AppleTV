package com.gathin.tvhome;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PathMeasure;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.os.SystemClock;
import android.view.KeyEvent;
import android.view.View;

/**
 * Glass Current: the thin lit progress ribbon above the dock. A level stroke (3.2dp), brighter over the elapsed
 * part, with the elapsed time at its left end and the remaining time at its right end.
 *
 * <p>While music plays, small luminous swells ({@link RibbonSwells}) rise from the elapsed part of the line,
 * in a palette from the cover ({@link ArtPalette}), and sink back. They are procedural decoration, not audio
 * analysis. The line itself is always a straight horizontal segment: the swells are drawn underneath it and
 * never move it, the time labels or the knob. Reduced motion turns the swells off.
 *
 * <p>Focusable only when the session supports seeking: then Left/Right seek 15 s per press and are consumed;
 * Up/Down pass through. Unfocused, it never touches D-pad keys.
 */
final class GlassCurrentView extends View {
    interface SeekListener {
        void onSeekBy(long deltaMs);
    }

    /** 20 Hz is smooth for these slow swells and leaves the GPU almost idle; nothing redraws while paused. */
    private static final long FRAME_MS = 50L;
    private static final long PALETTE_FADE_MS = 900L, LEVEL_FADE_MS = 700L;
    private static final int GRADIENT_STEPS = 6;

    private final float density;
    private final Paint lit = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint glow = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint rest = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint knob = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint swellPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint label = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path path = new Path();
    private final Path seg = new Path();
    private final Path swellPath = new Path();
    private final PathMeasure measure = new PathMeasure();
    private final float[] pos = new float[2];
    private final RibbonSwells.Swell swell = new RibbonSwells.Swell();
    private final int[] samples = new int[64];
    private float fraction = -1f;
    private String elapsed = "", remaining = "";
    private boolean seekable;
    private SeekListener seekListener;
    private int pathW = -1;
    private float pathStart, pathEnd;
    private boolean playing;
    private boolean animateWave = true;

    /** Palette cross-fade: from the previous track's colours to the new ones. */
    private int[] fromPalette = ArtPalette.DEFAULT.clone(), toPalette = ArtPalette.DEFAULT.clone();
    private final int[] palette = ArtPalette.DEFAULT.clone();
    private long paletteAt = -PALETTE_FADE_MS;
    private int gradientStep = -1;

    /** Overall swell presence 0..1, eased toward 1 while playing and 0 while paused. */
    private float level;
    private long levelAt;

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
        glow.setStrokeWidth(stroke * 2.6f);
        glow.setColor(0x24FFFFFF);
        rest.setColor(0x47FFFFFF);
        knob.setColor(0xFFFFFFFF);
        swellPaint.setStyle(Paint.Style.FILL);
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
        levelAt = SystemClock.uptimeMillis();
        invalidate();
    }

    /** A restrained, multi-hue palette from the album's pixels; cross-fades from the previous track's. */
    void setArtwork(Bitmap artwork) {
        int[] next = ArtPalette.DEFAULT;
        if (artwork != null && !artwork.isRecycled() && artwork.getConfig() != Bitmap.Config.HARDWARE) {
            try {
                int w = artwork.getWidth(), h = artwork.getHeight();
                for (int y = 0; y < 8; y++) for (int x = 0; x < 8; x++) {
                    samples[y * 8 + x] = artwork.getPixel((x * 2 + 1) * (w - 1) / 16, (y * 2 + 1) * (h - 1) / 16);
                }
                next = ArtPalette.from(samples);
            } catch (RuntimeException ignored) { /* artwork may be revoked between session updates */ }
        }
        if (java.util.Arrays.equals(next, toPalette)) return;
        fromPalette = palette.clone();
        toPalette = next.clone();
        paletteAt = SystemClock.uptimeMillis();
        if (!animateWave) paletteAt -= PALETTE_FADE_MS;   // reduced motion: switch at once
        gradientStep = -1;
        invalidate();
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

    /** The seek path is always horizontal. Only the separate swells are animated. */
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
        gradientStep = -1;
    }

    /** Advances the palette fade; rebuilds the line's shader only at a few steps of it, not every frame. */
    private boolean updatePalette(long now) {
        float t = Math.min(1f, (now - paletteAt) / (float) PALETTE_FADE_MS);
        for (int i = 0; i < 3; i++) palette[i] = ArtPalette.lerp(fromPalette[i], toPalette[i], t);
        int step = Math.round(t * GRADIENT_STEPS);
        if (step != gradientStep) {
            gradientStep = step;
            // Mostly white, warming to the cover's colours toward the played end: a hint, not a rainbow.
            lit.setShader(new LinearGradient(pathStart, 0f, pathEnd, 0f,
                    new int[]{0xFFFFFFFF, ArtPalette.toward(palette[0], 0xFFFFFFFF, 0.55f),
                            ArtPalette.toward(palette[1], 0xFFFFFFFF, 0.5f)},
                    new float[]{0f, 0.55f, 1f}, Shader.TileMode.CLAMP));
            glow.setColor((palette[0] & 0x00FFFFFF) | 0x26000000);
        }
        return t < 1f;
    }

    private boolean updateLevel(long now) {
        float target = playing && animateWave ? 1f : 0f;
        float stepAmount = Math.max(0f, (now - levelAt) / (float) LEVEL_FADE_MS);
        levelAt = now;
        if (level < target) level = Math.min(target, level + stepAmount);
        else if (level > target) level = Math.max(target, level - stepAmount);
        return level != target;
    }

    /** The swells, under the line, inside the elapsed span [from, to]. */
    private void drawSwells(Canvas c, float from, float to, float cy, long now) {
        if (level <= 0f || to - from < 12f * density) return;
        float span = to - from;
        // A short elapsed span gets proportionally smaller swells, so they never spill past the played part.
        float room = Math.min(1f, span / (120f * density));
        for (int i = 0; i < RibbonSwells.COUNT; i++) {
            RibbonSwells.at(i, now, level, swell);
            if (swell.envelope <= 0.01f) continue;
            float half = Math.min(swell.widthDp * density, span) / 2f;
            float x = from + half + (span - 2f * half) * swell.centre;
            float height = swell.heightDp * density * room;
            int colour = palette[swell.colour];
            bell(x, cy, half, height);
            swellPaint.setColor((colour & 0x00FFFFFF) | (Math.round(0x3A * swell.envelope) << 24));
            c.drawPath(swellPath, swellPaint);
            // A brighter, narrower core: light that seems to come from the line itself.
            bell(x, cy, half * 0.55f, height * 0.62f);
            swellPaint.setColor((ArtPalette.toward(colour, 0xFFFFFFFF, 0.25f) & 0x00FFFFFF)
                    | (Math.round(0x4C * swell.envelope) << 24));
            c.drawPath(swellPath, swellPaint);
        }
    }

    /** A smooth mound on the baseline: zero slope where it meets the line, rounded at its top. */
    private void bell(float x, float cy, float half, float height) {
        swellPath.reset();
        swellPath.moveTo(x - half, cy);
        swellPath.cubicTo(x - half * 0.45f, cy, x - half * 0.38f, cy - height, x, cy - height);
        swellPath.cubicTo(x + half * 0.38f, cy - height, x + half * 0.45f, cy, x + half, cy);
        swellPath.close();
    }

    @Override
    protected void onDraw(Canvas c) {
        int w = getWidth(), h = getHeight();
        if (w <= 0 || h <= 0) return;
        if (pathW != w) buildPath(w, h);
        long now = SystemClock.uptimeMillis();
        boolean fading = updatePalette(now);
        boolean easing = updateLevel(now);
        boolean focused = isFocused();
        float cy = h / 2f;
        label.setAlpha(focused ? 255 : 210);
        float base = cy - (label.descent() + label.ascent()) / 2f;
        c.drawText(elapsed, pathStart - 14f * density - label.measureText(elapsed), base, label);
        if (!remaining.isEmpty()) c.drawText(remaining, pathEnd + 14f * density, base, label);
        float len = measure.getLength();
        if (fraction < 0f) {
            drawSwells(c, pathStart, pathEnd, cy, now);
            c.drawPath(path, rest);
        } else {
            float at = len * fraction;
            drawSwells(c, pathStart, pathStart + at, cy, now);
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
        }
        // Frames only while something moves and Home is visible; paused and reduced motion stay still.
        boolean moving = level > 0f || easing || fading;
        if (moving && isShown()) postInvalidateDelayed(FRAME_MS);
    }
}
