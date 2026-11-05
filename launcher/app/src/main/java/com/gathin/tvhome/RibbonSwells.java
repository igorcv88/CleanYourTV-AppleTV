package com.gathin.tvhome;

/**
 * The waves that rise from the played part of the progress line while music plays, after Samsung Music's
 * player: a few filled, overlapping translucent layers (a pale neutral one behind, then the cover's colours),
 * each a soft mountain range of independent swells that pulse at their own tempo and drift along the line.
 * The line itself never moves: the waves are drawn on it, inside the played span, and sink to nothing at both
 * ends so they never cross the playhead.
 *
 * <p>This is decoration, not audio analysis: nothing here reads Spotify's samples (that would need audio
 * capture, a new permission and work on the audio path). Heights are a pure function of position and time,
 * so they are deterministic, allocation-free and tested on the JVM.
 */
final class RibbonSwells {
    private RibbonSwells() { }

    /** Layer 0 is the pale neutral one at the back; 1..3 take the cover's palette, front last. */
    static final int LAYERS = 4;
    /** Tallest a wave may stand above the line, in dp (the line is 4dp thick): Samsung's stand about six lines tall. */
    static final float MAX_HEIGHT_DP = 26f;
    /** The played span over which the waves reach their full height; a shorter one gets smaller waves. */
    static final float FULL_SPAN_DP = 180f;

    private static final int BUMPS = 3;
    /** Where each layer's swells sit, as a share of the played span: spread over it, offset between layers. */
    private static final float[][] CENTRE = {
            {0.30f, 0.62f, 0.86f},
            {0.20f, 0.52f, 0.80f},
            {0.40f, 0.70f, 0.92f},
            {0.13f, 0.46f, 0.74f},
    };
    /** Half-width of each swell, as a share of the played span. */
    private static final float[][] WIDTH = {
            {0.19f, 0.16f, 0.12f},
            {0.15f, 0.17f, 0.11f},
            {0.13f, 0.12f, 0.10f},
            {0.12f, 0.13f, 0.10f},
    };
    /** Pulse periods (ms): brisk, a little apart so the layers never beat together. */
    private static final int[][] PULSE_MS = {
            {1180, 1430, 970},
            {860, 1090, 760},
            {930, 700, 1210},
            {780, 990, 840},
    };
    /** Drift periods (ms): the swells wander slowly along the line. */
    private static final int[][] DRIFT_MS = {
            {7300, 9100, 6100},
            {6700, 8300, 5900},
            {7900, 6400, 8800},
            {5600, 7700, 6900},
    };
    /** Peak height of each layer relative to MAX_HEIGHT_DP: the neutral back layer is the tallest. */
    private static final float[] LAYER_PEAK = {0.95f, 0.85f, 0.68f, 0.52f};

    /** Per-frame state: each swell's centre, half-width and amplitude, which depend only on time. */
    static final class Frame {
        final float[] centre = new float[LAYERS * BUMPS];
        final float[] half = new float[LAYERS * BUMPS];
        final float[] amp = new float[LAYERS * BUMPS];
    }

    /** Fills {@code out} for {@code nowMs}: once per drawn frame, not per point. */
    static void frame(long nowMs, Frame out) {
        double t = Math.max(0L, nowMs);
        for (int l = 0; l < LAYERS; l++) {
            for (int k = 0; k < BUMPS; k++) {
                int i = l * BUMPS + k;
                double phase = (l * 3 + k) * 1.7;
                out.centre[i] = CENTRE[l][k] + 0.06f * (float) Math.sin(2 * Math.PI * t / DRIFT_MS[l][k] + phase);
                out.half[i] = WIDTH[l][k]
                        * (0.85f + 0.3f * (float) Math.sin(2 * Math.PI * t / (DRIFT_MS[l][k] * 1.3) + phase));
                float beat = 0.5f + 0.5f * (float) Math.sin(2 * Math.PI * t / PULSE_MS[l][k] + phase);
                out.amp[i] = 0.42f + 0.58f * beat * beat;
            }
        }
    }

    /**
     * Height in dp of {@code layer} at {@code u} (0 at the start of the played span, 1 at the playhead).
     *
     * @param level 0..1 overall presence: eases in on play and out on pause, so nothing pops
     * @param room  0..1 scale for a short played span (see {@link #room})
     */
    static float height(Frame f, int layer, float u, float level, float room) {
        if (level <= 0f || room <= 0f || u <= 0f || u >= 1f) return 0f;
        int l = Math.max(0, Math.min(LAYERS - 1, layer));
        float sum = 0f;
        for (int k = 0; k < BUMPS; k++) {
            int i = l * BUMPS + k;
            float d = (u - f.centre[i]) / f.half[i];
            sum += f.amp[i] * (float) Math.exp(-d * d);
        }
        return soft(sum) * LAYER_PEAK[l] * MAX_HEIGHT_DP * taper(u) * Math.min(1f, level) * Math.min(1f, room);
    }

    /** As {@link #height(Frame, int, float, float, float)} at {@code nowMs}; for tests (allocates a Frame). */
    static float height(int layer, float u, long nowMs, float level, float room) {
        Frame f = new Frame();
        frame(nowMs, f);
        return height(f, layer, u, level, room);
    }

    /** Overlapping swells merge into a rounded crest instead of being clipped flat; never above 1. */
    static float soft(float sum) {
        if (sum <= 0.75f) return Math.max(0f, sum);
        return 0.75f + 0.25f * (float) Math.tanh((sum - 0.75f) / 0.25f);
    }

    /** Waves fade in from the start of the line and out before the playhead, with zero slope at both. */
    static float taper(float u) {
        return GlassTone.smoothstep(0f, 0.07f, u) * (1f - GlassTone.smoothstep(0.93f, 1f, u));
    }

    /** Scale for a played span of {@code spanDp}: full waves from FULL_SPAN_DP, none under 12dp. */
    static float room(float spanDp) {
        if (spanDp < 12f) return 0f;
        return Math.min(1f, spanDp / FULL_SPAN_DP);
    }
}
