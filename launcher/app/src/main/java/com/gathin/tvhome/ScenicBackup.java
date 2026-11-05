package com.gathin.tvhome;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONObject;

import java.util.Map;

/** Explicit user-selected document backup. Never backs up Android accounts or launcher-private tokens. */
final class ScenicBackup {
    private ScenicBackup() { }

    private static final String FORMAT = "scenic-launcher-settings";
    private static final String[] SOURCES = {
            "scenic_favorites", "scenic_home", "scenic_weather", "scenic_myspace", Config.SETTINGS_NAME
    };

    static String encode(Context context) throws Exception {
        JSONObject root = new JSONObject();
        root.put("format", FORMAT);
        root.put("version", 1);
        JSONObject sources = new JSONObject();
        for (String name : SOURCES) {
            SharedPreferences prefs = context.getSharedPreferences(name, Context.MODE_PRIVATE);
            JSONObject entries = new JSONObject();
            for (Map.Entry<String, ?> entry : prefs.getAll().entrySet()) {
                Object value = entry.getValue();
                if (value instanceof String || value instanceof Boolean || value instanceof Integer
                        || value instanceof Long || value instanceof Float) {
                    JSONObject typed = new JSONObject();
                    typed.put("type", value instanceof String ? "string" : value instanceof Boolean ? "boolean"
                            : value instanceof Integer ? "int" : value instanceof Long ? "long" : "float");
                    typed.put("value", value);
                    entries.put(entry.getKey(), typed);
                }
            }
            sources.put(name, entries);
        }
        root.put("preferences", sources);
        return root.toString(2);
    }

    /** Validate the complete document before modifying even one preference file. */
    static void restore(Context context, String json) throws Exception {
        if (json == null || json.length() > 131072) throw new IllegalArgumentException("Backup too large");
        JSONObject root = new JSONObject(json);
        if (!FORMAT.equals(root.optString("format")) || root.optInt("version") != 1)
            throw new IllegalArgumentException("Not a Scenic backup");
        JSONObject source = root.getJSONObject("preferences");
        java.util.Map<String, java.util.Map<String, Object>> checked = new java.util.LinkedHashMap<>();
        for (String name : SOURCES) {
            JSONObject values = source.getJSONObject(name);
            java.util.Map<String, Object> destination = new java.util.LinkedHashMap<>();
            java.util.Iterator<String> keys = values.keys();
            while (keys.hasNext()) {
                String key = keys.next();
                if (key.isEmpty() || key.length() > 160 || destination.size() >= 128)
                    throw new IllegalArgumentException("Invalid backup entry");
                JSONObject item = values.getJSONObject(key);
                String type = item.getString("type");
                Object value;
                switch (type) {
                    case "string": value = item.getString("value"); break;
                    case "boolean": value = item.getBoolean("value"); break;
                    case "int": value = item.getInt("value"); break;
                    case "long": value = item.getLong("value"); break;
                    case "float": value = (float) item.getDouble("value"); break;
                    default: throw new IllegalArgumentException("Invalid backup value type");
                }
                if (value instanceof String && ((String) value).length() > 32768)
                    throw new IllegalArgumentException("Backup field too large");
                destination.put(key, value);
            }
            checked.put(name, destination);
        }
        for (Map.Entry<String, java.util.Map<String, Object>> entry : checked.entrySet()) {
            SharedPreferences.Editor editor = context.getSharedPreferences(entry.getKey(), Context.MODE_PRIVATE)
                    .edit().clear();
            for (Map.Entry<String, Object> item : entry.getValue().entrySet()) {
                Object v = item.getValue();
                if (v instanceof String) editor.putString(item.getKey(), (String) v);
                else if (v instanceof Boolean) editor.putBoolean(item.getKey(), (Boolean) v);
                else if (v instanceof Integer) editor.putInt(item.getKey(), (Integer) v);
                else if (v instanceof Long) editor.putLong(item.getKey(), (Long) v);
                else if (v instanceof Float) editor.putFloat(item.getKey(), (Float) v);
            }
            if (!editor.commit()) throw new IllegalStateException("Could not save restored settings");
        }
    }
}
