package com.gathin.tvhome;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/** Artwork is never stretched: true banners crop (little loss), odd banners letterbox, icons stay icons. */
public class BannerFitTest {
    private static final float TILE = 144f / 84f;

    @Test public void tvBannerFillsTheTile() {
        assertEquals(BannerFit.CROP, BannerFit.mode(320, 180, TILE, true));
        assertEquals(BannerFit.CROP, BannerFit.mode(1280, 720, TILE, true));
        assertTrue(BannerFit.cropLoss(320f / 180f, TILE) < BannerFit.MAX_CROP_LOSS);
    }

    @Test public void wideBannerIsLetterboxedNotCropped() {
        assertEquals(BannerFit.LETTERBOX, BannerFit.mode(400, 200, TILE, true));   // 2:1 would lose 14%
        assertEquals(BannerFit.LETTERBOX, BannerFit.mode(600, 200, TILE, true));
        assertEquals(BannerFit.LETTERBOX, BannerFit.mode(150, 100, TILE, true));   // 1.5:1
    }

    @Test public void squareOrMissingBannerFallsBackToTheIcon() {
        assertEquals(BannerFit.ICON, BannerFit.mode(256, 256, TILE, true));
        assertEquals(BannerFit.ICON, BannerFit.mode(100, 160, TILE, true));
        assertEquals(BannerFit.ICON, BannerFit.mode(0, 0, TILE, false));
    }

    @Test public void shapeWithoutSizeStillFills() {
        assertEquals(BannerFit.CROP, BannerFit.mode(-1, -1, TILE, true));
    }

    @Test public void fallbackIconKeepsItsProportions() {
        int[] square = BannerFit.fit(192, 192, 50, 50);
        assertEquals(50, square[0]);
        assertEquals(50, square[1]);
        int[] wide = BannerFit.fit(200, 100, 50, 50);
        assertEquals(50, wide[0]);
        assertEquals(25, wide[1]);
        int[] tall = BannerFit.fit(100, 200, 50, 50);
        assertEquals(25, tall[0]);
        assertEquals(50, tall[1]);
        int[] unknown = BannerFit.fit(-1, -1, 40, 30);
        assertEquals(unknown[0], unknown[1]);   // treated as square, never stretched to the box
    }

    @Test public void edgeAverageReadsOnlyTheBorder() {
        int w = 4, h = 3;
        int[] px = new int[w * h];
        java.util.Arrays.fill(px, 0xFFFF0000);   // red border
        px[1 * w + 1] = 0xFF0000FF;              // blue inside
        px[1 * w + 2] = 0xFF0000FF;
        assertEquals(255, BannerFit.edgeAverage(px, w, h, 16));
        assertEquals(0, BannerFit.edgeAverage(px, w, h, 0));
    }
}
