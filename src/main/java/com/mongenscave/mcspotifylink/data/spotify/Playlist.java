package com.mongenscave.mcspotifylink.data.spotify;

import org.jetbrains.annotations.NotNull;

public record Playlist(@NotNull String id, @NotNull String name, int totalTracks) {}
