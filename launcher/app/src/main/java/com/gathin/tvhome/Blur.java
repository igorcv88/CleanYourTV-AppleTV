package com.gathin.tvhome;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;

/**
 * A cached, once-per-scene blur: the image is drawn down to a few dozen pixels, box-blurred three times
 * (close to a gaussian) on the CPU, and the small result is drawn back up with bilinear filtering, which
 * smooths it further for free. Runs off the UI thread when a scene changes, never per frame. The pixel pass
 * works on an int[] so it is tested on the JVM. Approach after GlassLauncher (MIT, tlfares/GlassLauncher,
 * gfx/Blur.kt): blur a small cached copy once and sample it, never the live screen.
 */
final class Blur {
    private Blur() { }

    /** Downsamples src to fit maxSide (aspect kept) and blurs it; a new bitmap, src untouched. */
    static Bitmap blurred(Bitmap src, int maxSide, int radius) {
        int w = src.getWidth(), h = src.getHeight();
        float s = Math.min(1f, maxSide / (float) Math.max(w, h));
        int sw = Math.max(2, Math.round(w * s)), sh = Math.max(2, Math.round(h * s));
        Bitmap small = Bitmap.createBitmap(sw, sh, Bitmap.Config.ARGB_8888);
        new Canvas(small).drawBitmap(src, new Rect(0, 0, w, h), new Rect(0, 0, sw, sh), new Paint(Paint.FILTER_BITMAP_FLAG));
        int[] px = new int[sw * sh];
        small.getPixels(px, 0, sw, 0, 0, sw, sh);
        boxBlur(px, sw, sh, radius);
        small.setPixels(px, 0, sw, 0, 0, sw, sh);
        return small;
    }

    /** Three horizontal + vertical box passes of this radius, in place, opaque output. */
    static void boxBlur(int[] px, int w, int h, int radius) {
        if (radius < 1 || w < 1 || h < 1) return;
        int[] tmp = new int[px.length];
        for (int pass = 0; pass < 3; pass++) {
            boxLine(px, tmp, w, h, radius, true);
            boxLine(tmp, px, w, h, radius, false);
        }
    }

    private static void boxLine(int[] in, int[] out, int w, int h, int r, boolean horizontal) {
        int lines = horizontal ? h : w, len = horizontal ? w : h;
        int div = 2 * r + 1;
        for (int line = 0; line < lines; line++) {
            int sr = 0, sg = 0, sb = 0;
            for (int i = -r; i <= r; i++) {
                int p = in[index(line, Math.max(0, Math.min(len - 1, i)), w, horizontal)];
                sr += (p >> 16) & 0xFF; sg += (p >> 8) & 0xFF; sb += p & 0xFF;
            }
            for (int i = 0; i < len; i++) {
                out[index(line, i, w, horizontal)] = 0xFF000000 | ((sr / div) << 16) | ((sg / div) << 8) | (sb / div);
                int add = in[index(line, Math.min(len - 1, i + r + 1), w, horizontal)];
                int sub = in[index(line, Math.max(0, i - r), w, horizontal)];
                sr += ((add >> 16) & 0xFF) - ((sub >> 16) & 0xFF);
                sg += ((add >> 8) & 0xFF) - ((sub >> 8) & 0xFF);
                sb += (add & 0xFF) - (sub & 0xFF);
            }
        }
    }

    private static int index(int line, int i, int w, boolean horizontal) {
        return horizontal ? line * w + i : i * w + line;
    }
}
