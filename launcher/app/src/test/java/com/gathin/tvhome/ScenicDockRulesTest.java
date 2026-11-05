package com.gathin.tvhome;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;

public class ScenicDockRulesTest {
    @Test public void configuredColumnsBoundFavouriteSlotsNotTheAllAppsControl() {
        assertEquals(4, ScenicDockRules.capacity(4));
        assertEquals(5, ScenicDockRules.capacity(5));
        assertEquals(6, ScenicDockRules.capacity(6));
        assertEquals(7, ScenicDockRules.capacity(7));
        assertEquals(Arrays.asList("a", "b", "c", "d"),
                ScenicDockRules.takeVisible(Arrays.asList("a", "b", "c", "d", "e"), 4));
    }

    @Test public void addingAtCapacityRequiresExplicitReplacement() {
        assertTrue(ScenicDockRules.needsReplacement(Arrays.asList("a", "b"), "c", 2));
        assertFalse(ScenicDockRules.needsReplacement(Arrays.asList("a", "b"), "a", 2));
        assertFalse(ScenicDockRules.needsReplacement(Collections.singletonList("a"), "b", 2));
    }

    @Test public void replacementKeepsSlotAndOtherFavouritesUnchanged() {
        assertEquals(Arrays.asList("a", "replacement", "c"),
                ScenicDockRules.replace(Arrays.asList("a", "b", "c"), "b", "replacement"));
        assertEquals(Arrays.asList("a", "b"),
                ScenicDockRules.replace(Arrays.asList("a", "b"), "missing", "x"));
        assertEquals(Arrays.asList("a", "b"),
                ScenicDockRules.replace(Arrays.asList("a", "b"), "a", "b"));
    }
}
