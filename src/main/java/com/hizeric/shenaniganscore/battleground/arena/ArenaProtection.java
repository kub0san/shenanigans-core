package com.hizeric.shenaniganscore.battleground.arena;

import com.hizeric.shenaniganscore.ShenanigansCore;
import com.hizeric.shenaniganscore.battleground.BattlegroundDefaults;
import com.hizeric.shenaniganscore.battleground.arena.snapshot.ArenaSnapshot;
import com.hizeric.shenaniganscore.battleground.arena.snapshot.ArenaSnapshotStore;
import com.hizeric.shenaniganscore.battleground.command.Permissions;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockBurnEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockFromToEvent;
import org.bukkit.event.block.BlockPistonExtendEvent;
import org.bukkit.event.block.BlockPistonRetractEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityExplodeEvent;

import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Keeps the captured arena structure immutable while player-placed blocks stay breakable.
 *
 * <p>Solid-at lookup is provided by an immutable {@link ArenaSnapshot}. When no snapshot is
 * available this guard protects nothing and performs no world scan.</p>
 *
 * <p>Performance: the active region is resolved once per event (not once per block) and block
 * tests use plain coordinates, so a 3,000-block explosion no longer allocates 3,000 Locations or
 * walks the plugin/manager/store chain 3,000 times.</p>
 */
public final class ArenaProtection implements Listener {
    private static final ArenaProtection INSTANCE = new ArenaProtection();

    /** Current answer to "is this coordinate captured, solid arena structure?". */
    @FunctionalInterface
    public interface SolidAtProvider {
        boolean isSolidAt(int x, int y, int z);
    }

    /** Captured coordinates currently holding a player block. Never persisted; cleared on restore. */
    private final Set<Long> playerPlaced = ConcurrentHashMap.newKeySet();
    private volatile ArenaSnapshot snapshot;
    private volatile SolidAtProvider solidAt = (x, y, z) -> false;

    ArenaProtection() {
    }

    public static ArenaProtection get() {
        return INSTANCE;
    }

    public void register(ShenanigansCore plugin) {
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
        var manager = plugin.getBattlegroundManager();
        ArenaRegion region = manager == null ? null : manager.arenaStore().region();
        String mapId = manager == null ? ArenaSnapshotStore.DEFAULT_MAP_ID
                : manager.arenaStore().activeMapId();
        ArenaSnapshot loaded = manager == null ? null : manager.loadSnapshotIfPresent(mapId);
        reload(region, loaded);
    }

    /** Repoints the solid-at lookup; a null provider restores snapshot lookup. */
    public void setSolidAtProvider(SolidAtProvider provider) {
        this.solidAt = provider == null ? defaultProvider() : provider;
    }

    /** Clears player-placed overrides after a restore has rebuilt captured structure. */
    public void clearPlayerPlaced() {
        playerPlaced.clear();
    }

    /** Reload protection from an in-memory snapshot. Clears player overrides; no world rescan. */
    public void reload(ArenaRegion region, ArenaSnapshot snapshot) {
        playerPlaced.clear();
        reloadPreservingPlaced(snapshot);
    }

    /**
     * Repoints protection at a freshly captured snapshot without dropping player-placed overrides.
     * A live capture keeps the round running, so in-memory overrides must stay in force; use
     * {@link #reload(ArenaRegion, ArenaSnapshot)} after a restore when they must be cleared.
     */
    public void reloadPreservingPlaced(ArenaSnapshot snapshot) {
        this.snapshot = snapshot;
        this.solidAt = defaultProvider();
    }

    public void reload(ArenaRegion region) {
        reload(region, this.snapshot);
    }

    private SolidAtProvider defaultProvider() {
        ArenaSnapshot snap = this.snapshot;
        return snap != null ? snap::isSolidAt : (x, y, z) -> false;
    }

    // ------------------------------------------------------------------
    // Pure decision surface (no Bukkit objects, no server required)
    // ------------------------------------------------------------------

    /** Read live so a {@code /bg reload} swap is visible to the very next event. */
    static boolean protectionEnabled() {
        return ArenaRuntimeConfig.current().protectionEnabled();
    }

    boolean isSolidAt(int x, int y, int z) {
        return solidAt.isSolidAt(x, y, z);
    }

    boolean isProtectedAt(int x, int y, int z) {
        return isSolidAt(x, y, z) && !playerPlaced.contains(pack(x, y, z));
    }

    /** The whole break decision: captured structure, not admin, and the arena gate on. */
    boolean cancelBreak(int x, int y, int z, boolean adminBypass) {
        return protectionEnabled() && !adminBypass && isProtectedAt(x, y, z);
    }

    void notePlaced(int x, int y, int z) {
        playerPlaced.add(pack(x, y, z));
    }

    void noteDestroyed(int x, int y, int z) {
        playerPlaced.remove(pack(x, y, z));
    }

