package com.gathin.tvhome;

import java.util.Locale;

/**
 * Glass Current's arithmetic, kept free of Android types so it is tested on the JVM. Positions come from a
 * session's PlaybackState (position, lastPositionUpdateTime, speed) and are extrapolated only while playing,
 * then clamped to [0, duration]. Unknown values (negative position, zero or negative duration) stay unknown:
 * no remaining time and no progress fraction are invented for them.
 */
final class MediaTime {
    private MediaTime() { }

    static final long UNKNOWN = -1L;
    /** One Left/Right press on the focused ribbon. */
    static final long SEEK_STEP_MS = 15_000L;

    // android.media.session.PlaybackState values (stable public API constants).
    static final int STATE_NONE = 0, STATE_STOPPED = 1, STATE_PAUSED = 2, STATE_PLAYING = 3,
            STATE_FAST_FORWARDING = 4, STATE_REWINDING = 5, STATE_BUFFERING = 6, STATE_ERROR = 7;
    static final long ACTION_STOP = 1L, ACTION_PAUSE = 1L << 1, ACTION_PLAY = 1L << 2, ACTION_REWIND = 1L << 3,
            ACTION_SKIP_TO_PREVIOUS = 1L << 4, ACTION_SKIP_TO_NEXT = 1L << 5, ACTION_FAST_FORWARD = 1L << 6,
            ACTION_SEEK_TO = 1L << 8, ACTION_PLAY_PAUSE = 1L << 9;

    static boolean advancing(int state) {
        return state == STATE_PLAYING || state == STATE_FAST_FORWARDING || state == STATE_REWINDING;
    }

    /** The position now, in ms, or UNKNOWN. */
    static long position(int state, long position, long updateTime, float speed, long now, long duration) {
        if (position < 0) return UNKNOWN;
        long p = position;
        if (advancing(state) && updateTime > 0 && now > updateTime) {
            p += (long) ((now - updateTime) * (double) speed);
        }
        if (p < 0) p = 0;
        if (duration > 0 && p > duration) p = duration;
        return p;
    }

    static long remaining(long position, long duration) {
        if (position < 0 || duration <= 0) return UNKNOWN;
        return Math.max(0, duration - Math.min(position, duration));
    }

    /** 0..1 of the way through, or -1 when either value is unknown. */
    static float fraction(long position, long duration) {
        if (position < 0 || duration <= 0) return -1f;
        return Math.max(0f, Math.min(1f, position / (float) duration));
    }

    /** Where a seek by delta lands: never before 0, never past a known duration. */
    static long seekTarget(long position, long delta, long duration) {
        long p = Math.max(0, position) + delta;
        if (p < 0) p = 0;
        if (duration > 0 && p > duration) p = duration;
        return p;
    }

    /** m:ss, or h:mm:ss from an hour; "" for unknown. */
    static String format(long ms) {
        if (ms < 0) return "";
        long s = ms / 1000;
        long h = s / 3600, m = (s % 3600) / 60, sec = s % 60;
        return h > 0 ? String.format(Locale.US, "%d:%02d:%02d", h, m, sec) : String.format(Locale.US, "%d:%02d", m, sec);
    }

    /** Remaining time with a minus sign ("-2:41"), "" when unknown. */
    static String formatRemaining(long position, long duration) {
        long r = remaining(position, duration);
        return r < 0 ? "" : "-" + format(r);
    }

    static boolean can(long actions, long flag) {
        return (actions & flag) != 0;
    }

    /** Seeking needs the session's SEEK_TO action and a known duration (the ribbon needs both ends). */
    static boolean canSeek(long actions, long duration) {
        return can(actions, ACTION_SEEK_TO) && duration > 0;
    }

    /** The play/pause button shows only when the session can do what it would do now. */
    static boolean canToggle(long actions, int state) {
        if (can(actions, ACTION_PLAY_PAUSE)) return true;
        return advancing(state) || state == STATE_BUFFERING ? can(actions, ACTION_PAUSE) : can(actions, ACTION_PLAY);
    }

    /** Worth showing on Home: playing, buffering or paused, with a title. */
    static boolean showable(int state, String title) {
        return title != null && !title.trim().isEmpty()
                && (advancing(state) || state == STATE_PAUSED || state == STATE_BUFFERING);
    }
}
