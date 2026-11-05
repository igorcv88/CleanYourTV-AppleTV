package com.gathin.tvhome;

import android.graphics.Bitmap;
import android.graphics.BitmapShader;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.ColorMatrix;
import android.graphics.ColorMatrixColorFilter;
import android.graphics.LinearGradient;
import android.graphics.Matrix;
import android.graphics.Outline;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.drawable.Drawable;
import android.view.View;
import android.view.ViewOutlineProvider;

import androidx.annotation.NonNull;

/**
 * Scenic Glass's single Liquid Glass material, shared by the dock, transport buttons, header buttons, panels,
 * Control Center and menus.
 *
 * <p>Composition, back to front, over whatever is really behind the view (the aerial SurfaceView, the photo
 * or the album wash, all of which keep drawing underneath):
 * <ol>
 *   <li>A <em>share</em> of the cached, scene-aligned pre-blur ({@link Scene}), slightly saturated. The old
 *       drawable painted this sample at full alpha, which replaced the scenery under every surface with a
 *       64-px smear: the milky, almost opaque look. Now 40–62% of it is laid over the live scene, so the
 *       glass reads as moderate blur with the scenery still recognisable through it.</li>
 *   <li>One adaptive tint from {@link GlassTone}: a smoked veil over bright scenes, a faint white lift over
 *       dark ones; a single fill, never two stacked sheets.</li>
 *   <li>A sheen across the upper third, so the surface has volume.</li>
 *   <li>Along the rim, the same sample nudged by two pixels: the edge lensing of thick glass.</li>
 *   <li>A hairline rim lit from above, fading down the sides and catching light again at the bottom.</li>
 * </ol>
 *
 * <p>Cost: four or five round-rect fills per surface per frame it is redrawn, all with shaders built in
 * {@link #onBoundsChange} or once per scene bitmap. No capture, blur, decode or allocation in {@link #draw}.
 * The elevation shadow is kept faint through {@link #getOutline}, because a full shadow under translucent
 * glass shows through as grey haze.
 */
final class SceneGlassDrawable extends Drawable {
    private static boolean frosted;

    private final View host;
    private final float radius;
    private final GlassTone.Kind kind;
    private final float density;
    private final Paint scene = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    private final Paint refractedRim = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    private final Paint tint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint sheen = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint edge = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Matrix matrix = new Matrix();
    private final Matrix rimMatrix = new Matrix();
    private final RectF rect = new RectF();
    private final int[] loc = new int[2];
    private Bitmap shaderBitmap;
    private int alpha = 255;
    private boolean focused;
    private float toneLum = Float.NaN;
    private boolean toneFocused;
    private int toneColor;

    /** Applies the material to a view: background, outline from the drawable, faint shadow. */
    static SceneGlassDrawable apply(View v, float radiusDp, GlassTone.Kind kind) {
        SceneGlassDrawable d = new SceneGlassDrawable(v, radiusDp, kind);
        v.setBackground(d);
        v.setOutlineProvider(ViewOutlineProvider.BACKGROUND);
        return d;
    }

    /** The user's Frosted choice (Appearance): more material for very busy scenery. Set before building views. */
    static void setFrosted(boolean value) {
        frosted = value;
    }

    SceneGlassDrawable(View host, float radiusDp, GlassTone.Kind kind) {
        this.host = host;
        this.kind = kind;
        density = host.getResources().getDisplayMetrics().density;
        radius = radiusDp < 0 ? -1f : radiusDp * density;
        ColorMatrix vibrancy = new ColorMatrix();
        vibrancy.setSaturation(1.22f);
        ColorFilter vibrant = new ColorMatrixColorFilter(vibrancy);
        scene.setColorFilter(vibrant);
        refractedRim.setColorFilter(vibrant);
        refractedRim.setStyle(Paint.Style.STROKE);
        refractedRim.setStrokeWidth(Math.max(1.5f, density * 2f));
        edge.setStyle(Paint.Style.STROKE);
        edge.setStrokeWidth(Math.max(1f, density));
        if (kind.sampleShare > 0f) Scene.register(host);
    }

    GlassTone.Kind kind() {
        return kind;
    }

    @Override public boolean isStateful() { return true; }

    @Override protected boolean onStateChange(int[] state) {
        boolean next = false;
        for (int value : state) if (value == android.R.attr.state_focused) { next = true; break; }
        if (focused == next) return false;
        focused = next;
        invalidateSelf();
        return true;
    }

    @Override
    protected void onBoundsChange(@NonNull android.graphics.Rect b) {
        if (b.isEmpty()) return;
        edge.setShader(new LinearGradient(b.left, b.top, b.left, b.bottom,
                new int[]{0x99FFFFFF, 0x30FFFFFF, 0x14FFFFFF, 0x3DFFFFFF},
                new float[]{0f, 0.3f, 0.78f, 1f}, Shader.TileMode.CLAMP));
        float sheenEnd = b.top + Math.min(b.height() * 0.55f, 120f * density);
        sheen.setShader(new LinearGradient(b.left, b.top, b.left, sheenEnd,
                0x1CFFFFFF, 0x00FFFFFF, Shader.TileMode.CLAMP));
    }

