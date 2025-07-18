package com.mongenscave.mcspotifylink.identifiers.keys;

import com.mongenscave.mcspotifylink.McSpotifyLink;
import com.mongenscave.mcspotifylink.config.Config;
import com.mongenscave.mcspotifylink.processor.MessageProcessor;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public enum ConfigKeys {
    ALIASES("aliases"),
    DATABASE_TYPE("database.type"),

    SPOTIFY_CLIENT_ID("spotify.client-id"),
    SPOTIFY_CLIENT_SECRET("spotify.client-secret"),
    SPOTIFY_REDIRECT_URI("spotify.redirect-uri"),
    SPOTIFY_REDIRECT_URI_PORT("spotify.redirect-uri-port"),
    SPOTIFY_SCOPES("spotify.scopes"),

    WEB_SPOTIFY_ERROR("web-errors.spotify-error"),
    WEB_NO_CODE("web-errors.no-code"),
    WEB_NO_STATE("web-errors.no-state"),
    WEB_NO_PLAYER("web-errors.no-player"),
    WEB_INVALID_PLAYER("web-errors.invalid-player"),

    SONGS_TIMESTAMP_ENABLED("songs-timestamp.show-enabled"),
    SONGS_TIMESTAMP_CHARACTER("songs-timestamp.character"),
    SONGS_TIMESTAMP_PASSED("songs-timestamp.passed"),
    SONGS_TIMESTAMP_REMAINING("songs-timestamp.remaining"),
    SONGS_TIMESTAMP_ACTIONBAR("songs-timestamp.actionbar"),
    SONGS_TIMESTAMP_PLAYING("songs-timestamp.playing"),
    SONGS_TIMESTAMP_PAUSE("songs-timestamp.pause"),
    SONGS_TIMESTAMP_LENGTH("songs-timestamp.length"),

    TOAST_ENABLED("toast.enabled"),
    TOAST_MESSAGE("toast.message"),
    TOAST_MATERIAL("toast.material"),

    PLACEHOLDERS_TRUE("placeholders.true"),
    PLACEHOLDERS_FALSE("placeholders.false");

    private static final Config config = McSpotifyLink.getInstance().getConfiguration();
    private final String path;

    ConfigKeys(@NotNull String path) {
        this.path = path;
    }

    public static @NotNull String getString(@NotNull String path) {
        return config.getString(path);
    }

    public @NotNull String getString() {
        return MessageProcessor.process(config.getString(path));
    }

    public @NotNull String getString(@NotNull String placeholder, @NotNull String value) {
        return getString().replace(placeholder, value);
    }

    public boolean getBoolean() {
        return config.getBoolean(path);
    }

    public int getInt() {
        return config.getInt(path);
    }

    public List<String> getList() {
        return config.getList(path);
    }
}