    /** Packs a block position: 26 bits x, 12 bits y, 26 bits z. */
    static long pack(int x, int y, int z) {
        return ((long) (x & 0x3FFFFFF) << 38) | ((long) (y & 0xFFF) << 26) | (z & 0x3FFFFFF);
    }

    // ------------------------------------------------------------------
    // Handlers
    // ------------------------------------------------------------------

    private static boolean bypass(Player player) {
        return player.hasPermission(Permissions.BG_ADMIN);
    }

    /** The active region, or null when the plugin or arena is not ready. One lookup per event. */
    private static ArenaRegion activeRegion() {
        ShenanigansCore plugin = ShenanigansCore.getInstance();
        if (plugin == null) return null;
        var manager = plugin.getBattlegroundManager();
        return manager == null ? null : manager.arenaStore().region();
    }

    private static boolean inside(ArenaRegion region, Block block) {
        return region != null
                && region.containsBlock(block.getWorld(), block.getX(), block.getY(), block.getZ());
    }

    /** Captured structure inside the configured region: exactly what this guard protects. */
    private boolean isProtectedBlock(ArenaRegion region, Block block) {
        return inside(region, block) && isProtectedAt(block.getX(), block.getY(), block.getZ());
    }

    @EventHandler(ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        ArenaRegion region = activeRegion();
        Block block = event.getBlock();
        if (!inside(region, block)) return;
        int x = block.getX(), y = block.getY(), z = block.getZ();
        if (cancelBreak(x, y, z, bypass(event.getPlayer()))) {
            event.setCancelled(true);
            event.getPlayer().sendMessage(MiniMessage.miniMessage()
                    .deserialize(BattlegroundDefaults.PROTECTION_MESSAGE));
            return;
        }
        if (!isProtectedAt(x, y, z)) {
            // A player block on a captured coordinate is destructible; drop its override now
            // that the block is actually gone so the coordinate becomes structure again.
            noteDestroyed(x, y, z);
        }
    }

    /**
     * Marks a player block that landed on a captured coordinate. Without this in-memory override
     * a block placed into a water pool or tall-grass tile would be indistinguishable from the
     * captured block and survive explosions. Never persisted and cleared on every restore.
     */
    @EventHandler(ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) {
        ArenaRegion region = activeRegion();
        Block block = event.getBlock();
        if (!inside(region, block)) return;
        int x = block.getX(), y = block.getY(), z = block.getZ();
        if (isSolidAt(x, y, z)) {
            notePlaced(x, y, z);
        }
    }

    /**
     * Fluid must flow freely: a captured pond is itself water in the snapshot, so cancelling
     * flow here froze every water/lava spread. This handler never cancels; it only drops the
     * in-memory player-placed override when a flow replaces the block the player placed.
     */
    @EventHandler(ignoreCancelled = true)
    public void onFluidReplace(BlockFromToEvent event) {
        ArenaRegion region = activeRegion();
        Block to = event.getToBlock();
        if (!inside(region, to)) return;
        noteDestroyed(to.getX(), to.getY(), to.getZ());
    }

    /** Fire consuming an arena block is exactly what the captured set protects. */
    @EventHandler(ignoreCancelled = true)
    public void onBurn(BlockBurnEvent event) {
        ArenaRegion region = activeRegion();
        Block block = event.getBlock();
        if (isProtectedBlock(region, block)) {
            if (protectionEnabled()) event.setCancelled(true);
            return;
        }
        noteDestroyed(block.getX(), block.getY(), block.getZ());
    }

    @EventHandler(ignoreCancelled = true)
    public void onExplode(BlockExplodeEvent event) {
        keepDestructible(event.blockList());
    }

    /** Creepers, TNT minecarts, end crystals and withers do not fire BlockExplodeEvent. */
    @EventHandler(ignoreCancelled = true)
    public void onEntityExplode(EntityExplodeEvent event) {
        keepDestructible(event.blockList());
    }

    @EventHandler(ignoreCancelled = true)
    public void onPistonExtend(BlockPistonExtendEvent event) {
        if (movesCaptured(event.getBlocks())) event.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void onPistonRetract(BlockPistonRetractEvent event) {
        if (movesCaptured(event.getBlocks())) event.setCancelled(true);
    }

    private boolean movesCaptured(List<Block> blocks) {
        if (!protectionEnabled() || blocks.isEmpty()) return false;
        ArenaRegion region = activeRegion();
        if (region == null) return false;
        for (Block block : blocks) {
            if (isProtectedBlock(region, block)) return true;
        }
        return false;
    }

    /** Strips captured blocks out of an explosion's block list, drops overrides for the rest. */
    private void keepDestructible(List<Block> blocks) {
        if (blocks.isEmpty()) return;
        ArenaRegion region = activeRegion();
        boolean enabled = protectionEnabled();
        Iterator<Block> it = blocks.iterator();
        while (it.hasNext()) {
            Block block = it.next();
            if (isProtectedBlock(region, block)) {
                if (enabled) it.remove();
            } else {
                noteDestroyed(block.getX(), block.getY(), block.getZ());
            }
        }
    }
}
