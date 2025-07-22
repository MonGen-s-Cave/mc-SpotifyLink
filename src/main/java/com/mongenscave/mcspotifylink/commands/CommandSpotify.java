package com.mongenscave.mcspotifylink.commands;

import com.mongenscave.mcspotifylink.McSpotifyLink;
import com.mongenscave.mcspotifylink.data.common.MenuController;
import com.mongenscave.mcspotifylink.gui.models.NavigationMenu;
import com.mongenscave.mcspotifylink.identifiers.keys.MessageKeys;
import com.mongenscave.mcspotifylink.manager.SpotifyManager;
import com.mongenscave.mcspotifylink.model.CurrentTrack;
import com.mongenscave.mcspotifylink.processor.MessageProcessor;
import com.mongenscave.mcspotifylink.utils.LinkUtils;
import com.mongenscave.mcspotifylink.utils.LoggerUtils;
import net.coma112.easiermessages.EasierMessages;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;
import revxrsal.commands.annotation.CommandPlaceholder;
import revxrsal.commands.annotation.Subcommand;
import revxrsal.commands.bukkit.annotation.CommandPermission;
import revxrsal.commands.orphan.OrphanCommand;

import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

public class CommandSpotify implements OrphanCommand {
    private static final McSpotifyLink plugin = McSpotifyLink.getInstance();

    @CommandPlaceholder
    public void defaultCommand(@NotNull CommandSender sender) {
        for (String line : MessageKeys.HELP.getMessages()) {
            sender.sendMessage(MessageProcessor.process(line));
        }
    }

    @Subcommand("menu")
    @CommandPermission("spotifylink.menu")
    public void menu(@NotNull Player player) {
        if (!plugin.getSpotifyManager().isConnected(player)) {
            player.sendMessage(MessageKeys.NOT_CONNECTED.getMessage());
            return;
        }

        MenuController menuController = MenuController.getMenuUtils(player);

        new NavigationMenu(menuController).open();
    }

    @Subcommand("reload")
    @CommandPermission("spotifylink.reload")
    public void reload(@NotNull CommandSender sender) {
        plugin.getConfiguration().reload();
        plugin.getLanguage().reload();
        plugin.getGuis().reload();
        sender.sendMessage(MessageKeys.RELOAD.getMessage());
    }

    @Subcommand("connect")
    @CommandPermission("spotifylink.connect")
    public void connect(@NotNull Player player) {
        if (plugin.getSpotifyManager().isConnected(player)) {
            player.sendMessage(MessageKeys.ALREADY_CONNECTED.getMessage());
            return;
        }

        String authURL = plugin.getSpotifyManager().getAuthorizationUrl(player);

        for (String line : MessageKeys.CONNECT.getMessages()) {
            String processedLine = line.replace("{authURL}", authURL);
            Component component = EasierMessages.translateMessage(processedLine).build();

            player.sendMessage(component);
        }
    }

    @Subcommand("disconnect")
    @CommandPermission("spotifylink.disconnect")
    public void disconnect(@NotNull Player player) {
        if (!plugin.getSpotifyManager().isConnected(player)) {
            player.sendMessage(MessageKeys.NOT_CONNECTED.getMessage());
            return;
        }

        plugin.getSpotifyManager().disconnectUser(player)
                .thenRun(() -> player.sendMessage(MessageKeys.DISCONNECTED.getMessage()))
                .exceptionally(exception -> {
                    LoggerUtils.error(exception.getMessage());
                    return null;
                });
    }

    @Subcommand("status")
    @CommandPermission("spotifylink.status")
    public void status(@NotNull Player player) {
        if (!plugin.getSpotifyManager().isConnected(player)) {
            player.sendMessage(MessageKeys.NOT_CONNECTED.getMessage());
            return;
        }

        CurrentTrack track = plugin.getSpotifyManager().getCurrentTrack(player);

        if (track != null) {
            for (String line : MessageKeys.CURRENT_LISTENING.getMessages()) {
                player.sendMessage(MessageProcessor.process(line)
                        .replace("{song}", track.getTrackName())
                        .replace("{author}", track.getArtistName()));
            }
        } else player.sendMessage(MessageKeys.NOT_LISTENING.getMessage());
    }

    @Subcommand("control action play")
    @CommandPermission("spotifylink.control.play")
    public void controlPlay(@NotNull Player player) {
        handleControlAction(player, sm -> sm.resumePlayback(player));
    }

