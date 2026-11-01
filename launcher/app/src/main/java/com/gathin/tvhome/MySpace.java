package com.gathin.tvhome;

import android.content.Context;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * My Space: titles the owner saved on this TV, in named collections, each optionally tied to an app that
 * opens it (and an optional deep link the app understands). Stored locally; it never reads another app's
 * private watchlist. Encoded one title per line, fields tab-separated with \t, \n and \\ escaped, so the
 * format is tested on the JVM.
 */
final class MySpace {
    private MySpace() { }

    static final String DEFAULT_COLLECTION = "Watchlist";
    private static final String PREFS = "scenic_myspace", KEY = "titles";

    static final class Title {
        final String title, collection, pkg, uri;

        Title(String title, String collection, String pkg, String uri) {
            this.title = title == null ? "" : title.trim();
            String c = collection == null ? "" : collection.trim();
            this.collection = c.isEmpty() ? DEFAULT_COLLECTION : c;
            this.pkg = pkg == null ? "" : pkg.trim();
            this.uri = uri == null ? "" : uri.trim();
        }

        @Override public boolean equals(Object o) {
            if (!(o instanceof Title)) return false;
            Title t = (Title) o;
            return t.title.equals(title) && t.collection.equals(collection) && t.pkg.equals(pkg) && t.uri.equals(uri);
        }

        @Override public int hashCode() {
            return (title + '\u0000' + collection + '\u0000' + pkg + '\u0000' + uri).hashCode();
        }
    }

    static List<Title> load(Context ctx) {
        return decode(ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, ""));
    }

    static void save(Context ctx, List<Title> titles) {
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY, encode(titles)).apply();
    }

    static String encode(List<Title> titles) {
        StringBuilder b = new StringBuilder();
        for (Title t : titles) {
            if (t.title.isEmpty()) continue;
            if (b.length() > 0) b.append('\n');
            b.append(esc(t.title)).append('\t').append(esc(t.collection)).append('\t')
                    .append(esc(t.pkg)).append('\t').append(esc(t.uri));
        }
        return b.toString();
    }

    static List<Title> decode(String raw) {
        List<Title> out = new ArrayList<>();
        if (raw == null || raw.isEmpty()) return out;
        for (String line : raw.split("\n", -1)) {
            String[] f = line.split("\t", -1);
            if (f.length < 1 || f[0].isEmpty()) continue;
            Title t = new Title(unesc(f[0]), f.length > 1 ? unesc(f[1]) : "", f.length > 2 ? unesc(f[2]) : "",
                    f.length > 3 ? unesc(f[3]) : "");
            if (!t.title.isEmpty() && !out.contains(t)) out.add(t);
        }
        return out;
    }

    /** Collections in first-seen order, each with its titles. */
    static Map<String, List<Title>> byCollection(List<Title> titles) {
        Map<String, List<Title>> out = new LinkedHashMap<>();
        for (Title t : titles) {
            List<Title> l = out.get(t.collection);
            if (l == null) out.put(t.collection, l = new ArrayList<>());
            l.add(t);
        }
        return out;
    }

    /** Case-insensitive match on title or collection. */
    static List<Title> search(List<Title> titles, String query) {
        List<Title> out = new ArrayList<>();
        String q = query == null ? "" : query.trim().toLowerCase(java.util.Locale.ROOT);
        if (q.isEmpty()) return out;
        for (Title t : titles) {
            if (t.title.toLowerCase(java.util.Locale.ROOT).contains(q)
                    || t.collection.toLowerCase(java.util.Locale.ROOT).contains(q)) out.add(t);
        }
        return out;
    }

    private static String esc(String s) {
        return s.replace("\\", "\\\\").replace("\t", "\\t").replace("\n", "\\n");
    }

    private static String unesc(String s) {
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '\\' && i + 1 < s.length()) {
                char n = s.charAt(++i);
                b.append(n == 't' ? '\t' : n == 'n' ? '\n' : n);
            } else {
                b.append(c);
            }
        }
        return b.toString();
    }
}
