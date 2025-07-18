package com.mongenscave.mcspotifylink.identifiers.keys;

import com.mongenscave.mcspotifylink.McSpotifyLink;
import com.mongenscave.mcspotifylink.item.ItemFactory;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;

public enum ItemKeys {
    NAVIGATION_PLAYLIST("navigation-menu.items.playlist-item"),
    NAVIGATION_CONTROLS("navigation-menu.items.controls-item"),

    PLAYLIST_BACK("playlist-menu.items.back"),
    PLAYLIST_FORWARD("playlist-menu.items.forward"),

    TRACKS_BACK("tracks-menu.items.back"),
    TRACKS_FORWARD("tracks-menu.items.forward"),

    CONTROLS_PLAY("controls-menu.items.play-item"),
    CONTROLS_PAUSE("controls-menu.items.pause-item"),
    CONTROLS_NEXT("controls-menu.items.next-item"),
    CONTROLS_PREVIOUS("controls-menu.items.previous-item"),
    CONTROLS_BACK("controls-menu.items.back");

    private final String path;

    ItemKeys(@NotNull final String path) {
        this.path = path;
    }

    public ItemStack getItem() {
        return ItemFactory.createItemFromString(path, McSpotifyLink.getInstance().getGuis()).orElse(null);
    }

    public int getSlot() {
        return McSpotifyLink.getInstance().getGuis().getInt(path + ".slot");
    }
}
