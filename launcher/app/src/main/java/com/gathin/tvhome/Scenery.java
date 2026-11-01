package com.gathin.tvhome;

import android.graphics.Bitmap;
import android.graphics.BlurMaskFilter;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RadialGradient;
import android.graphics.Shader;

import java.util.Random;

/**
 * The built-in still scene for Scenic Home when no aerial video and no wallpaper are on the TV: a night sky
 * over layered ridges with a soft aurora, painted once in code (no bundled photo, nothing downloaded). Drawn
 * off the UI thread at a fraction of the screen size and shown centre-cropped; its blurred copy is the glass
 * scene. Deterministic, so it looks the same every start.
 */
final class Scenery {
    private Scenery() { }

    static Bitmap paint(int w, int h) {
        Bitmap b = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(b);
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setShader(new LinearGradient(0, 0, 0, h,
                new int[]{0xFF060B1E, 0xFF0E1E4A, 0xFF27407E, 0xFF5B4A8F, 0xFF8C5A86},
                new float[]{0f, 0.35f, 0.62f, 0.8f, 1f}, Shader.TileMode.CLAMP));
        c.drawRect(0, 0, w, h, p);
        p.setShader(null);

        Random r = new Random(20211);
        for (int i = 0; i < w * h / 2600; i++) {
            float x = r.nextFloat() * w, y = r.nextFloat() * h * 0.62f;
            float s = 0.4f + r.nextFloat() * (r.nextFloat() < 0.08f ? 1.6f : 0.8f);
            p.setColor(((int) (90 + r.nextFloat() * 165) << 24) | 0xFFFFFF);
            c.drawCircle(x, y, s * w / 960f, p);
        }

        // Aurora: two wide soft ribbons of cyan-green and magenta.
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeCap(Paint.Cap.ROUND);
        p.setMaskFilter(new BlurMaskFilter(w * 0.035f, BlurMaskFilter.Blur.NORMAL));
        drawRibbon(c, p, w, h, 0.30f, 0.06f, w * 0.10f, 0x7038E8C8);
        drawRibbon(c, p, w, h, 0.22f, 0.05f, w * 0.06f, 0x5566A6FF);
        drawRibbon(c, p, w, h, 0.38f, 0.04f, w * 0.05f, 0x40D45AD8);
        p.setMaskFilter(null);
        p.setStyle(Paint.Style.FILL);

        // Low glow on the horizon.
        p.setShader(new RadialGradient(w * 0.62f, h * 0.78f, w * 0.55f, 0x55E89AC0, 0x00E89AC0, Shader.TileMode.CLAMP));
        c.drawRect(0, 0, w, h, p);
        p.setShader(null);

        ridge(c, p, w, h, 0.70f, 0.10f, 0xFF1C2350, 7);
        ridge(c, p, w, h, 0.78f, 0.08f, 0xFF121733, 11);
        ridge(c, p, w, h, 0.88f, 0.05f, 0xFF0A0D1F, 5);
        return b;
    }

    private static void drawRibbon(Canvas c, Paint p, int w, int h, float y, float amp, float width, int color) {
        Path path = new Path();
        path.moveTo(-w * 0.1f, h * (y + amp));
        path.cubicTo(w * 0.25f, h * (y - amp * 2), w * 0.55f, h * (y + amp * 2.5f), w * 1.1f, h * (y - amp));
        p.setStrokeWidth(width);
        p.setColor(color);
        c.drawPath(path, p);
    }

    private static void ridge(Canvas c, Paint p, int w, int h, float base, float amp, int color, long seed) {
        Random r = new Random(seed);
        Path path = new Path();
        path.moveTo(0, h);
        int n = 9;
        for (int i = 0; i <= n; i++) {
            float x = w * i / (float) n;
            float y = h * (base - amp * r.nextFloat());
            path.lineTo(x, y);
        }
        path.lineTo(w, h);
        path.close();
        p.setColor(color);
        c.drawPath(path, p);
    }
}
