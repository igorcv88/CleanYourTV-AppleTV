package com.gathin.tvhome;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;

public class ScenicGridFilterTest {
    private static final class Tile {
        final String label, appPackage;
        Tile(String label, String appPackage) { this.label = label; this.appPackage = appPackage; }
    }

    @Test public void excludesOnlyDockPackagesAndPreservesTheOtherCards() {
        List<Tile> all = Arrays.asList(new Tile("Netflix", "netflix"), new Tile("Settings", null),
                new Tile("Spotify", "spotify"), new Tile("Inputs", null), new Tile("YouTube", "youtube"));
        List<Tile> grid = ScenicGridFilter.withoutDock(all,
                new HashSet<>(Arrays.asList("spotify", "netflix")), tile -> tile.appPackage);
        assertEquals(Arrays.asList(all.get(1), all.get(3), all.get(4)), grid);
        assertEquals(5, all.size());
    }

    @Test public void changingTheDockRestoresFormerFavourites() {
        List<String> all = Arrays.asList("a", "b", "c");
        assertEquals(Arrays.asList("b", "c"), ScenicGridFilter.withoutDock(all,
                Collections.singleton("a"), value -> value));
        assertEquals(Arrays.asList("a", "c"), ScenicGridFilter.withoutDock(all,
                Collections.singleton("b"), value -> value));
        assertEquals(all, ScenicGridFilter.withoutDock(all, Collections.emptySet(), value -> value));
    }
}
