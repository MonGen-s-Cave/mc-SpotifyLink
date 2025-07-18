package com.mongenscave.mcspotifylink.handlers;

import com.mongenscave.mcspotifylink.McSpotifyLink;
import com.mongenscave.mcspotifylink.identifiers.keys.ConfigKeys;
import com.mongenscave.mcspotifylink.identifiers.keys.MessageKeys;
import com.mongenscave.mcspotifylink.utils.LoggerUtils;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;

public class CallbackHandler implements HttpHandler {
    private static final McSpotifyLink plugin = McSpotifyLink.getInstance();

    @Override
    public void handle(@NotNull HttpExchange exchange) throws IOException {
        String query = exchange.getRequestURI().getQuery();

        ConcurrentHashMap<String, String> params = parseQuery(query);

        String code = params.get("code");
        String state = params.get("state");
        String error = params.get("error");

        String responseText;

        if (error != null) responseText = createErrorPage(ConfigKeys.WEB_SPOTIFY_ERROR.getString().replace("{error}", error));
        else if (code == null) responseText = createErrorPage(ConfigKeys.WEB_NO_CODE.getString());
        else if (state == null) responseText = createErrorPage(ConfigKeys.WEB_NO_STATE.getString());
        else {
            try {
                UUID playerId = UUID.fromString(state);
                Player player = Bukkit.getPlayer(playerId);

                if (player != null && player.isOnline()) {
                    plugin.getScheduler().runTaskAsynchronously(() -> {
                        boolean success;
                        try {
                            success = plugin.getSpotifyManager().connectUser(player, code).get();
                        } catch (InterruptedException | ExecutionException e) {
                            throw new RuntimeException(e);
                        }

                        plugin.getScheduler().runTask(() -> {
                            if (success) {
                                player.sendMessage(MessageKeys.CONNECTED.getMessage());
                                plugin.getSpotifyManager().updatePlayer(player);
                            } else player.sendMessage(MessageKeys.ERROR.getMessage());
                        });
                    });

                    responseText = createSuccessPage(player.getName());
                } else responseText = createErrorPage(ConfigKeys.WEB_NO_PLAYER.getString());
            } catch (IllegalArgumentException exception) {
                responseText = createErrorPage(ConfigKeys.WEB_INVALID_PLAYER.getString());
            }
        }

        exchange.getResponseHeaders().set("Content-Type", "text/html; charset=utf-8");
        exchange.sendResponseHeaders(200, responseText.getBytes().length);

        try (OutputStream os = exchange.getResponseBody()) {
            os.write(responseText.getBytes());
        }
    }

    @NotNull
    private ConcurrentHashMap<String, String> parseQuery(@Nullable String query) {
        ConcurrentHashMap<String, String> params = new ConcurrentHashMap<>();

        if (query != null && !query.isEmpty()) {
            String[] pairs = query.split("&");

            for (String pair : pairs) {
                String[] keyValue = pair.split("=", 2);

                if (keyValue.length == 2) {
                    try {
                        String key = URLDecoder.decode(keyValue[0], StandardCharsets.UTF_8);
                        String value = URLDecoder.decode(keyValue[1], StandardCharsets.UTF_8);
                        params.put(key, value);
                    } catch (Exception exception) {
                        LoggerUtils.error(exception.getMessage());
                    }
                }
            }
        }
        return params;
    }

    @NotNull
    private String loadResourceFile(@NotNull String fileName) {
        try (InputStream inputStream = plugin.getResource("web/" + fileName)) {
            if (inputStream == null) {
                LoggerUtils.error("Not found: web/" + fileName);
                return "";
            }
            return new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException exception) {
            LoggerUtils.error(exception.getMessage());
            return "";
        }
    }

    @NotNull
    private String createSuccessPage(@NotNull String playerName) {
        String htmlTemplate = loadResourceFile("success.html");
        String css = loadResourceFile("styles.css");

        return htmlTemplate
                .replace("{{PLAYER_NAME}}", playerName)
                .replace("{{CSS_CONTENT}}", css);
    }

    @NotNull
    private String createErrorPage(@NotNull String errorMessage) {
        String htmlTemplate = loadResourceFile("error.html");
        String css = loadResourceFile("styles.css");

        return htmlTemplate
                .replace("{{ERROR_MESSAGE}}", errorMessage)
                .replace("{{CSS_CONTENT}}", css);
    }
}