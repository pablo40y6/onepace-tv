package com.pablo40y6.onepacetv;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.BitmapFactory;
import android.os.SystemClock;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

final class CatalogRefresher {
    private static final long CHECK_INTERVAL_MS = 60 * 60 * 1_000;
    private static final long INTRO_CHECK_INTERVAL_MS = 30_000;
    private static final String INTRO_MIRROR = "https://api.github.com/repos/pablo40y6/onepace-tv/contents/"
            + IntroTimings.REMOTE_FILE + "?ref=tv-catalog";
    private static final int MAX_IMAGE_BYTES = 2 * 1024 * 1024;
    private static final String CHECKED_AT = "checkedAt";
    private final Activity activity;
    private final SharedPreferences settings;
    private final File snapshotFile;
    private final File introsFile;
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private JSONArray snapshot;
    private IntroTimings intros;
    private long lastIntroCheck = -INTRO_CHECK_INTERVAL_MS;
    private boolean checkingIntros;
    private boolean running;
    private boolean closed;

    CatalogRefresher(Activity activity, String bundled, String bundledIntros) throws JSONException {
        this.activity = activity;
        settings = activity.getSharedPreferences("one-pace-tv-catalogue", Context.MODE_PRIVATE);
        snapshotFile = new File(activity.getFilesDir(), "catalog-v1.json");
        introsFile = new File(activity.getFilesDir(), IntroTimings.FILE);
        intros = new IntroTimings(new JSONObject(bundledIntros));
        if (introsFile.isFile()) {
            try (FileInputStream file = new FileInputStream(introsFile)) {
                IntroTimings saved = new IntroTimings(new JSONObject(new String(CatalogSync.read(file, CatalogSync.MAX_JSON_BYTES),
                        StandardCharsets.UTF_8)));
                if (saved.isNewerThan(intros)) intros = saved;
            } catch (IOException | JSONException invalidCache) {
                Log.d("OnePaceCatalog", "Could not read the last intro timings", invalidCache);
            }
        }
        JSONArray packaged = new JSONArray(bundled);
        snapshot = packaged;
        if (snapshotFile.isFile()) {
            try (FileInputStream file = new FileInputStream(snapshotFile)) {
                JSONArray saved = new JSONArray(new String(CatalogSync.read(file, CatalogSync.MAX_JSON_BYTES),
                        StandardCharsets.UTF_8));
                if (CatalogSync.compatible(packaged, saved)) snapshot = saved;
            } catch (IOException | JSONException invalidCache) {
                Log.d("OnePaceCatalog", "Could not read the last catalogue", invalidCache);
            }
        }
    }

    PlaybackCatalog initialCatalog() throws JSONException {
        return new PlaybackCatalog(snapshot.toString());
    }

    IntroTimings.Window introAt(String episode, String file, double time, double duration) {
        return intros.at(episode, file, time, duration);
    }

    void check(Consumer<PlaybackCatalog> apply) {
        if (closed) return;
        checkIntros();
        long now = System.currentTimeMillis();
        long elapsed = now - settings.getLong(CHECKED_AT, 0);
        if (running || (elapsed >= 0 && elapsed < CHECK_INTERVAL_MS)) return;
        running = true;
        JSONArray current = snapshot;
        worker.execute(() -> {
            JSONArray updated = current;
            PlaybackCatalog replacement = null;
            try {
                updated = CatalogSync.refresh(current, readIndex(), id -> CatalogSync.json(CatalogSync.GALLERY_ROOT + id));
                if (Thread.currentThread().isInterrupted()) return;
                String serialized = updated.toString();
                boolean changed = !serialized.equals(current.toString());
                if (changed) {
                    replacement = new PlaybackCatalog(serialized);
                    write(snapshotFile, serialized.getBytes(StandardCharsets.UTF_8));
                }
                if (downloadCovers(updated) && replacement == null) replacement = new PlaybackCatalog(serialized);
            } catch (IOException | JSONException error) {
                Log.d("OnePaceCatalog", "Could not refresh catalogue", error);
            }
            JSONArray latest = updated;
            PlaybackCatalog available = replacement;
            activity.runOnUiThread(() -> {
                if (closed) return;
                running = false;
                settings.edit().putLong(CHECKED_AT, now).apply();
                if (available != null) {
                    snapshot = latest;
                    apply.accept(available);
                }
            });
        });
    }

