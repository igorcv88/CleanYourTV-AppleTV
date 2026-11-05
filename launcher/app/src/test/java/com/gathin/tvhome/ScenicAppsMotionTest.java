package com.gathin.tvhome;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class ScenicAppsMotionTest {
    @Test public void dockAndGridRemainBelowTheHeader() {
        int dockTop = ScenicAppsMotion.dockTop(96, 12);
        int gridTop = ScenicAppsMotion.gridTop(dockTop, 120, 14);
        assertEquals(108, dockTop);
        assertEquals(242, gridTop);
        assertTrue(dockTop >= 96);
        assertTrue(gridTop > dockTop + 120);
    }

    @Test public void gridStartsBelowTheScreenAndEndsUnderTheDock() {
        int gridTop = ScenicAppsMotion.gridTop(108, 120, 14);
        int travel = ScenicAppsMotion.entryTravel(1080, gridTop, 32);
        assertEquals(870, travel);
        assertTrue(gridTop + travel > 1080);
        assertEquals(32, ScenicAppsMotion.entryTravel(200, gridTop, 32));
    }

    @Test public void revealAndReturnNeverOvershootOpacity() {
        assertEquals(0f, ScenicAppsMotion.gridAlpha(-1f), 0f);
        assertEquals(0.5f, ScenicAppsMotion.gridAlpha(0.25f), 0f);
        assertEquals(1f, ScenicAppsMotion.gridAlpha(0.5f), 0f);
        assertEquals(1f, ScenicAppsMotion.gridAlpha(1.5f), 0f);
    }
}
