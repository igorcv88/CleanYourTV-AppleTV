package com.gathin.tvhome;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/** The shared glass material: a real, light frost; adaptive to the scene; never milky, never a white veil. */
public class GlassToneTest {
    private static int alpha(int argb) {
        return argb >>> 24;
    }

    private static float lum(int argb) {
        return GlassTone.luminance(0xFF000000 | (argb & 0xFFFFFF));
    }

    @Test public void glassDrawsALightFrostAlmostOpaqueSoItReallyBlurs() {
        // A faint share of the frost let the sharp UI and video show straight through: "no blur at all".
        for (GlassTone.Kind k : new GlassTone.Kind[]{GlassTone.Kind.CHROME, GlassTone.Kind.PANEL,
                GlassTone.Kind.CONTROL, GlassTone.Kind.INSET}) {
            assertTrue(k + " clear", GlassTone.sampleShare(k, false) >= 0.85f);
            assertTrue(k + " frosted", GlassTone.sampleShare(k, true) <= 1f);
        }
        assertEquals(1f, GlassTone.sampleShare(GlassTone.Kind.BACKDROP, false), 0f);
    }

    @Test public void theFrostIsDiffuseButNotASmear() {
        // ~90 px (the old 96 px / radius 4 sample) was milk; ~15-28 px still showed every terrace step and pixel.
        float still = Blur.screenSigma(Blur.GLASS_SIDE, Blur.GLASS_RADIUS, 3, 1920);
        float motion = Blur.screenSigma(Blur.MOTION_SIDE, Blur.MOTION_RADIUS, 3, 1920);
        assertTrue("still " + still, still >= 40f && still <= 70f);
        assertTrue("motion " + motion, motion >= 40f && motion <= 70f);
        assertTrue("still and moving glass match", Math.abs(still - motion) < 0.25f * still);
        // Each sample pixel stretched over the screen stays well inside the blur, so no block ever shows.
        assertTrue(1920f / Blur.MOTION_SIDE < still / 2f);
    }

    @Test public void focusBrightensTheGlassWithoutAWhiteVeil() {
        for (GlassTone.Kind k : GlassTone.Kind.values()) {
            for (float l = 0f; l <= 1f; l += 0.1f) {
                int rest = GlassTone.tint(k, l, false, false), focus = GlassTone.tint(k, l, true, false);
                assertTrue(k + " " + l, alpha(focus) <= alpha(rest) + 0x10);
            }
        }
    }

    @Test public void theLensBandIsAThinRimNotAFrame() {
        assertTrue(GlassTone.lensBandDp(400f) <= 6f);
        assertTrue(GlassTone.lensBandDp(10f) >= 2.5f);
        assertTrue(GlassTone.LENS_MAGNIFY > 1f && GlassTone.LENS_MAGNIFY < 1.2f);
    }

    @Test public void brightScenesGetASmokedVeilDarkScenesAFaintLift() {
        for (GlassTone.Kind k : new GlassTone.Kind[]{GlassTone.Kind.CHROME, GlassTone.Kind.PANEL, GlassTone.Kind.CONTROL,
                GlassTone.Kind.BACKDROP}) {
            int overBright = GlassTone.tint(k, 0.9f, false, false);
            int overDark = GlassTone.tint(k, 0.1f, false, false);
            assertTrue(k + ": more material over a bright scene", alpha(overBright) > alpha(overDark));
            assertTrue(k + ": the bright-scene veil is dark, for white text", lum(overBright) < 0.25f);
            assertTrue(k + ": the dark-scene tint is lighter than the bright-scene one", lum(overDark) > lum(overBright));
        }
    }

    @Test public void tintStaysTranslucentEverywhere() {
        for (GlassTone.Kind k : GlassTone.Kind.values()) {
            for (float l = 0f; l <= 1f; l += 0.05f) {
                for (boolean focused : new boolean[]{false, true}) {
                    int t = GlassTone.tint(k, l, focused, false);
                    assertTrue(k + " " + l + " " + focused + " alpha " + alpha(t), alpha(t) <= 0x80);
                    int frosted = GlassTone.tint(k, l, focused, true);
                    assertTrue(k + " frosted alpha " + alpha(frosted), alpha(frosted) <= 0xA0);
                }
            }
        }
    }

    @Test public void focusLiftsTheGlass() {
        for (GlassTone.Kind k : GlassTone.Kind.values()) {
            for (float l : new float[]{0.1f, 0.5f, 0.9f}) {
                int rest = GlassTone.tint(k, l, false, false), focus = GlassTone.tint(k, l, true, false);
                assertTrue(k + " " + l, lum(focus) >= lum(rest));
            }
        }
    }

    @Test public void unknownSceneIsTreatedAsMid() {
        assertEquals(GlassTone.tint(GlassTone.Kind.CHROME, 0.5f, false, false),
                GlassTone.tint(GlassTone.Kind.CHROME, GlassTone.UNKNOWN, false, false));
    }

    @Test public void overComposesStraightAlpha() {
        assertEquals(0xFFFFFFFF, GlassTone.over(255, 0xFFFFFF, 128, 0x000000));
        assertEquals(0, GlassTone.over(0, 0xFFFFFF, 0, 0x000000));
        int half = GlassTone.over(128, 0xFFFFFF, 255, 0x000000);
        assertEquals(255, alpha(half));
        assertEquals(128, half & 0xFF, 1);
    }

    @Test public void meanLuminanceReadsTheScene() {
        int[] white = new int[400], black = new int[400];
        java.util.Arrays.fill(white, 0xFFFFFFFF);
        java.util.Arrays.fill(black, 0xFF000000);
        assertEquals(1f, GlassTone.meanLuminance(white), 1e-4f);
        assertEquals(0f, GlassTone.meanLuminance(black), 1e-4f);
        assertEquals(0.5f, GlassTone.meanLuminance(new int[0]), 0f);
    }

    @Test public void largeSurfacesCastFainterShadowsThanFullOutlines() {
        // A full-alpha shadow under translucent glass shows through as grey haze.
        for (GlassTone.Kind k : GlassTone.Kind.values()) assertTrue(k.toString(), k.shadowAlpha <= 0.4f);
    }
}