    private void checkIntros() {
        long now = SystemClock.elapsedRealtime();
        if (checkingIntros || now - lastIntroCheck < INTRO_CHECK_INTERVAL_MS) return;
        lastIntroCheck = now;
        checkingIntros = true;
        IntroTimings current = intros;
        worker.execute(() -> {
            IntroTimings updated = refreshIntros(current);
            activity.runOnUiThread(() -> {
                if (closed) return;
                checkingIntros = false;
                if (updated != null && updated.isNewerThan(intros)) intros = updated;
            });
        });
    }

    private IntroTimings refreshIntros(IntroTimings current) {
        try {
            JSONObject source;
            IntroTimings updated;
            try {
                source = CatalogSync.json(CatalogSync.INDEX_ROOT + IntroTimings.REMOTE_FILE);
                updated = new IntroTimings(source);
            } catch (IOException | JSONException primaryError) {
                source = CatalogSync.json(INTRO_MIRROR, "application/vnd.github.raw+json");
                updated = new IntroTimings(source);
            }
            if (Thread.currentThread().isInterrupted() || !updated.isNewerThan(current)) return null;
            try {
                write(introsFile, source.toString().getBytes(StandardCharsets.UTF_8));
            } catch (IOException cacheError) {
                Log.d("OnePaceCatalog", "Could not save intro timings", cacheError);
            }
            return updated;
        } catch (IOException | JSONException error) {
            Log.d("OnePaceCatalog", "Could not refresh intro timings", error);
            return null;
        }
    }

    private JSONObject readIndex() {
        try {
            return CatalogSync.json(CatalogSync.INDEX_ROOT + "catalog-index.json");
        } catch (IOException | JSONException error) {
            Log.d("OnePaceCatalog", "Could not refresh arc index", error);
            return null;
        }
    }

    private boolean downloadCovers(JSONArray catalog) throws JSONException {
        boolean changed = false;
        for (int i = 0; i < catalog.length(); i++) {
            String cover = catalog.getJSONObject(i).optString("cover");
            if (cover.isEmpty()) continue;
            File target = coverFile(activity, cover);
            if (target.isFile()) continue;
            try {
                byte[] bytes = CatalogSync.download(CatalogSync.INDEX_ROOT + "covers/" + cover, MAX_IMAGE_BYTES);
                BitmapFactory.Options image = new BitmapFactory.Options();
                image.inJustDecodeBounds = true;
                BitmapFactory.decodeByteArray(bytes, 0, bytes.length, image);
                if (!"image/jpeg".equals(image.outMimeType) || image.outWidth < 200 || image.outWidth > 2048
                        || image.outHeight < 100 || image.outHeight > 1152) continue;
                write(target, bytes);
                changed = true;
            } catch (IOException error) {
                Log.d("OnePaceCatalog", "Could not refresh cover", error);
            }
        }
        return changed;
    }

    static File coverFile(Context context, String filename) {
        return new File(context.getFilesDir(), "catalog-cover-" + filename);
    }

    private static void write(File target, byte[] bytes) throws IOException {
        File temporary = File.createTempFile("catalog-", ".tmp", target.getParentFile());
        try {
            Files.write(temporary.toPath(), bytes);
            Files.move(temporary.toPath(), target.toPath(), StandardCopyOption.ATOMIC_MOVE,
                    StandardCopyOption.REPLACE_EXISTING);
        } finally {
            Files.deleteIfExists(temporary.toPath());
        }
    }

    void close() {
        closed = true;
        worker.shutdownNow();
    }
}
