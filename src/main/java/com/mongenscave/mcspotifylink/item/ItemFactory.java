package com.mongenscave.mcspotifylink.item;

import com.mongenscave.mcspotifylink.McSpotifyLink;
import com.mongenscave.mcspotifylink.config.Config;
import com.mongenscave.mcspotifylink.data.common.ItemData;
import com.mongenscave.mcspotifylink.processor.MessageProcessor;
import com.mongenscave.mcspotifylink.utils.LoggerUtils;
import dev.dejvokep.boostedyaml.block.implementation.Section;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Unmodifiable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@SuppressWarnings("deprecation")
public interface ItemFactory {
    @Contract("_ -> new")
    static @NotNull ItemFactory create(@NotNull Material material) {
        return new ItemBuilder(material);
    }

    @Contract("_, _ -> new")
    static @NotNull ItemFactory create(@NotNull Material material, int count) {
        return new ItemBuilder(material, count);
    }

    @Contract("_, _, _ -> new")
    static @NotNull ItemFactory create(@NotNull Material material, int count, short damage) {
        return new ItemBuilder(material, count, damage);
    }

    @Contract("_ -> new")
    static @NotNull ItemFactory create(ItemStack item) {
        return new ItemBuilder(item);
    }

    static Optional<ItemStack> buildItem(@NotNull Section section, @NotNull String configPath) {
        try {
            String materialName = section.getString("material");
            if (materialName == null || materialName.isEmpty()) return Optional.empty();

            Material material;
            try {
                material = Material.valueOf(materialName.toUpperCase());
            } catch (IllegalArgumentException exception) {
                return Optional.empty();
            }

            int amount = section.getInt("amount", 1);
            amount = Math.max(1, Math.min(amount, 64));

            String rawName = section.getString("name", "");
            String processedName = rawName.isEmpty() ? "" : MessageProcessor.process(rawName);

            List<String> lore = section.getStringList("lore").stream()
                    .map(MessageProcessor::process)
                    .toList();

            List<ItemFlag> flags = section.getStringList("flag").stream()
                    .map(ItemFlag::valueOf)
                    .toList();

            ItemStack item = ItemFactory.create(material, amount)
                    .setName(processedName)
                    .addLore(lore.toArray(new String[0]))
                    .finish();

            List<String> enchantmentStrings = section.getStringList("enchantments");
            for (String enchantmentString : enchantmentStrings) {
                String[] parts = enchantmentString.split(":");
                if (parts.length == 2) {
                    try {
                        Enchantment enchantment = Enchantment.getByKey(NamespacedKey.minecraft(parts[0].toLowerCase()));
                        if (enchantment != null) {
                            int level = Integer.parseInt(parts[1]);
                            item.addUnsafeEnchantment(enchantment, level);
                        }
                    } catch (Exception ignored) {}
                }
            }

            boolean unbreakable = section.getBoolean("unbreakable", false);
            if (unbreakable) item.editMeta(meta -> meta.setUnbreakable(true));

            item.editMeta(meta -> {
                PersistentDataContainer pdc = meta.getPersistentDataContainer();
                NamespacedKey key = new NamespacedKey(McSpotifyLink.getInstance(), "mcSpotifyLink");
                pdc.set(key, PersistentDataType.STRING, configPath);

                meta.addItemFlags(flags.toArray(new ItemFlag[0]));
            });

            return Optional.of(item);
        } catch (Exception exception) {
            return Optional.empty();
        }
    }

    static Optional<ItemStack> createItemFromString(@NotNull String path, @NotNull Config config) {
        Section section = config.getSection(path);
        return section != null ? buildItem(section, path) : Optional.empty();
    }

    ItemFactory setType(@NotNull Material material);

    ItemFactory setCount(int newCount);

    ItemFactory setName(@NotNull String name);

    ItemBuilder setLore(@NotNull List<String> lore);

    void addEnchantment(@NotNull Enchantment enchantment, int level);

    default ItemFactory addEnchantments(@NotNull ConcurrentHashMap<Enchantment, Integer> enchantments) {
        enchantments.forEach(this::addEnchantment);
        return this;
    }

    ItemBuilder addLore(@NotNull String... lores);

    ItemFactory setUnbreakable();

    default void addFlag(@NotNull ItemFlag... flags) {
        Arrays.stream(flags).forEach(this::addFlag);
    }

    ItemFactory removeLore(int line);

    ItemStack finish();

    boolean isFinished();

