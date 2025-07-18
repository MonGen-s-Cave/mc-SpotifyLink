package com.mongenscave.mcspotifylink.utils;

import com.mongenscave.mcspotifylink.McSpotifyLink;
import com.mongenscave.mcspotifylink.commands.CommandSpotify;
import com.mongenscave.mcspotifylink.handlers.CommandExceptionHandler;
import com.mongenscave.mcspotifylink.identifiers.keys.ConfigKeys;
import lombok.experimental.UtilityClass;
import revxrsal.commands.bukkit.BukkitLamp;
import revxrsal.commands.orphan.Orphans;

@UtilityClass
public class RegisterUtils {
    private static final McSpotifyLink plugin = McSpotifyLink.getInstance();

    public void startUpdateTask() {
        plugin.getScheduler().runTaskTimerAsynchronously(() -> plugin.getServer().getOnlinePlayers().forEach(player -> plugin.getSpotifyManager().updatePlayer(player)), 0, 20L * 30);
    }

    public void registerCommands() {
        var lamp = BukkitLamp.builder(plugin)
                .exceptionHandler(new CommandExceptionHandler())
                .build();

        lamp.register(Orphans.path(ConfigKeys.ALIASES.getList().toArray(String[]::new)).handler(new CommandSpotify()));
    }
}
