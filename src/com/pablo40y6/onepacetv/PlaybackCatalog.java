package com.pablo40y6.onepacetv;

import android.net.Uri;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import com.pablo40y6.onepacetv.SubtitleSources.Source;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

final class PlaybackCatalog {
    private final List<Episode> ordered = new ArrayList<>();
    private final List<Arc> arcs = new ArrayList<>();
    private final Map<String, Arc> lists = new HashMap<>();
    private final Map<String, Episode> byId = new HashMap<>();

    PlaybackCatalog(String source) throws JSONException {
        JSONArray entries = new JSONArray(source);
        for (int i = 0; i < entries.length(); i++) {
            JSONObject entry = entries.getJSONObject(i);
            Arc arc = new Arc(entry.getString("list"), entry.getString("title"),
                    entry.optString("artworkList", entry.getString("list")), entry.optString("cover"));
            JSONArray entriesInArc = entry.getJSONArray("episodes");
            for (int item = 0; item < entriesInArc.length(); item++) {
                JSONObject chapter = entriesInArc.getJSONObject(item);
                Episode episode = new Episode(chapter.getString("id"), arc, item, ordered.size(),
                        chapter.getInt("number"), chapter.getString("quality"));
                arc.episodes.add(episode);
                ordered.add(episode);
                byId.put(episode.id, episode);
            }
            arcs.add(arc);
            lists.put(arc.list, arc);
        }
        if (ordered.isEmpty()) throw new JSONException("Empty catalogue");
    }

    static String pixeldrainList(Uri uri) {
        if (!"https".equals(uri.getScheme()) || uri.getUserInfo() != null
                || (uri.getPort() != -1 && uri.getPort() != 443)) return null;
        String host = uri.getHost();
        if (!"pixeldrain.net".equals(host) && !"pixeldrain.com".equals(host)) return null;
        String path = uri.getEncodedPath();
        if (path == null || !path.startsWith("/l/")) return null;
        String list = path.substring(3);
        return SubtitleSources.validMediaId(list) ? list : null;
    }

    List<Arc> arcs() {
        return Collections.unmodifiableList(arcs);
    }

    Episode resume(String id) {
        Episode episode = byId.get(id);
        return episode == null ? ordered.get(0) : episode;
    }

    Episode find(String list, int item) {
        Arc arc = lists.get(list);
        return arc != null && item >= 0 && item < arc.episodes.size() ? arc.episodes.get(item) : null;
    }

    Episode next(Episode episode) {
        int next = episode.position + 1;
        return next < ordered.size() ? ordered.get(next) : null;
    }

    Episode previous(Episode episode) {
        return episode.position > 0 ? ordered.get(episode.position - 1) : null;
    }

    static final class Arc {
        final String list;
        final String title;
        final String artworkList;
        final String cover;
        final List<Episode> episodes = new ArrayList<>();

        Arc(String list, String title, String artworkList, String cover) {
            this.list = list;
            this.title = title;
            this.artworkList = artworkList;
            this.cover = cover;
        }
    }

    static final class Episode {
        final String id;
        final Arc arc;
        final int item;
        final int position;
        final int number;
        final String quality;

        Episode(String id, Arc arc, int item, int position, int number, String quality) {
            this.id = id;
            this.arc = arc;
            this.item = item;
            this.position = position;
            this.number = number;
            this.quality = quality;
        }

        Source englishSource() {
            return new Source(arc.list, item, null, quality);
        }
    }
}
