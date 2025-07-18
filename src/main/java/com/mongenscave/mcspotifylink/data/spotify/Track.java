package com.mongenscave.mcspotifylink.data.spotify;

import com.mongenscave.mcspotifylink.model.CurrentTrack;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;

public record Track(@NotNull String id, @NotNull String name, @NotNull String artist, @NotNull String uri, long durationMs) {
    @NotNull
    @Contract(pure = true)
    public String getFormattedDuration() {
        return CurrentTrack.formatTime(durationMs);
    }
}
