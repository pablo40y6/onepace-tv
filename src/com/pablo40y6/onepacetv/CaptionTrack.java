package com.pablo40y6.onepacetv;

import org.json.JSONArray;
import org.json.JSONException;

final class CaptionTrack {
    private final long[] starts;
    private final long[] ends;
    private final String[] captions;

    CaptionTrack(String data) throws JSONException {
        JSONArray entries = new JSONArray(data);
        starts = new long[entries.length()];
        ends = new long[entries.length()];
        captions = new String[entries.length()];
        long previousEnd = 0;
        for (int i = 0; i < entries.length(); i++) {
            JSONArray entry = entries.getJSONArray(i);
            long start = entry.getLong(0);
            long end = entry.getLong(1);
            String text = entry.getString(2);
            if (start < previousEnd || end <= start || text.trim().isEmpty()) {
                throw new JSONException("Invalid caption at index " + i);
            }
            starts[i] = start;
            ends[i] = end;
            captions[i] = text;
            previousEnd = end;
        }
    }

    String at(long positionMs) {
        int index = java.util.Arrays.binarySearch(starts, positionMs);
        if (index < 0) index = -index - 2;
        return index >= 0 && positionMs < ends[index] ? captions[index] : "";
    }
}
