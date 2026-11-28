package com.pablo40y6.onepacetv;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.regex.Pattern;

final class UpdateRelease {
    private static final String PREFIX = "apk-v";
    private static final String DOWNLOAD_BASE = "https://github.com/pablo40y6/onepace-tv/releases/download/";
    private static final Pattern VERSION = Pattern.compile("(?:0|[1-9][0-9]{0,5})(?:\\.(?:0|[1-9][0-9]{0,5})){1,2}");
    static final long MAX_APK_BYTES = 256L * 1024 * 1024;

    final String version;
    final String url;
    final String sha256;
    final long size;

    private UpdateRelease(String version, String url, String sha256, long size) {
        this.version = version;
        this.url = url;
        this.sha256 = sha256;
        this.size = size;
    }

    static UpdateRelease latest(String body, String installedVersion) throws JSONException {
        if (installedVersion == null || !VERSION.matcher(installedVersion).matches()) return null;
        JSONArray entries = new JSONArray(body);
        UpdateRelease latest = null;
        for (int i = 0; i < entries.length(); i++) {
            JSONObject entry = entries.optJSONObject(i);
            if (entry == null || entry.optBoolean("draft", true)) continue;
            String tag = entry.optString("tag_name");
            if (!tag.startsWith(PREFIX)) continue;
            String version = tag.substring(PREFIX.length());
            if (!VERSION.matcher(version).matches()) continue;
            if (compare(version, latest == null ? installedVersion : latest.version) <= 0) continue;

            String apk = "OnePaceTV-" + version + ".apk";
            String compatibleApk = "OnePaceTV-Prueba-" + version + ".apk";
            JSONArray assets = entry.optJSONArray("assets");
            if (assets == null) continue;
            for (int a = 0; a < assets.length(); a++) {
                JSONObject asset = assets.optJSONObject(a);
                if (asset == null) continue;
                String name = asset.optString("name");
                String url = DOWNLOAD_BASE + tag + "/" + name;
                if ((!apk.equals(name) && !compatibleApk.equals(name)) || !url.equals(asset.optString("browser_download_url"))
                        || !"uploaded".equals(asset.optString("state"))) continue;
                String digest = asset.optString("digest");
                long size = asset.optLong("size", 0);
                if (digest.matches("sha256:[a-f0-9]{64}") && size > 0 && size <= MAX_APK_BYTES) {
                    latest = new UpdateRelease(version, url, digest.substring(7), size);
                    break;
                }
            }
        }
        return latest;
    }

    private static int compare(String first, String second) {
        String[] left = first.split("\\.");
        String[] right = second.split("\\.");
        for (int i = 0; i < 3; i++) {
            int a = i < left.length ? Integer.parseInt(left[i]) : 0;
            int b = i < right.length ? Integer.parseInt(right[i]) : 0;
            if (a != b) return Integer.compare(a, b);
        }
        return 0;
    }
}
