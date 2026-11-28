package com.pablo40y6.onepacetv;

public final class EpisodeProgressTest {
    public static void main(String[] args) {
        EpisodeProgress sample = EpisodeProgress.fromMedia("demoFile", 94.375, 190);
        EpisodeProgress restored = EpisodeProgress.fromStorage(sample.storedValue());
        require(restored.file.equals("demoFile") && restored.seconds() == 94.375,
                "Preserve the file and exact position across restarts");
        require(restored.barProgress() == 496 && restored.timeLabel().equals("1:34"),
                "Show the elapsed fraction and readable time");
        require(EpisodeProgress.fromMedia("demoFile", 0, 190).barProgress() == 0,
                "A video at the beginning has an empty bar");
        require(EpisodeProgress.fromMedia("demoFile", 189.999, 190).barProgress() == EpisodeProgress.BAR_MAX - 1,
                "An unfinished episode cannot have a full bar");
        require(EpisodeProgress.fromMedia("demoFile", 3665, 7200).timeLabel().equals("1:01:05"),
                "Include hours on long videos");
        require(EpisodeProgress.fromMedia("demoFile", 190, 190) == null, "Reject the completed position");
        require(EpisodeProgress.fromMedia("demoFile", 15, Double.POSITIVE_INFINITY) == null,
                "Reject unbounded media");
        require(EpisodeProgress.fromMedia(null, 15, 190) == null, "Reject an unknown file");
        require(EpisodeProgress.fromStorage(null) == null && EpisodeProgress.fromStorage("bad:data") == null,
                "An absent or invalid checkpoint is ignored");
        System.out.println("Verified playback checkpoint precision, progress bars, and input validation.");
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
