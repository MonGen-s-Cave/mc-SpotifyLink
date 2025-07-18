package com.mongenscave.mcspotifylink.gui.models;

import com.mongenscave.mcspotifylink.McSpotifyLink;
import com.mongenscave.mcspotifylink.data.common.MenuController;
import com.mongenscave.mcspotifylink.gui.Menu;
import com.mongenscave.mcspotifylink.identifiers.keys.ItemKeys;
import com.mongenscave.mcspotifylink.identifiers.keys.MenuKeys;
import com.mongenscave.mcspotifylink.identifiers.keys.MessageKeys;
import com.mongenscave.mcspotifylink.item.ItemFactory;
import com.mongenscave.mcspotifylink.manager.SpotifyManager;
import com.mongenscave.mcspotifylink.utils.LoggerUtils;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

public class ControlsMenu extends Menu {
    private static final McSpotifyLink plugin = McSpotifyLink.getInstance();
    private final ConcurrentHashMap<Integer, ItemKeys> slotToItemKeyMap = new ConcurrentHashMap<>();

    public ControlsMenu(@NotNull MenuController menuController) {
        super(menuController);
    }

    @Override
    public void handleMenu(final @NotNull InventoryClickEvent event) {
        event.setCancelled(true);
        ItemStack clickedItem = event.getCurrentItem();
        int slot = event.getSlot();

        if (clickedItem == null || clickedItem.getType() == Material.AIR) return;

        ItemKeys clickedItemKey = slotToItemKeyMap.get(slot);
        SpotifyManager spotifyManager = plugin.getSpotifyManager();

        if (clickedItemKey != null) {
            event.setCancelled(true);

            Player player = menuController.owner();

            switch (clickedItemKey) {
                case CONTROLS_BACK -> new NavigationMenu(menuController).open();

                case CONTROLS_NEXT -> handleAsyncAction(player, spotifyManager::nextTrack);
                case CONTROLS_PAUSE -> handleAsyncAction(player, spotifyManager::pausePlayback);
                case CONTROLS_PLAY -> handleAsyncAction(player, spotifyManager::resumePlayback);
                case CONTROLS_PREVIOUS -> handleAsyncAction(player, spotifyManager::previousTrack);
            }
        }
    }

    @Override
    public void setMenuItems() {
        inventory.clear();
        slotToItemKeyMap.clear();

        ItemFactory.setItemsForMenu("controls-menu.items", inventory);

        setMenuItem(ItemKeys.CONTROLS_BACK);
        setMenuItem(ItemKeys.CONTROLS_NEXT);
        setMenuItem(ItemKeys.CONTROLS_PAUSE);
        setMenuItem(ItemKeys.CONTROLS_PLAY);
        setMenuItem(ItemKeys.CONTROLS_PREVIOUS);
    }

    @Override
    public String getMenuName() {
        return MenuKeys.MENU_NAVIGATION_TITLE.getString();
    }

    @Override
    public int getSlots() {
        return MenuKeys.MENU_NAVIGATION_SIZE.getInt();
    }

    @Override
    public int getMenuTick() {
        return 20;
    }

    private void setMenuItem(@NotNull ItemKeys itemKey) {
        ItemStack item = itemKey.getItem();
        if (item == null) return;

        int slot = itemKey.getSlot();
        inventory.setItem(slot, item);
        slotToItemKeyMap.put(slot, itemKey);
    }

    private void handleAsyncAction(Player player, Function<Player, CompletableFuture<Void>> action) {
        if (!plugin.getSpotifyManager().isConnected(player)) {
            player.sendMessage(MessageKeys.NOT_CONNECTED.getMessage());
            return;
        }

        action.apply(player).whenComplete((result, exception) -> {
            if (exception != null) LoggerUtils.error(exception.getMessage());
            else plugin.getScheduler().runTask(() -> plugin.getSpotifyManager().updatePlayer(player));
        });
    }
}
