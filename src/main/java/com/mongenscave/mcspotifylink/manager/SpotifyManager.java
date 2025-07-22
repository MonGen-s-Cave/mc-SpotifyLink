package com.mongenscave.mcspotifylink.manager;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.mongenscave.mcspotifylink.McSpotifyLink;
import com.mongenscave.mcspotifylink.data.spotify.Playlist;
import com.mongenscave.mcspotifylink.data.spotify.Track;
import com.mongenscave.mcspotifylink.identifiers.keys.ConfigKeys;
import com.mongenscave.mcspotifylink.interfaces.HttpRequestFactory;
import com.mongenscave.mcspotifylink.model.CurrentTrack;
import com.mongenscave.mcspotifylink.data.spotify.SpotifyUser;
import com.mongenscave.mcspotifylink.processor.MessageProcessor;
import com.mongenscave.mcspotifylink.render.SpotifyCoverMapRenderer;
import com.mongenscave.mcspotifylink.utils.LoggerUtils;
import com.mongenscave.mcspotifylink.utils.PlayerUtils;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpPut;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.MapMeta;
import org.bukkit.map.MapView;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.InputStream;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

public class SpotifyManager {
    private static final McSpotifyLink plugin = McSpotifyLink.getInstance();
    private final Gson gson;
    private final CloseableHttpClient httpClient;
    private final ConcurrentHashMap<UUID, SpotifyUser> connectedUsers;
    private final ConcurrentHashMap<UUID, CurrentTrack> currentTracks;
    private final ConcurrentHashMap<UUID, String> lastTrackIds;
    private final ConcurrentHashMap<UUID, Long> lastUpdateTime;

    public SpotifyManager() {
        this.gson = new Gson();
        this.httpClient = HttpClients.createDefault();
        this.connectedUsers = new ConcurrentHashMap<>();
        this.currentTracks = new ConcurrentHashMap<>();
        this.lastTrackIds = new ConcurrentHashMap<>();
        this.lastUpdateTime = new ConcurrentHashMap<>();

        loadConnectedUsers();
        startContinuousUpdates();
    }

    private void startContinuousUpdates() {
        plugin.getScheduler().runTaskTimerAsynchronously(() -> {
            List<CompletableFuture<Void>> updateFutures = Collections.synchronizedList(new ArrayList<>());

            for (UUID playerId : connectedUsers.keySet()) {
                Player player = plugin.getServer().getPlayer(playerId);

                if (player != null && player.isOnline()) {
                    CompletableFuture<Void> updateFuture = updatePlayerInternal(player);
                    updateFutures.add(updateFuture);
                }
            }

            CompletableFuture.allOf(updateFutures.toArray(new CompletableFuture[0]))
                    .exceptionally(throwable -> {
                        LoggerUtils.error("Error during batch player updates: " + throwable.getMessage());
                        return null;
                    });
        }, 20L, 10);
    }

    public String getAuthorizationUrl(@NotNull Player player) {
        String clientId = ConfigKeys.SPOTIFY_CLIENT_ID.getString();
        String redirectUri = ConfigKeys.SPOTIFY_REDIRECT_URI.getString();
        String state = player.getUniqueId().toString();
        String scopes = String.join("%20", ConfigKeys.SPOTIFY_SCOPES.getList());

        return "https://accounts.spotify.com/authorize" +
                "?response_type=code" +
                "&client_id=" + clientId +
                "&scope=" + scopes +
                "&redirect_uri=" + URLEncoder.encode(redirectUri, StandardCharsets.UTF_8) +
                "&state=" + state +
                "&show_dialog=true";
    }

    public CompletableFuture<Boolean> connectUser(@NotNull Player player, @NotNull String authCode) {
        return exchangeCodeForToken(authCode)
                .thenCompose(accessToken -> {
                    if (accessToken != null) {
                        SpotifyUser user = new SpotifyUser(player.getUniqueId(), accessToken);
                        connectedUsers.put(player.getUniqueId(), user);

                        return plugin.getDatabase().saveUser(user)
                                .thenCompose(v -> updatePlayerInternal(player))
                                .thenApply(v -> true);
                    } else return CompletableFuture.completedFuture(false);
                })
                .exceptionally(throwable -> {
                    LoggerUtils.error("Error connecting user: " + throwable.getMessage());
                    return false;
                });
    }

