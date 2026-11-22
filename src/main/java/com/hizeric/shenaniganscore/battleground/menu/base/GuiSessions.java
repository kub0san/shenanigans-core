package com.hizeric.shenaniganscore.battleground.menu.base;

import org.bukkit.plugin.Plugin;

/** The one shared {@link GuiSession}, registered by the battleground GUI facade. */
public final class GuiSessions {

    private static GuiSession current;

    private GuiSessions() {
    }

    public static synchronized GuiSession currentOr(Plugin fallback) {
        return current != null ? current : GuiSession.ofPlugin(fallback);
    }

    public static synchronized void set(GuiSession session) {
        current = session;
    }

    public static synchronized void clear() {
        current = null;
    }
}
