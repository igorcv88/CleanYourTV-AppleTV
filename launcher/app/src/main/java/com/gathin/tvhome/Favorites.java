package com.gathin.tvhome;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;

/**
 * The Scenic dock's favourites: an ordered list of package names, kept in preferences. A package that is not
 * installed right now stays in the list (hidden), so an app that is updated, reinstalled or briefly disabled
 * comes back in its old place. The order is the owner's; the dock shows {@link #visible} then All Apps.
 */
final class Favorites {
    private Favorites() { }

    private static final String PREFS = "scenic_favorites", KEY = "packages";

    /** null when the owner never changed the dock (seed from config dock_apps). */
    static List<String> load(Context ctx) {
        String raw = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, null);
        return raw == null ? null : decode(raw);
    }

    static void save(Context ctx, List<String> pkgs) {
        SharedPreferences.Editor e = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit();
        e.putString(KEY, encode(pkgs)).apply();
    }

    static String encode(List<String> pkgs) {
        StringBuilder b = new StringBuilder();
        for (String p : pkgs) {
            if (p == null || p.isEmpty() || p.indexOf('\n') >= 0) continue;
            if (b.length() > 0) b.append('\n');
            b.append(p);
        }
        return b.toString();
    }

    static List<String> decode(String raw) {
        List<String> out = new ArrayList<>();
        if (raw == null) return out;
        for (String p : raw.split("\n")) {
            String t = p.trim();
            if (!t.isEmpty() && !out.contains(t)) out.add(t);
        }
        return out;
    }

    /** The stored list, or for a first run the configured dock apps that are installed (else the first few apps). */
    static List<String> effective(List<String> stored, List<String> configured, List<String> installedInOrder, int seedCount) {
        if (stored != null) return new ArrayList<>(stored);
        List<String> out = new ArrayList<>();
        for (String p : configured) if (installedInOrder.contains(p) && !out.contains(p)) out.add(p);
        if (out.isEmpty()) {
            for (String p : installedInOrder) {
                if (out.size() >= seedCount) break;
                if (!out.contains(p)) out.add(p);
            }
        }
        return out;
    }

    /** The favourites to show now, in order: only installed ones. */
    static List<String> visible(List<String> favourites, Collection<String> installed) {
        List<String> out = new ArrayList<>();
        for (String p : favourites) if (installed.contains(p) && !out.contains(p)) out.add(p);
        return out;
    }

    /** Adds at the end, or removes; returns the new list. */
    static List<String> toggle(List<String> favourites, String pkg) {
        List<String> out = new ArrayList<>(favourites);
        if (!out.remove(pkg)) out.add(pkg);
        return out;
    }

    /**
     * Moves a visible favourite one place left or right among the visible ones, keeping hidden (uninstalled)
     * entries where they are relative to the others. Returns the new full list.
     */
    static List<String> moveVisible(List<String> favourites, Set<String> installed, String pkg, int step) {
        List<String> vis = visible(favourites, installed);
        int i = vis.indexOf(pkg), j = i + step;
        if (i < 0 || j < 0 || j >= vis.size()) return new ArrayList<>(favourites);
        String other = vis.get(j);
        List<String> out = new ArrayList<>(favourites);
        int a = out.indexOf(pkg), b = out.indexOf(other);
        out.set(a, other);
        out.set(b, pkg);
        return out;
    }
}
