package com.hizeric.shenaniganscore.core.network;

import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.protocol.world.states.WrappedBlockState;
import com.github.retrooper.packetevents.util.Vector3i;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerBlockChange;
import com.hizeric.shenaniganscore.ShenanigansCore;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import static com.hizeric.shenaniganscore.core.util.BlockKey.pack;

/**
 * Targeted, view-tracked, resend-safe client-side block overlays.
 *
 * <p>An overlay is a set of <b>client-only</b> block changes layered on top of
 * the real world via PacketEvents. The server never changes its own blocks.
 * Each overlay is owned by a domain session (or another owner) and can be
 * cleared on domain end; the manager keeps a per-viewer set of blocks the
 * client is known to see, and can resend the overlay when a viewer (re)enters
 * the overlay range or joins, and can revert the overlay to the real block
 * states when a viewer leaves the range or the overlay is cleared.</p>
 */
public final class DomainBlockOverlayManager {

    private static final DomainBlockOverlayManager INSTANCE = new DomainBlockOverlayManager();

    private static final int VIEW_TICKS = 2;
    private static final int MAX_BLOCK_Y = 4096;

    public static DomainBlockOverlayManager get() {
        return INSTANCE;
    }

    private final Map<UUID, DomainBlockOverlay> overlays = new ConcurrentHashMap<>();
    // viewer -> (overlay, packed position) currently fake-shown. Including the
    // overlay id prevents same-coordinate blocks in different overlays/worlds
    // from colliding (F-21, F-27).
    private final Map<UUID, Set<HiddenBlock>> hiddenFrom = new ConcurrentHashMap<>();
    private boolean running;
    private BukkitTask task;

    private DomainBlockOverlayManager() {}

    /** Registers a new overlay and starts view tracking if needed. */
    public UUID register(UUID owner, World world, Map<Location, BlockData> blocks) {
        Objects.requireNonNull(owner, "owner");
        Objects.requireNonNull(world, "world");
        if (blocks == null || blocks.isEmpty()) {
            return null;
        }
        DomainBlockOverlay overlay = new DomainBlockOverlay(owner, world, blocks);
        overlays.put(overlay.id(), overlay);
        ensureTask();
        return overlay.id();
    }

    /** Removes and reverts the given overlay. */
    public void clear(UUID overlayId) {
        if (overlayId == null) return;
        DomainBlockOverlay overlay = overlays.remove(overlayId);
        if (overlay == null) return;
        revertOverlayForAll(overlay);
    }

    /** Clears every overlay owned by the given session. */
    public void clearForOwner(UUID owner) {
        if (owner == null) return;
        java.util.List<DomainBlockOverlay> clearing = new java.util.ArrayList<>();
        Iterator<Map.Entry<UUID, DomainBlockOverlay>> it = overlays.entrySet().iterator();
        while (it.hasNext()) {
            DomainBlockOverlay overlay = it.next().getValue();
            if (overlay.owner.equals(owner)) {
                clearing.add(overlay);
                it.remove();
            }
        }
        for (DomainBlockOverlay overlay : clearing) {
            revertOverlayForAll(overlay);
        }
    }

    /** Clears all overlays. */
    public void clearAll() {
        if (overlays.isEmpty()) return;
        for (DomainBlockOverlay overlay : new HashMap<>(overlays).values()) {
            revertOverlayForAll(overlay);
        }
        overlays.clear();
    }

    /**
     * PlugManX/reload hook. Reverts every overlay, then clears the static task and flags so a
     * re-enabled plugin starts with a clean viewer-tracking loop instead of a stale "running"
     * state whose task was cancelled by the plugin disable.
     */
    public void shutdown() {
        clearAll();
        hiddenFrom.clear();
        if (task != null) {
            try {
                task.cancel();
            } catch (Throwable ignored) {
                // the task may already be gone if the scheduler cancelled it on disable
            }
            task = null;
        }
        running = false;
    }

    /** Sends overlays that should be visible to this viewer right now. */
    public void resendTo(Player player) {
        if (player == null || !player.isOnline()) return;
        World world = player.getWorld();
        for (DomainBlockOverlay overlay : overlays.values()) {
            if (!overlay.world.equals(world)) continue;
            if (!viewerShouldSee(player, overlay)) {
                revertForViewer(player, overlay);
                continue;
            }
            sendTo(player, overlay);
        }
    }

