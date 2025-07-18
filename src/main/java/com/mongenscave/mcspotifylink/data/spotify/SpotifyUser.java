package com.mongenscave.mcspotifylink.data.spotify;

import org.jetbrains.annotations.NotNull;

import java.util.UUID;

public record SpotifyUser(@NotNull UUID playerId, @NotNull String accessToken) {}
