package com.gathin.tvhome;

import android.content.Context;
import android.text.Html;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/** Optional, user-initiated Wikimedia Commons 4K downloads with author and licence stored alongside the photo. */
final class CommonsWallpapers {
    static final class Photo {
        final int id, width, height;
        final String name, download, source, credit, license;

        Photo(int id, String name, int width, int height, String download, String source, String credit, String license) {
            this.id = id;
            this.name = name;
            this.width = width;
            this.height = height;
            this.download = download;
            this.source = source;
            this.credit = credit;
            this.license = license;
        }
    }

    static List<Photo> search(ScenicWallpaperCatalog.Group group) throws Exception {
        // The API returns photo metadata only; no images are fetched until the user presses OK on a result.
        String category = brazilCategory(group.id);
        String gallery = category == null
                ? "&generator=search&gsrnamespace=6&gsrlimit=50&gsrsearch="
                    + java.net.URLEncoder.encode(group.commonsSearch, "UTF-8")
                : "&generator=categorymembers&gcmtype=file&gcmlimit=100&gcmtitle="
                    + java.net.URLEncoder.encode(category, "UTF-8");
        String query = "https://commons.wikimedia.org/w/api.php?action=query&format=json&formatversion=2"
                + gallery + "&prop=imageinfo&iiprop=url%7Csize%7Cmime%7Cextmetadata&iiurlwidth=3840";
        JSONObject json = new JSONObject(new String(downloadBytes(query, 2 * 1024 * 1024), StandardCharsets.UTF_8));
        JSONArray pages = json.optJSONObject("query") == null ? null : json.optJSONObject("query").optJSONArray("pages");
        List<Photo> photos = new ArrayList<>();
        if (pages == null) return photos;
        for (int i = 0; i < pages.length() && photos.size() < 24; i++) {
            JSONObject page = pages.optJSONObject(i);
            if (page == null) continue;
            JSONArray infos = page.optJSONArray("imageinfo");
            JSONObject info = infos == null ? null : infos.optJSONObject(0);
            if (info == null) continue;
            int w = info.optInt("width"), h = info.optInt("height");
            // User asked for 4K and landscape TV photography, not vertical photos or thumbnails.
            if (w < 3840 || h < 2160 || w > 20000 || h > 20000 || w / (float) h < 1.3f) continue;
            if (!"image/jpeg".equals(info.optString("mime"))) continue;
            JSONObject metadata = info.optJSONObject("extmetadata");
            if (metadata == null) continue;
            String license = value(metadata, "LicenseShortName");
            if (!(license.startsWith("CC BY") || license.startsWith("CC0") || license.startsWith("Public domain")
                    || license.startsWith("PD"))) continue;
            String artist = plain(value(metadata, "Artist"));
            String credit = (artist.isEmpty() ? "Wikimedia Commons contributor" : artist) + " · " + license;
            String thumb = info.optString("thumburl", "");
            String source = info.optString("descriptionurl", "");
            if (!safeUrl(thumb) || !source.startsWith("https://commons.wikimedia.org/wiki/File:")) continue;
            String name = page.optString("title", "Photo").replaceFirst("^File:", "")
                    .replaceAll("\\.[^.]+$", "").replace('_', ' ');
            photos.add(new Photo(page.optInt("pageid"), name, w, h, thumb, source, credit, license));
        }
        return photos;
    }

    private static String brazilCategory(String group) {
        switch (group) {
            case "brazil_fortaleza": return "Category:Aerial photographs of Fortaleza";
            case "brazil_salvador": return "Category:Aerial photographs of Salvador (Bahia)";
            case "brazil_rio": return "Category:Aerial photographs of Rio de Janeiro (city)";
            case "brazil_recife": return "Category:Aerial photographs of Recife";
            default: return null;
        }
    }

