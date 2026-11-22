package com.hizeric.shenaniganscore.battleground.command;

/** Central permission constants. Brigadier checks and config defaults read these. */
public final class Permissions {
    public static final String USE = "shenaniganscore.use";
    public static final String BG_ADMIN = "shenaniganscore.battleground.admin";
    public static final String BG_PLAY = "shenaniganscore.battleground.play";
    /** Server-loadout editing. Inherited by BG_ADMIN, but grantable on its own. */
    public static final String KIT_ADMIN = "shenaniganscore.battleground.kit";

    private Permissions() {
    }
}
