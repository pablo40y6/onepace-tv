package com.pablo40y6.onepacetv;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.net.ssl.HttpsURLConnection;

final class CatalogSync {
    static final String INDEX_ROOT = "https://raw.githubusercontent.com/pablo40y6/onepace-tv/tv-catalog/";
    static final String GALLERY_ROOT = "https://pixeldrain.net/api/list/";
    static final int MAX_JSON_BYTES = 1024 * 1024;
    private static final Pattern ARC_ID = Pattern.compile("[a-z0-9]+(?:-[a-z0-9]+)*");
    private static final Pattern FILE_ID = Pattern.compile("[a-zA-Z0-9_-]{6,16}");
    private static final Pattern COVER_NAME = Pattern.compile("[a-z0-9-]{1,96}\\.jpg");
    private static final Pattern CHAPTER = Pattern.compile("(\\d{1,3})(?:v\\d+)?\\s*\\[(\\d{3,4}p)\\]", Pattern.CASE_INSENSITIVE);
    private static final int MAX_ARCS = 120;
    private static final int MAX_EPISODES = 250;

    interface Galleries {
        JSONObject get(String id) throws IOException, JSONException;
    }

    private CatalogSync() {}

    static JSONArray refresh(JSONArray current, JSONObject index, Galleries galleries) throws JSONException {
        Map<String, JSONObject> previous = arcMap(current, true);
        Map<String, JSONObject> plan = previous;
        if (index != null) {
            try {
                Map<String, JSONObject> proposed = arcMap(index.getJSONArray("arcs"), false);
                if (index.optInt("version") == 1 && proposed.keySet().containsAll(previous.keySet())) plan = proposed;
            } catch (JSONException invalidIndex) {
                // Keep checking the known lists if the index is unavailable or incomplete.
            }
        }
        JSONArray refreshed = new JSONArray();
        for (Map.Entry<String, JSONObject> arc : plan.entrySet()) {
            JSONObject old = previous.get(arc.getKey());
            JSONObject entry = arc.getValue();
            String list = entry.getString("list");
            int first = entry.getInt("first");
            if (old != null && old.getInt("first") != first) {
                refreshed.put(old);
                continue;
            }
            try {
                JSONArray episodes = episodes(galleries.get(list), list, arc.getKey(), first,
                        old == null ? 1 : old.getJSONArray("episodes").length());
                JSONObject updated = new JSONObject()
                        .put("id", arc.getKey()).put("list", list).put("title", entry.getString("title"))
                        .put("first", first);
                if (old != null) updated.put("artworkList", old.optString("artworkList", old.getString("list")));
                String cover = entry.optString("cover", old == null ? "" : old.optString("cover"));
                if (!cover.isEmpty()) updated.put("cover", cover);
                refreshed.put(updated.put("episodes", episodes));
            } catch (IOException | JSONException invalidGallery) {
                if (old != null) refreshed.put(old);
            }
        }
        try {
            arcMap(refreshed, true);
            return refreshed;
        } catch (JSONException inconsistent) {
            // A failed list replacement may leave two arcs pointing at one gallery.
            return current;
        }
    }

    static boolean compatible(JSONArray packaged, JSONArray saved) throws JSONException {
        Map<String, JSONObject> base = arcMap(packaged, true);
        Map<String, JSONObject> cache = arcMap(saved, true);
        for (Map.Entry<String, JSONObject> arc : base.entrySet()) {
            JSONObject bundled = arc.getValue();
            JSONObject cached = cache.get(arc.getKey());
            if (cached == null || cached.getInt("first") != bundled.getInt("first")
                    || cached.getJSONArray("episodes").length() < bundled.getJSONArray("episodes").length()) return false;
        }
        return true;
    }

