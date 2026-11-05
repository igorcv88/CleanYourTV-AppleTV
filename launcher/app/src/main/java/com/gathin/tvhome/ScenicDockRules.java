package com.gathin.tvhome;

import java.util.ArrayList;
import java.util.List;

/** Pure rules for the configurable Scenic dock; the All Apps shortcut does not consume a favourite slot. */
final class ScenicDockRules {
    private ScenicDockRules() { }

    static int capacity(int configuredColumns) {
        return Math.max(1, Math.min(7, configuredColumns));
    }

    static boolean needsReplacement(List<String> visible, String candidate, int capacity) {
        return !visible.contains(candidate) && visible.size() >= capacity;
    }

    static List<String> replace(List<String> ordered, String oldPkg, String newPkg) {
        List<String> result = new ArrayList<>(ordered);
        int slot = result.indexOf(oldPkg);
        if (slot < 0 || result.contains(newPkg)) return result;
        result.set(slot, newPkg);
        return result;
    }

    static List<String> takeVisible(List<String> ordered, int capacity) {
        List<String> result = new ArrayList<>();
        for (String pkg : ordered) {
            if (result.size() >= capacity) break;
            if (!result.contains(pkg)) result.add(pkg);
        }
        return result;
    }
}
