package com.pablo40y6.onepacetv;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

final class SubtitleSources {
    static final String FILE = "spanish-subtitles.json";
    static final String OVERRIDES = "caption-overrides.json";
    private final Map<String, Source> spanish = new HashMap<>();

    SubtitleSources(String data, String overrides) throws JSONException {
        JSONObject entries = new JSONObject(data);
        for (Iterator<String> ids = entries.keys(); ids.hasNext();) {
            String id = ids.next();
            JSONObject entry = entries.getJSONObject(id);
            String list = entry.getString("list");
            String file = entry.getString("file");
            int item = entry.getInt("item");
            String quality = entry.getString("quality");
            if (!id.matches("[a-z0-9-]+:[0-9]{1,3}") || !validMediaId(list) || !validMediaId(file)
                    || item < 0 || item >= 250 || !quality.matches("480p|720p|1080p")) {
                throw new JSONException("Invalid Spanish subtitle source for " + id);
            }
            spanish.put(id, new Source(list, item, file, quality));
        }
        JSONObject alternateCaptions = new JSONObject(overrides);
        for (Iterator<String> ids = alternateCaptions.keys(); ids.hasNext();) {
            String id = ids.next();
            JSONObject entry = alternateCaptions.getJSONObject(id);
            String file = entry.getString("file");
            String quality = entry.getString("quality");
            String captions = entry.getString("captions");
            if (!id.matches("[a-z0-9-]+:[0-9]{1,3}") || !validMediaId(file)
                    || !quality.matches("480p|720p|1080p") || !captions.matches("[a-z0-9-]+\\.es-419\\.json")) {
                throw new JSONException("Invalid alternate caption source for " + id);
            }
            spanish.putIfAbsent(id, new Source(file, 0, file, quality, captions));
        }
    }

    Source spanish(String episodeId) {
        return spanish.get(episodeId);
    }

    static boolean validMediaId(String value) {
        return value.matches("[A-Za-z0-9_-]{6,16}");
    }

    static final class Source {
        final String list;
        final int item;
        final String file;
        final String quality;
        final String captions;

        Source(String list, int item, String file, String quality) {
            this(list, item, file, quality, null);
        }

        Source(String list, int item, String file, String quality, String captions) {
            this.list = list;
            this.item = item;
            this.file = file;
            this.quality = quality;
            this.captions = captions;
        }

        boolean spanish() {
            return file != null;
        }

        String url() {
            if (captions != null) return "https://pixeldrain.net/api/file/" + file;
            return "https://pixeldrain.net/l/" + list + "#item=" + item;
        }
    }
}