    private float cornerRadius() {
        return radius < 0 ? Math.min(rect.width(), rect.height()) / 2f
                : Math.min(radius, Math.min(rect.width(), rect.height()) / 2f);
    }

    @Override
    public void draw(@NonNull Canvas c) {
        rect.set(getBounds());
        if (rect.isEmpty()) return;
        float r = cornerRadius();
        float share = GlassTone.sampleShare(kind, frosted);
        boolean sampled = share > 0f && drawSample(c, r, share);
        int color = tone();
        tint.setColor(color);
        tint.setAlpha(Math.round((color >>> 24) * alpha / 255f));
        c.drawRoundRect(rect, r, r, tint);
        sheen.setAlpha(Math.round((focused ? 255 : 170) * alpha / 255f));
        c.drawRoundRect(rect, r, r, sheen);
        if (sampled && kind != GlassTone.Kind.CONTROL) drawLensRim(c, r);
        float inset = edge.getStrokeWidth() / 2f;
        rect.inset(inset, inset);
        edge.setAlpha(Math.round((focused ? 255 : kind == GlassTone.Kind.INSET ? 120 : 175) * alpha / 255f));
        c.drawRoundRect(rect, Math.max(0f, r - inset), Math.max(0f, r - inset), edge);
    }

    /** The scene-aligned share of the blur. False when there is nothing sampled yet. */
    private boolean drawSample(Canvas c, float r, float share) {
        Bitmap bmp = Scene.current();
        // Absolute screen alignment also works for PopupWindow panels, whose root view is not fullscreen.
        int screenW = host.getResources().getDisplayMetrics().widthPixels;
        int screenH = host.getResources().getDisplayMetrics().heightPixels;
        if (bmp == null || bmp.isRecycled() || screenW <= 0 || screenH <= 0) return false;
        if (bmp != shaderBitmap) {
            scene.setShader(new BitmapShader(bmp, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP));
            refractedRim.setShader(new BitmapShader(bmp, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP));
            shaderBitmap = bmp;
        }
        float[] cover = SceneCrop.cover(bmp.getWidth(), bmp.getHeight(), screenW, screenH);
        host.getLocationOnScreen(loc);
        matrix.setScale(cover[0], cover[0]);
        matrix.postTranslate(cover[1] - loc[0], cover[2] - loc[1]);
        // The host may be scaled (focus); undo it so the image stays still behind the glass.
        if (host.getScaleX() != 1f || host.getScaleY() != 1f) {
            matrix.postScale(1f / host.getScaleX(), 1f / host.getScaleY());
        }
        scene.getShader().setLocalMatrix(matrix);
        scene.setAlpha(Math.round(255 * share * alpha / 255f));
        c.drawRoundRect(rect, r, r, scene);
        return true;
    }

    /** The rim bends a cached part of the same scene by a couple of pixels: lensing, no capture or blur. */
    private void drawLensRim(Canvas c, float r) {
        rimMatrix.set(matrix);
        rimMatrix.postTranslate(density * 2f, -density);
        refractedRim.getShader().setLocalMatrix(rimMatrix);
        refractedRim.setAlpha(Math.round((focused ? 150 : 105) * alpha / 255f));
        float rimInset = refractedRim.getStrokeWidth();
        rect.inset(rimInset, rimInset);
        float rr = Math.max(0f, r - rimInset);
        c.drawRoundRect(rect, rr, rr, refractedRim);
        rect.inset(-rimInset, -rimInset);
    }

    /** The adaptive tint, recomputed only when the scene's luminance or the focus changes. */
    private int tone() {
        float lum = Scene.luminance();
        if (lum != toneLum || focused != toneFocused) {
            toneLum = lum;
            toneFocused = focused;
            toneColor = GlassTone.tint(kind, lum, focused, frosted);
        }
        return toneColor;
    }

    @Override public void getOutline(@NonNull Outline outline) {
        android.graphics.Rect b = getBounds();
        if (b.isEmpty()) return;
        float r = radius < 0 ? Math.min(b.width(), b.height()) / 2f
                : Math.min(radius, Math.min(b.width(), b.height()) / 2f);
        outline.setRoundRect(b, r);
        outline.setAlpha(kind.shadowAlpha * alpha / 255f);
    }

    @Override public void setAlpha(int a) {
        alpha = a;
        invalidateSelf();
    }

    @Override public int getAlpha() {
        return alpha;
    }

    @Override public void setColorFilter(ColorFilter cf) { }

    @Override public int getOpacity() {
        return PixelFormat.TRANSLUCENT;
    }
}
