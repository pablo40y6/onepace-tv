package com.pablo40y6.onepacetv;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.ClipData;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.Signature;
import android.net.Uri;
import android.os.Build;
import android.os.SystemClock;
import android.provider.Settings;
import android.util.Log;
import android.widget.Toast;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.Arrays;
import java.util.HashSet;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.BooleanSupplier;

final class AppUpdater {
    private static final long CHECK_INTERVAL_MS = 60_000;
    private final Activity activity;
    private final BooleanSupplier catalogueVisible;
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private long lastCheck = -CHECK_INTERVAL_MS;
    private UpdateRelease available;
    private String promptedVersion;
    private boolean checking;
    private boolean downloading;
    private boolean readyToInstall;
    private boolean awaitingPermission;
    private boolean closed;
    private AlertDialog prompt;

    AppUpdater(Activity activity, BooleanSupplier catalogueVisible) {
        this.activity = activity;
        this.catalogueVisible = catalogueVisible;
    }

    void onResume() {
        if (closed || !catalogueVisible.getAsBoolean()) return;
        if (awaitingPermission) {
            awaitingPermission = false;
            if (!activity.getPackageManager().canRequestPackageInstalls()) {
                readyToInstall = false;
                message("Permiso pendiente. Puedes volver a intentarlo al abrir One Pace TV.");
            }
        }
        present();
        long now = SystemClock.elapsedRealtime();
        if (closed || checking || downloading || awaitingPermission || prompt != null || now - lastCheck < CHECK_INTERVAL_MS) return;
        lastCheck = now;
        checking = true;
        worker.execute(() -> {
            UpdateRelease release = null;
            try {
                release = UpdateFiles.check(installedPackage(0).versionName);
            } catch (Exception error) {
                Log.d("OnePaceUpdate", "Could not check releases", error);
            }
            UpdateRelease result = release;
            deliver(() -> {
                checking = false;
                available = result;
                present();
            });
        });
    }

    void present() {
        if (closed || activity.isFinishing() || !catalogueVisible.getAsBoolean() || prompt != null) return;
        if (readyToInstall && !awaitingPermission) {
            showInstaller();
            return;
        }
        if (downloading || awaitingPermission || available == null || available.version.equals(promptedVersion)) return;
        UpdateRelease offer = available;
        promptedVersion = offer.version;
        prompt = new AlertDialog.Builder(activity)
                .setTitle("Actualización disponible")
                .setMessage("One Pace TV " + offer.version + " ya está disponible.")
                .setNegativeButton("Más tarde", null)
                .setPositiveButton("Actualizar", (dialog, button) -> download(offer))
                .create();
        prompt.setOnDismissListener(dialog -> {
            prompt = null;
            present();
        });
        prompt.show();
        prompt.getButton(AlertDialog.BUTTON_POSITIVE).requestFocus();
    }

    private void download(UpdateRelease release) {
        if (closed || downloading) return;
        downloading = true;
        message("Descargando One Pace TV " + release.version + "…");
        worker.execute(() -> {
            File partial = null;
            try {
                partial = File.createTempFile("onepace-", ".apk", activity.getCacheDir());
                UpdateFiles.download(release, partial);
                verifyPackage(partial, release.version);
                Files.move(partial.toPath(), UpdatePackageProvider.file(activity).toPath(),
                        StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
                deliver(() -> {
                    downloading = false;
                    readyToInstall = true;
                    present();
                });
            } catch (Exception error) {
                Log.w("OnePaceUpdate", "Update download rejected", error);
                deliver(() -> {
                    downloading = false;
                    if (catalogueVisible.getAsBoolean()) message("No se pudo preparar la actualización. Inténtalo al volver a abrir la app.");
                });
            } finally {
                if (partial != null && partial.exists() && !partial.delete()) {
                    Log.w("OnePaceUpdate", "Could not remove the temporary download");
                }
            }
        });
    }

    private void verifyPackage(File apk, String expectedVersion) throws IOException, PackageManager.NameNotFoundException {
        int flags = Build.VERSION.SDK_INT >= 28 ? PackageManager.GET_SIGNING_CERTIFICATES : PackageManager.GET_SIGNATURES;
        PackageInfo installed = installedPackage(flags);
        PackageInfo update = activity.getPackageManager().getPackageArchiveInfo(apk.getAbsolutePath(), flags);
        if (update == null || !activity.getPackageName().equals(update.packageName)
                || !expectedVersion.equals(update.versionName) || versionCode(update) <= versionCode(installed)) {
            throw new IOException("Unexpected update package or version");
        }
        Signature[] trusted = signers(installed);
        Signature[] candidate = signers(update);
        if (trusted == null || trusted.length == 0 || candidate == null
                || !new HashSet<>(Arrays.asList(trusted)).equals(new HashSet<>(Arrays.asList(candidate)))) {
            throw new IOException("The update has a different signer");
        }
    }

    private PackageInfo installedPackage(int flags) throws PackageManager.NameNotFoundException {
        return activity.getPackageManager().getPackageInfo(activity.getPackageName(), flags);
    }

    private static long versionCode(PackageInfo info) {
        return Build.VERSION.SDK_INT >= 28 ? info.getLongVersionCode() : info.versionCode;
    }

    private static Signature[] signers(PackageInfo info) {
        if (Build.VERSION.SDK_INT < 28) return info.signatures;
        return info.signingInfo == null ? null : info.signingInfo.getApkContentsSigners();
    }

    private void showInstaller() {
        if (!activity.getPackageManager().canRequestPackageInstalls()) {
            awaitingPermission = true;
            try {
                message("Activa «Permitir desde esta fuente» para One Pace TV y vuelve con Atrás.");
                activity.startActivity(new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                        Uri.parse("package:" + activity.getPackageName())));
                return;
            } catch (ActivityNotFoundException | SecurityException error) {
                // The system installer can provide its own permission prompt on other TV builds.
                awaitingPermission = false;
            }
        }

        readyToInstall = false;
        Uri apk = UpdatePackageProvider.address(activity);
        Intent install = new Intent(Intent.ACTION_VIEW).setDataAndType(apk, UpdatePackageProvider.MIME_TYPE)
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        install.setClipData(ClipData.newRawUri("One Pace TV", apk));
        try {
            activity.startActivity(install);
        } catch (ActivityNotFoundException | SecurityException error) {
            message("Google TV no pudo abrir el instalador.");
        }
    }

    private void message(String text) {
        Toast.makeText(activity, text, Toast.LENGTH_LONG).show();
    }

    private void deliver(Runnable action) {
        activity.runOnUiThread(() -> { if (!closed) action.run(); });
    }

    void close() {
        closed = true;
        if (prompt != null) prompt.dismiss();
        worker.shutdownNow();
    }
}
