package com.mongenscave.mcspotifylink.render;

import org.bukkit.entity.Player;
import org.bukkit.map.MapCanvas;
import org.bukkit.map.MapRenderer;
import org.bukkit.map.MapView;
import org.jetbrains.annotations.NotNull;

import java.awt.image.BufferedImage;

public class SpotifyCoverMapRenderer extends MapRenderer {
    private final BufferedImage image;
    private boolean rendered = false;

    public SpotifyCoverMapRenderer(@NotNull BufferedImage image) {
        this.image = image;
    }

    @Override
    public void render(@NotNull MapView mapView, @NotNull MapCanvas canvas, @NotNull Player player) {
        if (rendered) return;

        canvas.drawImage(0, 0, image);

        rendered = true;
    }
}
