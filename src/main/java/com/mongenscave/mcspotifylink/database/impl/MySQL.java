package com.mongenscave.mcspotifylink.database.impl;

import com.mongenscave.mcspotifylink.database.Database;
import com.mongenscave.mcspotifylink.data.spotify.SpotifyUser;
import com.mongenscave.mcspotifylink.utils.LoggerUtils;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.jetbrains.annotations.NotNull;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

public class MySQL implements Database {
    private HikariDataSource dataSource;
    private final Executor executor = Executors.newFixedThreadPool(5);

    public MySQL(@NotNull String host,
                 int port,
                 @NotNull String database,
                 @NotNull String username,
                 @NotNull String password,
                 int poolSize) {
        setupDataSource(host, port, database, username, password, poolSize);
        initializeDatabase();
    }

    private void setupDataSource(@NotNull String host,
                                 int port,
                                 @NotNull String database,
                                 @NotNull String username,
                                 @NotNull String password,
                                 int poolSize) {
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl("jdbc:mysql://" + host + ":" + port + "/" + database);
        config.setUsername(username);
        config.setPassword(password);
        config.setMaximumPoolSize(poolSize);
        config.setMinimumIdle(2);
        config.setConnectionTimeout(30000);
        config.setIdleTimeout(600000);
        config.setMaxLifetime(1800000);
        config.setLeakDetectionThreshold(60000);

        config.addDataSourceProperty("cachePrepStmts", "true");
        config.addDataSourceProperty("prepStmtCacheSize", "250");
        config.addDataSourceProperty("prepStmtCacheSqlLimit", "2048");
        config.addDataSourceProperty("useServerPrepStmts", "true");
        config.addDataSourceProperty("useLocalSessionState", "true");
        config.addDataSourceProperty("rewriteBatchedStatements", "true");
        config.addDataSourceProperty("cacheResultSetMetadata", "true");
        config.addDataSourceProperty("cacheServerConfiguration", "true");
        config.addDataSourceProperty("elideSetAutoCommits", "true");
        config.addDataSourceProperty("maintainTimeStats", "false");

        this.dataSource = new HikariDataSource(config);
    }

    @Override
    public void initializeDatabase() {
        CompletableFuture.runAsync(() -> {
            try {
                createTable();
            } catch (SQLException exception) {
                LoggerUtils.error(exception.getMessage());
                throw new RuntimeException(exception);
            }
        }, executor);
    }

    private void createTable() throws SQLException {
        try (Connection conn = dataSource.getConnection()) {
            String spotifyUsersTable = """
                CREATE TABLE IF NOT EXISTS spotify_users (
                    player_id VARCHAR(36) PRIMARY KEY,
                    access_token TEXT NOT NULL,
                    INDEX idx_player_id (player_id)
                )
                """;

            try (Statement stmt = conn.createStatement()) {
                stmt.execute(spotifyUsersTable);
            }
        }
    }

    @Override
    public CompletableFuture<Void> saveUser(@NotNull SpotifyUser user) {
        return CompletableFuture.runAsync(() -> {
            String sql = """
                INSERT INTO spotify_users (player_id, access_token)
                VALUES (?, ?)
                ON DUPLICATE KEY UPDATE
                access_token = VALUES(access_token)
                """;

            try (Connection conn = dataSource.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setString(1, user.playerId().toString());
                stmt.setString(2, user.accessToken());

                stmt.executeUpdate();
            } catch (SQLException exception) {
                LoggerUtils.error(exception.getMessage());
                throw new RuntimeException(exception);
            }
        }, executor);
    }

    @Override
    public CompletableFuture<ConcurrentHashMap<UUID, SpotifyUser>> loadUsers() {
        return CompletableFuture.supplyAsync(() -> {
            ConcurrentHashMap<UUID, SpotifyUser> users = new ConcurrentHashMap<>();
            String sql = "SELECT player_id, access_token FROM spotify_users";

            try (Connection conn = dataSource.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql);
                 ResultSet rs = stmt.executeQuery()) {

                while (rs.next()) {
                    try {
                        UUID playerId = UUID.fromString(rs.getString("player_id"));
                        String accessToken = rs.getString("access_token");

                        SpotifyUser user = new SpotifyUser(playerId, accessToken);
                        users.put(playerId, user);
                    } catch (Exception exception) {
                        LoggerUtils.error(exception.getMessage());
                    }
                }
            } catch (SQLException exception) {
                LoggerUtils.error(exception.getMessage());
                throw new RuntimeException(exception);
            }

            return users;
        }, executor);
    }

    @Override
    public CompletableFuture<Void> removeUser(@NotNull UUID playerId) {
        return CompletableFuture.runAsync(() -> {
            String sql = "DELETE FROM spotify_users WHERE player_id = ?";

            try (Connection conn = dataSource.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql)) {

                stmt.setString(1, playerId.toString());
                stmt.executeUpdate();
            } catch (SQLException exception) {
                LoggerUtils.error(exception.getMessage());
                throw new RuntimeException(exception);
            }
        }, executor);
    }

    @Override
    public CompletableFuture<Void> close() {
        return CompletableFuture.runAsync(() -> {
            if (dataSource != null && !dataSource.isClosed()) {
                dataSource.close();
            }
        }, executor);
    }
}