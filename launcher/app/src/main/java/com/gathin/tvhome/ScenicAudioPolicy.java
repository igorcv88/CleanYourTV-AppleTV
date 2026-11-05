package com.gathin.tvhome;

/** No change to Spotify's audio: the wallpaper yields unless its source has been verified video-only. */
final class ScenicAudioPolicy {
    static final long KEEP_FREE_BYTES = 500L << 20;

    private ScenicAudioPolicy() { }

    static boolean allowAerial(boolean musicPlaying, boolean verifiedVideoOnly) {
        return !musicPlaying || verifiedVideoOnly;
    }

    static boolean hasRoomForVideoCopy(long availableBytes, long sourceBytes) {
        return sourceBytes > 0 && availableBytes >= sourceBytes
                && availableBytes - sourceBytes >= KEEP_FREE_BYTES;
    }
}
