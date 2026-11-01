package com.gathin.tvhome;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/** Back focus restoration, the glass crop model, the blur, My Space storage, weather codes and recents. */
public class ScenicLogicTest {
    @Test public void backRestoresTheExactPriorFocusInOrder() {
        FocusMemory<String> m = new FocusMemory<>();
        m.push("panel", "dock:2");
        m.push("screen", "panel-row:My Space");
        assertEquals("screen", m.top());
        assertTrue(m.isOpen("panel"));
        assertEquals("panel-row:My Space", m.pop());
        assertEquals("dock:2", m.pop());
        assertNull(m.pop());
        assertEquals(0, m.depth());
    }

    @Test public void coverCentresAndCropsTheLongerSide() {
        float[] c = SceneCrop.cover(100, 100, 1920, 1080);   // square art on a 16:9 screen
        assertEquals(19.2f, c[0], 1e-4f);
        assertEquals(0f, c[1], 1e-3f);
        assertEquals((1080 - 1920) / 2f, c[2], 1e-3f);
        // The middle band of the art lies under the screen.
        float[] full = SceneCrop.source(100, 100, 1920, 1080, 0, 0, 1920, 1080);
        assertEquals(0f, full[0], 1e-3f);
        assertEquals(21.875f, full[1], 1e-3f);
        assertEquals(100f, full[2], 1e-3f);
        assertEquals(78.125f, full[3], 1e-3f);
    }

    @Test public void dockCropMatchesItsScreenPlace() {
        float[] src = SceneCrop.source(960, 540, 1920, 1080, 96, 840, 1824, 1080);
        assertEquals(48f, src[0], 1e-3f);
        assertEquals(420f, src[1], 1e-3f);
        assertEquals(912f, src[2], 1e-3f);
        assertEquals(540f, src[3], 1e-3f);
    }

    @Test public void blurKeepsFlatColourAndSpreadsAPoint() {
        int w = 9, h = 9;
        int[] flat = new int[w * h];
        Arrays.fill(flat, 0xFF336699);
        Blur.boxBlur(flat, w, h, 2);
        for (int p : flat) assertEquals(0xFF336699, p);
        int[] dot = new int[w * h];
        Arrays.fill(dot, 0xFF000000);
        dot[4 * w + 4] = 0xFFFFFFFF;
        Blur.boxBlur(dot, w, h, 1);
        int centre = dot[4 * w + 4] & 0xFF, near = dot[4 * w + 5] & 0xFF;
        assertTrue(centre < 255 && centre > 0);
        assertTrue(near > 0 && near <= centre);
        for (int p : dot) assertEquals(0xFF, p >>> 24);
    }

    @Test public void mySpaceRoundTripsAwkwardText() {
        List<MySpace.Title> in = Arrays.asList(
                new MySpace.Title("Tab\there", "Films", "com.app", "app://x?a=1\\b"),
                new MySpace.Title("Two\nlines", "", "", ""));
        List<MySpace.Title> out = MySpace.decode(MySpace.encode(in));
        assertEquals(in, out);
        assertEquals(MySpace.DEFAULT_COLLECTION, out.get(1).collection);
    }

    @Test public void mySpaceGroupsAndSearches() {
        List<MySpace.Title> t = Arrays.asList(new MySpace.Title("Dune", "Films", "", ""),
                new MySpace.Title("Severance", "Series", "", ""), new MySpace.Title("Arrival", "Films", "", ""));
        assertEquals(Arrays.asList("Films", "Series"), new java.util.ArrayList<>(MySpace.byCollection(t).keySet()));
        assertEquals(2, MySpace.byCollection(t).get("Films").size());
        assertEquals(1, MySpace.search(t, "sev").size());
        assertEquals(2, MySpace.search(t, "films").size());
        assertEquals(0, MySpace.search(t, " ").size());
        assertEquals(Collections.emptyList(), MySpace.decode(""));
    }

    @Test public void weatherCodes() {
        assertEquals("Clear", Weather.describe(0));
        assertEquals("Partly cloudy", Weather.describe(2));
        assertEquals("Rain", Weather.describe(63));
        assertEquals("Thunderstorm", Weather.describe(95));
        assertEquals("", Weather.describe(-1));
    }

    @Test public void recentsAreNewestFirstWithoutDuplicates() {
        assertEquals(Arrays.asList("b", "a", "c"), Recents.push(Arrays.asList("a", "b", "c"), "b", 8));
        assertEquals(Arrays.asList("d", "a"), Recents.push(Arrays.asList("a", "b", "c"), "d", 2));
    }
}