    /** Forces an overlay resend for every candidate viewer; call occasionally. */
    public void tickViewers() {
        if (overlays.isEmpty()) return;
        for (DomainBlockOverlay overlay : overlays.values()) {
            for (Player player : overlay.world.getPlayers()) {
                if (viewerShouldSee(player, overlay)) {
                    sendTo(player, overlay);
                } else {
                    revertForViewer(player, overlay);
                }
            }
        }
    }

    /** Sends the overlay to one viewer. */
    public void sendTo(Player player, UUID overlayId) {
        DomainBlockOverlay overlay = overlays.get(overlayId);
        if (overlay != null) sendTo(player, overlay);
    }

    /** Reverts the overlay for one viewer. */
    public void revertFor(Player player, UUID overlayId) {
        DomainBlockOverlay overlay = overlays.get(overlayId);
        if (overlay != null) revertForViewer(player, overlay);
    }

    /** Number of overlays currently tracked. Diagnostics. */
    public int overlayCount() {
        return overlays.size();
    }

    private void ensureTask() {
        if (running) return;
        running = true;
        ShenanigansCore plugin = ShenanigansCore.getInstance();
        try {
            task = new BukkitRunnable() {
                @Override
                public void run() {
                    if (overlays.isEmpty()) {
                        cancel();
                        task = null;
                        running = false;
                        return;
                    }
                    tickViewers();
                }
            }.runTaskTimer(plugin, VIEW_TICKS, VIEW_TICKS);
        } catch (Throwable ignored) {
            // Scheduling is best-effort (e.g. plugin disable mid-register): overlays just stop ticking.
            task = null;
            running = false;
        }
    }

    private boolean viewerShouldSee(Player player, DomainBlockOverlay overlay) {
        if (player == null || overlay == null) return false;
        if (!player.isOnline()) return false;
        if (player.getWorld() != overlay.world) return false;
        Location loc = player.getLocation();

        int viewDistance = Math.max(2, Bukkit.getServer().getViewDistance());
        double max = Math.min(viewDistance * 16.0 + 8.0, MAX_BLOCK_Y);

        double dx = loc.getX() - (overlay.minX + overlay.maxX) / 2.0;
        double dy = loc.getY() - (overlay.minY + overlay.maxY) / 2.0;
        double dz = loc.getZ() - (overlay.minZ + overlay.maxZ) / 2.0;

        double radius = Math.sqrt(
                Math.pow(Math.max(overlay.maxX - overlay.minX, 0) / 2.0, 2) +
                Math.pow(Math.max(overlay.maxY - overlay.minY, 0) / 2.0, 2) +
                Math.pow(Math.max(overlay.maxZ - overlay.minZ, 0) / 2.0, 2));

        double distSq = dx * dx + dy * dy + dz * dz;
        double reach = radius + max;
        return distSq <= reach * reach;
    }

    private void sendTo(Player player, DomainBlockOverlay overlay) {
        if (player == null || overlay == null || !player.isOnline()) return;
        World world = player.getWorld();
        if (world != overlay.world) return;

        Set<HiddenBlock> already = hiddenFrom.computeIfAbsent(player.getUniqueId(),
                k -> ConcurrentHashMap.newKeySet());
        for (Map.Entry<Location, BlockData> entry : overlay.blocks.entrySet()) {
            Location loc = entry.getKey();
            HiddenBlock key = new HiddenBlock(overlay.id(),
                    pack(loc.getBlockX(), loc.getBlockY(), loc.getBlockZ()));
            // Already visible to this viewer; re-sending every 2 ticks would spam the client.
            if (!already.add(key)) continue;
            sendBlock(player, loc, entry.getValue());
        }
    }

    private void revertOverlayForAll(DomainBlockOverlay overlay) {
        if (overlay == null) return;
        for (Player player : overlay.world.getPlayers()) {
            revertForViewer(player, overlay);
        }
    }

