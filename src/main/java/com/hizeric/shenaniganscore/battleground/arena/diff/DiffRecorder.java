package com.hizeric.shenaniganscore.battleground.arena.diff;

import com.hizeric.shenaniganscore.battleground.arena.ArenaRegion;
import com.hizeric.shenaniganscore.core.domain.DomainRegistry;
import com.hizeric.shenaniganscore.core.util.BlockKey;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.BlockState;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockBurnEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockFadeEvent;
import org.bukkit.event.block.BlockFertilizeEvent;
import org.bukkit.event.block.BlockFormEvent;
import org.bukkit.event.block.BlockFromToEvent;
import org.bukkit.event.block.BlockGrowEvent;
import org.bukkit.event.block.BlockIgniteEvent;
import org.bukkit.event.block.BlockMultiPlaceEvent;
import org.bukkit.event.block.BlockPistonEvent;
import org.bukkit.event.block.BlockPistonExtendEvent;
import org.bukkit.event.block.BlockPistonRetractEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.block.BlockSpreadEvent;
import org.bukkit.event.block.LeavesDecayEvent;
import org.bukkit.event.block.SpongeAbsorbEvent;
import org.bukkit.event.entity.EntityChangeBlockEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.player.PlayerBucketEmptyEvent;
import org.bukkit.event.player.PlayerBucketFillEvent;
import org.bukkit.event.world.StructureGrowEvent;

import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Main-thread diff recorder: the MONITOR listeners only filter and enqueue packed positions.
 * No disk I/O, chunk access or world mutation happens here; the single async writer owns that.
 *
 * <p>Improvements over the first version:</p>
 * <ul>
 *   <li><b>One sink call per event.</b> An explosion that touches 3,000 blocks now hands the writer
 *       one batch instead of 3,000 single-position sets (and 3,000 lock acquisitions).</li>
 *   <li><b>Bounds are read once per event</b>, not once per block.</li>
 *   <li><b>Pistons record destinations.</b> The old recorder logged where blocks came from but not
 *       where they landed, the piston itself, or the head.</li>
 *   <li><b>More mutation sources</b>: leaf decay, crop/tree growth, bonemeal, fire ignition,
 *       bucket use, sponges and multi-block placement (beds, doors).</li>
 * </ul>
 *
 * <p>Anything still missed is caught by restore verification, so this list only has to be good,
 * not perfect. {@code BlockPhysicsEvent} stays out on purpose: it is a hint stream, not a
 * mutation source.</p>
 */
public final class DiffRecorder implements Listener {

    /** Axis-aligned bounds of the active arena region, checked per event. */
    @FunctionalInterface
    public interface Bounds {
        boolean contains(int x, int y, int z);
    }

    /** Domain claim query: true means the position belongs to a domain and is not ours to log. */
    @FunctionalInterface
    public interface ClaimCheck {
        boolean claimed(int x, int y, int z);
    }

    private volatile Supplier<Bounds> bounds;
    private final ClaimCheck claims;
    private volatile Consumer<Set<Long>> sink;

    public DiffRecorder(Supplier<Bounds> bounds, ClaimCheck claims, Consumer<Set<Long>> sink) {
        this.bounds = Objects.requireNonNull(bounds, "bounds");
        this.claims = Objects.requireNonNull(claims, "claims");
        this.sink = Objects.requireNonNull(sink, "sink");
    }

    /** Binds the domain registry's first-match-wins {@code ownerOf(int,int,int)} query. */
    public static ClaimCheck domainClaims() {
        DomainRegistry registry = DomainRegistry.get();
        return (x, y, z) -> registry.ownerOf(x, y, z) != null;
    }

    /** Adapts a captured arena footprint to the injectable bounds type (no Bukkit calls). */
    public static Bounds boundsOf(ArenaRegion region) {
        Objects.requireNonNull(region, "region");
        final int minX = region.min().getBlockX();
        final int minY = region.min().getBlockY();
        final int minZ = region.min().getBlockZ();
        final int maxX = region.max().getBlockX();
        final int maxY = region.max().getBlockY();
        final int maxZ = region.max().getBlockZ();
        return (x, y, z) -> x >= minX && x <= maxX
                && y >= minY && y <= maxY
                && z >= minZ && z <= maxZ;
    }

    /** Called when the active map changes; a supplier returning null means "no active region". */
    public void setBounds(Supplier<Bounds> bounds) {
        this.bounds = Objects.requireNonNull(bounds, "bounds");
    }

    /** Called if the writer instance is replaced (for example on reload). */
    public void setSink(Consumer<Set<Long>> sink) {
        this.sink = Objects.requireNonNull(sink, "sink");
    }

    /** @return true when the position was accepted and handed to the writer. */
    public boolean record(int x, int y, int z) {
        Bounds active = activeBounds();
        if (active == null || !tracked(active, x, y, z)) return false;
        sink.accept(Collections.singleton(BlockKey.pack(x, y, z)));
        return true;
    }

