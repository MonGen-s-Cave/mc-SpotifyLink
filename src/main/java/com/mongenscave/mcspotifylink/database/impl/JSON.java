package com.mongenscave.mcspotifylink.database.impl;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import com.mongenscave.mcspotifylink.McSpotifyLink;
import com.mongenscave.mcspotifylink.database.Database;
import com.mongenscave.mcspotifylink.data.spotify.SpotifyUser;
import com.mongenscave.mcspotifylink.utils.LoggerUtils;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.lang.reflect.Type;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.locks.ReentrantReadWriteLock;

public class JSON implements Database {
    private static final McSpotifyLink plugin = McSpotifyLink.getInstance();
    private final File dataFile;
    private final Gson gson;
    private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();
    private final Executor executor = Executors.newFixedThreadPool(3);

    public JSON() {
        this.dataFile = new File(plugin.getDataFolder(), "spotify_users.json");
        this.gson = new GsonBuilder().setPrettyPrinting().create();
        initializeDatabase();
    }

    @Override
    public void initializeDatabase() {
        CompletableFuture.runAsync(() -> {
            if (!dataFile.exists()) {
                try {
                    if (!dataFile.getParentFile().exists()) dataFile.getParentFile().mkdirs();
                    dataFile.createNewFile();

                    try (FileWriter writer = new FileWriter(dataFile)) {
                        writer.write("{}");
                    }
                } catch (IOException exception) {
                    LoggerUtils.error(exception.getMessage());
                    throw new RuntimeException(exception);
                }
            }
        }, executor);
    }

    @Override
    public CompletableFuture<Void> saveUser(@NotNull SpotifyUser user) {
        return CompletableFuture.runAsync(() -> {
            lock.writeLock().lock();
            try {
                ConcurrentHashMap<String, SpotifyUser> users = loadUsersFromFileSync();
                users.put(user.playerId().toString(), user);
                saveUsersToFileSync(users);
            } catch (Exception exception) {
                LoggerUtils.error(exception.getMessage());
                throw new RuntimeException(exception);
            } finally {
                lock.writeLock().unlock();
            }
        }, executor);
    }

    @Override
    public CompletableFuture<ConcurrentHashMap<UUID, SpotifyUser>> loadUsers() {
        return CompletableFuture.supplyAsync(() -> {
            ConcurrentHashMap<UUID, SpotifyUser> users = new ConcurrentHashMap<>();

            lock.readLock().lock();
            try {
                ConcurrentHashMap<String, SpotifyUser> userData = loadUsersFromFileSync();

                for (ConcurrentHashMap.Entry<String, SpotifyUser> entry : userData.entrySet()) {
                    try {
                        UUID playerId = UUID.fromString(entry.getKey());
                        SpotifyUser user = entry.getValue();

                        if (user != null) {
                            users.put(playerId, user);
                        }
                    } catch (Exception exception) {
                        LoggerUtils.error(exception.getMessage());
                    }
                }
            } catch (Exception exception) {
                LoggerUtils.error(exception.getMessage());
                throw new RuntimeException(exception);
            } finally {
                lock.readLock().unlock();
            }

            return users;
        }, executor);
    }

    @Override
    public CompletableFuture<Void> removeUser(@NotNull UUID playerId) {
        return CompletableFuture.runAsync(() -> {
            lock.writeLock().lock();
            try {
                ConcurrentHashMap<String, SpotifyUser> users = loadUsersFromFileSync();
                users.remove(playerId.toString());
                saveUsersToFileSync(users);
            } catch (Exception exception) {
                LoggerUtils.error(exception.getMessage());
                throw new RuntimeException(exception);
            } finally {
                lock.writeLock().unlock();
            }
        }, executor);
    }

    @NotNull
    private ConcurrentHashMap<String, SpotifyUser> loadUsersFromFileSync() {
        try {
            if (!dataFile.exists() || dataFile.length() == 0) {
                return new ConcurrentHashMap<>();
            }

            try (FileReader reader = new FileReader(dataFile)) {
                Type type = new TypeToken<ConcurrentHashMap<String, SpotifyUser>>(){}.getType();
                ConcurrentHashMap<String, SpotifyUser> result = gson.fromJson(reader, type);
                return result != null ? result : new ConcurrentHashMap<>();
            }
        } catch (Exception exception) {
            LoggerUtils.error("Hiba a JSON fájl olvasása során: " + exception.getMessage());
            return new ConcurrentHashMap<>();
        }
    }

    private void saveUsersToFileSync(ConcurrentHashMap<String, SpotifyUser> users) {
        try (FileWriter writer = new FileWriter(dataFile)) {
            gson.toJson(users, writer);
        } catch (IOException exception) {
            LoggerUtils.error("Hiba a JSON fájl írása során: " + exception.getMessage());
            throw new RuntimeException(exception);
        }
    }

    @Override
    public CompletableFuture<Void> close() {
        return CompletableFuture.runAsync(() -> {
            // No need for cleanup in JSON implementation
        }, executor);
    }
}