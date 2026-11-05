package com.gathin.tvhome;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/** The progress line's waves: layered, tall enough to see, lively, spread over the played span, never past it. */
public class RibbonSwellsTest {
    private static float maxOver(int layer, long t) {
        float max = 0f;
        for (int i = 0; i <= 200; i++) max = Math.max(max, RibbonSwells.height(layer, i / 200f, t, 1f, 1f));
        return max;
    }

    @Test public void wavesStayAboveTheLineAndUnderTheirCeiling() {
        for (long t = 0; t < 30_000; t += 97) {
            for (int layer = 0; layer < RibbonSwells.LAYERS; layer++) {
                for (int i = 0; i <= 50; i++) {
                    float h = RibbonSwells.height(layer, i / 50f, t, 1f, 1f);
                    assertTrue(h >= 0f);
                    assertTrue(h <= RibbonSwells.MAX_HEIGHT_DP + 1e-4f);
                }
            }
        }
    }

    @Test public void theyRiseFromTheLineAndSinkBeforeThePlayhead() {
        for (long t = 0; t < 10_000; t += 333) {
            for (int layer = 0; layer < RibbonSwells.LAYERS; layer++) {
                assertEquals(0f, RibbonSwells.height(layer, 0f, t, 1f, 1f), 0f);
                assertEquals(0f, RibbonSwells.height(layer, 1f, t, 1f, 1f), 0f);
                assertTrue(RibbonSwells.height(layer, 0.995f, t, 1f, 1f) < 0.05f * RibbonSwells.MAX_HEIGHT_DP);
            }
        }
    }

    @Test public void theyAreTallEnoughToSee() {
        // The first version peaked at 9dp and mostly sat near 3dp: invisible from the sofa.
        for (long t = 0; t < 20_000; t += 500) {
            assertTrue("back layer at " + t, maxOver(0, t) > 0.45f * RibbonSwells.MAX_HEIGHT_DP);
            assertTrue("front layer at " + t, maxOver(RibbonSwells.LAYERS - 1, t) > 0.2f * RibbonSwells.MAX_HEIGHT_DP);
        }
        assertTrue(RibbonSwells.MAX_HEIGHT_DP >= 20f);
    }

    @Test public void theyCoverThePlayedSpanNotOneCorner() {
        // At any instant some layer stands at least a quarter high over most of the played span.
        for (long t = 0; t < 20_000; t += 700) {
            int covered = 0;
            for (int i = 1; i < 100; i++) {
                float u = i / 100f, best = 0f;
                for (int layer = 0; layer < RibbonSwells.LAYERS; layer++) {
                    best = Math.max(best, RibbonSwells.height(layer, u, t, 1f, 1f));
                }
                if (best > 0.25f * RibbonSwells.MAX_HEIGHT_DP) covered++;
            }
            assertTrue("covered " + covered + "% at " + t, covered >= 60);
        }
    }

    @Test public void theyMoveBrisklyNotOverSeconds() {
        // Within a quarter of a second the crest of the front layer changes visibly.
        float a = maxOver(1, 10_000), b = maxOver(1, 10_250);
        float change = 0f;
        for (int i = 1; i < 100; i++) {
            change = Math.max(change, Math.abs(RibbonSwells.height(1, i / 100f, 10_000, 1f, 1f)
                    - RibbonSwells.height(1, i / 100f, 10_250, 1f, 1f)));
        }
        assertTrue("a=" + a + " b=" + b + " change=" + change, change > 0.08f * RibbonSwells.MAX_HEIGHT_DP);
    }

    @Test public void layersAreIndependent() {
        int differ = 0;
        for (int i = 1; i < 20; i++) {
            float u = i / 20f;
            if (Math.abs(RibbonSwells.height(1, u, 7_000, 1f, 1f) - RibbonSwells.height(2, u, 7_000, 1f, 1f)) > 1f) differ++;
        }
        assertTrue(differ >= 8);
    }

    @Test public void pausedReducedMotionOrATinySpanMeansNoWaves() {
        for (int layer = 0; layer < RibbonSwells.LAYERS; layer++) {
            assertEquals(0f, RibbonSwells.height(layer, 0.5f, 9_000, 0f, 1f), 0f);
            assertEquals(0f, RibbonSwells.height(layer, 0.5f, 9_000, 1f, 0f), 0f);
        }
        assertEquals(0f, RibbonSwells.room(8f), 0f);
        assertEquals(1f, RibbonSwells.room(RibbonSwells.FULL_SPAN_DP * 2), 0f);
        assertTrue(RibbonSwells.room(RibbonSwells.FULL_SPAN_DP / 2) < 1f);
    }

    @Test public void overlappingSwellsMergeIntoARoundedCrest() {
        assertEquals(0.5f, RibbonSwells.soft(0.5f), 0f);
        assertTrue(RibbonSwells.soft(3f) <= 1f);
        assertTrue(RibbonSwells.soft(1.2f) > RibbonSwells.soft(1f));
    }

    @Test public void motionIsDeterministic() {
        assertEquals(RibbonSwells.height(2, 0.4f, 44_444, 0.7f, 0.9f),
                RibbonSwells.height(2, 0.4f, 44_444, 0.7f, 0.9f), 0f);
    }
}
