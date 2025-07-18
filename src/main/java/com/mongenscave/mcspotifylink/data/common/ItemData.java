package com.mongenscave.mcspotifylink.data.common;

import org.bukkit.inventory.ItemStack;

import java.util.List;

public record ItemData(ItemStack item, List<Integer> slots, int priority) {}
