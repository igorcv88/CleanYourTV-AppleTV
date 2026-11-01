package com.gathin.tvhome;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** The Scenic dock's favourites: order kept, uninstalled apps hidden but remembered, All Apps last (DockNav). */
public class FavoritesTest {
    @Test public void roundTrip() {
        List<String> l = Arrays.asList("com.netflix.ninja", "com.google.android.youtube.tv", "com.spotify.tv.android");
        assertEquals(l, Favorites.decode(Favorites.encode(l)));
        assertEquals(Arrays.asList("a", "b"), Favorites.decode("a\n\nb\na\n"));
    }

    @Test public void firstRunSeedsFromConfiguredDockThenFirstApps() {
        List<String> installed = Arrays.asList("x", "netflix", "y", "youtube");
        assertEquals(Arrays.asList("youtube", "netflix"),
                Favorites.effective(null, Arrays.asList("youtube", "missing", "netflix"), installed, 6));
        assertEquals(Arrays.asList("x", "netflix"),
                Favorites.effective(null, Collections.singletonList("missing"), installed, 2));
        assertEquals(Collections.singletonList("y"),
                Favorites.effective(Collections.singletonList("y"), Arrays.asList("x"), installed, 6));
    }

    @Test public void uninstalledFavouriteIsHiddenAndComesBackInPlace() {
        List<String> fav = Arrays.asList("a", "b", "c");
        Set<String> without = new HashSet<>(Arrays.asList("a", "c"));
        assertEquals(Arrays.asList("a", "c"), Favorites.visible(fav, without));
        Set<String> reinstalled = new HashSet<>(Arrays.asList("a", "b", "c"));
        assertEquals(fav, Favorites.visible(fav, reinstalled));
    }

    @Test public void toggleAddsAtEndOrRemoves() {
        assertEquals(Arrays.asList("a", "b", "c"), Favorites.toggle(Arrays.asList("a", "b"), "c"));
        assertEquals(Arrays.asList("a"), Favorites.toggle(Arrays.asList("a", "b"), "b"));
    }

    @Test public void moveSkipsHiddenEntriesAndStopsAtEnds() {
        List<String> fav = Arrays.asList("a", "gone", "b", "c");
        Set<String> installed = new HashSet<>(Arrays.asList("a", "b", "c"));
        assertEquals(Arrays.asList("b", "gone", "a", "c"), Favorites.moveVisible(fav, installed, "a", 1));
        assertEquals(fav, Favorites.moveVisible(fav, installed, "a", -1));
        assertEquals(fav, Favorites.moveVisible(fav, installed, "c", 1));
        assertEquals(Arrays.asList("a", "gone", "c", "b"), Favorites.moveVisible(fav, installed, "c", -1));
    }

    @Test public void dockEndsWithAllAppsAndClampsKeys() {
        assertEquals(4, DockNav.count(3));
        assertEquals(true, DockNav.isAllApps(3, 3));
        assertEquals(false, DockNav.isAllApps(2, 3));
        assertEquals(1, DockNav.count(0));            // no favourites: All Apps alone
        assertEquals(0, DockNav.step(0, -1, 4));      // Left at the start stays
        assertEquals(3, DockNav.step(3, 1, 4));       // Right on All Apps stays
        assertEquals(2, DockNav.step(1, 1, 4));
        assertEquals(3, DockNav.restore(9, 4));       // a favourite removed: focus falls on the last item
        assertEquals(0, DockNav.restore(-2, 4));
    }
}
