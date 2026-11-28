package com.pablo40y6.onepacetv;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Arrays;

public final class UpdateReleaseTest {
    private static final String SHA256_ABC = "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad";
    private static final String RELEASES = "https://github.com/pablo40y6/onepace-tv/releases/download/";

    public static void main(String[] args) throws Exception {
        JSONObject draft = release("0.20").put("draft", true);
        JSONObject invalidDigest = release("0.19");
        invalidDigest.getJSONArray("assets").getJSONObject(0).put("digest", "");
        JSONObject wrongSource = release("0.18");
        wrongSource.getJSONArray("assets").getJSONObject(0).put("browser_download_url", "https://example.test/app.apk");
        JSONArray versions = new JSONArray().put(draft).put(release("0.10")).put(invalidDigest).put(wrongSource)
                .put(release("0.9")).put(release("0.7"));
        UpdateRelease available = UpdateRelease.latest(versions.toString(), "0.7");
        require(available != null && "0.10".equals(available.version), "Select newest valid prerelease numerically");
        require(UpdateRelease.latest(versions.toString(), "0.10.0") == null, "Ignore equal and older versions");
        require(UpdateRelease.latest(versions.toString(), "2.0") == null, "Ignore downgrades");

        JSONObject branded = release("1.1").put("prerelease", false);
        String brandedName = "OnePaceTV-1.1.apk";
        branded.getJSONArray("assets").getJSONObject(0).put("name", brandedName)
                .put("browser_download_url", RELEASES + "apk-v1.1/" + brandedName);
        UpdateRelease nextRelease = UpdateRelease.latest(new JSONArray().put(branded).toString(), "1.0");
        require(nextRelease != null && nextRelease.url.endsWith("/" + brandedName), "Accept branded APK filenames");

        byte[] expected = "abc".getBytes(StandardCharsets.UTF_8);
        ByteArrayOutputStream result = new ByteArrayOutputStream();
        UpdateFiles.verifyCopy(available, new ByteArrayInputStream(expected), result);
        require(Arrays.equals(expected, result.toByteArray()), "Copy the complete checked APK");
        reject(available, "abd");
        reject(available, "ab");
        reject(available, "abcd");

        if (args.length == 3) {
            UpdateRelease published = UpdateFiles.check(args[0]);
            require(published != null && args[1].equals(published.version), "Detect expected real published version");
            File temporary = File.createTempFile("onepace-verified-", ".apk");
            try {
                UpdateFiles.download(published, temporary);
                require(Arrays.equals(Files.readAllBytes(temporary.toPath()), Files.readAllBytes(new File(args[2]).toPath())),
                        "Download the exact published APK");
            } finally {
                Files.deleteIfExists(temporary.toPath());
            }
            require(UpdateFiles.check(args[1]) == null, "Do not prompt after updating to latest");
        }
        System.out.println("Updater checks passed");
    }

    private static JSONObject release(String version) throws Exception {
        String tag = "apk-v" + version;
        String name = "OnePaceTV-Prueba-" + version + ".apk";
        JSONObject asset = new JSONObject().put("name", name).put("state", "uploaded")
                .put("browser_download_url", RELEASES + tag + "/" + name).put("digest", "sha256:" + SHA256_ABC).put("size", 3);
        return new JSONObject().put("draft", false).put("prerelease", true).put("tag_name", tag)
                .put("assets", new JSONArray().put(asset));
    }

    private static void reject(UpdateRelease release, String data) throws Exception {
        try {
            UpdateFiles.verifyCopy(release, new ByteArrayInputStream(data.getBytes(StandardCharsets.UTF_8)), new ByteArrayOutputStream());
            throw new AssertionError("Accepted corrupted, short or oversized download");
        } catch (IOException expected) {
            // Expected. Nothing from this stream can reach the installer.
        }
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
