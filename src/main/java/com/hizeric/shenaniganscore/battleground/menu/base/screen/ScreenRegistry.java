package com.hizeric.shenaniganscore.battleground.menu.base.screen;

import com.hizeric.shenaniganscore.battleground.command.Permissions;
import com.hizeric.shenaniganscore.battleground.menu.base.GuiFrame;
import com.hizeric.shenaniganscore.battleground.menu.base.screen.ScreenSpec;
import com.hizeric.shenaniganscore.battleground.menu.base.screen.ScreenRegistry;

/** The single source of truth for every screen's size, title and permission. */
public final class ScreenRegistry {

    private ScreenRegistry() {
    }

    public static final ScreenSpec CONFIRM = new ScreenSpec("confirm", "CONFIRM",
            3, GuiFrame.Density.NONE, null);

    public static final ScreenSpec HUB = new ScreenSpec("hub",
            "BATTLEGROUND", 5, GuiFrame.Density.NONE, Permissions.BG_PLAY);

    public static final ScreenSpec MARKET = new ScreenSpec("market",
            "MARKET", 5, GuiFrame.Density.NONE, Permissions.BG_PLAY);

    public static final ScreenSpec LOADOUT = new ScreenSpec("loadout",
            "LOADOUT", 5, GuiFrame.Density.NONE, Permissions.BG_PLAY);

    public static final ScreenSpec ADMIN = new ScreenSpec("admin",
            "ADMIN", 5, GuiFrame.Density.NONE, Permissions.BG_ADMIN);

    public static final ScreenSpec ROUND_CONTROLS = new ScreenSpec("round-controls",
            "ROUND CONTROLS", 5, GuiFrame.Density.NONE, Permissions.BG_ADMIN);

    public static final ScreenSpec GLOBAL_KIT = new ScreenSpec("global-kit",
            "LOADOUT", 3, GuiFrame.Density.NONE, Permissions.KIT_ADMIN);

    /** All specs, for startup validation. */
    public static final ScreenSpec[] ALL = {
            CONFIRM, HUB, MARKET, LOADOUT, ADMIN, ROUND_CONTROLS, GLOBAL_KIT
    };
}