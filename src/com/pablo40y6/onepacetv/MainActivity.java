package com.pablo40y6.onepacetv;

import android.app.Activity;
import android.app.AlertDialog;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.view.KeyEvent;
import android.view.View;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;
import android.widget.Toast;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import com.pablo40y6.onepacetv.PlaybackCatalog.Episode;
import com.pablo40y6.onepacetv.SubtitleSources.Source;

public final class MainActivity extends Activity {
    private static final long TICK_MS = 400;
    private static final long OPEN_TIMEOUT_MS = 30_000;
    private static final long RESUME_TIMEOUT_MS = 60_000;
    private static final long MENU_IDLE_MS = 5_000;
    private static final long SAVE_PROGRESS_MS = 5_000;
    private static final long CLOSE_SNAPSHOT_TIMEOUT_MS = 300;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable tick = this::pollPlayback;
    private final Runnable hideIdleMenu = this::closeControls;
    private final Runnable finishReturn = this::finishClosePlayer;
    private FrameLayout root;
    private CatalogScreen launcher;
    private LanguageScreen languageScreen;
    private AppUpdater updater;
    private AlertDialog resumeFailure;
    private CatalogRefresher catalogueRefresh;
    private PlaybackCatalog refreshedCatalogue;
    private WebView player;
    private SubtitlePlayer subtitlePlayer;
    private PlayerControls controls;
    private PlaybackCatalog catalog;
    private String playbackScript;
    private View fullscreen;
    private WebChromeClient.CustomViewCallback fullscreenCallback;
    private boolean foreground;
    private boolean playerOpen;
    private boolean returningToCatalog;
    private boolean controllerReady;
    private boolean cinemaReady;
    private boolean playbackRunning;
    private int documentGeneration;
    private int timelineDirection;
    private Episode pending;
    private Episode reported;
    private Episode selected;
    private Source source;
    private Episode cancelledNext;
    private Episode positionEpisode;
    private EpisodeProgress latestPosition;
    private EpisodeProgress resumePosition;
    private IntroTimings.Window activeIntro;
    private long pendingUntil;
    private long lastSavedAt;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        SubtitleSources subtitles;
        try {
            catalogueRefresh = new CatalogRefresher(this, assetText("catalog.json"), assetText(IntroTimings.FILE));
            catalog = catalogueRefresh.initialCatalog();
            playbackScript = assetText("player.js");
            subtitles = new SubtitleSources(assetText(SubtitleSources.FILE), assetText(SubtitleSources.OVERRIDES));
        } catch (IOException | JSONException error) {
            Toast.makeText(this, "No se pudo cargar el catálogo de la app.", Toast.LENGTH_LONG).show();
            finish();
            return;
        }

