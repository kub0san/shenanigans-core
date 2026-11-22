package com.hizeric.shenaniganscore.battleground.menu.shell;

import org.bukkit.entity.Player;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

/**
 * Keeps a staged player in the hub only when they deliberately close it.
 *
 * <p>Opening any GUI fires an InventoryCloseEvent for the one it replaces. If
 * that close also scheduled a hub reopen, each screen switch would open a hub,
 * close it, and schedule another hub — a once-per-tick loop that pins the
 * server thread in GUI rendering. Every screen open therefore holds a
 * suppression token until the tick after it completes. The token is a counter,
 * so nested opens cannot release each other prematurely.</p>
 */
public final class MenuLatch {
    /** A balanced suppress/release pair never exceeds this; exceeding it means a leak. */
    private static final int MAX_SUPPRESSIONS = 10;
    private static final Logger LOG = Logger.getLogger(MenuLatch.class.getName());

    private static final MenuLatch INSTANCE = new MenuLatch();

    private final Map<UUID, Integer> suppressed = new ConcurrentHashMap<>();

    private MenuLatch() {
    }

    public static MenuLatch get() {
        return INSTANCE;
    }

    public void suppress(Player player) {
        if (player != null) suppress(player.getUniqueId());
    }

    public void suppress(UUID id) {
        if (id == null) return;
        suppressed.compute(id, (key, count) -> {
            int next = (count == null ? 0 : count) + 1;
            if (next > MAX_SUPPRESSIONS) {
                LOG.warning("MenuLatch suppression count for " + key + " exceeded "
                        + MAX_SUPPRESSIONS + " — a release() is missing; capping to avoid a leak");
                return MAX_SUPPRESSIONS;
            }
            return next;
        });
    }

    public void release(Player player) {
        if (player != null) release(player.getUniqueId());
    }

    public void release(UUID id) {
        if (id == null) return;
        suppressed.computeIfPresent(id, (key, count) -> count <= 1 ? null : count - 1);
    }

    /** Drops every outstanding token for a player (deploy, quit, respawn). */
    public void clear(Player player) {
        if (player != null) clear(player.getUniqueId());
    }

    public void clear(UUID id) {
        if (id != null) suppressed.remove(id);
    }

    public boolean isSuppressed(Player player) {
        return player != null && isSuppressed(player.getUniqueId());
    }

    public boolean isSuppressed(UUID id) {
        return id != null && suppressed.containsKey(id);
    }

    /** Whether a close of the menu should bring the hub back. */
    public boolean shouldReopen(Player player, boolean staged) {
        return staged && player != null && !isSuppressed(player.getUniqueId());
    }

    public boolean shouldReopen(UUID id, boolean staged) {
        return staged && id != null && !isSuppressed(id);
    }

    public void clearAll() {
        suppressed.clear();
    }
}
