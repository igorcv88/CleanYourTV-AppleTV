package com.gathin.tvhome;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/** Glass Current's times: extrapolated only while playing, clamped, unknown stays unknown; flags gate controls. */
public class MediaTimeTest {
    @Test public void playingPositionAdvancesWithSpeed() {
        assertEquals(15_000, MediaTime.position(MediaTime.STATE_PLAYING, 10_000, 1_000, 1f, 6_000, 200_000));
        assertEquals(20_000, MediaTime.position(MediaTime.STATE_PLAYING, 10_000, 1_000, 2f, 6_000, 200_000));
    }

    @Test public void pausedPositionDoesNotMove() {
        assertEquals(10_000, MediaTime.position(MediaTime.STATE_PAUSED, 10_000, 1_000, 1f, 60_000, 200_000));
    }

    @Test public void positionIsClampedToTheTrack() {
        assertEquals(200_000, MediaTime.position(MediaTime.STATE_PLAYING, 199_000, 0 + 1, 1f, 10_000, 200_000));
        assertEquals(0, MediaTime.position(MediaTime.STATE_REWINDING, 1_000, 1, -4f, 10_000, 200_000));
    }

    @Test public void unknownPositionAndDurationStayUnknown() {
        assertEquals(MediaTime.UNKNOWN, MediaTime.position(MediaTime.STATE_PLAYING, -1, 1, 1f, 5, 1000));
        assertEquals(MediaTime.UNKNOWN, MediaTime.remaining(5_000, 0));
        assertEquals(MediaTime.UNKNOWN, MediaTime.remaining(-1, 1000));
        assertEquals(-1f, MediaTime.fraction(5_000, 0), 0f);
        assertEquals("", MediaTime.formatRemaining(5_000, 0));
        assertEquals("", MediaTime.format(-1));
        // A live stream with no duration keeps counting up, unclamped.
        assertEquals(70_000, MediaTime.position(MediaTime.STATE_PLAYING, 10_000, 1, 1f, 60_001, 0));
    }

    @Test public void remainingAndFormat() {
        assertEquals(161_000, MediaTime.remaining(39_000, 200_000));
        assertEquals("-2:41", MediaTime.formatRemaining(39_000, 200_000));
        assertEquals("0:39", MediaTime.format(39_999));
        assertEquals("1:02:03", MediaTime.format(3_723_000));
        assertEquals(0.5f, MediaTime.fraction(100, 200), 1e-6f);
        assertEquals(1f, MediaTime.fraction(500, 200), 0f);
    }

    @Test public void seekStepsClamp() {
        assertEquals(25_000, MediaTime.seekTarget(10_000, MediaTime.SEEK_STEP_MS, 200_000));
        assertEquals(0, MediaTime.seekTarget(10_000, -MediaTime.SEEK_STEP_MS, 200_000));
        assertEquals(200_000, MediaTime.seekTarget(195_000, MediaTime.SEEK_STEP_MS, 200_000));
        assertEquals(15_000, MediaTime.seekTarget(-1, MediaTime.SEEK_STEP_MS, 200_000));
    }

    @Test public void controlsFollowTheSessionsActions() {
        long seek = MediaTime.ACTION_SEEK_TO;
        assertTrue(MediaTime.canSeek(seek, 200_000));
        assertFalse(MediaTime.canSeek(seek, 0));                 // no known end: no seek ribbon focus
        assertFalse(MediaTime.canSeek(MediaTime.ACTION_PLAY, 200_000));
        assertTrue(MediaTime.canToggle(MediaTime.ACTION_PAUSE, MediaTime.STATE_PLAYING));
        assertFalse(MediaTime.canToggle(MediaTime.ACTION_PLAY, MediaTime.STATE_PLAYING));
        assertTrue(MediaTime.canToggle(MediaTime.ACTION_PLAY, MediaTime.STATE_PAUSED));
        assertTrue(MediaTime.canToggle(MediaTime.ACTION_PLAY_PAUSE, MediaTime.STATE_PAUSED));
        assertFalse(MediaTime.can(MediaTime.ACTION_PLAY, MediaTime.ACTION_SKIP_TO_NEXT));
    }

    @Test public void constantsMatchPlaybackState() {
        // Values of android.media.session.PlaybackState (public, stable API).
        assertEquals(3, MediaTime.STATE_PLAYING);
        assertEquals(2, MediaTime.STATE_PAUSED);
        assertEquals(256L, MediaTime.ACTION_SEEK_TO);
        assertEquals(32L, MediaTime.ACTION_SKIP_TO_NEXT);
        assertEquals(16L, MediaTime.ACTION_SKIP_TO_PREVIOUS);
        assertEquals(512L, MediaTime.ACTION_PLAY_PAUSE);
    }

    @Test public void onlyRealTracksShow() {
        assertTrue(MediaTime.showable(MediaTime.STATE_PLAYING, "Leave Her"));
        assertTrue(MediaTime.showable(MediaTime.STATE_PAUSED, "x"));
        assertFalse(MediaTime.showable(MediaTime.STATE_STOPPED, "x"));
        assertFalse(MediaTime.showable(MediaTime.STATE_PLAYING, "  "));
        assertFalse(MediaTime.showable(MediaTime.STATE_PLAYING, null));
    }
}
