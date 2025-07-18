package com.mongenscave.mcspotifylink.manager;

import com.github.Anon8281.universalScheduler.scheduling.tasks.MyScheduledTask;
import com.mongenscave.mcspotifylink.McSpotifyLink;
import com.mongenscave.mcspotifylink.identifiers.keys.ConfigKeys;
import com.mongenscave.mcspotifylink.model.CurrentTrack;
import com.mongenscave.mcspotifylink.processor.MessageProcessor;
import com.mongenscave.mcspotifylink.utils.LoggerUtils;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.UUID;

@SuppressWarnings("deprecation")
public class TimestampManager {
    private static final McSpotifyLink plugin = McSpotifyLink.getInstance();
    private MyScheduledTask timestampTask;

    public void startTimestampUpdates() {
        if (!ConfigKeys.SONGS_TIMESTAMP_ENABLED.getBoolean()) return;
        if (timestampTask != null && !timestampTask.isCancelled()) return;

        stopTimestampUpdates();

        timestampTask = plugin.getScheduler().runTaskTimerAsynchronously(this::updateAllPlayersActionBar, 0L, 20L);
    }

    public void stopTimestampUpdates() {
        if (timestampTask != null && !timestampTask.isCancelled()) {
            timestampTask.cancel();
            timestampTask = null;
        }
    }

    private void updateAllPlayersActionBar() {
        plugin.getSpotifyManager().getAllCurrentTracks()
                .thenAccept(currentTracks -> {
                    if (currentTracks.isEmpty()) {
                        stopTimestampUpdates();
                        return;
                    }

                    for (UUID playerId : currentTracks.keySet()) {
                        Player player = Bukkit.getPlayer(playerId);
                        if (player != null && player.isOnline()) updatePlayerActionBar(player, currentTracks.get(playerId));
                    }
                })
                .exceptionally(exception -> {
                    LoggerUtils.error(exception.getMessage());
                    return null;
                });
    }

    private void updatePlayerActionBar(@NotNull Player player, @NotNull CurrentTrack track) {
        String timestampBar = generateTimestampBar(track);
        String stateIndicator = track.isPlaying() ?
                ConfigKeys.SONGS_TIMESTAMP_PLAYING.getString() :
                ConfigKeys.SONGS_TIMESTAMP_PAUSE.getString();

        String actionBarMessage = ConfigKeys.SONGS_TIMESTAMP_ACTIONBAR.getString()
                .replace("{timestamp}", timestampBar)
                .replace("{passedLength}", track.getFormattedProgress())
                .replace("{remainingLength}", track.getFormattedDuration())
                .replace("{song}", track.getTrackName())
                .replace("{author}", track.getArtistName())
                .replace("{state}", stateIndicator);

        player.sendActionBar(MessageProcessor.process(actionBarMessage));
    }

    @NotNull
    private String generateTimestampBar(@NotNull CurrentTrack track) {
        String character = ConfigKeys.SONGS_TIMESTAMP_CHARACTER.getString();
        String passedColor = ConfigKeys.SONGS_TIMESTAMP_PASSED.getString();
        String remainingColor = ConfigKeys.SONGS_TIMESTAMP_REMAINING.getString();

        int totalLength = ConfigKeys.SONGS_TIMESTAMP_LENGTH.getInt();

        double progressPercentage = track.getProgressPercentage();
        int passedLength = (int) Math.round(progressPercentage * totalLength);
        int remainingLength = totalLength - passedLength;

        return passedColor +
                character.repeat(Math.max(0, passedLength)) +
                remainingColor +
                character.repeat(Math.max(0, remainingLength));
    }

    @NotNull
    public String getTimestampPlaceholder(@NotNull CurrentTrack track) {
        String timestampBar = generateTimestampBar(track);
        String stateIndicator = track.isPlaying() ?
                ConfigKeys.SONGS_TIMESTAMP_PLAYING.getString() :
                ConfigKeys.SONGS_TIMESTAMP_PAUSE.getString();

        return ConfigKeys.SONGS_TIMESTAMP_ACTIONBAR.getString()
                .replace("{timestamp}", timestampBar)
                .replace("{passedLength}", track.getFormattedProgress())
                .replace("{remainingLength}", track.getFormattedDuration())
                .replace("{song}", track.getTrackName())
                .replace("{author}", track.getArtistName())
                .replace("{state}", stateIndicator);
    }
}