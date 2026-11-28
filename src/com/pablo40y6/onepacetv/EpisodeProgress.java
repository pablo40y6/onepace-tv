package com.pablo40y6.onepacetv;

import java.util.Locale;

final class EpisodeProgress {
    static final int BAR_MAX = 1000;
    private static final long MAX_DURATION_MS = 24 * 60 * 60 * 1000;
    final String file;
    final long positionMs;
    final long durationMs;

    private EpisodeProgress(String file, long positionMs, long durationMs) {
        this.file = file;
        this.positionMs = positionMs;
        this.durationMs = durationMs;
    }

    static EpisodeProgress fromMedia(String file, double position, double duration) {
        if (!Double.isFinite(position) || !Double.isFinite(duration) || position < 0) return null;
        return fromMillis(file, Math.round(position * 1000), Math.round(duration * 1000));
    }

    static EpisodeProgress fromStorage(String value) {
        if (value == null) return null;
        String[] fields = value.split(":", -1);
        if (fields.length != 3) return null;
        try {
            return fromMillis(fields[0], Long.parseLong(fields[1]), Long.parseLong(fields[2]));
        } catch (NumberFormatException invalid) {
            return null;
        }
    }

    private static EpisodeProgress fromMillis(String file, long positionMs, long durationMs) {
        if (file == null || !file.matches("[A-Za-z0-9_-]{6,16}") || positionMs < 0 || positionMs >= durationMs
                || durationMs > MAX_DURATION_MS) return null;
        return new EpisodeProgress(file, positionMs, durationMs);
    }

    String storedValue() {
        return file + ":" + positionMs + ":" + durationMs;
    }

    double seconds() {
        return positionMs / 1000.0;
    }

    int barProgress() {
        return positionMs == 0 ? 0 : Math.max(1, (int) (positionMs * BAR_MAX / durationMs));
    }

    String timeLabel() {
        return timeLabel(positionMs / 1000);
    }

    static String timeLabel(long seconds) {
        if (seconds >= 3600) {
            return String.format(Locale.ROOT, "%d:%02d:%02d", seconds / 3600, seconds / 60 % 60, seconds % 60);
        }
        return String.format(Locale.ROOT, "%d:%02d", seconds / 60, seconds % 60);
    }
}