    @Subcommand("control action pause")
    @CommandPermission("spotifylink.control.pause")
    public void controlPause(@NotNull Player player) {
        handleControlAction(player, sm -> sm.pausePlayback(player));
    }

    @Subcommand("control action next")
    @CommandPermission("spotifylink.control.next")
    public void controlNext(@NotNull Player player) {
        handleControlAction(player, sm -> sm.nextTrack(player));
    }

    @Subcommand("control action previous")
    @CommandPermission("spotifylink.control.previous")
    public void controlPrevious(@NotNull Player player) {
        handleControlAction(player, sm -> sm.previousTrack(player));
    }

    @Subcommand("control")
    public void control(@NotNull Player player) {
        if (!plugin.getSpotifyManager().isConnected(player)) {
            player.sendMessage(MessageKeys.NOT_CONNECTED.getMessage());
            return;
        }

        for (String line : MessageKeys.CONTROL.getMessages()) {
            Component component = EasierMessages.translateMessage(line).build();

            player.sendMessage(component);
        }
    }

    @Subcommand("cover")
    @CommandPermission("spotifylink.cover")
    public void cover(@NotNull Player player, @NotNull String spotifyUrl) {
        if (!plugin.getSpotifyManager().isConnected(player)) {
            player.sendMessage(MessageKeys.NOT_CONNECTED.getMessage());
            return;
        }

        if (!LinkUtils.isValidSpotifyTrackUrl(spotifyUrl)) {
            player.sendMessage(MessageKeys.INVALID_URL.getMessage());
            return;
        }

        String trackId = LinkUtils.extractTrackIdFromUrl(spotifyUrl);
        if (trackId == null) {
            player.sendMessage(MessageKeys.FAILED_COVER.getMessage());
            return;
        }

        if (player.getInventory().firstEmpty() == -1) {
            player.sendMessage(MessageKeys.NOT_ENOUGH_SPACE.getMessage());
            return;
        }

        ItemStack mapItem = new ItemStack(Material.FILLED_MAP);

        plugin.getSpotifyManager().createCoverMap(player, trackId, mapItem)
                .thenAccept(success -> {
                    if (success) player.getInventory().addItem(mapItem);
                    else player.sendMessage(MessageKeys.FAILED_COVER.getMessage());
                })
                .exceptionally(exception -> {
                    LoggerUtils.error("Error creating cover map: " + exception.getMessage());
                    return null;
                });
    }

    @Subcommand("code")
    @CommandPermission("spotifylink.code")
    public void code(@NotNull Player player, @NotNull String spotifyUrl) {
        if (!plugin.getSpotifyManager().isConnected(player)) {
            player.sendMessage(MessageKeys.NOT_CONNECTED.getMessage());
            return;
        }

        if (!LinkUtils.isValidSpotifyTrackUrl(spotifyUrl)) {
            player.sendMessage(MessageKeys.INVALID_URL.getMessage());
            return;
        }

        String trackId = LinkUtils.extractTrackIdFromUrl(spotifyUrl);
        if (trackId == null) {
            player.sendMessage(MessageKeys.FAILED_COVER.getMessage());
            return;
        }

        if (player.getInventory().firstEmpty() == -1) {
            player.sendMessage(MessageKeys.NOT_ENOUGH_SPACE.getMessage());
            return;
        }

        ItemStack mapItem = new ItemStack(Material.FILLED_MAP);

        plugin.getSpotifyManager().createCodeMap(player, trackId, mapItem)
                .thenAccept(success -> {
                    if (success) player.getInventory().addItem(mapItem);
                    else player.sendMessage(MessageKeys.FAILED_COVER.getMessage());
                })
                .exceptionally(exception -> {
                    LoggerUtils.error("Error creating code map: " + exception.getMessage());
                    player.sendMessage(MessageKeys.FAILED_COVER.getMessage());
                    return null;
                });
    }

    private void handleControlAction(@NotNull Player player, @NotNull Function<SpotifyManager, CompletableFuture<Void>> action) {
        if (!plugin.getSpotifyManager().isConnected(player)) {
            player.sendMessage(MessageKeys.NOT_CONNECTED.getMessage());
            return;
        }

        action.apply(plugin.getSpotifyManager())
                .thenRun(() -> plugin.getScheduler().runTask(() -> plugin.getSpotifyManager().updatePlayer(player)))
                .exceptionally(exception -> null);
    }
}
