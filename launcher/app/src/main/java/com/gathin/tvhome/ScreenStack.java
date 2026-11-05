package com.gathin.tvhome;

import java.util.ArrayList;
import java.util.List;

/**
 * The navigation levels inside Scenic Settings: Settings → Wallpapers → a collection → its 4K search, and so on.
 * Back pops one level and the parent comes back with its scroll position and focused row, rather than every
 * Back closing straight to Home.
 *
 * <p>Screens identify themselves by a key (their title). Opening the key already on top is a refresh (a status
 * changed on that screen); opening a key further down, such as "Back to Wallpapers" or a save that returns to
 * My Space, unwinds to it instead of stacking a duplicate. Transient screens (downloads) are entered with no
 * way to reopen them and are skipped by Back. Generic in the reopen action, so it runs on the JVM.
 */
final class ScreenStack<T> {
    enum Move { ROOT, PUSH, REFRESH, POP_TO }

    static final class Entry<T> {
        final String key;
        T reopen;
        /** Where the screen was when the user left it: restored when it comes back. */
        int scrollY;
        int focusIndex = -1;

        Entry(String key, T reopen) {
            this.key = key;
            this.reopen = reopen;
        }
    }

    private final List<Entry<T>> entries = new ArrayList<>();

    /** A screen opens; returns what that means for the stack. {@code reopen} null marks it transient. */
    Move enter(String key, T reopen) {
        if (entries.isEmpty()) {
            entries.add(new Entry<>(key, reopen));
            return Move.ROOT;
        }
        Entry<T> top = top();
        if (top.key.equals(key)) {
            if (reopen != null) top.reopen = reopen;
            return Move.REFRESH;
        }
        for (int i = entries.size() - 2; i >= 0; i--) {
            if (entries.get(i).key.equals(key)) {
                while (entries.size() > i + 1) entries.remove(entries.size() - 1);
                if (reopen != null) entries.get(i).reopen = reopen;
                return Move.POP_TO;
            }
        }
        entries.add(new Entry<>(key, reopen));
        return Move.PUSH;
    }

    /**
     * Back: drops the top screen and any transient ones under it. Returns the screen to show again, or null
     * when the stack is empty (the screen layer closes and its opener, Settings or Home, takes over).
     */
    Entry<T> back() {
        if (!entries.isEmpty()) entries.remove(entries.size() - 1);
        while (!entries.isEmpty() && top().reopen == null) entries.remove(entries.size() - 1);
        return entries.isEmpty() ? null : top();
    }

    /** The screen in front, or null. */
    Entry<T> top() {
        return entries.isEmpty() ? null : entries.get(entries.size() - 1);
    }

    int depth() {
        return entries.size();
    }

    void clear() {
        entries.clear();
    }
}
