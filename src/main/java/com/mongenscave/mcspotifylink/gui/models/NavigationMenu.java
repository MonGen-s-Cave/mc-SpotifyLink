package com.mongenscave.mcspotifylink.gui.models;

import com.mongenscave.mcspotifylink.data.common.MenuController;
import com.mongenscave.mcspotifylink.gui.Menu;
import com.mongenscave.mcspotifylink.identifiers.keys.ItemKeys;
import com.mongenscave.mcspotifylink.identifiers.keys.MenuKeys;
import com.mongenscave.mcspotifylink.item.ItemFactory;
import org.bukkit.Material;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;

import java.util.concurrent.ConcurrentHashMap;

public class NavigationMenu extends Menu {
    private final ConcurrentHashMap<Integer, ItemKeys> slotToItemKeyMap = new ConcurrentHashMap<>();

    public NavigationMenu(@NotNull MenuController menuController) {
        super(menuController);
    }

    @Override
    public void handleMenu(final @NotNull InventoryClickEvent event) {
        event.setCancelled(true);
        ItemStack clickedItem = event.getCurrentItem();
        int slot = event.getSlot();

        if (clickedItem == null || clickedItem.getType() == Material.AIR) return;

        ItemKeys clickedItemKey = slotToItemKeyMap.get(slot);

        if (clickedItemKey != null) {
            event.setCancelled(true);

            switch (clickedItemKey) {
                case NAVIGATION_CONTROLS -> new ControlsMenu(menuController).open();
                case NAVIGATION_PLAYLIST -> new PlayListsMenu(menuController).open();
            }
        }
    }

    @Override
    public void setMenuItems() {
        inventory.clear();
        slotToItemKeyMap.clear();

        ItemFactory.setItemsForMenu("navigation-menu.items", inventory);

        setMenuItem(ItemKeys.NAVIGATION_CONTROLS);
        setMenuItem(ItemKeys.NAVIGATION_PLAYLIST);
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
}