    // ------------------------------------------------------------------ helpers

    private Bounds activeBounds() {
        Supplier<Bounds> supplier = bounds;
        return supplier == null ? null : supplier.get();
    }

    private boolean tracked(Bounds active, int x, int y, int z) {
        return active.contains(x, y, z) && !claims.claimed(x, y, z);
    }

    private void add(Bounds active, Set<Long> out, int x, int y, int z) {
        if (tracked(active, x, y, z)) out.add(BlockKey.pack(x, y, z));
    }

    private void emit(Set<Long> batch) {
        if (!batch.isEmpty()) sink.accept(batch);
    }

    private void recordBlock(Block block) {
        if (block != null) record(block.getX(), block.getY(), block.getZ());
    }

    private void recordBlocks(Collection<Block> blocks) {
        if (blocks == null || blocks.isEmpty()) return;
        Bounds active = activeBounds();
        if (active == null) return;
        Set<Long> batch = new HashSet<>(Math.max(16, blocks.size() * 2));
        for (Block block : blocks) add(active, batch, block.getX(), block.getY(), block.getZ());
        emit(batch);
    }

    private void recordStates(Collection<? extends BlockState> states) {
        if (states == null || states.isEmpty()) return;
        Bounds active = activeBounds();
        if (active == null) return;
        Set<Long> batch = new HashSet<>(Math.max(16, states.size() * 2));
        for (BlockState state : states) add(active, batch, state.getX(), state.getY(), state.getZ());
        emit(batch);
    }

    /**
     * Pistons change more than the list they hand us: the piston block itself, the head position,
     * and every destination. Retract direction semantics differ between sticky pulls and plain
     * retracts, so both neighbours along the axis are logged; over-logging is harmless because a
     * restored position is simply rewritten to its baseline.
     */
    private void recordPiston(BlockPistonEvent event, Collection<Block> moved) {
        Bounds active = activeBounds();
        if (active == null) return;
        BlockFace face = event.getDirection();
        int dx = face.getModX();
        int dy = face.getModY();
        int dz = face.getModZ();
        Block piston = event.getBlock();
        Set<Long> batch = new HashSet<>(Math.max(16, moved.size() * 3 + 4));
        add(active, batch, piston.getX(), piston.getY(), piston.getZ());
        add(active, batch, piston.getX() + dx, piston.getY() + dy, piston.getZ() + dz);
        for (Block block : moved) {
            int x = block.getX();
            int y = block.getY();
            int z = block.getZ();
            add(active, batch, x, y, z);
            add(active, batch, x + dx, y + dy, z + dz);
            add(active, batch, x - dx, y - dy, z - dz);
        }
        emit(batch);
    }

    // ------------------------------------------------------------------ listeners

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        recordBlock(event.getBlock());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) {
        recordBlock(event.getBlock());
        if (event instanceof BlockMultiPlaceEvent multi) {
            recordStates(multi.getReplacedBlockStates());
        }
    }

    /** Fluid: both the drained source and the receiving block changed. */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onFluid(BlockFromToEvent event) {
        Bounds active = activeBounds();
        if (active == null) return;
        Set<Long> batch = new HashSet<>(4);
        Block from = event.getBlock();
        Block to = event.getToBlock();
        add(active, batch, from.getX(), from.getY(), from.getZ());
        add(active, batch, to.getX(), to.getY(), to.getZ());
        emit(batch);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEntityExplode(EntityExplodeEvent event) {
        recordBlocks(event.blockList());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockExplode(BlockExplodeEvent event) {
        recordBlocks(event.blockList());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPistonExtend(BlockPistonExtendEvent event) {
        recordPiston(event, event.getBlocks());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPistonRetract(BlockPistonRetractEvent event) {
        recordPiston(event, event.getBlocks());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBurn(BlockBurnEvent event) {
        recordBlock(event.getBlock());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onIgnite(BlockIgniteEvent event) {
        recordBlock(event.getBlock());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onForm(BlockFormEvent event) {
        recordBlock(event.getBlock());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onSpread(BlockSpreadEvent event) {
        recordBlock(event.getBlock());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onFade(BlockFadeEvent event) {
        recordBlock(event.getBlock());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onGrow(BlockGrowEvent event) {
        recordBlock(event.getBlock());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onLeavesDecay(LeavesDecayEvent event) {
        recordBlock(event.getBlock());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onStructureGrow(StructureGrowEvent event) {
        recordStates(event.getBlocks());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onFertilize(BlockFertilizeEvent event) {
        recordStates(event.getBlocks());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onSponge(SpongeAbsorbEvent event) {
        recordBlock(event.getBlock());
        recordStates(event.getBlocks());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBucketEmpty(PlayerBucketEmptyEvent event) {
        recordBlock(event.getBlock());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBucketFill(PlayerBucketFillEvent event) {
        recordBlock(event.getBlock());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEntityChangeBlock(EntityChangeBlockEvent event) {
        recordBlock(event.getBlock());
    }
}