    private void revertForViewer(Player player, DomainBlockOverlay overlay) {
        if (player == null || overlay == null) return;
        UUID uuid = player.getUniqueId();
        Set<HiddenBlock> fake = hiddenFrom.get(uuid);
        if (fake == null || fake.isEmpty()) return;
        for (Location loc : overlay.blocks.keySet()) {
            HiddenBlock key = new HiddenBlock(overlay.id(),
                    pack(loc.getBlockX(), loc.getBlockY(), loc.getBlockZ()));
            if (!fake.remove(key)) continue;
            try {
                if (loc.isChunkLoaded()) {
                    sendBlock(player, loc, loc.getBlock().getBlockData());
                }
            } catch (Throwable ignored) {
                // Real-block revert during teardown; an unload mid-revert is not worth crashing over.
            }
        }
        if (fake.isEmpty()) {
            hiddenFrom.remove(uuid);
        } else {
            hiddenFrom.put(uuid, fake);
        }
    }

    private static void sendBlock(Player player, Location loc, BlockData data) {
        if (player == null || loc == null || data == null) return;
        if (!player.isOnline()) return;
        if (loc.getWorld() == null || loc.getWorld() != player.getWorld()) return;

        PacketEventsBridge.requireReady();
        String state = data.getAsString(true);
        WrappedBlockState wrapped;
        try {
            // getByString() returns AIR for unmapped states, so a null check alone
            // can never detect a mismatch (SP-F-001).
            wrapped = PacketEventsTransport.mapBlockState(state);
        } catch (RuntimeException e) {
            ShenanigansCore plugin = ShenanigansCore.getInstance();
            if (plugin != null) {
                plugin.getLogger().warning("Skipping unmapped block overlay state '" + state + "': "
                        + e.getMessage());
            }
            return;
        }
        try {
            PacketEvents.getAPI().getPlayerManager().sendPacket(
                    player,
                    new WrapperPlayServerBlockChange(new Vector3i(loc.getBlockX(), loc.getBlockY(), loc.getBlockZ()), wrapped));
        } catch (Throwable t) {
            ShenanigansCore plugin = ShenanigansCore.getInstance();
            if (plugin != null) {
                plugin.getLogger().warning("Failed to send block overlay packet to " + player.getName() + ": " + t.getMessage());
            }
        }
    }
    /**
     * Identity of one fake-shown block for a viewer. The overlay id makes the
     * key unique across overlays (and therefore worlds), so identical packed
     * coordinates in different domains cannot cancel each other out (F-21, F-27).
     */
    private record HiddenBlock(UUID overlayId, long packedPosition) { }

    private static final class DomainBlockOverlay {
        final UUID id = UUID.randomUUID();
        final UUID owner;
        final World world;
        final Map<Location, BlockData> blocks;
        final int minX, minY, minZ, maxX, maxY, maxZ;

        DomainBlockOverlay(UUID owner, World world, Map<Location, BlockData> blocks) {
            this.owner = owner;
            this.world = world;
            this.blocks = Collections.unmodifiableMap(new HashMap<>(blocks));
            int mnX = Integer.MAX_VALUE, mnY = Integer.MAX_VALUE, mnZ = Integer.MAX_VALUE;
            int mxX = Integer.MIN_VALUE, mxY = Integer.MIN_VALUE, mxZ = Integer.MIN_VALUE;
            for (Location loc : blocks.keySet()) {
                mnX = Math.min(mnX, loc.getBlockX());
                mnY = Math.min(mnY, loc.getBlockY());
                mnZ = Math.min(mnZ, loc.getBlockZ());
                mxX = Math.max(mxX, loc.getBlockX());
                mxY = Math.max(mxY, loc.getBlockY());
                mxZ = Math.max(mxZ, loc.getBlockZ());
            }
            this.minX = mnX == Integer.MAX_VALUE ? 0 : mnX;
            this.minY = mnY == Integer.MAX_VALUE ? 0 : mnY;
            this.minZ = mnZ == Integer.MAX_VALUE ? 0 : mnZ;
            this.maxX = mxX == Integer.MIN_VALUE ? 0 : mxX;
            this.maxY = mxY == Integer.MIN_VALUE ? 0 : mxY;
            this.maxZ = mxZ == Integer.MIN_VALUE ? 0 : mxZ;
        }

        UUID id() {
            return id;
        }
    }
}
