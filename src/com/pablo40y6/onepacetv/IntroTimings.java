package com.pablo40y6.onepacetv;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.HashMap;
import java.util.Map;

final class IntroTimings {
    static final String FILE = "intro-timings.json";
    static final String REMOTE_FILE = "intro-timings-v2.json";
    // Increment the published revision when changing the list or any timing.
    private final int revision;
    private final Map<String, Window> byFile = new HashMap<>();

    IntroTimings(JSONObject source) throws JSONException {
        JSONArray entries = source.getJSONArray("intros");
        int version = source.optInt("version");
        revision = source.has("revision") ? source.getInt("revision") : 0;
        // Version 2 includes native playback windows that older apps cannot seek to.
        if ((version != 1 && version != 2) || revision < 0 || entries.length() > 4000) {
            throw new JSONException("Invalid intro catalogue");
        }
        for (int i = 0; i < entries.length(); i++) {
            JSONObject entry = entries.getJSONObject(i);
            String file = entry.getString("file");
            String episode = entry.getString("episode");
            double start = entry.getDouble("start");
            double end = entry.getDouble("end");
            if (!file.matches("[a-zA-Z0-9_-]{6,16}") || !episode.matches("[a-z0-9-]{1,96}:[0-9]{1,3}")
                    || !Double.isFinite(start) || !Double.isFinite(end) || start < 0 || end <= start
                    || end > 600 || byFile.put(file, new Window(file, episode, start, end)) != null) {
                throw new JSONException("Invalid intro entry");
            }
        }
    }

    boolean isNewerThan(IntroTimings other) {
        return revision > other.revision;
    }

    Window at(String episode, String file, double time, double duration) {
        Window window = byFile.get(file);
        return window != null && window.matches(episode, file) && window.contains(time, duration) ? window : null;
    }

    static final class Window {
        final String file;
        final String episode;
        final double start;
        final double end;

        Window(String file, String episode, double start, double end) {
            this.file = file;
            this.episode = episode;
            this.start = start;
            this.end = end;
        }

        boolean matches(String episode, String file) {
            return this.episode.equals(episode) && this.file.equals(file);
        }

        boolean contains(double time, double duration) {
            return time >= start && time < end && Double.isFinite(duration) && end < duration;
        }
    }
}
