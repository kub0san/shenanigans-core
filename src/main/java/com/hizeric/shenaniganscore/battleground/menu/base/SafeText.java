package com.hizeric.shenaniganscore.battleground.menu.base;

import net.kyori.adventure.text.minimessage.MiniMessage;

/** Escapes config/player-supplied text before it reaches MiniMessage. */
public final class SafeText {

    private static final MiniMessage MM = MiniMessage.miniMessage();

    private SafeText() {
    }

    public static String escape(String value) {
        return value == null ? "" : MM.escapeTags(value);
    }
}
