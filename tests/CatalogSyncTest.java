package com.pablo40y6.onepacetv;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;

public final class CatalogSyncTest {
    private static final String WANO = "6rSQPvNJ";
    private static final String EGGHEAD = "PRTmztoz";
    private static final String REPLACEMENT = "newWano1";
    private static final String NEW_LIST = "newArc01";

    public static void main(String[] args) throws Exception {
        JSONArray bundled = new JSONArray(Files.readString(Paths.get(args[0])));
        JSONObject index = new JSONObject(Files.readString(Paths.get(args[1])));
        JSONObject realGalleries = new JSONObject(Files.readString(Paths.get(args[2])));
        JSONArray actual = CatalogSync.refresh(bundled, index, realGalleries::getJSONObject);
        require(actual.length() == 38 && CatalogSync.compatible(bundled, actual), "Verify the 38 published galleries");
        for (int i = 0; i < actual.length(); i++) {
            require(actual.getJSONObject(i).has("artworkList"), "Gallery passed metadata validation");
        }

        JSONObject wano = realGalleries.getJSONObject(WANO);
        JSONObject extended = new JSONObject(wano.toString()).put("file_count", 63);
        extended.getJSONArray("files").put(video(63, "video063"));
        JSONArray more = CatalogSync.refresh(actual, index, list -> list.equals(WANO) ? extended : realGalleries.getJSONObject(list));
        PlaybackCatalog next = new PlaybackCatalog(more.toString());
        PlaybackCatalog.Episode oldProgress = next.resume("wano:62");
        require("wano:62".equals(oldProgress.id) && oldProgress.item == 61, "Keep old progress");
        require("wano:63".equals(next.next(oldProgress).id), "Play new chapter from its gallery position");
        require("egghead:1".equals(next.next(next.next(oldProgress)).id), "Continue across the arc boundary");
        require(next.resume("long-ring-long-land:0").item == 0, "Preserve chapter zero");

        JSONObject reversed = new JSONObject(extended.toString());
        JSONArray files = reversed.getJSONArray("files");
        JSONObject firstFile = files.getJSONObject(0);
        files.put(0, files.getJSONObject(1)).put(1, firstFile);
        JSONArray rejected = CatalogSync.refresh(more, index, list -> list.equals(WANO) ? reversed : realGalleries.getJSONObject(list));
        require(rejected.toString().equals(more.toString()), "Reject a reordered gallery");
        JSONArray unavailable = CatalogSync.refresh(more, null, list -> { throw new IOException("offline"); });
        require(unavailable.toString().equals(more.toString()), "Keep the last catalogue offline");
        require(CatalogSync.compatible(bundled, more) && !CatalogSync.compatible(more, bundled), "Reject a shrinking cache");

        JSONObject updatedIndex = new JSONObject(index.toString());
        JSONArray entries = updatedIndex.getJSONArray("arcs");
        for (int i = 0; i < entries.length(); i++) {
            JSONObject arc = entries.getJSONObject(i);
            if (WANO.equals(arc.getString("list"))) arc.put("list", REPLACEMENT);
        }
        entries.put(new JSONObject().put("id", "new-arc").put("title", "New arc")
                .put("list", NEW_LIST).put("first", 1).put("cover", "new-arc.jpg"));
        JSONArray updated = CatalogSync.refresh(more, updatedIndex, list -> {
            if (list.equals(REPLACEMENT)) return new JSONObject(extended.toString()).put("id", REPLACEMENT);
            if (list.equals(NEW_LIST)) return new JSONObject().put("id", NEW_LIST).put("success", true)
                    .put("file_count", 1).put("files", new JSONArray().put(video(1, "video001")));
            return realGalleries.getJSONObject(list);
        });
        PlaybackCatalog replacement = new PlaybackCatalog(updated.toString());
        PlaybackCatalog.Episode resumed = replacement.resume("wano:62");
        require(REPLACEMENT.equals(resumed.arc.list) && WANO.equals(resumed.arc.artworkList), "Replace the URL and keep progress and image");
        PlaybackCatalog.Episode added = replacement.resume("new-arc:1");
        require(NEW_LIST.equals(added.arc.list) && "new-arc.jpg".equals(added.arc.cover), "Add a new arc and its cover");
        require(replacement.find(EGGHEAD, 0) != null, "Keep existing neighbouring arcs");
        System.out.println("Passed: published galleries, new episode, saved progress, reorder, offline, replacement, and new arc.");
    }

    private static JSONObject video(int number, String id) throws JSONException {
        return new JSONObject().put("id", id).put("success", true).put("mime_type", "video/mp4")
                .put("name", "[One Pace] Sample " + number + " [480p][En Sub].mp4");
    }

    private static void require(boolean valid, String test) {
        if (!valid) throw new AssertionError(test);
    }
}
