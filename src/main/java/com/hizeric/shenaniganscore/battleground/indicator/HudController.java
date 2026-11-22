package com.hizeric.shenaniganscore.battleground.indicator;

import com.hizeric.shenaniganscore.ShenanigansCore;
import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class HudController {

    private static final long DEFAULT_MIN_INTERVAL_MS = 100L;

    private final ShenanigansCore plugin;
    private final long minIntervalNanos;

    private final Map<UUID, CacheEntry> cache = new ConcurrentHashMap<>();

    private record CacheEntry(Component lastSent, long lastSentNanos) { }

    public HudController(ShenanigansCore plugin) {
        this(plugin, DEFAULT_MIN_INTERVAL_MS);
    }

    public HudController(ShenanigansCore plugin, long minIntervalMs) {
        this.plugin = plugin;
        this.minIntervalNanos = minIntervalMs * 1_000_000L;
    }

    public void update(Player player, HudModelSupplier modelSupplier) {
        UUID id = player.getUniqueId();
        long now = System.nanoTime();

        CacheEntry cached = cache.get(id);
        if (cached != null && now - cached.lastSentNanos < minIntervalNanos) {
            return;
        }

        Component rendered = ActionBarView.render(modelSupplier.build());

        if (cached != null && rendered.equals(cached.lastSent)) {
            cache.put(id, new CacheEntry(cached.lastSent, now));
            return;
        }

        player.getScheduler().run(plugin, task -> {
            CacheEntry latest = cache.get(id);
            if (latest != null && rendered.equals(latest.lastSent)) return;
            player.sendActionBar(rendered);
            cache.put(id, new CacheEntry(rendered, System.nanoTime()));
        }, null);
    }

    public void forget(UUID playerId) {
        cache.remove(playerId);
    }

    @FunctionalInterface
    public interface HudModelSupplier {
        java.util.List<ActionBarView.Slot> build();
    }
}