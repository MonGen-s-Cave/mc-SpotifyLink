package com.mongenscave.mcspotifylink.data.common;

import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public record MenuController(@NotNull Player owner) {
    private static final ConcurrentHashMap<UUID, MenuController> menuMap = new ConcurrentHashMap<>();

    public static MenuController getMenuUtils(@NotNull Player player) {
        return menuMap.computeIfAbsent(player.getUniqueId(), uuid -> new MenuController(player));
    }

    public static void remove(@NotNull Player player) {
        menuMap.remove(player.getUniqueId());
    }
}
