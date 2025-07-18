package com.mongenscave.mcspotifylink.handlers;

import com.mongenscave.mcspotifylink.identifiers.keys.MessageKeys;
import org.jetbrains.annotations.NotNull;
import revxrsal.commands.bukkit.actor.BukkitCommandActor;
import revxrsal.commands.bukkit.exception.BukkitExceptionHandler;
import revxrsal.commands.bukkit.exception.SenderNotPlayerException;
import revxrsal.commands.exception.NoPermissionException;

public class CommandExceptionHandler extends BukkitExceptionHandler {
    @Override
    public void onSenderNotPlayer(SenderNotPlayerException exception, @NotNull BukkitCommandActor actor) {
        actor.error(MessageKeys.PLAYER_REQUIRED.getMessage());
    }

    @Override
    public void onNoPermission(@NotNull NoPermissionException exception, @NotNull BukkitCommandActor actor) {
        actor.error(MessageKeys.NO_PERMISSION.getMessage());
    }
}
