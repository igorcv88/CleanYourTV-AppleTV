package com.gathin.tvhome;

/** Geometry shared by the dock and the app grid as the Apps sheet rises from below Home. */
final class ScenicAppsMotion {
    private ScenicAppsMotion() { }

    static int dockTop(int headerBottom, int headerGap) {
        return Math.max(0, headerBottom) + Math.max(0, headerGap);
    }

    static int gridTop(int dockTop, int dockHeight, int dockGap) {
        return dockTop + Math.max(0, dockHeight) + Math.max(0, dockGap);
    }

    /** Place the first app row below the viewport before bringing it up under the dock. */
    static int entryTravel(int screenHeight, int gridTop, int overshootRoom) {
        return Math.max(0, screenHeight - gridTop) + Math.max(0, overshootRoom);
    }

    static float clamp(float value) {
        return Math.max(0f, Math.min(1f, value));
    }

    static float gridAlpha(float reveal) {
        return clamp(reveal * 2f);
    }
}
