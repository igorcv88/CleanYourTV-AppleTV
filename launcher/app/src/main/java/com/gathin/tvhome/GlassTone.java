package com.gathin.tvhome;

/**
 * The numbers behind Scenic Glass's one material, kept free of Android types so they run on the JVM.
 *
 * <p>A glass surface is: a share of the pre-blurred scene (never all of it, so the real wallpaper or aerial
 * still reads through), one adaptive tint, a sheen and a hairline rim. The tint adapts to how bright the scene
 * behind is: over a bright scene it is a smoked veil that keeps white text legible; over a dark scene it is
 * a faint white lift that gives the glass presence without turning milky. Both are blended analytically into
 * a single colour so a surface never stacks two tint fills.
 */
final class GlassTone {
    private GlassTone() { }

    /** What a surface is for. Larger surfaces carry more material; controls stay lighter. */
    enum Kind {
        /** The dock: wide, low, holds coloured cards; the scenery must stay recognisable through it. */
        CHROME(0.46f, 0x0A, 0x40, 0.30f),
        /** Text-bearing panels (Settings, Control Center, menus, secondary screens). */
        PANEL(0.62f, 0x18, 0x6A, 0.38f),
        /** Round buttons and capsules sitting directly on the scene. */
        CONTROL(0.40f, 0x08, 0x46, 0.22f),
        /** Platters inside a panel: no second scene sample (glass on glass turns opaque), just a lift. */
        INSET(0f, 0x00, 0x00, 0f);

        /** Share of the blurred scene sample drawn over the live background, 0..1. */
        final float sampleShare;
        /** Smoke alpha over a dark scene and over a bright one. */
        final int smokeDark, smokeBright;
        /** Outline alpha for the elevation shadow: a full shadow shows through translucent glass as grey haze. */
        final float shadowAlpha;

        Kind(float sampleShare, int smokeDark, int smokeBright, float shadowAlpha) {
            this.sampleShare = sampleShare;
            this.smokeDark = smokeDark;
            this.smokeBright = smokeBright;
            this.shadowAlpha = shadowAlpha;
        }
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
        if (kind == Kind.INSET) {
            int a = focused ? 0x34 : 0x14;
            return (a << 24) | 0xFFFFFF;
        }
        float l = lum < 0f ? 0.5f : Math.max(0f, Math.min(1f, lum));
        float bright = smoothstep(0.32f, 0.78f, l);
        float smoke = kind.smokeDark + (kind.smokeBright - kind.smokeDark) * bright;
        // White lift: present over dark scenes, almost gone over bright ones (where it would be milk).
        float white = 0x16 - 0x10 * bright;
        if (focused) {
            white += 0x1E;
            smoke *= 0.8f;
        }
        if (frosted) smoke += 0x22;
        return over(Math.round(white), 0xFFFFFF, Math.round(smoke), SMOKE_RGB);
    }

    /** Share of the blurred sample for this surface; frosted glass carries a little more of it. */
    static float sampleShare(Kind kind, boolean frosted) {
        return Math.min(0.85f, kind.sampleShare + (frosted && kind != Kind.INSET ? 0.18f : 0f));
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
