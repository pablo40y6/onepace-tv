package com.pablo40y6.onepacetv;

import android.content.Context;
import android.graphics.Color;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.SystemClock;
import android.view.Gravity;
import android.view.SurfaceHolder;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.TextView;
import android.widget.VideoView;

import org.json.JSONException;
import org.json.JSONObject;

final class SubtitlePlayer extends FrameLayout {
    private final VideoView video;
    private final TextView caption;
    private final TextView loading;
    private final CaptionTrack track;
    private final Runnable captionTick = this::updateCaption;
    private MediaPlayer media;
    private int resumeAt;
    private long resumeSeekStartedAt = -1;
    private boolean autoStart = true;
    private boolean restoring;
    private boolean seeking;
    private boolean ended;
    private boolean finished;
    private boolean manualEnd;
    private boolean closed;
    private int preview = -1;
    private long lastStep;

    SubtitlePlayer(Context context, CaptionTrack track) {
        super(context);
        this.track = track;
        setBackgroundColor(Color.BLACK);
        video = new VideoView(context);
        addView(video, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT, Gravity.CENTER));

        caption = new TextView(context);
        caption.setGravity(Gravity.CENTER);
        caption.setTextColor(Color.WHITE);
        caption.setTextSize(23);
        caption.setShadowLayer(dp(2), dp(1), dp(2), Color.BLACK);
        caption.setBackgroundColor(0xd0000000);
        caption.setPadding(dp(12), dp(3), dp(12), dp(3));
        caption.setMaxWidth((int) (getResources().getDisplayMetrics().widthPixels * 0.85));
        caption.setVisibility(INVISIBLE);
        addView(caption, new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT,
                Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL));
        setControlsHeight(0);

        loading = new TextView(context);
        loading.setGravity(Gravity.CENTER);
        loading.setTextColor(Color.WHITE);
        loading.setTextSize(24);
        loading.setBackgroundColor(Color.BLACK);
        loading.setVisibility(GONE);
        addView(loading, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));
    }

    void open(Uri uri, int checkpoint, Runnable onFailure) {
        resumeAt = checkpoint;
        if (checkpoint > 0) showLoading("Cargando capítulo…");
        video.getHolder().addCallback(new SurfaceHolder.Callback() {
            @Override public void surfaceCreated(SurfaceHolder holder) { }
            @Override public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) { }
            @Override public void surfaceDestroyed(SurfaceHolder holder) { media = null; }
        });
        video.setOnErrorListener((player, what, detail) -> {
            android.util.Log.w("OnePacePlayer", "Native playback error " + what + "/" + detail
                    + ", restoring=" + restoring + ", seekStarted=" + (resumeSeekStartedAt >= 0));
            if (!closed) onFailure.run();
            return true;
        });
        video.setOnInfoListener((player, what, detail) -> {
            if (what == MediaPlayer.MEDIA_INFO_VIDEO_RENDERING_START) seekAfterStartup(player);
            return false;
        });
        video.setOnPreparedListener(player -> {
            if (closed) return;
            media = player;
            ended = false;
            seeking = false;
            restoring = resumeAt > 0 && resumeAt < video.getDuration();
            resumeSeekStartedAt = -1;
            player.setVolume(restoring ? 0f : 1f, restoring ? 0f : 1f);
            if (restoring) showLoading("Cargando capítulo…");
            else loading.setVisibility(GONE);
            selectTracks(player);
            player.setOnSeekCompleteListener(ignored -> {
                if (closed || media != player) return;
                seeking = false;
                if (restoring && resumeSeekStartedAt >= 0) {
                    restoring = false;
                    player.setVolume(1f, 1f);
                    if (autoStart) video.start();
                    loading.setVisibility(GONE);
                }
                updateCaption();
            });
            if (autoStart) video.start();
            updateCaption();
        });
        video.setOnCompletionListener(player -> {
            ended = true;
            finished = !manualEnd;
            updateCaption();
        });
        video.setVideoURI(uri);
        video.requestFocus();
    }

    private void seekAfterStartup(MediaPlayer player) {
        if (closed || player != media || !restoring || !autoStart || resumeSeekStartedAt >= 0) return;
        resumeSeekStartedAt = SystemClock.uptimeMillis();
        showLoading("Reanudando en " + EpisodeProgress.timeLabel(resumeAt / 1000) + "…");
        video.pause();
        seekTo(resumeAt);
    }

    private void showLoading(String message) {
        loading.setText(message);
        loading.setVisibility(VISIBLE);
    }

    long resumeSeekStartedAt() {
        return resumeSeekStartedAt;
    }

    private static void selectTracks(MediaPlayer player) {
        // The clean picture comes from the official MKV. Display only the separately adjusted Spanish text.
        for (int type : new int[]{MediaPlayer.TrackInfo.MEDIA_TRACK_TYPE_SUBTITLE,
                MediaPlayer.TrackInfo.MEDIA_TRACK_TYPE_TIMEDTEXT}) {
            int selected = player.getSelectedTrack(type);
            if (selected >= 0) player.deselectTrack(selected);
        }
        MediaPlayer.TrackInfo[] choices = player.getTrackInfo();
        for (int i = 0; i < choices.length; i++) {
            if (choices[i].getTrackType() != MediaPlayer.TrackInfo.MEDIA_TRACK_TYPE_AUDIO) continue;
            String language = choices[i].getLanguage();
            if ("jpn".equals(language) || "ja".equals(language)) {
                if (player.getSelectedTrack(MediaPlayer.TrackInfo.MEDIA_TRACK_TYPE_AUDIO) != i) player.selectTrack(i);
                break;
            }
        }
    }

    JSONObject snapshot(SubtitleSources.Source source) throws JSONException {
        if (media == null) return null;
        // Some players advance without reporting the first rendered frame.
        if (restoring && video.isPlaying() && video.getCurrentPosition() >= 200) seekAfterStartup(media);
        updateCaption();
        int time = video.getCurrentPosition();
        int duration = video.getDuration();
        int remaining = duration - time;
        boolean complete = finished && preview < 0;
        if (complete) finished = false;
        return new JSONObject().put("item", source.item).put("file", source.file)
                .put("time", time / 1000.0).put("duration", duration / 1000.0)
                .put("cinema", duration > 0).put("playing", video.isPlaying())
                .put("seeking", seeking).put("restoring", restoring).put("ended", ended)
                .put("preview", preview < 0 ? JSONObject.NULL : preview / 1000.0)
                .put("finished", complete ? source.item : -1)
                .put("nextIn", !ended && !seeking && remaining > 0 && remaining <= 5000 ? (remaining + 999) / 1000 : 0);
    }

    void focusVideo() {
        video.requestFocus();
    }

    void setControlsHeight(int height) {
        LayoutParams params = (LayoutParams) caption.getLayoutParams();
        int margin = dp(24) + height;
        if (params.bottomMargin != margin) {
            params.bottomMargin = margin;
            caption.setLayoutParams(params);
        }
    }

    void control(String action) {
        if ("pause".equals(action) || ("toggle".equals(action) && video.isPlaying())) {
            autoStart = false;
            if (media != null && !restoring && !seeking && !ended) resumeAt = video.getCurrentPosition();
            video.pause();
        } else {
            autoStart = true;
            ended = false;
            manualEnd = false;
            if (!restoring || resumeSeekStartedAt < 0) video.start();
        }
        updateCaption();
    }

    void seekBy(int seconds) {
        seekTo(video.getCurrentPosition() + seconds * 1000);
    }

    void skipIntro(IntroTimings.Window intro) {
        if (media == null || seeking || restoring || ended) return;
        int duration = video.getDuration();
        if (!intro.contains(video.getCurrentPosition() / 1000.0, duration / 1000.0)) return;
        int target = (int) Math.round(intro.end * 1000);
        if (target < duration) seekTo(target);
    }

    private void seekTo(int position) {
        if (media == null) return;
        int duration = video.getDuration();
        if (duration <= 0) return;
        int target = Math.max(0, Math.min(position, duration));
        manualEnd = target == duration;
        ended = false;
        seeking = true;
        resumeAt = target;
        media.seekTo(target, MediaPlayer.SEEK_CLOSEST);
        updateCaption();
    }

    void preview(int direction, long heldMs) {
        if (media == null) return;
        if (heldMs == 0) preview = video.getCurrentPosition();
        else if (preview < 0 || heldMs < 400 || heldMs - lastStep < 280) return;
        int step = heldMs >= 2000 ? 60_000 : heldMs >= 1000 ? 30_000 : 10_000;
        preview = Math.max(0, Math.min(video.getDuration(), preview + direction * step));
        lastStep = heldMs;
    }

    void commitPreview() {
        if (preview < 0) return;
        boolean resume = finished && preview < video.getDuration();
        finished = false;
        seekTo(preview);
        if (resume) video.start();
        cancelPreview();
    }

    void cancelPreview() {
        preview = -1;
    }

    private void updateCaption() {
        removeCallbacks(captionTick);
        String value = media == null || restoring || ended || seeking ? "" : track.at(video.getCurrentPosition());
        if (!value.contentEquals(caption.getText())) caption.setText(value);
        caption.setVisibility(value.isEmpty() ? INVISIBLE : VISIBLE);
        if (!closed && video.isPlaying()) postDelayed(captionTick, 80);
    }

    void close() {
        closed = true;
        removeCallbacks(captionTick);
        video.setOnPreparedListener(null);
        video.setOnCompletionListener(null);
        video.setOnErrorListener(null);
        video.setOnInfoListener(null);
        video.stopPlayback();
        media = null;
        if (getParent() instanceof ViewGroup) ((ViewGroup) getParent()).removeView(this);
    }

    private int dp(int size) {
        return Math.round(size * getResources().getDisplayMetrics().density);
    }
}
