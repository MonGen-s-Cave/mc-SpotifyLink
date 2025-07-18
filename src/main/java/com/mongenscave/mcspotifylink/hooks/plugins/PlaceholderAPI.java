package com.mongenscave.mcspotifylink.hooks.plugins;

import com.mongenscave.mcspotifylink.McSpotifyLink;
import com.mongenscave.mcspotifylink.identifiers.keys.ConfigKeys;
import com.mongenscave.mcspotifylink.model.CurrentTrack;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@SuppressWarnings("deprecation")
public class PlaceholderAPI {
    public static boolean isRegistered = false;

    public static void registerHook() {
        if (Bukkit.getPluginManager().getPlugin("PlaceholderAPI") != null) {
            new PlaceholderIntegration().register();
            isRegistered = true;
        }
    }

    private static class PlaceholderIntegration extends PlaceholderExpansion {
        private static final McSpotifyLink plugin = McSpotifyLink.getInstance();

        @Override
        public @NotNull String getIdentifier() {
            return "mcspotifylink";
        }

        @Override
        public @NotNull String getAuthor() {
            return "coma112";
        }

        @Override
        public @NotNull String getVersion() {
            return plugin.getDescription().getVersion();
        }

        @Override
        public boolean canRegister() {
            return true;
        }

        @Override
        public boolean persist() {
            return true;
        }

        @Override
        public String onRequest(@Nullable OfflinePlayer player, @NotNull String params) {
            if (player == null) return "";

            Player onlinePlayer = player.getPlayer();
            if (onlinePlayer == null) return "";

            String[] args = params.split("_");
            if (args.length < 2) return "Invalid placeholder";

            String type = args[0].toLowerCase();
            String subType = args[1].toLowerCase();

            if (type.equals("get")) {
                return switch (subType) {
                    case "track" -> getCurrentTrackName(onlinePlayer);
                    case "author" -> getCurrentArtistName(onlinePlayer);
                    case "timestamp" -> getTimestamp(onlinePlayer);
                    case "state" -> getState(onlinePlayer);
                    case "time_passed" -> getTimePassed(onlinePlayer);
                    case "time_remaining" -> getLength(onlinePlayer);
                    default -> "Invalid placeholder";
                };
            }

            if (type.equals("is")) {
                if (subType.equals("connected")) return plugin.getSpotifyManager().isConnected(onlinePlayer) ? ConfigKeys.PLACEHOLDERS_TRUE.getString() : ConfigKeys.PLACEHOLDERS_FALSE.getString();
                else return "Invalid placeholder";
            }

            return "Invalid placeholder";
        }

        private String getCurrentTrackName(@NotNull Player player) {
            if (!plugin.getSpotifyManager().isConnected(player)) return "";

            CurrentTrack currentTrack = plugin.getSpotifyManager().getCurrentTrack(player);
            if (currentTrack == null) return "";

            return currentTrack.getTrackName();
        }

        private String getCurrentArtistName(@NotNull Player player) {
            if (!plugin.getSpotifyManager().isConnected(player)) return "";

            CurrentTrack currentTrack = plugin.getSpotifyManager().getCurrentTrack(player);
            if (currentTrack == null) return "";

            return currentTrack.getArtistName();
        }

        @NotNull
        private String getTimestamp(@NotNull Player player) {
            if (!plugin.getSpotifyManager().isConnected(player)) return "";

            CurrentTrack currentTrack = plugin.getSpotifyManager().getCurrentTrack(player);
            if (currentTrack == null) return "";

            return plugin.getTimestampManager().getTimestampPlaceholder(currentTrack);
        }

        @NotNull
        private String getState(@NotNull Player player) {
            if (!plugin.getSpotifyManager().isConnected(player)) return "";

            CurrentTrack currentTrack = plugin.getSpotifyManager().getCurrentTrack(player);
            if (currentTrack == null) return "";

            return currentTrack.isPlaying() ? ConfigKeys.SONGS_TIMESTAMP_PLAYING.getString() : ConfigKeys.SONGS_TIMESTAMP_PAUSE.getString();
        }

        @NotNull
        private String getLength(@NotNull Player player) {
            if (!plugin.getSpotifyManager().isConnected(player)) return "";

            CurrentTrack currentTrack = plugin.getSpotifyManager().getCurrentTrack(player);
            if (currentTrack == null) return "";

            return currentTrack.getFormattedDuration();
        }

        @NotNull
        private String getTimePassed(@NotNull Player player) {
            if (!plugin.getSpotifyManager().isConnected(player)) return "";

            CurrentTrack currentTrack = plugin.getSpotifyManager().getCurrentTrack(player);
            if (currentTrack == null) return "";

            return currentTrack.getFormattedProgress();
        }
    }
}