package com.gathin.tvhome;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PathMeasure;
import android.graphics.Typeface;
import android.view.KeyEvent;
import android.view.View;

/**
 * Glass Current: the thin lit progress ribbon above the dock. A low, gently curved stroke (3.2dp), brighter
 * over the elapsed part, with the elapsed time at its left end and the remaining time at its right end; the
 * stroke runs between the two labels, so they never overlap. It is not an equalizer: the curve's shape is
 * fixed per width and changes only when the progress does (once a second while playing).
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
    private final Paint label = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path path = new Path();
    private final Path seg = new Path();
    private final PathMeasure measure = new PathMeasure();
    private final float[] pos = new float[2];
    private float fraction = -1f;
    private String elapsed = "", remaining = "";
    private boolean seekable;
    private SeekListener seekListener;
    private int pathW = -1;
    private float pathStart, pathEnd;

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
        animate().scaleY(gainFocus ? 1.25f : 1f).setDuration(200).start();
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

    /** Builds the curve between the labels once per width: a slow, shallow arc with a hint of a wave. */
    private void buildPath(int w, int h) {
        float gap = 14f * density;
        float labelW = Math.max(label.measureText("0:00:00"), 40f * density);
        pathStart = labelW + gap;
        pathEnd = w - labelW - gap;
        float cy = h / 2f, amp = 3.5f * density;
        path.reset();
        int steps = 48;
        for (int i = 0; i <= steps; i++) {
            float t = i / (float) steps;
            float x = pathStart + (pathEnd - pathStart) * t;
            float y = cy - amp * (float) Math.sin(Math.PI * t) + amp * 0.35f * (float) Math.sin(2 * Math.PI * t * 1.5);
            if (i == 0) path.moveTo(x, y); else path.lineTo(x, y);
        }
        measure.setPath(path, false);
        pathW = w;
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
    }
}
