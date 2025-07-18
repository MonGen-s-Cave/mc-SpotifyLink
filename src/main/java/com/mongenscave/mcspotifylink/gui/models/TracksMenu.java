package com.mongenscave.mcspotifylink.gui.models;

import com.mongenscave.mcspotifylink.McSpotifyLink;
import com.mongenscave.mcspotifylink.data.common.MenuController;
import com.mongenscave.mcspotifylink.data.spotify.Playlist;
import com.mongenscave.mcspotifylink.data.spotify.Track;
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

public class TracksMenu extends PaginatedMenu {
    private final ConcurrentHashMap<Integer, ItemKeys> slotToItemKeyMap = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Integer, String> slotToTrackUriMap = new ConcurrentHashMap<>();
    private final Playlist playlist;
    private List<Track> tracks;
    private List<Integer> availableSlots;

    public TracksMenu(@NotNull MenuController menuController, @NotNull Playlist playlist) {
        super(menuController);
        this.playlist = playlist;
        loadTracks();
    }

    private void loadTracks() {
        Player player = menuController.owner();
        McSpotifyLink plugin = McSpotifyLink.getInstance();
        SpotifyManager spotifyManager = plugin.getSpotifyManager();

        spotifyManager.getPlaylistTracks(player, playlist.id())
                .whenComplete((tracks, exception) -> {
                    if (exception != null) {
                        LoggerUtils.error(exception.getMessage());
                        return;
                    }

                    this.tracks = tracks;
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
                case TRACKS_BACK -> {
                    if (page > 0) {
                        page--;
                        setMenuItems();
                    } else new PlayListsMenu(menuController).open();
                }
                case TRACKS_FORWARD -> {
                    if (hasNextPage()) {
                        page++;
                        setMenuItems();
                    }
                }
            }
        } else {
            String trackUri = slotToTrackUriMap.get(slot);
            McSpotifyLink plugin = McSpotifyLink.getInstance();

            if (trackUri != null) {
                Player player = (Player) event.getWhoClicked();
                plugin.getSpotifyManager().playTrack(player, trackUri)
                        .exceptionally(exception -> {
                            LoggerUtils.error(exception.getMessage());
                            return null;
                        });
            }
        }
    }

    @Override
    public void setMenuItems() {
        inventory.clear();
        slotToItemKeyMap.clear();
        slotToTrackUriMap.clear();

        ItemFactory.setItemsForMenu("tracks-menu.items", inventory);

        setMenuItem(ItemKeys.TRACKS_BACK);
        setMenuItem(ItemKeys.TRACKS_FORWARD);

        findAvailableSlots();

        if (tracks != null && !availableSlots.isEmpty()) populateTrackItems();
    }

    private void findAvailableSlots() {
        availableSlots = new ArrayList<>();

        for (int i = 0; i < inventory.getSize(); i++) {
            if (inventory.getItem(i) == null) availableSlots.add(i);
        }
    }

    private void populateTrackItems() {
        int tracksPerPage = availableSlots.size();
        int startIndex = page * tracksPerPage;
        int endIndex = Math.min(startIndex + tracksPerPage, tracks.size());

        for (int i = startIndex; i < endIndex; i++) {
            Track track = tracks.get(i);
            int slotIndex = i - startIndex;

            if (slotIndex >= availableSlots.size()) break;

            int slot = availableSlots.get(slotIndex);
            ItemStack trackItem = createTrackItem(track);

            inventory.setItem(slot, trackItem);
            slotToTrackUriMap.put(slot, track.uri());
        }
    }

    private ItemStack createTrackItem(@NotNull Track track) {
        McSpotifyLink plugin = McSpotifyLink.getInstance();

        String nameTemplate = plugin.getConfiguration().getString("gui.track-item.name");
        String processedName = MessageProcessor.process(nameTemplate
                        .replace("{name}", track.name())
                        .replace("{author}", track.artist())
        );

        List<String> loreTemplate = plugin.getConfiguration().getStringList("gui.track-item.lore");
        List<String> lore = loreTemplate.stream()
                .map(line -> MessageProcessor.process(line.replace("{length}", track.getFormattedDuration())))
                .toList();

        String materialName = plugin.getConfiguration().getString("gui.track-item.material");
        Material material;

        try {
            material = Material.valueOf(materialName.toUpperCase());
        } catch (IllegalArgumentException e) {
            material = Material.MUSIC_DISC_CAT;
        }

        return ItemFactory.create(material)
                .setName(processedName)
                .addLore(lore.toArray(new String[0]))
                .finish();
    }

    private boolean hasNextPage() {
        if (tracks == null || availableSlots == null || availableSlots.isEmpty()) return false;
        int tracksPerPage = availableSlots.size();
        return (page + 1) * tracksPerPage < tracks.size();
    }

    @Override
    public String getMenuName() {
        return MenuKeys.MENU_TRACKS_TITLE.getString().replace("{playlist}", playlist.name());
    }

    @Override
    public int getSlots() {
        return MenuKeys.MENU_TRACKS_SIZE.getInt();
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