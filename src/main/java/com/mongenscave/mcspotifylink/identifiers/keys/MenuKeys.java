package com.mongenscave.mcspotifylink.identifiers.keys;

import com.mongenscave.mcspotifylink.McSpotifyLink;
import com.mongenscave.mcspotifylink.config.Config;
import com.mongenscave.mcspotifylink.processor.MessageProcessor;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public enum MenuKeys {
    MENU_NAVIGATION_TITLE("navigation-menu.title"),
    MENU_NAVIGATION_SIZE("navigation-menu.size"),

    MENU_PLAYLIST_TITLE("playlist-menu.title"),
    MENU_PLAYLIST_SIZE("playlist-menu.size"),

    MENU_TRACKS_TITLE("tracks-menu.title"),
    MENU_TRACKS_SIZE("tracks-menu.size"),

    MENU_CONTROLS_TITLE("controls-menu.title"),
    MENU_CONTROLS_SIZE("controls-menu.size");

    private static final Config config = McSpotifyLink.getInstance().getGuis();
    private final String path;

    MenuKeys(@NotNull String path) {
        this.path = path;
    }

    public static @NotNull String getString(@NotNull String path) {
        return config.getString(path);
    }

    public @NotNull String getString() {
        return MessageProcessor.process(config.getString(path));
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