    static void setItemsForMenu(@NotNull String path, @NotNull Inventory inventory) {
        try {
            Config config = McSpotifyLink.getInstance().getGuis();
            Section section = config.getSection(path);

            if (section == null) {
                LoggerUtils.warn("No section found at path: " + path);
                return;
            }

            ConcurrentHashMap<String, ItemData> itemsToPlace = new ConcurrentHashMap<>();

            for (String key : section.getRoutesAsStrings(false)) {
                Section itemSection = section.getSection(key);
                if (itemSection == null) continue;

                String fullPath = path + "." + key;
                Optional<ItemStack> itemOptional = buildItem(itemSection, fullPath);

                if (itemOptional.isPresent()) {
                    Object slotConfig = itemSection.get("slot");
                    List<Integer> slots = parseSmartSlots(slotConfig, inventory);

                    if (!slots.isEmpty()) itemsToPlace.put(key, new ItemData(itemOptional.get(), slots, itemSection.getInt("priority", 0)));
                } else LoggerUtils.warn("Failed to build item from path: " + fullPath);
            }

            itemsToPlace.entrySet().stream()
                    .sorted((e1, e2) -> Integer.compare(e2.getValue().priority(), e1.getValue().priority()))
                    .forEach(entry -> placeItemInSlots(entry.getValue(), inventory));

        } catch (Exception exception) {
            LoggerUtils.error("Failed to set items for menu from path " + path + ": " + exception.getMessage());
        }
    }

    private static List<Integer> parseSmartSlots(Object slotConfig, Inventory inventory) {
        if (slotConfig == null) return Collections.emptyList();

        int size = inventory.getSize();

        if (slotConfig instanceof Integer) {
            int slot = (Integer) slotConfig;
            return isValidSlot(slot, size) ? List.of(slot) : Collections.emptyList();
        }

        if (slotConfig instanceof String) {
            String slotStr = ((String) slotConfig).toLowerCase().trim();

            switch (slotStr) {
                case "border" -> {
                    return getBorderSlots(size);
                }
                case "corners" -> {
                    return getCornerSlots(size);
                }
                case "center" -> {
                    return getCenterSlots(size);
                }
                case "edges" -> {
                    return getEdgeSlots(size);
                }
                case "top" -> {
                    return getTopRowSlots();
                }
                case "bottom" -> {
                    return getBottomRowSlots(size);
                }
                case "left" -> {
                    return getLeftColumnSlots(size);
                }
                case "right" -> {
                    return getRightColumnSlots(size);
                }
                case "fill" -> {
                    return getFillSlots(inventory);
                }
            }

            if (slotStr.startsWith("grid:")) return parseGridSlots(slotStr, size);
            if (slotStr.startsWith("chess:")) return parseChessSlots(slotStr, size);

            return parseCustomSlots(slotStr, size);
        }

        return Collections.emptyList();
    }

    @NotNull
    private static List<Integer> parseCustomSlots(@NotNull String slotStr, int size) {
        List<Integer> slots = new ArrayList<>();

        String[] parts = slotStr.split(",");

        for (String part : parts) {
            part = part.trim();

            if (part.contains("-")) {
                String[] range = part.split("-");
                if (range.length == 2) {
                    try {
                        int start = Integer.parseInt(range[0].trim());
                        int end = Integer.parseInt(range[1].trim());

                        for (int i = Math.min(start, end); i <= Math.max(start, end); i++) {
                            if (isValidSlot(i, size)) slots.add(i);
                        }
                    } catch (NumberFormatException ignored) {}
                }
            } else {
                try {
                    int slot = Integer.parseInt(part);
                    if (isValidSlot(slot, size)) slots.add(slot);
                } catch (NumberFormatException ignored) {}
            }
        }

        return slots;
    }

    private static List<Integer> parseGridSlots(@NotNull String gridStr, int size) {
        String[] parts = gridStr.split(":");
        if (parts.length < 2) return Collections.emptyList();

        String[] dimensions = parts[1].split("x");
        if (dimensions.length != 2) return Collections.emptyList();

        try {
            return getIntegers(size, dimensions, parts);
        } catch (NumberFormatException exception) {
            return Collections.emptyList();
        }
    }

