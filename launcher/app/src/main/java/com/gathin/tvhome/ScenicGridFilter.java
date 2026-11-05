package com.gathin.tvhome;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.function.Function;

/**
 * Scenic uses the installed-app list for two different surfaces: the favourites dock and the Apps grid.
 * Only actual dock apps disappear from the grid. Other entries (cards, settings and library shortcuts)
 * remain available, and the source list is never changed.
 */
final class ScenicGridFilter {
    private ScenicGridFilter() { }

    static <T> List<T> withoutDock(List<T> all, Set<String> dockPackages, Function<T, String> appPackage) {
        List<T> result = new ArrayList<>(all.size());
        for (T entry : all) {
            String pkg = appPackage.apply(entry);
            if (pkg == null || !dockPackages.contains(pkg)) result.add(entry);
        }
        return result;
    }
}
