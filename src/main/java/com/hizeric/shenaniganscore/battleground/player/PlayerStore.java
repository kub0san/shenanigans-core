package com.hizeric.shenaniganscore.battleground.player;

import com.hizeric.shenaniganscore.battleground.ability.AbilityIdMigration;
import com.hizeric.shenaniganscore.battleground.ability.AbilityRail;
import com.hizeric.shenaniganscore.battleground.storage.Database;
import com.hizeric.shenaniganscore.battleground.storage.Databases;
import com.hizeric.shenaniganscore.battleground.storage.PlayerRecord;
import com.hizeric.shenaniganscore.battleground.storage.PlayerRepository;
import com.hizeric.shenaniganscore.battleground.storage.StorageException;
import com.hizeric.shenaniganscore.core.ability.AbilityId;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;

public final class PlayerStore {

    private static final long SHUTDOWN_TIMEOUT_MS = 5_000L;

    private final JavaPlugin plugin;
    private final File folder;
    private final PlayerRepository repository;
    private final Map<UUID, BattlegroundPlayer> cache = new ConcurrentHashMap<>();
    private final Map<UUID, CompletableFuture<Void>> writeTails = new ConcurrentHashMap<>();
    private final Set<UUID> dirty = ConcurrentHashMap.newKeySet();
    private final ExecutorService ioExecutor;
    private volatile boolean closing;
    private BukkitTask flushTask;

    public PlayerStore(JavaPlugin plugin, File battlegroundFolder) {
        this(plugin, battlegroundFolder, Databases.require());
    }

    public PlayerStore(JavaPlugin plugin, File battlegroundFolder, Database database) {
        this.plugin = plugin;
        this.folder = battlegroundFolder;
        this.repository = new PlayerRepository(database);
        this.ioExecutor = Executors.newVirtualThreadPerTaskExecutor();
    }

    /** Explicit injection for callers/tests that own the database lifecycle (new API). */
    public PlayerStore(JavaPlugin plugin, Database database) {
        this(plugin, null, database);
    }

    public void enable() {
        if (plugin != null) {
            File legacy = folder == null ? null : new File(folder, "players");
            if (legacy != null) {
                File[] oldFiles = legacy.listFiles((dir, name) -> name.endsWith(".yml"));
                if (oldFiles != null && oldFiles.length > 0) {
                    plugin.getLogger().warning("Found " + oldFiles.length + " legacy player YAML file(s) in "
                            + legacy + " - they are NOT imported into the SQL store; that data will start fresh.");
                }
            }
            flushTask = Bukkit.getScheduler().runTaskTimer(plugin, this::flushDirty, 100L, 100L);
        }
        // Players already online at enable (restart / plugman reload) must have their records in
        // the cache before the first get(); later joins are preloaded by the pre-login listener.
        preloadOnline();
    }

    public void disable() {
        closing = true;
        if (flushTask != null) {
            try { flushTask.cancel(); } catch (Throwable ignored) { }
            flushTask = null;
        }

        CompletableFuture<?>[] pending = writeTails.values().toArray(CompletableFuture[]::new);
        try {
            CompletableFuture.allOf(pending).get(SHUTDOWN_TIMEOUT_MS, TimeUnit.MILLISECONDS);
        } catch (Exception e) {
            warn("Error draining player-data writes", e);
        }

        for (UUID uuid : new ArrayList<>(dirty)) {
            BattlegroundPlayer player = cache.get(uuid);
            if (player != null) writeSafely(snapshot(player));
        }
        dirty.clear();
        writeTails.clear();
        try { ioExecutor.shutdown(); } catch (Throwable ignored) { }
        cache.clear();
    }

    // ------------------------------------------------------------------ preload

    public void preload(UUID uuid) {
        if (uuid == null || cache.containsKey(uuid)) return;
        BattlegroundPlayer player = new BattlegroundPlayer(uuid);
        try {
            repository.load(uuid).ifPresent(record -> applySafely(player, record));
        } catch (StorageException e) {
            warn("Could not preload data for " + uuid, e);
        }
        cache.put(uuid, player);
    }