    @NotNull
    @Contract("_ -> new")
    private CompletableFuture<String> exchangeCodeForToken(@NotNull String code) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                String clientId = ConfigKeys.SPOTIFY_CLIENT_ID.getString();
                String clientSecret = ConfigKeys.SPOTIFY_CLIENT_SECRET.getString();
                String redirectUri = ConfigKeys.SPOTIFY_REDIRECT_URI.getString();

                HttpPost post = new HttpPost("https://accounts.spotify.com/api/token");
                post.setHeader("Content-Type", "application/x-www-form-urlencoded");

                String auth = clientId + ":" + clientSecret;
                String encodedAuth = Base64.getEncoder().encodeToString(auth.getBytes());
                post.setHeader("Authorization", "Basic " + encodedAuth);

                String body = String.format(
                        "grant_type=authorization_code&code=%s&redirect_uri=%s",
                        code, URLEncoder.encode(redirectUri, StandardCharsets.UTF_8)
                );

                post.setEntity(new StringEntity(body));

                try (CloseableHttpResponse response = httpClient.execute(post)) {
                    String responseBody = EntityUtils.toString(response.getEntity());
                    JsonObject json = gson.fromJson(responseBody, JsonObject.class);

                    if (json.has("access_token")) return json.get("access_token").getAsString();
                }
            } catch (Exception exception) {
                LoggerUtils.error(exception.getMessage());
            }
            return null;
        });
    }

    public CompletableFuture<Void> updatePlayer(@NotNull Player player) {
        return updatePlayerInternal(player);
    }

    private CompletableFuture<Void> updatePlayerInternal(@NotNull Player player) {
        SpotifyUser user = connectedUsers.get(player.getUniqueId());
        if (user == null) return CompletableFuture.completedFuture(null);

        long currentTime = System.currentTimeMillis();
        Long lastUpdate = lastUpdateTime.get(player.getUniqueId());
        if (lastUpdate != null && currentTime - lastUpdate < 1000) return CompletableFuture.completedFuture(null);

        lastUpdateTime.put(player.getUniqueId(), currentTime);

        return getCurrentTrack(user.accessToken())
                .thenAccept(track -> handleTrackUpdate(player, track))
                .exceptionally(throwable -> null);
    }

    private void handleTrackUpdate(@NotNull Player player, @Nullable CurrentTrack track) {
        UUID playerId = player.getUniqueId();

        if (track != null) {
            String currentTrackId = track.getTrackName() + ":" + track.getArtistName();
            String lastTrackId = lastTrackIds.get(playerId);

            boolean isNewTrack = !currentTrackId.equals(lastTrackId);

            if (isNewTrack) {
                if (ConfigKeys.TOAST_ENABLED.getBoolean()) {
                    PlayerUtils.sendToast(
                            player,
                            MessageProcessor.process(ConfigKeys.TOAST_MESSAGE.getString()
                                    .replace("{name}", track.getTrackName())
                                    .replace("{author}", track.getArtistName())),
                            "",
                            Material.valueOf(ConfigKeys.TOAST_MATERIAL.getString())
                    );
                }

                lastTrackIds.put(playerId, currentTrackId);
            }

            currentTracks.put(playerId, track);

            if (plugin.getTimestampManager() != null)
                plugin.getTimestampManager().startTimestampUpdates();
        } else {
            CurrentTrack lastTrack = currentTracks.get(playerId);
            if (lastTrack != null) {
                CurrentTrack stoppedTrack = new CurrentTrack(
                        lastTrack.getTrackName(),
                        lastTrack.getArtistName(),
                        lastTrack.getDurationMs(),
                        lastTrack.getProgressMs(),
                        false
                );

                currentTracks.put(playerId, stoppedTrack);
            }
        }
    }

    @NotNull
    @Contract("_ -> new")
    private CompletableFuture<CurrentTrack> getCurrentTrack(@NotNull String accessToken) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                HttpGet get = new HttpGet("https://api.spotify.com/v1/me/player/currently-playing");
                get.setHeader("Authorization", "Bearer " + accessToken);

                try (CloseableHttpResponse response = httpClient.execute(get)) {
                    if (response.getStatusLine().getStatusCode() == 200) {
                        String responseBody = EntityUtils.toString(response.getEntity());
                        JsonObject json = gson.fromJson(responseBody, JsonObject.class);

                        if (json.has("item")) {
                            JsonObject item = json.getAsJsonObject("item");
                            String trackName = item.get("name").getAsString();
                            String artistName = item.getAsJsonArray("artists").get(0).getAsJsonObject().get("name").getAsString();
                            long durationMs = item.get("duration_ms").getAsLong();
                            long progressMs = json.has("progress_ms") ? json.get("progress_ms").getAsLong() : 0;
                            boolean isPlaying = json.has("is_playing") && json.get("is_playing").getAsBoolean();

                            return new CurrentTrack(trackName, artistName, durationMs, progressMs, isPlaying);
                        }
                    }
                }
            } catch (Exception exception) {
                LoggerUtils.error(exception.getMessage());
            }
            return null;
        });
    }

    public CompletableFuture<Void> previousTrack(@NotNull Player player) {
        return executePlayerAction(player, "https://api.spotify.com/v1/me/player/previous", HttpPost::new);
    }

    public CompletableFuture<Void> nextTrack(@NotNull Player player) {
        return executePlayerAction(player, "https://api.spotify.com/v1/me/player/next", HttpPost::new);
    }

    public CompletableFuture<Void> pausePlayback(@NotNull Player player) {
        return executePlayerAction(player, "https://api.spotify.com/v1/me/player/pause", HttpPut::new);
    }

    public CompletableFuture<Void> resumePlayback(@NotNull Player player) {
        return executePlayerAction(player, "https://api.spotify.com/v1/me/player/play", HttpPut::new);
    }

    private CompletableFuture<Void> executePlayerAction(@NotNull Player player, @NotNull String url, @NotNull HttpRequestFactory requestFactory) {
        SpotifyUser user = connectedUsers.get(player.getUniqueId());
        if (user == null) return CompletableFuture.completedFuture(null);

        return CompletableFuture.runAsync(() -> {
            try {
                var request = requestFactory.create(url);
                request.setHeader("Authorization", "Bearer " + user.accessToken());

                try (CloseableHttpResponse response = httpClient.execute(request)) {
                    response.getStatusLine().getStatusCode();
                }
            } catch (Exception exception) {
                LoggerUtils.error(exception.getMessage());
            }
        }).thenCompose(v -> CompletableFuture.runAsync(() -> {
            try {
                Thread.sleep(1000);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
            }
        }).thenCompose(ignored -> updatePlayerInternal(player))).exceptionally(throwable -> {
            LoggerUtils.error("Error executing player action: " + throwable.getMessage());
            return null;
        });
    }

    public CompletableFuture<List<Playlist>> getUserPlaylists(@NotNull Player player) {
        SpotifyUser user = connectedUsers.get(player.getUniqueId());
        if (user == null) return CompletableFuture.completedFuture(Collections.emptyList());

        return CompletableFuture.supplyAsync(() -> {
            try {
                HttpGet get = new HttpGet("https://api.spotify.com/v1/me/playlists?limit=50");
                get.setHeader("Authorization", "Bearer " + user.accessToken());

                try (CloseableHttpResponse response = httpClient.execute(get)) {
                    if (response.getStatusLine().getStatusCode() == 200) {
                        String responseBody = EntityUtils.toString(response.getEntity());
                        JsonObject json = gson.fromJson(responseBody, JsonObject.class);

                        List<Playlist> playlists = Collections.synchronizedList(new ArrayList<>());

                        if (json.has("items")) {
                            for (var item : json.getAsJsonArray("items")) {
                                JsonObject playlistObj = item.getAsJsonObject();
                                String id = playlistObj.get("id").getAsString();
                                String name = playlistObj.get("name").getAsString();
                                int totalTracks = playlistObj.getAsJsonObject("tracks").get("total").getAsInt();

                                playlists.add(new Playlist(id, name, totalTracks));
                            }
                        }
                        return playlists;
                    }
                }
            } catch (Exception exception) {
                LoggerUtils.error(exception.getMessage());
            }
            return Collections.<Playlist>emptyList();
        }).exceptionally(throwable -> {
            LoggerUtils.error("Failed to get user playlists: " + throwable.getMessage());
            return Collections.emptyList();
        });
    }

    public CompletableFuture<List<Track>> getPlaylistTracks(@NotNull Player player, @NotNull String playlistId) {
        SpotifyUser user = connectedUsers.get(player.getUniqueId());
        if (user == null) return CompletableFuture.completedFuture(Collections.emptyList());

        return CompletableFuture.supplyAsync(() -> {
            try {
                HttpGet get = new HttpGet("https://api.spotify.com/v1/playlists/" + playlistId + "/tracks?limit=50");
                get.setHeader("Authorization", "Bearer " + user.accessToken());

                try (CloseableHttpResponse response = httpClient.execute(get)) {
                    if (response.getStatusLine().getStatusCode() == 200) {
                        String responseBody = EntityUtils.toString(response.getEntity());
                        JsonObject json = gson.fromJson(responseBody, JsonObject.class);

                        List<Track> tracks = new ArrayList<>();
                        if (json.has("items")) {
                            for (var item : json.getAsJsonArray("items")) {
                                JsonObject trackObj = item.getAsJsonObject();
                                if (trackObj.has("track") && !trackObj.get("track").isJsonNull()) {
                                    JsonObject track = trackObj.getAsJsonObject("track");

                                    String id = track.get("id").getAsString();
                                    String name = track.get("name").getAsString();
                                    String artist = track.getAsJsonArray("artists").get(0).getAsJsonObject().get("name").getAsString();
                                    String uri = track.get("uri").getAsString();
                                    long durationMs = track.get("duration_ms").getAsLong();

                                    tracks.add(new Track(id, name, artist, uri, durationMs));
                                }
                            }
                        }
                        return tracks;
                    }
                }
            } catch (Exception exception) {
                LoggerUtils.error(exception.getMessage());
            }

            return Collections.<Track>emptyList();
        }).exceptionally(throwable -> {
            LoggerUtils.error("Failed to get playlist tracks: " + throwable.getMessage());
            return Collections.emptyList();
        });
    }

    public CompletableFuture<Void> playTrack(@NotNull Player player, @NotNull String trackUri) {
        SpotifyUser user = connectedUsers.get(player.getUniqueId());
        if (user == null) return CompletableFuture.completedFuture(null);

        return CompletableFuture.runAsync(() -> {
            try {
                HttpPut put = getHttpPut(trackUri, user);

                try (CloseableHttpResponse response = httpClient.execute(put)) {
                    int statusCode = response.getStatusLine().getStatusCode();
                    if (statusCode != 204) LoggerUtils.warn("Failed to play track, status code: " + statusCode);
                }
            } catch (Exception exception) {
                LoggerUtils.error(exception.getMessage());
            }
        }).thenCompose(v -> CompletableFuture.runAsync(() -> {
            try {
                Thread.sleep(1000);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
            }
        }).thenCompose(ignored -> updatePlayerInternal(player))).exceptionally(throwable -> null);
    }

    @NotNull
    private static HttpPut getHttpPut(@NotNull String trackUri, @NotNull SpotifyUser user) {
        HttpPut put = new HttpPut("https://api.spotify.com/v1/me/player/play");
        put.setHeader("Authorization", "Bearer " + user.accessToken());
        put.setHeader("Content-Type", "application/json");

        JsonObject requestBody = new JsonObject();
        JsonArray uris = new JsonArray();
        uris.add(trackUri);
        requestBody.add("uris", uris);

        put.setEntity(new StringEntity(requestBody.toString(), StandardCharsets.UTF_8));
        return put;
    }

    public CompletableFuture<Void> disconnectUser(@NotNull Player player) {
        UUID playerId = player.getUniqueId();
        connectedUsers.remove(playerId);
        currentTracks.remove(playerId);
        lastTrackIds.remove(playerId);
        lastUpdateTime.remove(playerId);

        return plugin.getDatabase().removeUser(playerId)
                .exceptionally(throwable -> {
                    LoggerUtils.error("Error removing user from database: " + throwable.getMessage());
                    return null;
                });
    }

    public void removePlayer(@NotNull Player player) {
        UUID playerId = player.getUniqueId();
        currentTracks.remove(playerId);
        lastTrackIds.remove(playerId);
        lastUpdateTime.remove(playerId);

        if (currentTracks.isEmpty() && plugin.getTimestampManager() != null) plugin.getTimestampManager().stopTimestampUpdates();
    }

    public boolean isConnected(@NotNull Player player) {
        return connectedUsers.containsKey(player.getUniqueId());
    }

    public CurrentTrack getCurrentTrack(@NotNull Player player) {
        return currentTracks.get(player.getUniqueId());
    }

    public CompletableFuture<ConcurrentHashMap<UUID, CurrentTrack>> getAllCurrentTracks() {
        return CompletableFuture.supplyAsync(() -> new ConcurrentHashMap<>(currentTracks));
    }

    private void loadConnectedUsers() {
        plugin.getDatabase().loadUsers()
                .thenAccept(connectedUsers::putAll)
                .exceptionally(throwable -> null);
    }

    public void shutdown() {
        try {
            if (httpClient != null) httpClient.close();
        } catch (Exception exception) {
            LoggerUtils.error("Error shutting down HTTP client: " + exception.getMessage());
        }
    }

    public CompletableFuture<Boolean> createCoverMap(@NotNull Player player, @NotNull String trackId, @NotNull ItemStack mapItem) {
        SpotifyUser user = connectedUsers.get(player.getUniqueId());
        if (user == null) return CompletableFuture.completedFuture(false);

        return getTrackInfo(user.accessToken(), trackId)
                .thenCompose(trackInfo -> {
                    if (trackInfo != null && trackInfo.has("album")) {
                        JsonObject album = trackInfo.getAsJsonObject("album");
                        if (album.has("images") && !album.getAsJsonArray("images").isEmpty()) {
                            JsonObject largestImage = album.getAsJsonArray("images").get(0).getAsJsonObject();
                            String imageUrl = largestImage.get("url").getAsString();

                            return downloadAndCreateMap(player, imageUrl, mapItem);
                        }
                    }
                    return CompletableFuture.completedFuture(false);
                });
    }

    @NotNull
    @Contract("_, _, _ -> new")
    private CompletableFuture<Boolean> downloadAndCreateMap(@NotNull Player player, @NotNull String imageUrl, @NotNull ItemStack mapItem) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                HttpGet get = new HttpGet(imageUrl);
                try (CloseableHttpResponse response = httpClient.execute(get)) {
                    if (response.getStatusLine().getStatusCode() == 200) {
                        InputStream inputStream = response.getEntity().getContent();
                        BufferedImage originalImage = ImageIO.read(inputStream);

                        if (originalImage != null) {
                            BufferedImage resizedImage = resizeImage(originalImage);

                            CompletableFuture<Boolean> mapCreation = new CompletableFuture<>();
                            plugin.getScheduler().runTask(() -> {
                                try {
                                    createMapFromImage(player, resizedImage, mapItem);
                                    mapCreation.complete(true);
                                } catch (Exception exception) {
                                    LoggerUtils.error("Error creating map: " + exception.getMessage());
                                    mapCreation.complete(false);
                                }
                            });

                            return mapCreation.get();
                        }
                    }
                }
            } catch (Exception exception) {
                LoggerUtils.error("Error downloading and creating map: " + exception.getMessage());
            }
            return false;
        });
    }

    private void createMapFromImage(@NotNull Player player,
                                    @NotNull BufferedImage image,
                                    @NotNull ItemStack mapItem) {
        try {
            MapView mapView;

            if (mapItem.getType() == Material.FILLED_MAP || mapItem.getType() == Material.MAP) {
                mapView = plugin.getServer().createMap(player.getWorld());

                ItemMeta meta = mapItem.getItemMeta();
                if (meta instanceof MapMeta mapMeta) {
                    mapMeta.setMapView(mapView);
                    mapItem.setItemMeta(mapMeta);
                }
            } else {
                throw new IllegalArgumentException("Item must be a MAP or FILLED_MAP");
            }

            mapView.getRenderers().clear();

            SpotifyCoverMapRenderer renderer = new SpotifyCoverMapRenderer(image);
            mapView.addRenderer(renderer);
        } catch (Exception exception) {
            LoggerUtils.error("Error creating map from image: " + exception.getMessage());
            throw exception;
        }
    }

    @NotNull
    private BufferedImage resizeImage(@NotNull BufferedImage originalImage) {
        BufferedImage resizedImage = new BufferedImage(128, 128, BufferedImage.TYPE_INT_RGB);
        Graphics2D g2d = resizedImage.createGraphics();

        g2d.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g2d.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        g2d.drawImage(originalImage, 0, 0, 128, 128, null);
        g2d.dispose();

        return resizedImage;
    }

    @NotNull
    @Contract("_, _ -> new")
    private CompletableFuture<JsonObject> getTrackInfo(@NotNull String accessToken, @NotNull String trackId) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                HttpGet get = new HttpGet("https://api.spotify.com/v1/tracks/" + trackId);
                get.setHeader("Authorization", "Bearer " + accessToken);

                try (CloseableHttpResponse response = httpClient.execute(get)) {
                    if (response.getStatusLine().getStatusCode() == 200) {
                        String responseBody = EntityUtils.toString(response.getEntity());
                        return gson.fromJson(responseBody, JsonObject.class);
                    }
                }
            } catch (Exception exception) {
                LoggerUtils.error("Error getting track info: " + exception.getMessage());
            }
            return null;
        });
    }

    public CompletableFuture<Boolean> createCodeMap(@NotNull Player player, @NotNull String trackId, @NotNull ItemStack mapItem) {
        SpotifyUser user = connectedUsers.get(player.getUniqueId());
        if (user == null) return CompletableFuture.completedFuture(false);

        return getTrackInfo(user.accessToken(), trackId)
                .thenCompose(trackInfo -> {
                    if (trackInfo != null) {
                        String spotifyUri = "spotify:track:" + trackId;
                        return downloadAndCreateCodeMap(player, spotifyUri, mapItem);
                    }
                    return CompletableFuture.completedFuture(false);
                });
    }

    @NotNull
    @Contract("_, _, _ -> new")
    private CompletableFuture<Boolean> downloadAndCreateCodeMap(@NotNull Player player, @NotNull String spotifyUri, @NotNull ItemStack mapItem) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                String codeUrl = "https://scannables.scdn.co/uri/plain/jpeg/000000/white/640/" +
                        URLEncoder.encode(spotifyUri, StandardCharsets.UTF_8);

                HttpGet get = new HttpGet(codeUrl);
                try (CloseableHttpResponse response = httpClient.execute(get)) {
                    if (response.getStatusLine().getStatusCode() == 200) {
                        InputStream inputStream = response.getEntity().getContent();
                        BufferedImage originalImage = ImageIO.read(inputStream);

                        if (originalImage != null) {
                            BufferedImage resizedImage = resizeImageForCode(originalImage);

                            CompletableFuture<Boolean> mapCreation = new CompletableFuture<>();
                            plugin.getScheduler().runTask(() -> {
                                try {
                                    createMapFromImage(player, resizedImage, mapItem);
                                    mapCreation.complete(true);
                                } catch (Exception exception) {
                                    LoggerUtils.error("Error creating code map: " + exception.getMessage());
                                    mapCreation.complete(false);
                                }
                            });

                            return mapCreation.get();
                        }
                    }
                }
            } catch (Exception exception) {
                LoggerUtils.error("Error downloading and creating code map: " + exception.getMessage());
            }
            return false;
        });
    }

    @NotNull
    private BufferedImage resizeImageForCode(@NotNull BufferedImage originalImage) {
        BufferedImage resizedImage = new BufferedImage(128, 128, BufferedImage.TYPE_INT_RGB);
        Graphics2D g2d = resizedImage.createGraphics();

        g2d.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        g2d.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        g2d.setColor(Color.WHITE);
        g2d.fillRect(0, 0, 128, 128);

        int originalWidth = originalImage.getWidth();
        int originalHeight = originalImage.getHeight();

        double scaleX = 128.0 / originalWidth;
        double scaleY = 128.0 / originalHeight;
        double scale = Math.min(scaleX, scaleY);

        int scaledWidth = (int) (originalWidth * scale);
        int scaledHeight = (int) (originalHeight * scale);

        int x = (128 - scaledWidth) / 2;
        int y = (128 - scaledHeight) / 2;

        g2d.drawImage(originalImage, x, y, scaledWidth, scaledHeight, null);
        g2d.dispose();

        return resizedImage;
    }
}