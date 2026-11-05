package com.gathin.tvhome;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.Arrays;

/** The ribbon palette from a cover: more than one colour, restrained, sane on grey art. */
public class ArtPaletteTest {
    private static float hue(int argb) {
        float[] hsv = new float[3];
        ArtPalette.toHsv(argb, hsv);
        return hsv[0];
    }

    private static float hueDistance(float a, float b) {
        float d = Math.abs(a - b) % 360f;
        return Math.min(d, 360f - d);
    }

    @Test public void aTwoColourCoverGivesBothHues() {
        int[] px = new int[64];
        for (int i = 0; i < px.length; i++) px[i] = i < 40 ? 0xFFE0302A : 0xFF2A4FE0;   // red and blue
        int[] p = ArtPalette.from(px);
        assertEquals(3, p.length);
        assertTrue("strongest is red", hueDistance(hue(p[0]), 2f) < 20f);
        assertTrue("second is blue", hueDistance(hue(p[1]), 228f) < 20f);
    }

    @Test public void coloursAreRestrainedNotNeon() {
        int[] px = new int[64];
        Arrays.fill(px, 0xFF00FF00);
        for (int c : ArtPalette.from(px)) {
            float[] hsv = new float[3];
            ArtPalette.toHsv(c, hsv);
            assertTrue(hsv[1] <= 0.55f);
        }
    }

    @Test public void aGreyCoverFallsBackToTheGlassPalette() {
        int[] px = new int[64];
        Arrays.fill(px, 0xFF808080);
        assertArrayEquals(ArtPalette.DEFAULT, ArtPalette.from(px));
        assertArrayEquals(ArtPalette.DEFAULT, ArtPalette.from(new int[0]));
        assertArrayEquals(ArtPalette.DEFAULT, ArtPalette.from(null));
    }

    @Test public void aSingleHueCoverStillGetsThreeDistinctColours() {
        int[] px = new int[64];
        Arrays.fill(px, 0xFFD08020);
        int[] p = ArtPalette.from(px);
        assertNotEquals(p[0], p[1]);
        assertNotEquals(p[1], p[2]);
    }

    @Test public void lerpAndTowardBlend() {
        assertEquals(0xFF000000, ArtPalette.lerp(0xFF000000, 0xFFFFFFFF, 0f));
        assertEquals(0xFFFFFFFF, ArtPalette.lerp(0xFF000000, 0xFFFFFFFF, 1f));
        assertEquals(0xFF808080, ArtPalette.lerp(0xFF000000, 0xFFFFFFFF, 0.5f));
        assertEquals(0xFFFFFFFF, ArtPalette.toward(0xFF336699, 0xFFFFFFFF, 1f));
    }

    @Test public void hsvRoundTrips() {
        for (int c : new int[]{0xFFFF0000, 0xFF00FF00, 0xFF0000FF, 0xFF336699, 0xFFFFFFFF, 0xFF000000}) {
            float[] hsv = new float[3];
            ArtPalette.toHsv(c, hsv);
            int back = ArtPalette.fromHsv(hsv[0], hsv[1], hsv[2]);
            for (int shift : new int[]{16, 8, 0}) {
                assertEquals(((c >> shift) & 0xFF), ((back >> shift) & 0xFF), 1);
            }
        }
    }
}
