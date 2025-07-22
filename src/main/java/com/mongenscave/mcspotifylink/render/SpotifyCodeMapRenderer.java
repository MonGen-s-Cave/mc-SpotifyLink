package com.mongenscave.mcspotifylink.render;

import org.bukkit.entity.Player;
import org.bukkit.map.MapCanvas;
import org.bukkit.map.MapRenderer;
import org.bukkit.map.MapView;
import org.jetbrains.annotations.NotNull;

import java.awt.image.BufferedImage;

@SuppressWarnings("deprecation")
public class SpotifyCodeMapRenderer extends MapRenderer {
    private final BufferedImage codeImage;
    private boolean rendered = false;

    public SpotifyCodeMapRenderer(@NotNull BufferedImage codeImage) {
        this.codeImage = codeImage;
    }

    @Override
    public void render(@NotNull MapView mapView, @NotNull MapCanvas canvas, @NotNull Player player) {
        if (rendered) return;

        for (int x = 0; x < 128; x++) {
            for (int y = 0; y < 128; y++) {
                canvas.setPixel(x, y, (byte) 34);
            }
        }

        canvas.drawImage(0, 0, codeImage);

        rendered = true;
    }
}
