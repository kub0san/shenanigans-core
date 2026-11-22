package com.hizeric.shenaniganscore.core.domain;

import com.hizeric.shenaniganscore.ShenanigansCore;
import com.hizeric.shenaniganscore.core.session.SessionManager;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;

import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BooleanSupplier;

/**
 * Central ownership registry for blocks a domain session created or claims.
 *
 * <p>This is the single source of truth for "is this block part of a domain?".
 * {@link com.hizeric.shenaniganscore.core.terrain.DomainFloor} claims every block
 * it writes and hands the claim back when the block is restored; the
 * {@link DomainRegionGuard} listener consults this registry and denies any
 * external mutation (break, place, explosion, piston, fluid, fire, decay) that
 * targets a claimed position.</p>
 *
 * <p>Block positions are packed into a single {@code long}. World identity is
 * kept per-world, so a claim is only ever matched inside the world that owns it.
 * </p>
 *
 * <p>Thread-safety: writes normally happen on the server thread, but the maps
 * are concurrent so off-thread reads (for diagnostics) can never throw.</p>
 */
public final class DomainRegistry {

    private static final DomainRegistry INSTANCE = new DomainRegistry();

    public static DomainRegistry get() {
        return INSTANCE;
    }

    /** world -> (packed block -> owning session UUID). */
    private final Map<UUID, Map<Long, UUID>> ownersByWorld = new ConcurrentHashMap<>();

    /** session -> (world -> claimed packed blocks). */
    private final Map<UUID, Map<UUID, Set<Long>>> claimsBySession = new ConcurrentHashMap<>();

    private DomainRegistry() {}

    // ------------------------------------------------------------------
    // Claims
    // ------------------------------------------------------------------

    /** Claims a single block for the given session. */
    public void claim(UUID session, Block block) {
        if (session == null || block == null) return;
        claim(session, block.getWorld().getUID(), block.getX(), block.getY(), block.getZ());
    }

    /** Claims a position directly (used while planning, before a block object exists). */
    public void claim(UUID session, World world, int x, int y, int z) {
        if (session == null || world == null) return;
        claim(session, world.getUID(), x, y, z);
    }

    /** Shared claim path, keyed by world id so callers need no live {@link World}. */
    void claim(UUID session, UUID worldId, int x, int y, int z) {
        if (session == null || worldId == null) return;
        long key = pack(x, y, z);
        ownersByWorld.computeIfAbsent(worldId, k -> new ConcurrentHashMap<>())
                .put(key, session);
        claimsBySession.computeIfAbsent(session, k -> new ConcurrentHashMap<>())
                .computeIfAbsent(worldId, k -> ConcurrentHashMap.newKeySet())
                .add(key);
    }

    /** Releases a single block from the given session. No-op for unclaimed positions. */
    public void unclaim(UUID session, Block block) {
        if (session == null || block == null) return;
        World world = block.getWorld();
        unclaim(session, world.getUID(), pack(block.getX(), block.getY(), block.getZ()));
    }

    /** Releases a position directly. */
    public void unclaim(UUID session, World world, int x, int y, int z) {
        if (session == null || world == null) return;
        unclaim(session, world.getUID(), pack(x, y, z));
    }

    private void unclaim(UUID session, UUID worldId, long key) {
        Map<Long, UUID> worldMap = ownersByWorld.get(worldId);
        if (worldMap != null) worldMap.remove(key, session);
        Map<UUID, Set<Long>> sessionWorlds = claimsBySession.get(session);
        if (sessionWorlds != null) {
            Set<Long> keys = sessionWorlds.get(worldId);
            if (keys != null) {
                keys.remove(key);
                if (keys.isEmpty()) sessionWorlds.remove(worldId);
            }
            if (sessionWorlds.isEmpty()) claimsBySession.remove(session);
        }
    }

