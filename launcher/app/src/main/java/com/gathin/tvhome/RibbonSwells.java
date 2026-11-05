package com.gathin.tvhome;

/**
 * The procedural "swells" that rise from the played part of the progress ribbon while music plays: several
 * small, independent luminous mounds with their own width, height, period, drift and colour. Each one is born
 * from the line, grows, drifts a little and sinks back, then reappears somewhere else. They never form one
 * continuous sine and never touch the line's geometry.
 *
 * <p>This is decoration, not audio analysis: nothing here reads Spotify's samples (that would need audio
 * capture, a new permission and work on the audio path). The motion is a pure function of time, so it is
 * deterministic, allocation-free and tested on the JVM.
 */
final class RibbonSwells {
    private RibbonSwells() { }

    static final int COUNT = 5;
    /** Tallest swell, in dp above the line. Discreet on purpose. */
    static final float MAX_HEIGHT_DP = 9f;
    static final float MIN_WIDTH_DP = 20f, MAX_WIDTH_DP = 54f;

    private static final long[] PERIOD_MS = {3400L, 4100L, 4700L, 3800L, 5300L};
    private static final long[] OFFSET_MS = {0L, 1370L, 2810L, 650L, 3900L};

    /** One swell at one instant. {@code centre} is a fraction 0..1 of the played region. */
    static final class Swell {
        float centre, widthDp, heightDp, envelope;
        int colour;
    }

    /**
     * Fills {@code out} with swell {@code i} at time {@code nowMs}.
     *
     * @param level 0..1 overall presence: eases in on play and out on pause, so nothing pops
     */
    static void at(int i, long nowMs, float level, Swell out) {
        long period = PERIOD_MS[i % PERIOD_MS.length];
        long t = Math.max(0L, nowMs) + OFFSET_MS[i % OFFSET_MS.length];
        long cycle = t / period;
        float life = (t % period) / (float) period;
        // sin² rises from and returns to zero with zero slope: a swell is born from, and melts into, the line.
        float s = (float) Math.sin(Math.PI * life);
        float envelope = s * s * Math.max(0f, Math.min(1f, level));
        float drift = (life - 0.5f) * 0.07f;
        out.centre = clamp(0.1f + 0.8f * hash(cycle, i, 1) + drift, 0.04f, 0.96f);
        out.widthDp = MIN_WIDTH_DP + (MAX_WIDTH_DP - MIN_WIDTH_DP) * hash(cycle, i, 2);
        out.heightDp = MAX_HEIGHT_DP * (0.4f + 0.6f * hash(cycle, i, 3)) * envelope;
        out.envelope = envelope;
        out.colour = (int) ((i + cycle) % 3);
    }

    /** Deterministic 0..1 noise for (cycle, swell, channel). */
    static float hash(long cycle, int i, int channel) {
        long h = cycle * 0x9E3779B97F4A7C15L + i * 0xC2B2AE3D27D4EB4FL + channel * 0x165667B19E3779F9L;
        h ^= h >>> 33;
        h *= 0xFF51AFD7ED558CCDL;
        h ^= h >>> 33;
        return ((h >>> 40) & 0xFFFFFF) / (float) 0x1000000;
    }

    private static float clamp(float v, float lo, float hi) {
        return Math.max(lo, Math.min(hi, v));
    }
}
