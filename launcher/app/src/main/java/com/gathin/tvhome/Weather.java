package com.gathin.tvhome;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Header weather, off until the owner names a place (Settings, Weather). Uses Open-Meteo (open-meteo.com, free,
 * no key): its geocoding once when the place is set, then the current conditions at most every
 * {@link #REFRESH_MS} and only while Home is in front (no background polling). Unconfigured or failing, the
 * header shows nothing rather than a made-up reading.
 */
final class Weather {
    static final long REFRESH_MS = 30 * 60_000L;
    private static final String PREFS = "scenic_weather";

    interface Callback {
        void onWeather(String line);
    }

    private final Context ctx;
    private final Handler main = new Handler(Looper.getMainLooper());
    private static final ExecutorService NET = Executors.newSingleThreadExecutor();
    private long fetchedAt;
    private boolean busy;

    Weather(Context ctx) {
        this.ctx = ctx.getApplicationContext();
    }

    private SharedPreferences prefs() {
        return ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    boolean configured() {
        return prefs().contains("lat");
    }

    String place() {
        return prefs().getString("place", "");
    }

    /** The last good reading, shown at once on start (marked by age, never invented). */
    String cached() {
        return configured() ? prefs().getString("line", "") : "";
    }

    void clear() {
        prefs().edit().clear().apply();
        fetchedAt = 0;
    }

    /** Looks the place up and stores it; callback gets the resolved name or null when not found / offline. */
    void setPlace(String query, Callback done) {
        NET.execute(() -> {
            String name = null;
            try {
                String q = URLEncoder.encode(query.trim(), "UTF-8");
                JSONObject o = new JSONObject(get("https://geocoding-api.open-meteo.com/v1/search?count=1&format=json&name=" + q
                        + "&language=" + Locale.getDefault().getLanguage()));
                JSONArray r = o.optJSONArray("results");
                if (r != null && r.length() > 0) {
                    JSONObject first = r.getJSONObject(0);
                    name = first.optString("name", query);
                    prefs().edit().putString("place", name)
                            .putFloat("lat", (float) first.getDouble("latitude"))
                            .putFloat("lon", (float) first.getDouble("longitude"))
                            .remove("line").apply();
                    fetchedAt = 0;
                }
            } catch (Exception ignored) {
            }
            String result = name;
            main.post(() -> done.onWeather(result));
        });
    }

    /** Refreshes when stale; callback only on a new reading. */
    void refresh(Callback cb) {
        if (!configured() || busy || System.currentTimeMillis() - fetchedAt < REFRESH_MS) return;
        busy = true;
        float lat = prefs().getFloat("lat", 0), lon = prefs().getFloat("lon", 0);
        boolean fahrenheit = "US".equals(Locale.getDefault().getCountry());
        NET.execute(() -> {
            String line = null;
            try {
                JSONObject o = new JSONObject(get(String.format(Locale.US,
                        "https://api.open-meteo.com/v1/forecast?latitude=%.4f&longitude=%.4f&current=temperature_2m,weather_code,is_day%s",
                        lat, lon, fahrenheit ? "&temperature_unit=fahrenheit" : "")));
                JSONObject cur = o.getJSONObject("current");
                line = Math.round(cur.getDouble("temperature_2m")) + "\u00B0  " + describe(cur.optInt("weather_code", -1));
            } catch (Exception ignored) {
            }
            String result = line;
            main.post(() -> {
                busy = false;
                if (result == null) {
                    // Offline or refused: try again in 5 minutes, not on every minute tick.
                    fetchedAt = System.currentTimeMillis() - REFRESH_MS + 5 * 60_000L;
                    return;
                }
                fetchedAt = System.currentTimeMillis();
                prefs().edit().putString("line", result).apply();
                cb.onWeather(result);
            });
        });
    }

    private static String get(String url) throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        c.setConnectTimeout(8000);
        c.setReadTimeout(8000);
        try (InputStream in = c.getInputStream()) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buf = new byte[4096];
            int n;
            while ((n = in.read(buf)) > 0 && out.size() < 256 * 1024) out.write(buf, 0, n);
            return out.toString("UTF-8");
        } finally {
            c.disconnect();
        }
    }

    /** WMO weather interpretation codes (Open-Meteo's weather_code) to a short English description. */
    static String describe(int code) {
        if (code == 0) return "Clear";
        if (code == 1) return "Mostly clear";
        if (code == 2) return "Partly cloudy";
        if (code == 3) return "Cloudy";
        if (code == 45 || code == 48) return "Fog";
        if (code >= 51 && code <= 57) return "Drizzle";
        if (code >= 61 && code <= 67) return "Rain";
        if (code >= 71 && code <= 77) return "Snow";
        if (code >= 80 && code <= 82) return "Showers";
        if (code == 85 || code == 86) return "Snow showers";
        if (code >= 95 && code <= 99) return "Thunderstorm";
        return "";
    }
}
