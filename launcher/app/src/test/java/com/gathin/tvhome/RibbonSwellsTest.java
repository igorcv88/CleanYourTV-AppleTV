package com.gathin.tvhome;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.HashSet;
import java.util.Set;

/** The ribbon's swells: several independent mounds above the line, never a single sine, never below it. */
public class RibbonSwellsTest {
    private final RibbonSwells.Swell s = new RibbonSwells.Swell();

    @Test public void swellsStayAboveTheLineAndInsideThePlayedSpan() {
        for (long t = 0; t < 60_000; t += 37) {
            for (int i = 0; i < RibbonSwells.COUNT; i++) {
                RibbonSwells.at(i, t, 1f, s);
                assertTrue(s.heightDp >= 0f);
                assertTrue(s.heightDp <= RibbonSwells.MAX_HEIGHT_DP + 1e-4f);
                assertTrue(s.centre >= 0f && s.centre <= 1f);
                assertTrue(s.widthDp >= RibbonSwells.MIN_WIDTH_DP && s.widthDp <= RibbonSwells.MAX_WIDTH_DP);
                assertTrue(s.colour >= 0 && s.colour < 3);
            }
        }
    }

    @Test public void theyAreIndependentNotOneContinuousWave() {
        // At one instant, the swells sit at different places with different heights.
        Set<Integer> places = new HashSet<>(), heights = new HashSet<>();
        for (int i = 0; i < RibbonSwells.COUNT; i++) {
            RibbonSwells.at(i, 12_345, 1f, s);
            places.add(Math.round(s.centre * 100));
            heights.add(Math.round(s.heightDp * 10));
        }
        assertTrue(places.size() >= 4);
        assertTrue(heights.size() >= 3);
    }

    @Test public void eachSwellIsBornFromAndMeltsIntoTheLine() {
        // Envelope sin^2: zero at the start of each life, so nothing pops into view.
        RibbonSwells.at(0, 0, 1f, s);
        assertEquals(0f, s.heightDp, 1e-4f);
        RibbonSwells.at(0, 3400, 1f, s);
        assertEquals(0f, s.heightDp, 1e-3f);
        RibbonSwells.at(0, 1700, 1f, s);
        assertTrue(s.heightDp > 0.3f * RibbonSwells.MAX_HEIGHT_DP);
    }

    @Test public void pausedOrReducedMotionMeansNoSwells() {
        for (int i = 0; i < RibbonSwells.COUNT; i++) {
            RibbonSwells.at(i, 9_000, 0f, s);
            assertEquals(0f, s.heightDp, 0f);
            assertEquals(0f, s.envelope, 0f);
        }
    }

    @Test public void motionIsDeterministic() {
        RibbonSwells.Swell other = new RibbonSwells.Swell();
        RibbonSwells.at(3, 44_444, 0.7f, s);
        RibbonSwells.at(3, 44_444, 0.7f, other);
        assertEquals(s.centre, other.centre, 0f);
        assertEquals(s.heightDp, other.heightDp, 0f);
    }

    @Test public void aSwellReappearsElsewhereEachCycle() {
        Set<Integer> places = new HashSet<>();
        for (int cycle = 0; cycle < 8; cycle++) {
            RibbonSwells.at(1, cycle * 4100L + 2050L - 1370L + 4100L, 1f, s);
            places.add(Math.round(s.centre * 50));
        }
        assertTrue(places.size() >= 5);
    }
}
