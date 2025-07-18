package com.mongenscave.mcspotifylink.identifiers.keys;

import com.mongenscave.mcspotifylink.McSpotifyLink;
import com.mongenscave.mcspotifylink.config.Config;
import com.mongenscave.mcspotifylink.processor.MessageProcessor;
import lombok.Getter;
import org.jetbrains.annotations.NotNull;

import java.util.List;

@Getter
public enum MessageKeys {
    NO_PERMISSION("messages.no-permission"),
    PLAYER_REQUIRED("messages.player-required"),
    RELOAD("messages.reload"),

    HELP("messages.help"),
    ALREADY_CONNECTED("messages.already-connected"),
    CONNECT("messages.connect"),
    NOT_CONNECTED("messages.not-connected"),
    DISCONNECTED("messages.disconnected"),
    CURRENT_LISTENING("messages.current-listening"),
    NOT_LISTENING("messages.not-listening"),
    CONTROL("messages.control"),
    CONNECTED("messages.connected"),

    ERROR("messages.error");

    private static final Config language = McSpotifyLink.getInstance().getLanguage();
    private final String path;

    MessageKeys(@NotNull String path) {
        this.path = path;
    }

    public @NotNull String getMessage() {
        return MessageProcessor.process(language.getString(path)).replace("%prefix%", MessageProcessor.process(language.getString("prefix")));
    }

    public List<String> getMessages() {
        return language.getStringList(path)
                .stream()
                .toList();
    }
}