    @NotNull
    private static List<Integer> getIntegers(int size, @NotNull String[] dimensions, @NotNull String[] parts) {
        int cols = Integer.parseInt(dimensions[0]);
        int rows = Integer.parseInt(dimensions[1]);
        int offset = parts.length > 3 ? Integer.parseInt(parts[3]) : 0;

        List<Integer> slots = new ArrayList<>();

        for (int row = 0; row < rows; row++) {
            for (int col = 0; col < cols; col++) {
                int slot = offset + (row * 9) + col;
                if (isValidSlot(slot, size)) slots.add(slot);
            }
        }
        return slots;
    }

    @NotNull
    private static List<Integer> parseChessSlots(@NotNull String chessStr, int size) {
        String[] parts = chessStr.split(":");
        if (parts.length < 2) return Collections.emptyList();

        boolean isBlack = parts[1].equals("black");
        List<Integer> slots = Collections.synchronizedList(new ArrayList<>());

        int rows = size / 9;
        for (int row = 0; row < rows; row++) {
            for (int col = 0; col < 9; col++) {
                int slot = row * 9 + col;
                boolean isBlackSquare = (row + col) % 2 == 0;

                if (isBlack == isBlackSquare && isValidSlot(slot, size)) slots.add(slot);
            }
        }

        return slots;
    }

    private static List<Integer> getBorderSlots(int size) {
        List<Integer> slots = new ArrayList<>();
        int rows = size / 9;

        for (int i = 0; i < 9; i++) {
            slots.add(i);
            if (rows > 1) slots.add((rows - 1) * 9 + i);
        }

        for (int row = 1; row < rows - 1; row++) {
            slots.add(row * 9);
            slots.add(row * 9 + 8);
        }

        return slots.stream().distinct().sorted().toList();
    }

    @NotNull
    private static List<Integer> getCornerSlots(int size) {
        int rows = size / 9;
        List<Integer> corners = new ArrayList<>();

        corners.add(0);
        corners.add(8);
        if (rows > 1) {
            corners.add((rows - 1) * 9);
            corners.add((rows - 1) * 9 + 8);
        }

        return corners;
    }

    @NotNull
    private static List<Integer> getCenterSlots(int size) {
        int rows = size / 9;
        List<Integer> centers = new ArrayList<>();

        if (rows >= 3) {
            int centerRow = rows / 2;
            for (int col = 3; col <= 5; col++) {
                centers.add(centerRow * 9 + col);
            }
        }

        return centers;
    }

    @NotNull
    private static List<Integer> getEdgeSlots(int size) {
        List<Integer> edges = new ArrayList<>();
        int rows = size / 9;

        for (int row = 1; row < rows - 1; row++) {
            for (int col = 1; col < 8; col++) {
                edges.add(row * 9 + col);
            }
        }

        return edges;
    }

    @NotNull
    @Contract(value = " -> new", pure = true)
    private static @Unmodifiable List<Integer> getTopRowSlots() {
        return List.of(0, 1, 2, 3, 4, 5, 6, 7, 8);
    }

    @NotNull
    private static List<Integer> getBottomRowSlots(int size) {
        int lastRow = (size / 9) - 1;
        List<Integer> slots = Collections.synchronizedList(new ArrayList<>());

        for (int i = 0; i < 9; i++) {
            slots.add(lastRow * 9 + i);
        }

        return slots;
    }

    @NotNull
    private static List<Integer> getLeftColumnSlots(int size) {
        List<Integer> slots = Collections.synchronizedList(new ArrayList<>());
        int rows = size / 9;

        for (int row = 0; row < rows; row++) {
            slots.add(row * 9);
        }

        return slots;
    }

    @NotNull
    private static List<Integer> getRightColumnSlots(int size) {
        List<Integer> slots = Collections.synchronizedList(new ArrayList<>());
        int rows = size / 9;

        for (int row = 0; row < rows; row++) {
            slots.add(row * 9 + 8);
        }

        return slots;
    }

    @NotNull
    private static List<Integer> getFillSlots(@NotNull Inventory inventory) {
        List<Integer> emptySlots = Collections.synchronizedList(new ArrayList<>());

        for (int i = 0; i < inventory.getSize(); i++) {
            if (inventory.getItem(i) == null) emptySlots.add(i);
        }
        return emptySlots;
    }

    private static boolean isValidSlot(int slot, int size) {
        return slot >= 0 && slot < size;
    }

    private static void placeItemInSlots(@NotNull ItemData itemData, Inventory inventory) {
        for (int slot : itemData.slots()) {
            if (slot >= 0 && slot < inventory.getSize()) inventory.setItem(slot, itemData.item().clone());
        }
    }
}
