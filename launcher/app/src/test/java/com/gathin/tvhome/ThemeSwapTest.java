package com.gathin.tvhome;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class ThemeSwapTest {
    @Rule public TemporaryFolder temp = new TemporaryFolder();

    private static void write(File file, String value) throws IOException {
        Files.write(file.toPath(), value.getBytes(StandardCharsets.UTF_8));
    }

    private static String read(File file) throws IOException {
        return new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8);
    }

    private static final class Disk implements ThemeSwap.Filesystem {
        private final int failAt;
        private int moves;

        Disk(int failAt) { this.failAt = failAt; }

        @Override public void move(File from, File to) throws IOException {
            if (++moves == failAt) throw new IOException("Simulated failed move #" + moves);
            Files.move(from.toPath(), to.toPath(), StandardCopyOption.REPLACE_EXISTING);
        }

        @Override public void delete(File file) throws IOException {
            if (file.isDirectory()) {
                File[] children = file.listFiles();
                if (children == null) throw new IOException("Cannot list collection");
                for (File child : children) Files.delete(child.toPath());
            }
            Files.deleteIfExists(file.toPath());
        }
    }

    @Test public void everyFailedMovePreservesTheOriginalTheme() throws IOException {
        // The first three moves back up originals; the next three install new files.
        // In particular, a failed wallpaper backup must never delete that wallpaper.
        for (int failedMove = 1; failedMove <= 6; failedMove++) {
            File dir = temp.newFolder("failure-" + failedMove);
            File[] active = {new File(dir, "config.json"), new File(dir, "wallpaper.jpg"),
                    new File(dir, "wallpapers")};
            File[] previous = {new File(dir, "prev-config"), new File(dir, "prev-wallpaper"),
                    new File(dir, "prev-collection")};
            File[] staged = {new File(dir, "next-config"), new File(dir, "next-wallpaper"),
                    new File(dir, "next-collection")};
            write(active[0], "old config");
            write(active[1], "old wallpaper");
            assertTrue(active[2].mkdir());
            write(new File(active[2], "old.jpg"), "old collection");
            write(staged[0], "new config");
            write(staged[1], "new wallpaper");
            assertTrue(staged[2].mkdir());
            write(new File(staged[2], "new.jpg"), "new collection");

            try {
                ThemeSwap.apply(active, previous, staged, new Disk(failedMove));
                fail("Move #" + failedMove + " should fail");
            } catch (IOException expected) {
                assertEquals("Simulated failed move #" + failedMove, expected.getMessage());
            }
            assertEquals("old config", read(active[0]));
            assertEquals("old wallpaper", read(active[1]));
            assertEquals("old collection", read(new File(active[2], "old.jpg")));
        }
    }

    @Test public void successfulThemeWithoutWallpaperRemovesOnlyOldWallpaper() throws IOException {
        File dir = temp.newFolder("wallpaper-free");
        File config = new File(dir, "config.json"), wallpaper = new File(dir, "wallpaper.jpg");
        File next = new File(dir, "next-config");
        write(config, "old config");
        write(wallpaper, "old wallpaper");
        write(next, "new config");
        File previousConfig = new File(dir, "prev-config");
        File previousWallpaper = new File(dir, "prev-wallpaper");
        ThemeSwap.apply(new File[]{config, wallpaper, new File(dir, "wallpapers")},
                new File[]{previousConfig, previousWallpaper, new File(dir, "prev-collection")},
                new File[]{next, null, null}, new Disk(-1));
        assertEquals("new config", read(config));
        assertFalse(wallpaper.exists());
        assertEquals("old config", read(previousConfig));
        assertEquals("old wallpaper", read(previousWallpaper));
    }
}