    private static Map<String, JSONObject> arcMap(JSONArray array, boolean populated) throws JSONException {
        if (array.length() == 0 || array.length() > MAX_ARCS) throw new JSONException("Invalid number of arcs");
        Map<String, JSONObject> arcs = new LinkedHashMap<>();
        Set<String> lists = new HashSet<>();
        for (int i = 0; i < array.length(); i++) {
            JSONObject arc = array.getJSONObject(i);
            String id = arc.getString("id");
            String title = arc.getString("title");
            String list = arc.getString("list");
            int first = arc.getInt("first");
            String cover = arc.optString("cover");
            String artwork = arc.optString("artworkList", list);
            if (id.length() > 96 || !ARC_ID.matcher(id).matches() || title.trim().isEmpty() || title.length() > 160
                    || !FILE_ID.matcher(list).matches() || !FILE_ID.matcher(artwork).matches()
                    || (first != 0 && first != 1) || (!cover.isEmpty() && !COVER_NAME.matcher(cover).matches())
                    || arcs.put(id, arc) != null || !lists.add(list)) throw new JSONException("Invalid arc entry");
            if (populated) validateEpisodes(arc.getJSONArray("episodes"), id, first);
        }
        return arcs;
    }

    private static void validateEpisodes(JSONArray chapters, String arc, int first) throws JSONException {
        if (chapters.length() == 0 || chapters.length() > MAX_EPISODES) throw new JSONException("Invalid episode count");
        for (int i = 0; i < chapters.length(); i++) {
            JSONObject chapter = chapters.getJSONObject(i);
            int number = first + i;
            if (chapter.getInt("number") != number || !(arc + ":" + number).equals(chapter.getString("id"))
                    || !chapter.getString("quality").matches("\\d{3,4}p")) throw new JSONException("Invalid episode");
        }
    }

    private static JSONArray episodes(JSONObject gallery, String list, String arc, int first, int minimum) throws JSONException {
        if (!gallery.optBoolean("success") || !list.equals(gallery.optString("id"))) throw new JSONException("Invalid gallery");
        JSONArray files = gallery.getJSONArray("files");
        if (gallery.getInt("file_count") != files.length() || files.length() < minimum || files.length() > MAX_EPISODES) {
            throw new JSONException("Unexpected gallery size");
        }
        Set<String> seen = new HashSet<>();
        JSONArray result = new JSONArray();
        for (int i = 0; i < files.length(); i++) {
            JSONObject file = files.getJSONObject(i);
            String filename = file.getString("name");
            String id = file.getString("id");
            String lower = filename.toLowerCase(Locale.ROOT);
            Matcher chapter = CHAPTER.matcher(filename);
            int number = first + i;
            if (!file.optBoolean("success", true) || !file.optString("mime_type").startsWith("video/")
                    || !FILE_ID.matcher(id).matches() || !seen.add(id)
                    || !lower.startsWith("[one pace]") || !lower.contains("[en sub]")
                    || !chapter.find() || Integer.parseInt(chapter.group(1)) != number) {
                throw new JSONException("Unexpected file at position " + i);
            }
            result.put(new JSONObject().put("id", arc + ":" + number).put("number", number)
                    .put("quality", chapter.group(2).toLowerCase(Locale.ROOT)));
        }
        return result;
    }

    static JSONObject json(String url) throws IOException, JSONException {
        return json(url, null);
    }

    static JSONObject json(String url, String accept) throws IOException, JSONException {
        return new JSONObject(new String(download(url, MAX_JSON_BYTES, accept), StandardCharsets.UTF_8));
    }

    static byte[] download(String url, int limit) throws IOException {
        return download(url, limit, null);
    }

    private static byte[] download(String url, int limit, String accept) throws IOException {
        if (Thread.currentThread().isInterrupted()) throw new IOException("Cancelled catalogue refresh");
        HttpsURLConnection connection = (HttpsURLConnection) new URL(url).openConnection();
        connection.setInstanceFollowRedirects(false);
        connection.setConnectTimeout(4_000);
        connection.setReadTimeout(4_000);
        connection.setRequestProperty("User-Agent", "OnePaceTV-Catalogue");
        if (accept != null) connection.setRequestProperty("Accept", accept);
        try {
            if (connection.getResponseCode() != 200) throw new IOException("Could not load catalogue resource");
            try (InputStream content = connection.getInputStream()) {
                return read(content, limit);
            }
        } finally {
            connection.disconnect();
        }
    }

    static byte[] read(InputStream content, int limit) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        int count;
        while ((count = content.read(buffer)) != -1) {
            if (output.size() + count > limit || Thread.currentThread().isInterrupted()) {
                throw new IOException("Catalogue transfer stopped");
            }
            output.write(buffer, 0, count);
        }
        return output.toByteArray();
    }
}
