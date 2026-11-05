package com.gathin.tvhome;

/**
 * The numbers behind Scenic Glass's one material, kept free of Android types so they run on the JVM.
 *
 * <p>A glass surface is: the frosted scene ({@link Blur#GLASS_SIDE}, about a 50 px blur) drawn almost
 * opaque, one adaptive tint, a refracting lens band at the rim, a sheen and a specular edge. The frost is
 * of the live scene, so its colour and light move with it: it reads as clear glass that diffuses what is
 * behind, not a milky slab (an old, featureless ~90 px smear) nor nothing at all (a faint share of a stale
 * sample, which let the sharp UI and video show straight through).
 *
 * <p>The tint adapts to how bright the scene behind is: over a bright scene a smoked veil that keeps white
 * text legible, over a dark one a faint white lift. Both are blended analytically into a single colour so a
 * surface never stacks two tint fills.
 */
final class GlassTone {
    private GlassTone() { }

    /** What a surface is for. Larger surfaces carry more material; controls stay lighter. */
    enum Kind {
        /** The dock: wide, low, holds coloured cards. */
        CHROME(0.97f, 0x00, 0x0C, 0.28f, true),
        /** Text-bearing panels (Settings, Control Center, menus, secondary screens): hide what is behind. */
        PANEL(0.98f, 0x12, 0x4C, 0.34f, true),
        /** Round buttons and capsules sitting directly on the scene. */
        CONTROL(0.97f, 0x00, 0x0C, 0.22f, true),
        /**
         * Controls inside a panel (Control Center capsules, platters): the same frost again, clear, with its own
         * lens. Frost of the same scene on frost stays glass; a white lift made them milky.
         */
        INSET(0.95f, 0x00, 0x00, 0f, true),
        /** The full-screen veil under All Apps over a moving aerial: frost so the icons read, no rim. */
        BACKDROP(1f, 0x22, 0x46, 0f, false);

        /** Share of the frosted scene sample drawn over the live background, 0..1. */
        final float sampleShare;
        /** Smoke alpha over a dark scene and over a bright one. */
        final int smokeDark, smokeBright;
        /** Outline alpha for the elevation shadow: a full shadow shows through translucent glass as grey haze. */
        final float shadowAlpha;
        /** Draws the refracting band and the specular edge. */
        final boolean lens;

        Kind(float sampleShare, int smokeDark, int smokeBright, float shadowAlpha, boolean lens) {
            this.sampleShare = sampleShare;
            this.smokeDark = smokeDark;
            this.smokeBright = smokeBright;
            this.shadowAlpha = shadowAlpha;
            this.lens = lens;
        }
    }

    /**
     * The rim's lens: the band of glass along the edge shows the scene magnified by this factor about the
     * surface's centre, so the scenery visibly bends where it meets the edge, as thick glass does.
     */
    static final float LENS_MAGNIFY = 1.08f;

    /** Width of the lens band: a share of the surface's short side, within [min, max] dp. */
    static float lensBandDp(float shortSideDp) {
        return Math.max(2.5f, Math.min(6f, shortSideDp * 0.1f));
    }

    /** Luminance when nothing has been sampled yet (an aerial still starting): treated as a mid scene. */
    static final float UNKNOWN = -1f;

    /** Smoke colour: a cool, slightly blue near-black, not neutral grey (grey reads as dirty glass). */
    static final int SMOKE_RGB = 0x0E1420;

    /** Rec. 709 relative luminance of an opaque ARGB pixel, 0..1 (gamma-encoded approximation, adequate here). */
    static float luminance(int argb) {
        int r = (argb >> 16) & 0xFF, g = (argb >> 8) & 0xFF, b = argb & 0xFF;
        return (0.2126f * r + 0.7152f * g + 0.0722f * b) / 255f;
    }

    /** Mean luminance of a pixel buffer, sampling at most ~1024 pixels. 0.5 for an empty buffer. */
    static float meanLuminance(int[] px) {
        if (px == null || px.length == 0) return 0.5f;
        int step = Math.max(1, px.length / 1024);
        float sum = 0f;
        int n = 0;
        for (int i = 0; i < px.length; i += step) {
            sum += luminance(px[i]);
            n++;
        }
        return sum / n;
    }

    static float smoothstep(float edge0, float edge1, float x) {
        float t = Math.max(0f, Math.min(1f, (x - edge0) / (edge1 - edge0)));
        return t * t * (3f - 2f * t);
    }

    /**
     * The single tint colour (ARGB, straight alpha) for a surface.
     *
     * @param lum      mean scene luminance 0..1, or {@link #UNKNOWN}
     * @param frosted  the user's "Frosted" glass choice: more material, for very busy scenes
     */
    static int tint(Kind kind, float lum, boolean focused, boolean frosted) {
        // Inset glass (capsules, platters, focused rows) is the frost alone: any white tint read as milk.
        if (kind == Kind.INSET) return 0;
        float l = lum < 0f ? 0.5f : Math.max(0f, Math.min(1f, lum));
        float bright = smoothstep(0.32f, 0.78f, l);
        float smoke = kind.smokeDark + (kind.smokeBright - kind.smokeDark) * bright;
        // No white lift anywhere: it read as frost in the middle of clear glass. Panels keep only dark smoke.
        float white = 0f;
        // Focus brightens the glass itself (SceneGlassDrawable's focus filter), never a white veil over it.
        if (focused) smoke *= 0.6f;
        if (frosted) smoke += 0x1C;
        return over(Math.round(white), 0xFFFFFF, Math.round(smoke), SMOKE_RGB);
    }

    /** Share of the frosted sample for this surface; frosted glass carries all of it. */
    static float sampleShare(Kind kind, boolean frosted) {
        return kind.sampleShare <= 0f ? 0f : Math.min(1f, kind.sampleShare + (frosted ? 0.06f : 0f));
    }

    /**
     * Tint when there is no sample at all yet (an aerial's first half second): a little more smoke, so the
     * surface never reads as a bare outline over sharp video.
     */
    static int fallbackTint(Kind kind, float lum, boolean focused, boolean frosted) {
        int t = tint(kind, lum, focused, frosted);
        if (kind == Kind.INSET) return t;
        return over(t >>> 24, t & 0xFFFFFF, 0x30, SMOKE_RGB);
    }

    /** Porter-Duff "top over bottom" of two flat colours with straight 0..255 alphas, as one ARGB colour. */
    static int over(int topA, int topRgb, int bottomA, int bottomRgb) {
        float at = clamp255(topA) / 255f, ab = clamp255(bottomA) / 255f;
        float a = at + ab * (1f - at);
        if (a <= 0f) return 0;
        int r = mix(topRgb >> 16, bottomRgb >> 16, at, ab, a);
        int g = mix(topRgb >> 8, bottomRgb >> 8, at, ab, a);
        int b = mix(topRgb, bottomRgb, at, ab, a);
        return (Math.round(a * 255f) << 24) | (r << 16) | (g << 8) | b;
    }

    private static int mix(int top, int bottom, float at, float ab, float a) {
        float c = ((top & 0xFF) * at + (bottom & 0xFF) * ab * (1f - at)) / a;
        return Math.max(0, Math.min(255, Math.round(c)));
    }

    private static int clamp255(int v) {
        return Math.max(0, Math.min(255, v));
    }
}
