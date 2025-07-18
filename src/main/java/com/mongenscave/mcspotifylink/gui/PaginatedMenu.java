package com.mongenscave.mcspotifylink.gui;

import com.mongenscave.mcspotifylink.data.common.MenuController;
import org.jetbrains.annotations.NotNull;

public abstract class PaginatedMenu extends Menu {
    protected int page = 0;

    public PaginatedMenu(@NotNull MenuController menuController) {
        super(menuController);
    }
}
