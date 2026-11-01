package com.gathin.tvhome;

import java.io.File;
import java.io.IOException;

/** Transactional replacement of config, wallpaper and collection (config is installed last). */
final class ThemeSwap {
    interface Filesystem {
        void move(File from, File to) throws IOException;
        void delete(File file) throws IOException;
    }

    /** A null staged entry means the new theme has no file/collection at that position. */
    static void apply(File[] active, File[] previous, File[] staged, Filesystem fs) throws IOException {
        if (active.length != 3 || previous.length != 3 || staged.length != 3)
            throw new IllegalArgumentException("Expected config, wallpaper and collection");
        boolean[] backedUp = new boolean[3];
        boolean[] installed = new boolean[3];
        try {
            for (int i = 0; i < active.length; i++) {
                if (active[i].exists()) {
                    fs.move(active[i], previous[i]);
                    backedUp[i] = true;
                }
            }
            // Install images before config so Home never observes a partly installed theme.
            for (int i = 1; i < active.length; i++) {
                if (staged[i] != null) {
                    fs.move(staged[i], active[i]);
                    installed[i] = true;
                }
            }
            fs.move(staged[0], active[0]);
            installed[0] = true;
        } catch (IOException failure) {
            // Never delete a target that was not installed by this transaction: a failed
            // backup move may have left the user's original file in exactly that path.
            for (int i = active.length - 1; i >= 0; i--) {
                try {
                    if (installed[i]) fs.delete(active[i]);
                    if (backedUp[i]) fs.move(previous[i], active[i]);
                } catch (IOException rollback) {
                    failure.addSuppressed(rollback);
                }
            }
            throw failure;
        }
    }

    private ThemeSwap() {}
}
