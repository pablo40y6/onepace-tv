package com.pablo40y6.onepacetv;

import java.nio.file.Files;
import java.nio.file.Paths;
import org.json.JSONArray;
import org.json.JSONException;

public final class CaptionTrackTest {
    public static void main(String[] args) throws Exception {
        String body = Files.readString(Paths.get(args[0]));
        JSONArray parsed = new JSONArray(body);
        CaptionTrack track = new CaptionTrack(body);
        require(parsed.length() == 434, "Pilot dialogue cue count");
        verifyCues(track, parsed);
        for (int i = 1; i < args.length; i++) {
            String captions = Files.readString(Paths.get(args[i]));
            verifyCues(new CaptionTrack(captions), new JSONArray(captions));
        }
        require(track.at(0).isEmpty() && track.at(1_616_000).isEmpty(), "No dialogue during opening or ending");
        require(!track.at(243_000).isEmpty() && !track.at(884_800).isEmpty() && !track.at(1_533_000).isEmpty(),
                "Read cues after seeking into separate scenes");
        try {
            new CaptionTrack("[[5,12,\"a\"],[11,20,\"b\"]]");
            throw new AssertionError("Overlapping subtitles must be rejected");
        } catch (JSONException expected) {
            // The data is sorted and may display at most one cue at a time.
        }
        System.out.println("Verified subtitle timing, gaps, and seeks across all caption assets.");
    }

    private static void verifyCues(CaptionTrack track, JSONArray parsed) throws JSONException {
        require(parsed.length() > 0, "Track must contain dialogue");
        for (int i = 0; i < parsed.length(); i++) {
            JSONArray row = parsed.getJSONArray(i);
            require(track.at(row.getLong(0)).equals(row.getString(2)), "Find each cue at its start");
            require(track.at(row.getLong(1)-1).equals(row.getString(2)), "Find each cue through its last millisecond");
        }
    }

    private static void require(boolean ok, String reason) {
        if (!ok) throw new AssertionError(reason);
    }
}