    /** Releases every claim held by the given session — call when a domain ends. */
    public void release(UUID session) {
        if (session == null) return;
        Map<UUID, Set<Long>> worldClaims = claimsBySession.remove(session);
        if (worldClaims == null) return;
        for (Map.Entry<UUID, Set<Long>> entry : worldClaims.entrySet()) {
            Map<Long, UUID> worldMap = ownersByWorld.get(entry.getKey());
            if (worldMap == null) continue;
            for (Long key : entry.getValue()) {
                worldMap.remove(key, session);
            }
        }
    }

    // ------------------------------------------------------------------
    // Queries
    // ------------------------------------------------------------------

    /** The session that owns this location's block, or {@code null}. */
    public UUID ownerOf(Location location) {
        if (location == null) return null;
        World world = location.getWorld();
        if (world == null) return null;
        return ownerOf(world, location.getBlockX(), location.getBlockY(), location.getBlockZ());
    }

    /** The session that owns this block, or {@code null}. */
    public UUID ownerOf(World world, int x, int y, int z) {
        if (world == null) return null;
        Map<Long, UUID> worldMap = ownersByWorld.get(world.getUID());
        if (worldMap == null) return null;
        return worldMap.get(pack(x, y, z));
    }

    /**
     * The session that owns this position in any world, or {@code null}. Lets the arena diff
     * ledger filter domain-created blocks without a {@link World} reference; if the same packed
     * position is claimed in more than one world, the first match wins.
     */
    public UUID ownerOf(int x, int y, int z) {
        long key = pack(x, y, z);
        for (Map<Long, UUID> worldMap : ownersByWorld.values()) {
            UUID owner = worldMap.get(key);
            if (owner != null) return owner;
        }
        return null;
    }

    /** True when the block at this location is owned by any active domain. */
    public boolean isOwned(Location location) {
        return ownerOf(location) != null;
    }

    /** True when the block at this location is owned by the given session. */
    public boolean isOwnedBy(UUID session, Location location) {
        UUID owner = ownerOf(location);
        return owner != null && owner.equals(session);
    }

    /** True when the block at this location is owned by the given session. */
    public boolean isOwnedBy(UUID session, World world, int x, int y, int z) {
        UUID owner = ownerOf(world, x, y, z);
        return owner != null && owner.equals(session);
    }

    /** Number of currently claimed blocks (all sessions, all worlds). Diagnostics only. */
    public int claimCount() {
        int total = 0;
        for (Map<Long, UUID> worldMap : ownersByWorld.values()) {
            total += worldMap.size();
        }
        return total;
    }

    /** Drops every claim. PlugManX/reload safety net for a session that failed to close cleanly. */
    public void clearAll() {
        ownersByWorld.clear();
        claimsBySession.clear();
    }

    /**
     * Drops every claim, but only while no domain session is live. {@code /bg reload} calls this
     * to reclaim stale claims; an active domain keeps its claims so its floor stays protected.
     *
     * @return the number of claims cleared; zero means either a session is active or none were stale
     */
    public int clearClaimsIfIdle() {
        return clearClaimsIfIdle(() -> {
            ShenanigansCore plugin = ShenanigansCore.getInstance();
            SessionManager sessions = plugin == null ? null : plugin.getSessionManager();
            return sessions != null && sessions.hasActiveSessions();
        });
    }

    /** The idle probe is injectable so the valve's two branches can be tested without a server. */
    int clearClaimsIfIdle(BooleanSupplier hasActiveSessions) {
        if (hasActiveSessions.getAsBoolean()) return 0;
        int cleared = claimCount();
        clearAll();
        return cleared;
    }

    /** Immutable view of a session's claims: world UUID -> packed positions. */
    public Map<UUID, Set<Long>> claimsOf(UUID session) {
        Map<UUID, Set<Long>> claims = claimsBySession.get(session);
        if (claims == null) return Collections.emptyMap();
        return Collections.unmodifiableMap(claims);
    }

    /** Packs block coordinates into one long (x:26 | z:26 | y:12). */
    public static long pack(int x, int y, int z) {
        return ((long) (x & 0x3FFFFFF) << 38)
                | ((long) (z & 0x3FFFFFF) << 12)
                | (y & 0xFFF);
    }
}
