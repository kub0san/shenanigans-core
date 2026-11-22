package com.hizeric.shenaniganscore.battleground.menu.base;

import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * What the GUI foundation needs from its owner: a scheduler ({@link #plugin()})
 * and one hook to flush editor state before replacing a screen.
 *
 * <p>{@link #stagedStore()} is the per-player staged-value map used by the GlobalKit
 * editor. Values are tagged by a string key so several editors can coexist without
 * clashing.</p>
 */
public interface GuiSession {
    Plugin plugin();

    /** Saves whatever editor state the player has open. No-op when there is none. */
    default void flushOpenEditor(Player player) {
    }

    /** Per-player staged values, keyed by player UUID then by editor/setting key. */
    default Map<UUID, Map<String, Object>> stagedStore() {
        return NoOpStagedStore.INSTANCE;
    }

    /** A session with only a plugin: nothing to flush, no staging. */
    static GuiSession ofPlugin(Plugin plugin) {
        if (plugin == null) {
            throw new IllegalArgumentException("plugin must not be null");
        }
        return () -> plugin;
    }

    /** No-op staged store for sessions that do not support staging. */
    final class NoOpStagedStore {
        public static final Map<UUID, Map<String, Object>> INSTANCE = new ConcurrentHashMap<>();

        private NoOpStagedStore() {
        }
    }
}