        root = new FrameLayout(this);
        launcher = new CatalogScreen(this, catalog, subtitles, episode ->
                confirmLanguage(episode, () -> openPlayer(episode)), this::showLanguageScreen);
        root.addView(launcher, matchParent());
        setContentView(root);
        updater = new AppUpdater(this, () -> foreground && !playerOpen && languageScreen == null && resumeFailure == null);
        if (launcher.hasLanguagePreference()) launcher.showHome();
        else showLanguageScreen();
        immersive();
    }

    private void showLanguageScreen() {
        launcher.setVisibility(View.GONE);
        languageScreen = new LanguageScreen(this, spanish -> {
            launcher.selectLanguage(spanish);
            closeLanguageScreen();
        });
        root.addView(languageScreen, matchParent());
        languageScreen.focusLanguage(!launcher.hasLanguagePreference() || launcher.prefersSpanish());
    }

    private void closeLanguageScreen() {
        root.removeView(languageScreen);
        languageScreen = null;
        launcher.setVisibility(View.VISIBLE);
        launcher.showHome();
        applyCatalogueRefresh();
        updater.present();
    }

    private WebView makePlayer() {
        WebView view = new WebView(this);
        WebSettings settings = view.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setAllowFileAccess(false);
        settings.setAllowContentAccess(false);
        settings.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
        settings.setMediaPlaybackRequiresUserGesture(false);
        view.setBackgroundColor(0xff101012);
        view.setFocusableInTouchMode(true);
        view.setWebViewClient(new PlayerClient());
        view.setWebChromeClient(new PlayerChrome());
        return view;
    }

    private static FrameLayout.LayoutParams matchParent() {
        return new FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT);
    }

    private String assetText(String path) throws IOException {
        try (InputStream file = getAssets().open(path); ByteArrayOutputStream result = new ByteArrayOutputStream()) {
            byte[] bytes = new byte[4096];
            int read;
            while ((read = file.read(bytes)) != -1) result.write(bytes, 0, read);
            return new String(result.toByteArray(), StandardCharsets.UTF_8);
        }
    }

    private final class PlayerClient extends WebViewClient {
        @Override public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
            return request.isForMainFrame() && activeList(request.getUrl()) == null;
        }

        @Override public void onPageStarted(WebView view, String url, Bitmap favicon) {
            documentGeneration++;
            controllerReady = false;
        }
    }

    private final class PlayerChrome extends WebChromeClient {
        @Override public void onShowCustomView(View view, CustomViewCallback callback) {
            if (!playerOpen || fullscreen != null) {
                callback.onCustomViewHidden();
                return;
            }
            fullscreen = view;
            fullscreenCallback = callback;
            root.addView(view, matchParent());
            view.requestFocus();
            immersive();
        }

        @Override public void onHideCustomView() {
            hideFullscreen();
        }
    }

    private void hideFullscreen() {
        if (fullscreen == null) return;
        root.removeView(fullscreen);
        fullscreen = null;
        WebChromeClient.CustomViewCallback callback = fullscreenCallback;
        fullscreenCallback = null;
        callback.onCustomViewHidden();
        player.requestFocus();
        immersive();
    }

    private void openPlayer(Episode episode) {
        openPlayer(episode, true);
    }

    private void openPlayer(Episode episode, boolean useCheckpoint) {
        if (player == null) {
            try {
                player = makePlayer();
                root.addView(player, matchParent());
                controls = new PlayerControls(this, () -> changeChapter(false), this::seek,
                        () -> changeChapter(true), this::skipIntro, this::cancelNext);
                controls.attachTo(root);
            } catch (RuntimeException error) {
                Toast.makeText(this, "No se pudo abrir el reproductor de Android.", Toast.LENGTH_LONG).show();
                return;
            }
        }
        playerOpen = true;
        reported = null;
        launcher.setVisibility(View.GONE);
        player.setVisibility(View.VISIBLE);
        launcher.started(episode);
        loadEpisode(episode, useCheckpoint);
        schedulePoll();
    }

    private void loadEpisode(Episode episode) {
        loadEpisode(episode, true);
    }

    private void loadEpisode(Episode episode, boolean useCheckpoint) {
        saveLastPosition();
        latestPosition = null;
        positionEpisode = null;
        reported = null;
        closeControls();
        closeSubtitlePlayer();
        cinemaReady = false;
        controllerReady = false;
        documentGeneration++;
        source = launcher.playbackSource(episode);
        requestPlayback(episode, useCheckpoint);
        if (source.captions != null) {
            openSubtitlePlayer();
            return;
        }
        player.setVisibility(View.VISIBLE);
        String previousUrl = player.getUrl();
        Uri destination = Uri.parse(source.url());
        if (previousUrl != null && activeList(Uri.parse(previousUrl)) != null) {
            // Force a new document for an explicit jump in the same gallery.
            destination = destination.buildUpon()
                    .appendQueryParameter("onepace_tv", Integer.toString(documentGeneration)).build();
        }
        player.loadUrl(destination.toString());
        player.requestFocus();
    }

    private void openSubtitlePlayer() {
        try {
            CaptionTrack track = new CaptionTrack(assetText(source.captions));
            player.stopLoading();
            player.loadUrl("about:blank");
            player.setVisibility(View.GONE);
            subtitlePlayer = new SubtitlePlayer(this, track);
            root.addView(subtitlePlayer, root.indexOfChild(player) + 1, matchParent());
            int resume = resumePosition != null && source.file.equals(resumePosition.file)
                    ? (int) resumePosition.positionMs : 0;
            subtitlePlayer.open(Uri.parse(source.url()), resume, () -> {
                cinemaReady = false;
                announcePlaybackFailure();
            });
        } catch (IOException | JSONException error) {
            announcePlaybackFailure();
        }
    }

    private void closeSubtitlePlayer() {
        if (subtitlePlayer == null) return;
        subtitlePlayer.close();
        subtitlePlayer = null;
    }

    private void confirmLanguage(Episode episode, Runnable play) {
        if (!launcher.prefersSpanish() || launcher.playbackSource(episode).spanish()) {
            play.run();
            return;
        }
        if (playerOpen) closeControls();
        new AlertDialog.Builder(this).setTitle("Solo disponible en inglés")
                .setMessage(episode.arc.title + " · Capítulo " + episode.number
                        + "\nLos subtítulos en español aún no están disponibles para este capítulo.")
                .setNegativeButton("Volver", null)
                .setPositiveButton("Ver en inglés", (dialog, button) -> play.run()).show();
    }

    private String activeList(Uri uri) {
        return source != null && source.list.equals(PlaybackCatalog.pixeldrainList(uri)) ? source.list : null;
    }

    private Episode playingEpisode(String list, int item) {
        if (!source.spanish()) return catalog.find(list, item);
        return item == source.item ? selected : null;
    }

    private void closePlayer() {
        if (returningToCatalog) return;
        returningToCatalog = true;
        handler.removeCallbacks(tick);
        saveLastPosition();
        if (capturePausedPosition(finishReturn)) handler.postDelayed(finishReturn, CLOSE_SNAPSHOT_TIMEOUT_MS);
        else finishClosePlayer();
    }

    private void finishClosePlayer() {
        if (!returningToCatalog) return;
        returningToCatalog = false;
        handler.removeCallbacks(finishReturn);
        setIntro(null);
        showCountdown(0);
        setPlaybackRunning(false);
        closeControls();
        hideFullscreen();
        controlPlayer("pause");
        closeSubtitlePlayer();
        playerOpen = false;
        pending = null;
        positionEpisode = null;
        latestPosition = null;
        documentGeneration++;
        handler.removeCallbacks(tick);
        player.stopLoading();
        player.loadUrl("about:blank");
        player.setVisibility(View.GONE);
        launcher.setVisibility(View.VISIBLE);
        launcher.showHome();
        applyCatalogueRefresh();
        if (updater != null) updater.present();
    }

    private void schedulePoll() {
        handler.removeCallbacks(tick);
        if (foreground && playerOpen && !returningToCatalog) handler.post(tick);
    }

    private void requestPlayback(Episode episode, boolean useCheckpoint) {
        setIntro(null);
        showCountdown(0);
        setPlaybackRunning(false);
        cancelledNext = null;
        pending = episode;
        selected = episode;
        resumePosition = useCheckpoint ? launcher.position(episode, source.spanish()) : null;
        long now = SystemClock.uptimeMillis();
        lastSavedAt = now;
        pendingUntil = now + OPEN_TIMEOUT_MS;
    }

    private void pollPlayback() {
        if (!foreground || !playerOpen || returningToCatalog) return;
        handler.postDelayed(tick, TICK_MS);
        String url = player.getUrl();
        String list = url == null ? null : activeList(Uri.parse(url));
        long seekStartedAt = subtitlePlayer == null ? -1 : subtitlePlayer.resumeSeekStartedAt();
        long deadline = seekStartedAt >= 0 ? seekStartedAt + RESUME_TIMEOUT_MS : pendingUntil;
        if (pending != null && SystemClock.uptimeMillis() >= deadline) {
            announcePlaybackFailure();
            if (!playerOpen || returningToCatalog) return;
        }
        if (subtitlePlayer != null) {
            try {
                JSONObject state = subtitlePlayer.snapshot(source);
                if (state != null) {
                    subtitlePlayer.setControlsHeight(controls.visible() ? controls.getHeight() : 0);
                    updatePlayback(source.list, state);
                }
            } catch (JSONException ignored) {
                cinemaReady = false;
                announcePlaybackFailure();
            }
            return;
        }
        if (list == null) return;

        int requested = pending != null ? source.item : -1;
        int generation = documentGeneration;
        EpisodeProgress checkpoint = requested >= 0 ? resumePosition : null;
        String resume = checkpoint == null ? "null,null" : JSONObject.quote(checkpoint.file) + "," + checkpoint.seconds();
        String expected = source.spanish() ? JSONObject.quote(source.file) : "null";
        String poll = "\nwindow.onePacePlayback?.poll(" + requested + "," + resume + "," + expected + ") ?? null;";
        player.evaluateJavascript((controllerReady ? "" : playbackScript) + poll, result -> {
            if (!playerOpen || returningToCatalog || generation != documentGeneration) return;
            try {
                JSONObject state = new JSONObject(result);
                if (!("/l/" + list).equals(state.optString("path"))) return;
                controllerReady = true;
                updatePlayback(list, state);
            } catch (JSONException | NullPointerException ignored) {
                controllerReady = false;
            }
        });
    }

    private void updatePlayback(String list, JSONObject state) {
        Episode ended = playingEpisode(list, state.optInt("finished", -1));
        if (ended != null && ended == selected) {
            setIntro(null);
            showCountdown(0);
            setPlaybackRunning(false);
            closeControls();
            latestPosition = null;
            positionEpisode = null;
            launcher.completed(ended, source.spanish());
            Episode next = catalog.next(ended);
            pending = null;
            if (next == null) {
                Toast.makeText(this, "Último capítulo completado.", Toast.LENGTH_SHORT).show();
            } else if (ended != cancelledNext) {
                confirmLanguage(next, () -> loadEpisode(next));
            }
            return;
        }

        Episode current = playingEpisode(list, state.optInt("item", -1));
        boolean matches = current != null && (pending == null || current == pending);
        cinemaReady = matches && state.optBoolean("cinema") && !state.optBoolean("restoring");
        setIntro(cinemaReady && !state.optBoolean("seeking") ? catalogueRefresh.introAt(current.id, state.optString("file"),
                state.optDouble("time"), state.optDouble("duration")) : null);
        boolean playing = cinemaReady && state.optBoolean("playing");
        setPlaybackRunning(playing);
        showCountdown(cinemaReady && current != cancelledNext && catalog.next(current) != null
                ? state.optInt("nextIn") : 0);
        if (cinemaReady) updateTimeline(state);
        else {
            closeControls();
            controls.updateTimeline(Double.NaN, Double.NaN, Double.NaN);
        }
        if (matches) selected = current;
        if (playing) {
            if (current != reported) {
                launcher.started(current);
                reported = current;
            }
            if (current == pending) pending = null;
        }
        if (cinemaReady && current == reported && pending == null && rememberPosition(current, state)
                && SystemClock.uptimeMillis() - lastSavedAt >= SAVE_PROGRESS_MS) saveLastPosition();
        if (pending != null && current == pending && state.optBoolean("failed")) {
            announcePlaybackFailure();
        }
    }

    private boolean rememberPosition(Episode episode, JSONObject state) {
        if (state.optBoolean("restoring") || state.optBoolean("seeking") || state.optBoolean("ended")) return false;
        if (source.spanish() && !source.file.equals(state.optString("file"))) return false;
        EpisodeProgress position = EpisodeProgress.fromMedia(state.optString("file"),
                state.optDouble("time"), state.optDouble("duration"));
        if (position == null) return false;
        latestPosition = position;
        positionEpisode = episode;
        return true;
    }

    private void saveLastPosition() {
        if (latestPosition == null || positionEpisode == null) return;
        launcher.savePosition(positionEpisode, latestPosition, source.spanish());
        lastSavedAt = SystemClock.uptimeMillis();
    }

    private boolean capturePausedPosition(Runnable afterCapture) {
        Episode episode = reported;
        if (subtitlePlayer != null) {
            subtitlePlayer.control("pause");
            try {
                JSONObject state = subtitlePlayer.snapshot(source);
                if (episode == selected && episode != null && state != null && rememberPosition(episode, state)) {
                    saveLastPosition();
                }
            } catch (JSONException ignored) {
                // The last periodic snapshot is already saved.
            }
            return false;
        }
        if (!playerOpen || !cinemaReady || episode == null || episode != selected || pending != null) return false;
        int generation = documentGeneration;
        Source media = source;
        player.evaluateJavascript("window.onePacePlayback?.pauseAndSnapshot() ?? null;", result -> {
            if (!playerOpen || generation != documentGeneration || episode != selected || episode != reported) return;
            try {
                JSONObject state = new JSONObject(result);
                if (state.optInt("item", -1) == media.item && ("/l/" + media.list).equals(state.optString("path"))
                        && rememberPosition(episode, state)) saveLastPosition();
            } catch (JSONException | NullPointerException ignored) {
                // The latest periodic sample has already been saved.
            }
            if (afterCapture != null) afterCapture.run();
        });
        return true;
    }

    private void announcePlaybackFailure() {
        Episode failedResume = subtitlePlayer != null && resumePosition != null && resumePosition.positionMs > 0
                && source.file.equals(resumePosition.file) ? pending : null;
        pending = null;
        if (failedResume != null) {
            resumeFailure = new AlertDialog.Builder(this)
                    .setTitle("No se pudo reanudar el capítulo")
                    .setMessage("Tu posición del " + resumePosition.timeLabel() + " sigue guardada.")
                    .setPositiveButton("Reintentar", (dialog, which) -> openPlayer(failedResume))
                    .setNeutralButton("Ver desde el principio", (dialog, which) -> openPlayer(failedResume, false))
                    .setNegativeButton("Volver", null)
                    .create();
            resumeFailure.setOnDismissListener(dialog -> resumeFailure = null);
            closePlayer();
            resumeFailure.show();
            return;
        }
        if (source.spanish() && !cinemaReady) {
            Toast.makeText(this, "Pixeldrain no pudo abrir esta versión en español.", Toast.LENGTH_LONG).show();
            closePlayer();
            return;
        }
        String action = cinemaReady ? "Pulsa OK para intentarlo." : "Pulsa Reproducir para intentarlo.";
        Toast.makeText(this, "Pixeldrain no pudo iniciar el vídeo. " + action, Toast.LENGTH_LONG).show();
    }

    private void controlPlayer(String action) {
        if (subtitlePlayer != null) {
            subtitlePlayer.control(action);
            return;
        }
        if (!playerOpen || player.getUrl() == null || activeList(Uri.parse(player.getUrl())) == null) return;
        player.evaluateJavascript(playbackScript + "\nwindow.onePacePlayback?.control(" + JSONObject.quote(action) + ");", null);
    }

    private void seek(int seconds) {
        if (!cinemaReady) return;
        if (subtitlePlayer != null) subtitlePlayer.seekBy(seconds);
        else player.evaluateJavascript("window.onePacePlayback?.seek(" + seconds + ");", null);
    }

    private void updateTimeline(JSONObject state) {
        controls.updateTimeline(state.optDouble("time"), state.optDouble("duration"), state.optDouble("preview"));
    }

    private void timelineCommand(String command) {
        Episode episode = selected;
        Source media = source;
        int generation = documentGeneration;
        player.evaluateJavascript("window.onePacePlayback?." + command + " ?? null;", result -> {
            if (!playerOpen || !controls.visible() || episode != selected || generation != documentGeneration
                    || result == null || result.equals("null")) return;
            try {
                JSONObject state = new JSONObject(result);
                if (state.optInt("item", -1) == media.item && ("/l/" + media.list).equals(state.optString("path"))) {
                    updateTimeline(state);
                }
            } catch (JSONException ignored) {
                // The next playback poll supplies a fresh timeline.
            }
        });
    }

    private void navigateTimeline(KeyEvent event, int direction) {
        if (event.getAction() == KeyEvent.ACTION_DOWN) {
            boolean first = event.getRepeatCount() == 0;
            if (!first && timelineDirection != direction) return;
            timelineDirection = direction;
            long heldMs = first ? 0 : Math.max(1, event.getEventTime() - event.getDownTime());
            if (subtitlePlayer != null) subtitlePlayer.preview(direction, heldMs);
            else timelineCommand("previewTimelineSeek(" + source.item + "," + direction + "," + heldMs + ")");
            restartMenuTimeout();
        } else if (event.getAction() == KeyEvent.ACTION_UP && timelineDirection == direction) {
            if (event.isCanceled() || !controls.timelineFocused()) cancelTimelineSeek();
            else {
                timelineDirection = 0;
                if (subtitlePlayer != null) subtitlePlayer.commitPreview();
                else timelineCommand("commitTimelineSeek(" + source.item + "," + direction + ")");
            }
            restartMenuTimeout();
        }
    }

    private void cancelTimelineSeek() {
        if (timelineDirection == 0) return;
        timelineDirection = 0;
        if (subtitlePlayer != null) subtitlePlayer.cancelPreview();
        else player.evaluateJavascript("window.onePacePlayback?.cancelTimelineSeek();", null);
    }

    private void setIntro(IntroTimings.Window intro) {
        activeIntro = intro;
        controls.setIntroAvailable(intro != null);
    }

    private void skipIntro() {
        IntroTimings.Window intro = activeIntro;
        if (!cinemaReady || intro == null || selected == null) return;
        if (subtitlePlayer != null) {
            if (!intro.matches(selected.id, source.file)) return;
            subtitlePlayer.skipIntro(intro);
        } else {
            player.evaluateJavascript("window.onePacePlayback?.skipIntro(" + source.item + ","
                    + JSONObject.quote(intro.file) + "," + intro.start + "," + intro.end + ");", null);
        }
        setIntro(null);
        closeControls();
    }

    private void changeChapter(boolean forward) {
        if (!cinemaReady || selected == null) return;
        Episode destination = forward ? catalog.next(selected) : catalog.previous(selected);
        if (destination != null) {
            confirmLanguage(destination, () -> {
                launcher.started(destination);
                loadEpisode(destination);
            });
        }
    }

    private void cancelNext() {
        if (selected == null || !controls.countdownVisible()) return;
        cancelledNext = selected;
        showCountdown(0);
    }

    private void showCountdown(int seconds) {
        boolean restoreFocus = seconds <= 0 && controls.cancelFocused();
        controls.showCountdown(seconds);
        if (restoreFocus) focusPlayback();
    }

    private void focusPlayback() {
        if (controls.visible()) controls.focusMenu();
        else if (fullscreen != null) fullscreen.requestFocus();
        else if (subtitlePlayer != null) subtitlePlayer.focusVideo();
        else player.requestFocus();
    }

    private void setPlaybackRunning(boolean playing) {
        if (playbackRunning == playing) return;
        playbackRunning = playing;
        restartMenuTimeout();
    }

    private void restartMenuTimeout() {
        handler.removeCallbacks(hideIdleMenu);
        if (foreground && playbackRunning && controls.visible() && timelineDirection == 0) {
            handler.postDelayed(hideIdleMenu, MENU_IDLE_MS);
        }
    }

    private void closeControls() {
        handler.removeCallbacks(hideIdleMenu);
        cancelTimelineSeek();
        if (controls != null && controls.visible()) {
            controls.close();
            if (!controls.cancelFocused()) focusPlayback();
        }
    }

    private boolean handlePlayerNavigation(KeyEvent event) {
        if (!cinemaReady && pending == null && !controls.visible()) return false;
        boolean down = event.getAction() == KeyEvent.ACTION_DOWN;
        boolean first = down && event.getRepeatCount() == 0;
        switch (event.getKeyCode()) {
            case KeyEvent.KEYCODE_DPAD_DOWN:
                if (first && cinemaReady) {
                    if (!controls.visible()) {
                        if (controls.countdownVisible() && !controls.cancelFocused()) controls.focusCancel();
                        else {
                            controls.open(catalog.previous(selected) != null, catalog.next(selected) != null);
                            restartMenuTimeout();
                        }
                    } else if (controls.cancelFocused()) controls.focusMenu();
                    else if ((controls.timelineFocused() || !controls.focusTimeline()) && controls.countdownVisible()) {
                        cancelTimelineSeek();
                        controls.focusCancel();
                        restartMenuTimeout();
                    }
                }
                return true;
            case KeyEvent.KEYCODE_DPAD_UP:
                if (first && controls.cancelFocused()) focusPlayback();
                else if (first && controls.timelineFocused()) {
                    cancelTimelineSeek();
                    controls.focusMenu();
                    restartMenuTimeout();
                }
                else if (first) closeControls();
                return true;
            case KeyEvent.KEYCODE_DPAD_LEFT:
            case KeyEvent.KEYCODE_DPAD_RIGHT:
                int direction = event.getKeyCode() == KeyEvent.KEYCODE_DPAD_LEFT ? -1 : 1;
                if (cinemaReady && controls.timelineFocused()) navigateTimeline(event, direction);
                else if (down && controls.visible() && !controls.cancelFocused()) controls.move(direction);
                return true;
            case KeyEvent.KEYCODE_DPAD_CENTER:
            case KeyEvent.KEYCODE_ENTER:
            case KeyEvent.KEYCODE_NUMPAD_ENTER:
                if (first && controls.cancelFocused()) controls.activateCancel();
                else if (controls.timelineFocused()) return true;
                else if (first && controls.visible()) controls.activate();
                else if (first && cinemaReady) controlPlayer("toggle");
                return true;
            default:
                return false;
        }
    }

    @Override public boolean dispatchKeyEvent(KeyEvent event) {
        if (returningToCatalog) return true;
        if (playerOpen) {
            if (event.getAction() == KeyEvent.ACTION_DOWN && controls.visible()) restartMenuTimeout();
            if (handlePlayerNavigation(event)) return true;
            String action = null;
            switch (event.getKeyCode()) {
                case KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE: action = "toggle"; break;
                case KeyEvent.KEYCODE_MEDIA_PLAY: action = "play"; break;
                case KeyEvent.KEYCODE_MEDIA_PAUSE: action = "pause"; break;
                default: break;
            }
            if (action != null) {
                if (event.getAction() == KeyEvent.ACTION_DOWN && event.getRepeatCount() == 0) controlPlayer(action);
                return true;
            }
        }
        return super.dispatchKeyEvent(event);
    }

    @Override public void onBackPressed() {
        if (languageScreen != null) {
            if (launcher.hasLanguagePreference()) closeLanguageScreen();
            else super.onBackPressed();
        } else if (playerOpen && controls.cancelFocused()) focusPlayback();
        else if (playerOpen && controls.visible()) closeControls();
        else if (fullscreen != null) hideFullscreen();
        else if (playerOpen) closePlayer();
        else if (launcher == null || !launcher.back()) super.onBackPressed();
        else applyCatalogueRefresh();
    }

    private void applyCatalogueRefresh() {
        if (refreshedCatalogue == null || !foreground || playerOpen || languageScreen != null
                || isFinishing() || !launcher.isHome()) return;
        catalog = refreshedCatalogue;
        refreshedCatalogue = null;
        launcher.updateCatalog(catalog);
    }

    private void immersive() {
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_FULLSCREEN
                | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
    }

    @Override public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) immersive();
    }

    @Override protected void onResume() {
        super.onResume();
        foreground = true;
        if (player != null) player.onResume();
        if (pending != null && subtitlePlayer != null) subtitlePlayer.control("play");
        schedulePoll();
        if (updater != null) updater.onResume();
        applyCatalogueRefresh();
        if (catalogueRefresh != null) catalogueRefresh.check(replacement -> {
            refreshedCatalogue = replacement;
            applyCatalogueRefresh();
        });
    }

    @Override protected void onPause() {
        foreground = false;
        handler.removeCallbacks(tick);
        cancelTimelineSeek();
        setPlaybackRunning(false);
        saveLastPosition();
        if (!returningToCatalog && !capturePausedPosition(null)) controlPlayer("pause");
        if (player != null) player.onPause();
        super.onPause();
    }

    @Override protected void onDestroy() {
        handler.removeCallbacks(tick);
        handler.removeCallbacks(hideIdleMenu);
        handler.removeCallbacks(finishReturn);
        saveLastPosition();
        if (updater != null) updater.close();
        if (resumeFailure != null) resumeFailure.dismiss();
        if (catalogueRefresh != null) catalogueRefresh.close();
        if (root != null) {
            playerOpen = false;
            hideFullscreen();
            closeSubtitlePlayer();
            root.removeAllViews();
            if (player != null) player.destroy();
        }
        super.onDestroy();
    }
}
