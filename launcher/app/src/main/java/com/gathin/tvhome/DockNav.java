package com.gathin.tvhome;

/**
 * D-pad rules of the Scenic dock: favourites then the All Apps item, which is always last. Left/Right stop at
 * the ends (no wrap, no escape into another row), and a remembered position survives the list changing size.
 */
final class DockNav {
    private DockNav() { }

    /** Item count with the terminal All Apps item. */
    static int count(int favourites) {
        return Math.max(0, favourites) + 1;
    }

    static boolean isAllApps(int position, int favourites) {
        return position == favourites;
    }

    /** Target of Left (-1) or Right (+1); the same position at an end. */
    static int step(int position, int dir, int count) {
        if (count <= 0) return 0;
        int t = position + (dir < 0 ? -1 : 1);
        return Math.max(0, Math.min(count - 1, t));
    }

    /** A remembered position clamped into the current list. */
    static int restore(int remembered, int count) {
        if (count <= 0) return 0;
        return Math.max(0, Math.min(count - 1, remembered));
    }
}
