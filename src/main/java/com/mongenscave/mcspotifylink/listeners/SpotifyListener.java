package com.mongenscave.mcspotifylink.listeners;

import com.mongenscave.mcspotifylink.McSpotifyLink;
import com.mongenscave.mcspotifylink.utils.LoggerUtils;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.jetbrains.annotations.NotNull;

public class SpotifyListener implements Listener {
    private static final McSpotifyLink plugin = McSpotifyLink.getInstance();

    @EventHandler
    public void onPlayerJoin(final @NotNull PlayerJoinEvent event) {
        Player player = event.getPlayer();

        plugin.getScheduler().runTaskLaterAsynchronously(() -> plugin.getSpotifyManager().updatePlayer(player)
                .exceptionally(exception -> {
                    LoggerUtils.error(exception.getMessage());
                    return null;
                }), 20L);

        plugin.getTimestampManager().startTimestampUpdates();
    }

    @EventHandler
    public void onPlayerQuit(final @NotNull PlayerQuitEvent event) {
        plugin.getSpotifyManager().removePlayer(event.getPlayer());

        plugin.getScheduler().runTaskLaterAsynchronously(() -> {
            if (plugin.getSpotifyManager().getAllCurrentTracks().join().isEmpty()) plugin.getTimestampManager().stopTimestampUpdates();
        }, 20L);
    }
}
