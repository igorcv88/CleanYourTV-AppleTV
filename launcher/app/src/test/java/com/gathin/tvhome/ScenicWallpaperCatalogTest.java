package com.gathin.tvhome;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.Collections;
import java.util.List;
import java.util.Random;

public class ScenicWallpaperCatalogTest {
    @Test public void sceneGroupsContainBundledImagesAndBrazilSubgroups() {
        assertTrue(ScenicWallpaperCatalog.find("nature").builtIn.size() >= 10);
        assertTrue(ScenicWallpaperCatalog.find("illustration").builtIn.size() >= 10);
        assertEquals(7, ScenicWallpaperCatalog.BRAZIL.length);
        assertEquals("Fortaleza & Ceará", ScenicWallpaperCatalog.find("brazil_fortaleza").label);
        assertEquals("Salvador & Bahia", ScenicWallpaperCatalog.find("brazil_salvador").label);
        assertTrue(ScenicWallpaperCatalog.find("galaxies").builtIn.isEmpty());
        assertNull(ScenicWallpaperCatalog.find("bogus"));
    }

    @Test public void shuffleAvoidsImmediateRepetitionAndPinWins() {
        List<ScenicWallpaperCatalog.Item> photos = ScenicWallpaperCatalog.find("nature").builtIn;
        String first = photos.get(0).id;
        for (int i = 0; i < 100; i++) {
            assertNotEquals(first, ScenicWallpaperCatalog.pick(photos, first, "", new Random(i)).id);
            assertEquals(first, ScenicWallpaperCatalog.pick(photos, "", first, new Random(i)).id);
        }
    }

    @Test public void noPhotoIsGracefulAndSinglePhotoAlwaysAvailable() {
        List<ScenicWallpaperCatalog.Item> one = Collections.singletonList(
                ScenicWallpaperCatalog.find("space").builtIn.get(0));
        assertEquals(one.get(0), ScenicWallpaperCatalog.pick(one, one.get(0).id, "", new Random(3)));
        assertNull(ScenicWallpaperCatalog.pick(Collections.emptyList(), "", "", new Random(3)));
    }

    @Test public void idsAreStableAndDoNotCollide() {
        List<String> ids = new java.util.ArrayList<>();
        for (ScenicWallpaperCatalog.Group group : ScenicWallpaperCatalog.allGroups()) {
            for (ScenicWallpaperCatalog.Item photo : group.builtIn) {
                assertTrue(photo.id.startsWith(group.id + "/"));
                assertTrue("Duplicate id: " + photo.id, !ids.contains(photo.id));
                ids.add(photo.id);
            }
        }
        assertTrue(ids.size() > 40);
    }
}
