package com.mongenscave.mcspotifylink.model;

import lombok.Getter;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;

@Getter
public class CurrentTrack {
    private final String trackName;
    private final String artistName;
    private final long durationMs;
    private final long progressMs;
    private final long timestamp;
    private final boolean isPlaying;

    public CurrentTrack(@NotNull String trackName, @NotNull String artistName, long durationMs, long progressMs, boolean isPlaying) {
        this.trackName = trackName;
        this.artistName = artistName;
        this.durationMs = durationMs;
        this.progressMs = progressMs;
        this.timestamp = System.currentTimeMillis();
        this.isPlaying = isPlaying;
    }

    public long getCurrentProgressMs() {
        if (!isPlaying) return progressMs;

        long timePassed = System.currentTimeMillis() - timestamp;
        long currentProgress = progressMs + timePassed;

        return Math.min(currentProgress, durationMs);
    }

    public double getProgressPercentage() {
        if (durationMs == 0) return 0.0;
        return Math.min(1.0, (double) getCurrentProgressMs() / durationMs);
    }

    @NotNull
    @Contract(pure = true)
    public static String formatTime(long ms) {
        long minutes = ms / 60000;
        long seconds = (ms % 60000) / 1000;

        return String.format("%d:%02d", minutes, seconds);
    }

    public String getFormattedProgress() {
        return formatTime(getCurrentProgressMs());
    }

    public String getFormattedDuration() {
        return formatTime(durationMs);
    }
}