    static File save(Context context, ScenicWallpaperCatalog.Group group, Photo photo) throws Exception {
        File dir = ScenicWallpaperPlayer.directory(context);
        if (!dir.isDirectory() && !dir.mkdirs()) throw new java.io.IOException("Cannot create wallpaper folder");
        File target = new File(dir, group.id + "_" + photo.id + ".jpg");
        if (!target.exists()) {
            byte[] body = downloadBytes(photo.download, 14 * 1024 * 1024);
            if (body.length < 512 || body[0] != (byte) 0xFF || body[1] != (byte) 0xD8)
                throw new java.io.IOException("The image is not a JPEG");
            File tmp = new File(dir, group.id + "_" + photo.id + ".tmp");
            try (FileOutputStream out = new FileOutputStream(tmp)) { out.write(body); out.getFD().sync(); }
            if (!tmp.renameTo(target)) { // Original target is untouched on failure.
                tmp.delete();
                throw new java.io.IOException("Cannot finish download");
            }
        }
        File index = new File(dir, group.id + ".json");
        JSONArray list = new JSONArray();
        if (index.isFile() && index.length() < 128 * 1024) {
            byte[] old = new byte[(int) index.length()];
            try (FileInputStream in = new FileInputStream(index)) {
                int pos = 0, n;
                while (pos < old.length && (n = in.read(old, pos, old.length - pos)) > 0) pos += n;
                if (pos == old.length) list = new JSONArray(new String(old, StandardCharsets.UTF_8));
            } catch (Exception ignored) { }
        }
        String name = target.getName();
        for (int i = 0; i < list.length(); i++) {
            JSONObject entry = list.optJSONObject(i);
            if (entry != null && name.equals(entry.optString("file"))) return target;
        }
        JSONObject entry = new JSONObject();
        entry.put("file", name);
        entry.put("name", photo.name);
        entry.put("credit", photo.credit);
        entry.put("source", photo.source);
        list.put(entry);
        File tmpIndex = new File(dir, group.id + ".json.tmp");
        try (FileOutputStream out = new FileOutputStream(tmpIndex)) {
            out.write(list.toString().getBytes(StandardCharsets.UTF_8));
            out.getFD().sync();
        }
        if (!tmpIndex.renameTo(index)) throw new java.io.IOException("Cannot update wallpaper index");
        return target;
    }

    private static String value(JSONObject parent, String key) {
        JSONObject field = parent.optJSONObject(key);
        return field == null ? "" : field.optString("value", "");
    }

    private static String plain(String html) {
        String text = Html.fromHtml(html, Html.FROM_HTML_MODE_LEGACY).toString().trim();
        return text.length() > 180 ? text.substring(0, 180) : text;
    }

    private static boolean safeUrl(String raw) {
        try {
            URL url = new URL(raw);
            return "https".equals(url.getProtocol()) && (
                    url.getHost().equals("upload.wikimedia.org") || url.getHost().endsWith(".wikimedia.org"));
        } catch (Exception e) { return false; }
    }

    private static byte[] downloadBytes(String raw, int maxBytes) throws Exception {
        if (!safeUrl(raw)) throw new java.io.IOException("Unexpected image host");
        HttpURLConnection connection = (HttpURLConnection) new URL(raw).openConnection();
        connection.setConnectTimeout(7000);
        connection.setReadTimeout(18000);
        connection.setInstanceFollowRedirects(false);
        connection.setRequestProperty("User-Agent", "PersonalTVHome/3.3 (+https://github.com/igorcv88/CleanYourTV-AppleTV)");
        try {
            if (connection.getResponseCode() != HttpURLConnection.HTTP_OK) {
                throw new java.io.IOException("Server returned " + connection.getResponseCode());
            }
            if (connection.getContentLength() > maxBytes) throw new java.io.IOException("Image exceeds size limit");
            try (InputStream in = connection.getInputStream(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
                byte[] buf = new byte[16 * 1024];
                int n;
                while ((n = in.read(buf)) != -1) {
                    if (out.size() + n > maxBytes) throw new java.io.IOException("Image exceeds size limit");
                    out.write(buf, 0, n);
                }
                return out.toByteArray();
            }
        } finally { connection.disconnect(); }
    }

    private CommonsWallpapers() { }
}
