package com.gathin.tvhome;

import android.content.Context;

import java.util.ArrayList;
import java.util.List;

/** Apps recently opened from Scenic Home, newest first, kept on the TV: the honest source for Discover. */
final class Recents {
    private Recents() { }

    static final int MAX = 8;
    private static final String PREFS = "scenic_recents", KEY = "packages";

    static List<String> load(Context ctx) {
        return Favorites.decode(ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, ""));
    }

    static void record(Context ctx, String pkg) {
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                .putString(KEY, Favorites.encode(push(load(ctx), pkg, MAX))).apply();
    }

    static List<String> push(List<String> list, String pkg, int max) {
        List<String> out = new ArrayList<>();
        out.add(pkg);
        for (String p : list) if (!p.equals(pkg) && out.size() < max) out.add(p);
        return out;
    }
}
