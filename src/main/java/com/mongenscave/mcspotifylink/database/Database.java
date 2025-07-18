package com.mongenscave.mcspotifylink.database;

import com.mongenscave.mcspotifylink.data.spotify.SpotifyUser;
import org.jetbrains.annotations.NotNull;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

public interface Database {
    void initializeDatabase();

    CompletableFuture<Void> saveUser(@NotNull SpotifyUser user);

    CompletableFuture<ConcurrentHashMap<UUID, SpotifyUser>> loadUsers();

    CompletableFuture<Void> removeUser(@NotNull UUID playerId);

    CompletableFuture<Void> close();
}