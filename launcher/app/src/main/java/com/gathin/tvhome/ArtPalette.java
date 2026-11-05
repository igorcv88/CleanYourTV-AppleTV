package com.gathin.tvhome;

/**
 * A small, harmonious palette from album artwork for the progress ribbon: up to three hues, weighted by how
 * vivid each pixel is, pulled to a common brightness so no colour shouts. No Palette dependency; works on a
 * plain pixel array, so it runs on the JVM.
 */
final class ArtPalette {
    private ArtPalette() { }

    /** The ribbon's colours when there is no artwork (or it is grey): a cool glass blue and a soft violet. */
    static final int[] DEFAULT = {0xFFA9D7F4, 0xFFB9B4F2, 0xFF9FE3DA};

    private static final int BUCKETS = 12;            // 30° hue sectors
    private static final float MIN_WEIGHT = 0.6f;     // below this the art is effectively monochrome
    private static final float SATURATION = 0.50f, VALUE = 1f;

    /**
     * @param px opaque ARGB pixels (any sampling of the artwork)
     * @return three colours, strongest first; a monochrome cover gets {@link #DEFAULT}-like neighbours of its
     *         own dominant hue, or DEFAULT itself when it has no hue at all
     */
    static int[] from(int[] px) {
        if (px == null || px.length == 0) return DEFAULT.clone();
        float[] weight = new float[BUCKETS], sumX = new float[BUCKETS], sumY = new float[BUCKETS];
        float[] hsv = new float[3];
        float total = 0f;
        for (int p : px) {
            toHsv(p, hsv);
            float w = hsv[1] * hsv[2];
            if (w < 0.08f) continue;   // near-grey or near-black pixels carry no usable hue
            int b = Math.min(BUCKETS - 1, (int) (hsv[0] / (360f / BUCKETS)));
            double rad = Math.toRadians(hsv[0]);
            weight[b] += w;
            sumX[b] += (float) Math.cos(rad) * w;
            sumY[b] += (float) Math.sin(rad) * w;
            total += w;
        }
        if (total < MIN_WEIGHT) return DEFAULT.clone();
        int[] out = new int[3];
        int found = 0;
        boolean[] used = new boolean[BUCKETS];
        for (int k = 0; k < 3; k++) {
            int best = -1;
            for (int b = 0; b < BUCKETS; b++) {
                if (used[b] || weight[b] <= 0f) continue;
                // Skip a direct neighbour of a chosen hue: the palette should show more than one colour.
                if (k > 0 && adjacentToUsed(used, b)) continue;
                if (best < 0 || weight[b] > weight[best]) best = b;
            }
            if (best < 0 || (k > 0 && weight[best] < total * 0.06f)) break;
            used[best] = true;
            float h = (float) Math.toDegrees(Math.atan2(sumY[best], sumX[best]));
            out[found++] = fromHsv((h + 360f) % 360f, SATURATION, VALUE);
        }
        // Too few distinct hues: fill with gentle analogous shifts of the strongest one.
        float[] base = new float[3];
        toHsv(out[0], base);
        while (found < 3) {
            float shift = found == 1 ? 32f : -28f;
            out[found++] = fromHsv((base[0] + shift + 360f) % 360f, SATURATION * 0.9f, VALUE);
        }
        return out;
    }

    private static boolean adjacentToUsed(boolean[] used, int b) {
        return used[(b + 1) % BUCKETS] || used[(b + BUCKETS - 1) % BUCKETS];
    }

    /** Linear blend of two opaque colours, t in 0..1. */
    static int lerp(int from, int to, float t) {
        float u = Math.max(0f, Math.min(1f, t));
        int a = Math.round(((from >>> 24) & 0xFF) + (((to >>> 24) & 0xFF) - ((from >>> 24) & 0xFF)) * u);
        int r = Math.round(((from >> 16) & 0xFF) + (((to >> 16) & 0xFF) - ((from >> 16) & 0xFF)) * u);
        int g = Math.round(((from >> 8) & 0xFF) + (((to >> 8) & 0xFF) - ((from >> 8) & 0xFF)) * u);
        int b = Math.round((from & 0xFF) + ((to & 0xFF) - (from & 0xFF)) * u);
        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    /** Mixes a colour toward white by {@code amount} (0 = unchanged, 1 = white), keeping alpha. */
    static int toward(int argb, int target, float amount) {
        return lerp(argb, (argb & 0xFF000000) | (target & 0x00FFFFFF), amount);
    }

    static void toHsv(int argb, float[] out) {
        float r = ((argb >> 16) & 0xFF) / 255f, g = ((argb >> 8) & 0xFF) / 255f, b = (argb & 0xFF) / 255f;
        float max = Math.max(r, Math.max(g, b)), min = Math.min(r, Math.min(g, b)), d = max - min;
        float h;
        if (d == 0f) h = 0f;
        else if (max == r) h = 60f * (((g - b) / d) % 6f);
        else if (max == g) h = 60f * (((b - r) / d) + 2f);
        else h = 60f * (((r - g) / d) + 4f);
        if (h < 0f) h += 360f;
        out[0] = h;
        out[1] = max == 0f ? 0f : d / max;
        out[2] = max;
    }

    static int fromHsv(float h, float s, float v) {
        float c = v * s, x = c * (1f - Math.abs((h / 60f) % 2f - 1f)), m = v - c;
        float r, g, b;
        if (h < 60f) { r = c; g = x; b = 0f; }
        else if (h < 120f) { r = x; g = c; b = 0f; }
        else if (h < 180f) { r = 0f; g = c; b = x; }
        else if (h < 240f) { r = 0f; g = x; b = c; }
        else if (h < 300f) { r = x; g = 0f; b = c; }
        else { r = c; g = 0f; b = x; }
        return 0xFF000000 | (Math.round((r + m) * 255f) << 16) | (Math.round((g + m) * 255f) << 8)
                | Math.round((b + m) * 255f);
    }
}
