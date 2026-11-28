package com.pablo40y6.onepacetv;

import org.json.JSONException;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Locale;

import javax.net.ssl.HttpsURLConnection;

final class UpdateFiles {
    private static final String RELEASES = "https://api.github.com/repos/pablo40y6/onepace-tv/releases?per_page=20";
    private static final int MAX_RELEASE_BYTES = 4 * 1024 * 1024;

    private UpdateFiles() {}

    static UpdateRelease check(String installedVersion) throws IOException, JSONException {
        HttpsURLConnection connection = connect(new URL(RELEASES), false);
        try (InputStream content = connection.getInputStream(); ByteArrayOutputStream body = new ByteArrayOutputStream()) {
            copy(content, body, MAX_RELEASE_BYTES, null);
            return UpdateRelease.latest(new String(body.toByteArray(), StandardCharsets.UTF_8), installedVersion);
        } finally {
            connection.disconnect();
        }
    }

    static void download(UpdateRelease release, File destination) throws IOException {
        HttpsURLConnection connection = connect(new URL(release.url), true);
        try (InputStream content = connection.getInputStream(); OutputStream file = new FileOutputStream(destination)) {
            verifyCopy(release, content, file);
        } finally {
            connection.disconnect();
        }
    }

    static void verifyCopy(UpdateRelease release, InputStream content, OutputStream file) throws IOException {
        MessageDigest digest;
        try {
            digest = MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException(impossible);
        }
        long size = copy(content, file, release.size, digest);
        if (size != release.size || !hex(digest.digest()).equals(release.sha256)) {
            throw new IOException("APK checksum or size mismatch");
        }
    }

    private static HttpsURLConnection connect(URL url, boolean downloading) throws IOException {
        for (int redirect = 0; redirect < 5; redirect++) {
            String host = url.getHost();
            boolean knownHost = downloading ? "github.com".equals(host)
                    || "release-assets.githubusercontent.com".equals(host)
                    || "objects.githubusercontent.com".equals(host) : "api.github.com".equals(host);
            if (!"https".equals(url.getProtocol()) || !knownHost || url.getUserInfo() != null
                    || (url.getPort() != -1 && url.getPort() != 443)) throw new IOException("Unexpected update host");

            HttpsURLConnection request = (HttpsURLConnection) url.openConnection();
            request.setInstanceFollowRedirects(false);
            request.setConnectTimeout(8_000);
            request.setReadTimeout(12_000);
            request.setRequestProperty("User-Agent", "OnePaceTV-Updater");
            if (!downloading) request.setRequestProperty("Accept", "application/vnd.github+json");
            try {
                int status = request.getResponseCode();
                if (status == 200) return request;
                String location = request.getHeaderField("Location");
                if (!downloading || location == null || status < 300 || status >= 400) {
                    throw new IOException("Update server HTTP " + status);
                }
                url = new URL(url, location);
            } catch (IOException error) {
                request.disconnect();
                throw error;
            }
            request.disconnect();
        }
        throw new IOException("Too many update redirects");
    }

    private static long copy(InputStream input, OutputStream output, long limit, MessageDigest digest) throws IOException {
        byte[] buffer = new byte[32 * 1024];
        long size = 0;
        int count;
        while ((count = input.read(buffer)) != -1) {
            size += count;
            if (size > limit || Thread.currentThread().isInterrupted()) throw new IOException("Update transfer stopped");
            if (digest != null) digest.update(buffer, 0, count);
            output.write(buffer, 0, count);
        }
        return size;
    }

    private static String hex(byte[] bytes) {
        StringBuilder result = new StringBuilder(bytes.length * 2);
        for (byte value : bytes) result.append(String.format(Locale.ROOT, "%02x", value & 0xff));
        return result.toString();
    }
}
