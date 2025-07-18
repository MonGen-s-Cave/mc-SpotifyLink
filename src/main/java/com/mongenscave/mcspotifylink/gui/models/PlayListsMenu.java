package com.mongenscave.mcspotifylink.gui.models;

import com.mongenscave.mcspotifylink.McSpotifyLink;
import com.mongenscave.mcspotifylink.data.common.MenuController;
import com.mongenscave.mcspotifylink.data.spotify.Playlist;
import com.mongenscave.mcspotifylink.gui.PaginatedMenu;
import com.mongenscave.mcspotifylink.identifiers.keys.ItemKeys;
import com.mongenscave.mcspotifylink.identifiers.keys.MenuKeys;
import com.mongenscave.mcspotifylink.item.ItemFactory;
import com.mongenscave.mcspotifylink.manager.SpotifyManager;
import com.mongenscave.mcspotifylink.processor.MessageProcessor;
import com.mongenscave.mcspotifylink.utils.LoggerUtils;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

public class PlayListsMenu extends PaginatedMenu {
    private final ConcurrentHashMap<Integer, ItemKeys> slotToItemKeyMap = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Integer, Playlist> slotToPlaylistMap = new ConcurrentHashMap<>();
    private List<Playlist> playlists;
    private List<Integer> availableSlots;

    public PlayListsMenu(@NotNull MenuController menuController) {
        super(menuController);
        loadPlaylists();
    }

    private void loadPlaylists() {
        Player player = menuController.owner();
        McSpotifyLink plugin = McSpotifyLink.getInstance();
        SpotifyManager spotifyManager = plugin.getSpotifyManager();

        spotifyManager.getUserPlaylists(player).whenComplete((playlists, exception) -> {
            if (exception != null) {
                LoggerUtils.error(exception.getMessage());
                return;
            }

            this.playlists = playlists;
            plugin.getScheduler().runTask(() -> {
                if (inventory != null) setMenuItems();
            });
        });
    }

    @Override
    public void handleMenu(final @NotNull InventoryClickEvent event) {
        event.setCancelled(true);
        ItemStack clickedItem = event.getCurrentItem();
        int slot = event.getSlot();

        if (clickedItem == null || clickedItem.getType() == Material.AIR) return;

        ItemKeys clickedItemKey = slotToItemKeyMap.get(slot);

        if (clickedItemKey != null) {
            switch (clickedItemKey) {
                case PLAYLIST_BACK -> {
                    if (page > 0) {
                        page--;
                        setMenuItems();
                    } else new NavigationMenu(menuController).open();
                }
                case PLAYLIST_FORWARD -> {
                    if (hasNextPage()) {
                        page++;
                        setMenuItems();
                    }
                }
            }
        } else {
            Playlist playlist = slotToPlaylistMap.get(slot);

            if (playlist != null) {
                TracksMenu tracksMenu = new TracksMenu(menuController, playlist);
                tracksMenu.open();
            }
        }
    }

    @Override
    public void setMenuItems() {
        inventory.clear();
        slotToItemKeyMap.clear();
        slotToPlaylistMap.clear();

        ItemFactory.setItemsForMenu("playlist-menu.items", inventory);

        setMenuItem(ItemKeys.PLAYLIST_BACK);
        setMenuItem(ItemKeys.PLAYLIST_FORWARD);

        findAvailableSlots();

        if (playlists != null && !availableSlots.isEmpty()) populatePlaylistItems();
    }

    private void findAvailableSlots() {
        availableSlots = new ArrayList<>();

        for (int i = 0; i < inventory.getSize(); i++) {
            if (inventory.getItem(i) == null) availableSlots.add(i);
        }
    }

    private void populatePlaylistItems() {
        int playlistsPerPage = availableSlots.size();
        int startIndex = page * playlistsPerPage;
        int endIndex = Math.min(startIndex + playlistsPerPage, playlists.size());

        for (int i = startIndex; i < endIndex; i++) {
            Playlist playlist = playlists.get(i);
            int slotIndex = i - startIndex;

            if (slotIndex >= availableSlots.size()) break;

            int slot = availableSlots.get(slotIndex);
            ItemStack playlistItem = createPlaylistItem(playlist);

            inventory.setItem(slot, playlistItem);
            slotToPlaylistMap.put(slot, playlist);
        }
    }

    private ItemStack createPlaylistItem(@NotNull Playlist playlist) {
        try {
            McSpotifyLink plugin = McSpotifyLink.getInstance();

            String nameTemplate = plugin.getConfiguration().getString("gui.playlist-item.name", "&#39fc03{name} &8- &8(&#39fc03{count}&8)");
            String processedName = MessageProcessor.process(
                    nameTemplate
                            .replace("{name}", playlist.name())
                            .replace("{count}", String.valueOf(playlist.totalTracks()))
            );

            List<String> loreTemplate = plugin.getConfiguration().getStringList("gui.playlist-item.lore");
            if (loreTemplate == null || loreTemplate.isEmpty()) {
                loreTemplate = List.of("", " &8&l| &7ᴄʟɪᴄᴋ &8» &fᴏᴘᴇɴ", "");
            }

            List<String> lore = loreTemplate.stream()
                    .map(MessageProcessor::process)
                    .toList();

            String materialName = plugin.getConfiguration().getString("gui.playlist-item.material", "JUKEBOX");
            Material material;
            try {
                material = Material.valueOf(materialName.toUpperCase());
            } catch (IllegalArgumentException e) {
                material = Material.JUKEBOX;
                LoggerUtils.error("Invalid material '" + materialName + "' for playlist item, using JUKEBOX");
            }

            return ItemFactory.create(material)
                    .setName(processedName)
                    .addLore(lore.toArray(new String[0]))
                    .finish();
        } catch (Exception exception) {
            LoggerUtils.error("Failed to create playlist item: " + exception.getMessage());
            return ItemFactory.create(Material.JUKEBOX)
                    .setName("§cError: " + playlist.name())
                    .addLore("§7Tracks: " + playlist.totalTracks())
                    .finish();
        }
    }

    private boolean hasNextPage() {
        if (playlists == null || availableSlots == null || availableSlots.isEmpty()) return false;
        int playlistsPerPage = availableSlots.size();
        return (page + 1) * playlistsPerPage < playlists.size();
    }

    @Override
    public String getMenuName() {
        return MenuKeys.MENU_PLAYLIST_TITLE.getString();
    }

    @Override
    public int getSlots() {
        return MenuKeys.MENU_PLAYLIST_SIZE.getInt();
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