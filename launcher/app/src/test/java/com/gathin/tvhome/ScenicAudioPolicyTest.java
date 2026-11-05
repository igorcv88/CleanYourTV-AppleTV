package com.gathin.tvhome;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/** The fallback must never launch an unstripped aerial AudioTrack while Spotify is playing. */
public class ScenicAudioPolicyTest {
    @Test public void musicProtectsAudioWhenRemuxUnavailable() {
        assertFalse(ScenicAudioPolicy.allowAerial(true, false));
        assertTrue(ScenicAudioPolicy.allowAerial(false, false));
        assertTrue(ScenicAudioPolicy.allowAerial(true, true));
        assertTrue(ScenicAudioPolicy.allowAerial(false, true));
    }

    @Test public void remuxReservesStorageForOtherApps() {
        long reserve = ScenicAudioPolicy.KEEP_FREE_BYTES;
        assertFalse(ScenicAudioPolicy.hasRoomForVideoCopy(reserve, 1));
        assertFalse(ScenicAudioPolicy.hasRoomForVideoCopy(reserve + 200, 201));
        assertTrue(ScenicAudioPolicy.hasRoomForVideoCopy(reserve + 200, 200));
        assertFalse(ScenicAudioPolicy.hasRoomForVideoCopy(Long.MAX_VALUE, 0));
        assertFalse(ScenicAudioPolicy.hasRoomForVideoCopy(0, 100));
    }
}
