package com.mongenscave.mcspotifylink.utils;

import lombok.experimental.UtilityClass;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@UtilityClass
public class LinkUtils {
    public boolean isValidSpotifyTrackUrl(@NotNull String url) {
        return url.matches("^(https://open\\.spotify\\.com/track/[a-zA-Z0-9]{22}(\\?.*)?|spotify:track:[a-zA-Z0-9]{22})$");
    }

    @Nullable
    public String extractTrackIdFromUrl(@NotNull String url) {
        try {
            if (url.startsWith("https://open.spotify.com/track/")) {
                String[] parts = url.split("/track/");

                if (parts.length > 1) {
                    String trackPart = parts[1].split("\\?")[0].split("#")[0];
                    return trackPart.length() == 22 ? trackPart : null;
                }
            } else if (url.startsWith("spotify:track:")) {
                String id = url.substring("spotify:track:".length());
                return id.length() == 22 ? id : null;
            }
        } catch (Exception exception) {
            LoggerUtils.error("Error extracting track ID: " + exception.getMessage());
        }
        return null;
    }
}