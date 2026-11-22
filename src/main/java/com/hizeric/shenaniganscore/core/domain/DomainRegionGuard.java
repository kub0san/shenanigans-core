package com.hizeric.shenaniganscore.core.domain;

import com.hizeric.shenaniganscore.ShenanigansCore;
import com.hizeric.shenaniganscore.core.domain.DomainRegistry;
import com.hizeric.shenaniganscore.core.session.DomainSession;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockBurnEvent;
import org.bukkit.event.block.BlockDamageEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockFadeEvent;
import org.bukkit.event.block.BlockFormEvent;
import org.bukkit.event.block.BlockFromToEvent;
import org.bukkit.event.block.BlockIgniteEvent;
import org.bukkit.event.block.BlockPistonExtendEvent;
import org.bukkit.event.block.BlockPistonRetractEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.block.BlockSpreadEvent;
import org.bukkit.event.block.LeavesDecayEvent;
import org.bukkit.event.entity.EntityChangeBlockEvent;
import org.bukkit.event.entity.EntityExplodeEvent;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * Denies every external mutation of a block claimed by an active domain.
 *
 * <p>Domain-made blocks are considered absolute: players, explosions (including
 * TNT minecarts, end crystals and respawn anchors), pistons, fluids, fire and
 * natural decay may not change or remove them. The domain itself may still
 * mutate its own blocks through direct block API writes (e.g.
 * {@code DomainFloor#setSurface}), which never fire these events.</p>
 *
 * <p>Fluid flow and ignition use a narrower gate: they are denied only while a claim belongs
 * to a live, active domain. Old claims left behind after teardown must not freeze water/lava
 * forever; the fail-closed teardown rule stays in force for break/explode/burn.</p>
 *
 * <p>This listener is registered once, at plugin enable. It is cheap: every
 * handler does one hash lookup against {@link DomainRegistry}, and does nothing
 * when the touched position is not domain-owned.</p>
 */
public final class DomainRegionGuard implements Listener {

    private final DomainRegistry registry = DomainRegistry.get();

    private boolean isProtected(Location location) {
        UUID owner = registry.ownerOf(location);
        if (owner == null) return false;
        ShenanigansCore plugin = ShenanigansCore.getInstance();
        if (plugin == null || plugin.getSessionManager() == null) return true;
        DomainSession session = plugin.getSessionManager().getActiveSessions().get(owner);
        boolean known = session != null;
        // The teardown fail-safe stays here: a break/explode/burn on a still-claimed block
        // is denied even while the owner is finishing its restore.
        return mutationBlocked(true, known, known && session.blocksIndestructible());
    }

    /**
     * The ordinary mutation gate, expressed as a pure decision so it can be pinned by tests.
     * A claimed block with no known session fails closed ({@code true}) to protect the
     * teardown window where the floor is still handed back to the world.
     */
    static boolean mutationBlocked(boolean claimed, boolean sessionKnown, boolean indestructible) {
        return claimed && (!sessionKnown || indestructible);
    }

    /**
     * The fluid/ignite gate. Only a claim backed by a <em>live, active</em> domain denies
     * external flow or ignition. A claim whose session is gone (teardown finished, or a
     * leaked claim after an abnormal end) must never freeze every water/lava spread that
     * touches its old footprint — the fail-safe above is deliberately not used here.
     *
     * <p>The live-owner set is supplied lazily: unclaimed positions make up nearly every
     * fluid event, and must not pay for a fresh {@link HashSet} scan of the session map.</p>
     */
    static boolean deniedByLiveClaim(UUID claimOwner, Supplier<Set<UUID>> liveOwners) {
        if (claimOwner == null) return false;
        return liveOwners.get().contains(claimOwner);
    }

    /** Owners of domains that are live right now and whose blocks are indestructible. */
    private static Set<UUID> liveOwners() {
        ShenanigansCore plugin = ShenanigansCore.getInstance();
        if (plugin == null || plugin.getSessionManager() == null) return Set.of();
        Set<UUID> owners = new HashSet<>();
        for (DomainSession session : plugin.getSessionManager().getActiveSessions().values()) {
            if (session != null && session.isActive() && session.blocksIndestructible()) {
                owners.add(session.getUniqueId());
            }
        }
        return owners;
    }

    // ------------------------------------------------------------------
    // Players
    // ------------------------------------------------------------------

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        if (isProtected(event.getBlock().getLocation())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent event) {
        if (isProtected(event.getBlock().getLocation())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockDamage(BlockDamageEvent event) {
        if (isProtected(event.getBlock().getLocation())) {
            event.setCancelled(true);
        }
    }

    // ------------------------------------------------------------------
    // Explosions (TNT, TNT minecart, creeper, wither, end crystal, bed, anchor)
    // ------------------------------------------------------------------

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onEntityExplode(EntityExplodeEvent event) {
        stripOwnedBlocks(event.blockList());
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockExplode(BlockExplodeEvent event) {
        stripOwnedBlocks(event.blockList());
    }

    private void stripOwnedBlocks(List<Block> blocks) {
        blocks.removeIf(block -> isProtected(block.getLocation()));
    }

    // ------------------------------------------------------------------
    // Pistons
    // ------------------------------------------------------------------

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPistonExtend(BlockPistonExtendEvent event) {
        if (isProtected(event.getBlock().getLocation())) {
            event.setCancelled(true);
            return;
        }
        for (Block moved : event.getBlocks()) {
            if (isProtected(moved.getLocation())) {
                event.setCancelled(true);
                return;
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPistonRetract(BlockPistonRetractEvent event) {
        if (isProtected(event.getBlock().getLocation())) {
            event.setCancelled(true);
            return;
        }
        for (Block moved : event.getBlocks()) {
            if (isProtected(moved.getLocation())) {
                event.setCancelled(true);
                return;
            }
        }
    }

    // ------------------------------------------------------------------
    // Fluids, fire, decay, growth
    // ------------------------------------------------------------------

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockFromTo(BlockFromToEvent event) {
        UUID owner = registry.ownerOf(event.getToBlock().getLocation());
        if (deniedByLiveClaim(owner, DomainRegionGuard::liveOwners)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockIgnite(BlockIgniteEvent event) {
        UUID owner = registry.ownerOf(event.getBlock().getLocation());
        if (deniedByLiveClaim(owner, DomainRegionGuard::liveOwners)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockBurn(BlockBurnEvent event) {
        if (isProtected(event.getBlock().getLocation())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockFade(BlockFadeEvent event) {
        if (isProtected(event.getBlock().getLocation())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onLeavesDecay(LeavesDecayEvent event) {
        if (isProtected(event.getBlock().getLocation())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockSpread(BlockSpreadEvent event) {
        if (isProtected(event.getBlock().getLocation())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockForm(BlockFormEvent event) {
        if (isProtected(event.getBlock().getLocation())) {
            event.setCancelled(true);
        }
    }

    // ------------------------------------------------------------------
    // Entities changing blocks (endermen, falling blocks, ravagers, crops)
    // ------------------------------------------------------------------

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onEntityChangeBlock(EntityChangeBlockEvent event) {
        Block block = event.getBlock();
        Location location = block.getLocation();
        if (isProtected(location)) {
            event.setCancelled(true);
        }
    }
}