    public void preloadOnline() {
        if (plugin == null) return;
        for (var player : Bukkit.getOnlinePlayers()) {
            ioExecutor.execute(() -> preload(player.getUniqueId()));
        }
    }

    // ------------------------------------------------------------------ reads

    public BattlegroundPlayer get(UUID uuid) {
        if (uuid == null) return null;
        BattlegroundPlayer player = cache.get(uuid);
        if (player != null) return player;
        // Defensive fallback: preload was missed.
        preload(uuid);
        return cache.get(uuid);
    }

    public void markDirty(UUID uuid) {
        if (uuid != null) dirty.add(uuid);
    }

    public Collection<BattlegroundPlayer> all() {
        return cache.values();
    }

    public void resetCredits() {
        for (BattlegroundPlayer player : cache.values()) {
            player.setCredits(0);
            dirty.add(player.uuid());
        }
    }

    public void resetRounds() {
        for (BattlegroundPlayer player : cache.values()) {
            player.resetRound();
            dirty.add(player.uuid());
        }
    }

    // ------------------------------------------------------------------ writes

    private void flushDirty() {
        if (closing || dirty.isEmpty()) return;
        for (UUID uuid : new ArrayList<>(dirty)) {
            if (!dirty.remove(uuid)) continue;
            BattlegroundPlayer player = cache.get(uuid);
            if (player == null) continue;
            enqueueWrite(snapshot(player));
        }
    }

    private void enqueueWrite(PlayerRecord record) {
        CompletableFuture<Void> next = writeTails
                .computeIfAbsent(record.uuid(), k -> CompletableFuture.completedFuture(null))
                .thenRunAsync(() -> writeSafely(record), ioExecutor);
        writeTails.put(record.uuid(), next);
        next.whenComplete((ok, err) -> {
            if (err != null) dirty.add(record.uuid());
        });
    }

    private void writeSafely(PlayerRecord record) {
        try {
            repository.upsert(record);
        } catch (StorageException e) {
            warn("Could not save " + record.uuid(), e);
            dirty.add(record.uuid());
        }
    }

    // ------------------------------------------------------------------ mapping

    private static void applySafely(BattlegroundPlayer player, PlayerRecord record) {
        player.setCredits(record.credits());
        player.recordKills(record.kills());
        player.recordDeaths(record.deaths());
        player.setStreak(record.streak());
        player.recordBestStreak(record.bestStreak());
        for (String entry : record.owned()) {
            try {
                String canonical = LegacyOwnership.toCatalogEntry(entry);
                if (canonical != null) player.ownEntry(canonical);
            } catch (RuntimeException malformed) {
                // One corrupt legacy row must not abort the whole load.
            }
        }
        List<String> rail = record.rail();
        for (int i = 0; i < Math.min(AbilityRail.TOTAL, rail.size()); i++) {
            String value = rail.get(i);
            if (value == null || value.isBlank()) continue;
            try {
                player.rail().set(i, AbilityId.of(AbilityIdMigration.apply(value)));
            } catch (RuntimeException malformed) {
                // Skip the bad slot.
            }
        }
        player.setLayoutData(record.layout());
    }

    private static PlayerRecord snapshot(BattlegroundPlayer player) {
        List<String> rail = new ArrayList<>(AbilityRail.TOTAL);
        for (int i = 0; i < AbilityRail.TOTAL; i++) {
            AbilityId id = player.rail().get(i);
            rail.add(id == null ? "" : id.toString());
        }
        return new PlayerRecord(player.uuid(), player.credits(), player.kills(), player.deaths(),
                player.streak(), player.bestStreak(), List.copyOf(player.ownedEntries()), rail,
                player.layoutData());
    }

    private void warn(String message, Throwable cause) {
        if (plugin != null) plugin.getLogger().log(Level.WARNING, message, cause);
        else { System.err.println(message); if (cause != null) cause.printStackTrace(System.err); }
    }
}