package com.gathin.tvhome;

import android.graphics.Bitmap;
import android.graphics.BitmapShader;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.drawable.Drawable;
import android.view.View;

import androidx.annotation.NonNull;

/**
 * Real frosted glass without a live blur: paints the part of the {@link Scene}'s pre-blurred image that lies
 * under the host view (same centre-crop mapping as the backdrop, so it lines up), then a faint cool tint and
 * the {@link LiquidGlassDrawable} rim and bloom on top. With no scene (a moving aerial) only tint and rim draw.
 * Costs one bitmap-shader fill per draw; nothing is allocated per frame.
 */
final class SceneGlassDrawable extends Drawable {
    private final View host;
    private final float radius;
    private final LiquidGlassDrawable rim;
    private final Paint scene = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    private final Paint tint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Matrix matrix = new Matrix();
    private final RectF rect = new RectF();
    private final int[] loc = new int[2];
    private Bitmap shaderBitmap;
    private int alpha = 255;
    private final int tintColor;

    SceneGlassDrawable(View host, float radiusDp, int tintColor) {
        this.host = host;
        float d = host.getResources().getDisplayMetrics().density;
        this.radius = radiusDp * d;
        this.rim = new LiquidGlassDrawable(radiusDp, 0x00FFFFFF);
        this.tintColor = tintColor;
        tint.setColor(tintColor);
        Scene.register(host);
    }

    @Override
    protected void onBoundsChange(@NonNull android.graphics.Rect b) {
        rim.setBounds(b);
    }

    @Override
    public void draw(@NonNull Canvas c) {
        rect.set(getBounds());
        Bitmap bmp = Scene.current();
        if (bmp != null && !bmp.isRecycled()) {
            if (bmp != shaderBitmap) {
                scene.setShader(new BitmapShader(bmp, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP));
                shaderBitmap = bmp;
            }
            View rootView = host.getRootView();
            float[] cover = SceneCrop.cover(bmp.getWidth(), bmp.getHeight(), rootView.getWidth(), rootView.getHeight());
            host.getLocationInWindow(loc);
            matrix.setScale(cover[0], cover[0]);
            matrix.postTranslate(cover[1] - loc[0], cover[2] - loc[1]);
            // The host may be scaled (focus); undo it so the image stays still behind the glass.
            if (host.getScaleX() != 1f || host.getScaleY() != 1f) {
                matrix.postScale(1f / host.getScaleX(), 1f / host.getScaleY());
            }
            scene.getShader().setLocalMatrix(matrix);
            scene.setAlpha(alpha);
            c.drawRoundRect(rect, radius, radius, scene);
        }
        tint.setAlpha(Math.round((tintColor >>> 24) * alpha / 255f));
        c.drawRoundRect(rect, radius, radius, tint);
        rim.draw(c);
    }

    @Override public void setAlpha(int a) {
        alpha = a;
        rim.setAlpha(a);
        invalidateSelf();
    }

    @Override public void setColorFilter(ColorFilter cf) { }

    @Override public int getOpacity() {
        return PixelFormat.TRANSLUCENT;
    }
}
