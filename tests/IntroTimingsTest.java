package com.pablo40y6.onepacetv;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.HashSet;
import java.util.Set;

public final class IntroTimingsTest {
    private static final Set<String> PILOT_FILES = new HashSet<>(java.util.Arrays.asList(
            "q1ZVCfXq", "rUds75LZ", "XXNheVfk"));

    public static void main(String[] args) throws Exception {
        JSONObject document = new JSONObject(Files.readString(Paths.get(args[0])));
        IntroTimings timings = new IntroTimings(document);
        JSONArray entries = document.getJSONArray("intros");
        JSONArray pilotEntries = new JSONArray();
        Set<String> episodes = new HashSet<>();
        for (int i = 0; i < entries.length(); i++) {
            JSONObject entry = entries.getJSONObject(i);
            String episode = entry.getString("episode");
            String file = entry.getString("file");
            if (PILOT_FILES.contains(file)) pilotEntries.put(entry);
            episodes.add(episode);
            double start = entry.getDouble("start");
            double end = entry.getDouble("end");
            require(timings.at(episode, file, start - 0.01, 1200) == null, "Hide before the intro");
            IntroTimings.Window window = timings.at(episode, file, start, 1200);
            require(window != null && window.end == end, "Show at the exact start and use the measured end");
            require(timings.at(episode, file, end - 0.01, 1200) != null, "Show during the intro");
            require(timings.at(episode, file, end, 1200) == null, "Hide at the exact end");
            require(timings.at(episode, "otherFile", start, 1200) == null, "Reject a different edition");
            require(timings.at("skypiea:0", file, start, 1200) == null, "Reject another episode");
            require(timings.at(episode, file, start, end) == null, "Reject an interval past the loaded video");
        }
        for (int number = 1; number <= 25; number++) {
            require(episodes.contains("skypiea:" + number), "Include every Skypiea episode");
        }
        require(pilotEntries.length() == 3, "Retain the original three pilot episodes");
        IntroTimings.Window gallery = timings.at("skypiea:16", "8BMtLzoR", 2, 1200);
        require(gallery != null && gallery.matches("skypiea:16", "8BMtLzoR"),
                "Resolve the episode's Spanish gallery file");
        require(!gallery.matches("skypiea:16", "yJcjwqN7"), "Reject the English file for a Spanish window");
        IntroTimings.Window nativeVideo = timings.at("wano:63", "MtzrTiBh", 100, 1200);
        require(nativeVideo != null && nativeVideo.end == 146.670, "Use the chapter marker from the native Spanish path");
        require(!nativeVideo.contains(146.670, 1200), "Reject a click that arrives after the intro during native playback");
        JSONObject pilotData = new JSONObject().put("version", 1).put("intros", pilotEntries);
        IntroTimings pilot = new IntroTimings(pilotData);
        require(!pilot.isNewerThan(timings) && timings.isNewerThan(pilot),
                "The old three-episode cache cannot replace the packaged catalogue");
        JSONObject newer = new JSONObject(document.toString()).put("revision", document.getInt("revision") + 1);
        require(new IntroTimings(newer).isNewerThan(timings), "Accept new published revisions");
        require(!new IntroTimings(document).isNewerThan(timings), "Skip updates for an already loaded revision");
        JSONObject invalid = new JSONObject(document.toString());
        invalid.getJSONArray("intros").getJSONObject(0).put("end", -1);
        try {
            new IntroTimings(invalid);
            throw new AssertionError("Accepted an invalid remote timing");
        } catch (JSONException expected) {
            // The catalogue refresher can keep the last valid version.
        }
        System.out.println("Verified start and end boundaries, episode and file matches, and invalid timing rejection.");
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
