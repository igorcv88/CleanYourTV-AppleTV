package com.gathin.tvhome;

import android.graphics.Bitmap;
import android.view.View;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * What lies behind Scenic Home's glass, as one small pre-blurred bitmap in the screen's centre-crop model
 * ({@link SceneCrop}): the still wallpaper, a recent frame of the aerial (refreshed a few times a second by
 * {@link AerialBackdrop}), or the album art while music plays. Glass surfaces register and are invalidated
 * only when the scene changes.
 */
final class Scene {
    private Scene() { }

    private static Bitmap still, music;
    private static float stillLum = GlassTone.UNKNOWN, musicLum = GlassTone.UNKNOWN;
    private static final List<WeakReference<View>> hosts = new ArrayList<>();

    /** The current blurred scene, or null. */
    static Bitmap current() {
        return music != null ? music : still;
    }

    /** Mean luminance of {@link #current()}, 0..1, or {@link GlassTone#UNKNOWN}: drives the adaptive tint. */
    static float luminance() {
        return music != null ? musicLum : still != null ? stillLum : GlassTone.UNKNOWN;
    }

    static void setStill(Bitmap blurred) {
        setStill(blurred, blurred == still ? stillLum : measure(blurred));
    }

    /** A sample whose luminance was measured where it was made (the aerial's worker): nothing scanned here. */
    static void setStill(Bitmap blurred, float luminance) {
        still = blurred;
        stillLum = luminance;
        invalidateAll();
    }

    static void setMusic(Bitmap blurred) {
        if (music == blurred) return;
        music = blurred;
        musicLum = measure(blurred);
        invalidateAll();
    }

    /** Once per scene change, on a bitmap of a few thousand pixels: never per frame. */
    private static float measure(Bitmap b) {
        if (b == null || b.isRecycled() || b.getConfig() == Bitmap.Config.HARDWARE) return GlassTone.UNKNOWN;
        try {
            int w = b.getWidth(), h = b.getHeight();
            if (w <= 0 || h <= 0 || (long) w * h > 65536L) return GlassTone.UNKNOWN;
            int[] px = new int[w * h];
            b.getPixels(px, 0, w, 0, 0, w, h);
            return GlassTone.meanLuminance(px);
        } catch (RuntimeException e) {
            return GlassTone.UNKNOWN;
        }
    }

    static void register(View v) {
        hosts.add(new WeakReference<>(v));
    }

    static void invalidateAll() {
        for (Iterator<WeakReference<View>> it = hosts.iterator(); it.hasNext(); ) {
            View v = it.next().get();
            if (v == null) it.remove(); else v.invalidate();
        }
    }

    static void reset() {
        still = null;
        music = null;
        stillLum = GlassTone.UNKNOWN;
        musicLum = GlassTone.UNKNOWN;
        hosts.clear();
    }
}
