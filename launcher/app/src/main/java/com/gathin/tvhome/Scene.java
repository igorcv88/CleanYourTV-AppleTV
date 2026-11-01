package com.gathin.tvhome;

import android.graphics.Bitmap;
import android.view.View;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * What lies behind Scenic Home's glass, as one small pre-blurred bitmap in the screen's centre-crop model
 * ({@link SceneCrop}): the still wallpaper, or the album art while music plays. Null over a moving aerial,
 * where glass falls back to tint and rim only (no live capture of video). Glass surfaces register and are
 * invalidated only when the scene changes.
 */
final class Scene {
    private Scene() { }

    private static Bitmap still, music;
    private static final List<WeakReference<View>> hosts = new ArrayList<>();

    /** The current blurred scene, or null. */
    static Bitmap current() {
        return music != null ? music : still;
    }

    static void setStill(Bitmap blurred) {
        still = blurred;
        invalidateAll();
    }

    static void setMusic(Bitmap blurred) {
        if (music == blurred) return;
        music = blurred;
        invalidateAll();
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
        hosts.clear();
    }
}
