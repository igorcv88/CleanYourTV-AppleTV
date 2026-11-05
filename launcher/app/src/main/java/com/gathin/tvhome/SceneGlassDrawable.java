package com.gathin.tvhome;

import android.graphics.Bitmap;
import android.graphics.BitmapShader;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.LinearGradient;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.drawable.Drawable;
import android.view.View;

import androidx.annotation.NonNull;

/**
 * One continuous glass sheet: scene-aligned preblur, a single restrained tint and one hairline edge.
 * The old combination of a tinted pane and LiquidGlassDrawable's six additional layers produced
 * visible opaque bands on Android 11. All paints/shaders are cached outside draw().
 */
final class SceneGlassDrawable extends Drawable {
    private final View host;
    private final float radius;
    private final Paint scene = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    private final Paint tint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint edge = new Paint(Paint.ANTI_ALIAS_FLAG);
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
        this.tintColor = tintColor;
        tint.setColor(tintColor);
        edge.setStyle(Paint.Style.STROKE);
        edge.setStrokeWidth(Math.max(1f, d));
        Scene.register(host);
    }

    @Override
    protected void onBoundsChange(@NonNull android.graphics.Rect b) {
        edge.setShader(new LinearGradient(b.left, b.top, b.left, b.bottom,
                new int[]{0xA0FFFFFF, 0x3CFFFFFF, 0x24FFFFFF, 0x52FFFFFF},
                new float[]{0f, 0.22f, 0.8f, 1f}, Shader.TileMode.CLAMP));
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
        float inset = edge.getStrokeWidth() / 2f;
        rect.inset(inset, inset);
        edge.setAlpha(alpha);
        c.drawRoundRect(rect, Math.max(0, radius - inset), Math.max(0, radius - inset), edge);
    }

    @Override public void setAlpha(int a) {
        alpha = a;
        invalidateSelf();
    }

    @Override public void setColorFilter(ColorFilter cf) { }

    @Override public int getOpacity() {
        return PixelFormat.TRANSLUCENT;
    }
}
