package com.hizeric.shenaniganscore.core.util;

import com.hizeric.shenaniganscore.ShenanigansCore;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Level;

/**
 * Queues and tracks per-player fake block overlays.
 *
 * All public methods must be called on the Bukkit main thread. Fake blocks are
 * visual-only; separately cancel interactions and block actions where needed.
 */
public final class ClientBlockUtils {

    private ClientBlockUtils() {}

    /** Maximum updates included in one call to the transport. */
    private static final int MAX_UPDATES_PER_BATCH = 128;

    /** Global work limits per server tick. */
    private static final int BLOCKS_PER_TICK = 1_500;
    private static final int BATCHES_PER_TICK = 32;

    private static final Map<UUID, PlayerQueue> PENDING = new HashMap<>();
    private static final Deque<UUID> ROUND_ROBIN = new ArrayDeque<>();

    /**
     * Latest desired fake state, keyed by player and block position.
     * Keeping this separately from the pending queue lets callers reapply
     * overlays after the client receives real chunk/block updates.
     */
    private static final Map<UUID, Map<BlockKey, BlockData>> OVERLAYS = new HashMap<>();

    private static BlockChangeTransport transport = new PaperTransport();
    private static BukkitTask drainTask;

    private record BlockKey(UUID worldId, int x, int y, int z) {}

    private record PendingUpdate(BlockKey key, BlockData data) {}

    private static final class PlayerQueue {
        // Insertion-ordered so updates drain predictably; putting the same key
        // again replaces its value without consuming another queue slot.
        private final LinkedHashMap<BlockKey, BlockData> updates = new LinkedHashMap<>();
    }

    /**
     * Transport seam for targeted block changes. Paper's API is the default; the plugin installs
     * {@code PacketEventsTransport} at enable so overlays go out as individual block-change
     * packets instead of whole-chunk refreshes. Overlay tracking, {@code resendChunk} and
     * {@code clearPlayer} are deliberately transport-independent.
     */
    public interface BlockChangeTransport {
        void send(Player player, Map<Location, BlockData> changes);
    }

    /** Use this to install a PacketEvents-backed transport, if desired. */
    public static void setTransport(BlockChangeTransport newTransport) {
        requireMainThread();
        if (newTransport == null) throw new IllegalArgumentException("transport cannot be null");
        transport = newTransport;
    }

    /** Queues one fake block and records it as part of the active overlay. */
    public static void sendFakeBlock(Player player, Location location, BlockData data) {
        requireMainThread();
        if (player == null || location == null || data == null || !player.isOnline()) return;

        BlockKey key = keyOf(location);
        if (!isValidTarget(player.getWorld(), key)) return;

        BlockData copy = data.clone();
        OVERLAYS.computeIfAbsent(player.getUniqueId(), ignored -> new HashMap<>())
                .put(key, copy.clone());
        enqueue(player, key, copy);
    }

    /** Queues a batch of fake blocks; repeated positions coalesce to the latest value. */
    public static void sendFakeBlocks(Player player, Map<? extends Location, BlockData> batch) {
        requireMainThread();
        if (player == null || batch == null || batch.isEmpty() || !player.isOnline()) return;

        for (Map.Entry<? extends Location, BlockData> entry : batch.entrySet()) {
            Location location = entry.getKey();
            BlockData data = entry.getValue();
            if (location == null || data == null) continue;

            BlockKey key = keyOf(location);
            if (!isValidTarget(player.getWorld(), key)) continue;

            BlockData copy = data.clone();
            OVERLAYS.computeIfAbsent(player.getUniqueId(), ignored -> new HashMap<>())
                    .put(key, copy.clone());
            enqueue(player, key, copy);
        }
    }

    /**
     * Removes the overlay state at these locations and queues the current real
     * block data. Real data is read at restore time, not from an old snapshot.
     */
    public static void revertBlocks(Player player, Iterable<Location> locations) {
        requireMainThread();
        if (player == null || locations == null || !player.isOnline()) return;

        UUID playerId = player.getUniqueId();
        Map<BlockKey, BlockData> overlays = OVERLAYS.get(playerId);

        for (Location location : locations) {
            if (location == null || location.getWorld() == null) continue;

            BlockKey key = keyOf(location);
            if (overlays != null) {
                overlays.remove(key);
                if (overlays.isEmpty()) OVERLAYS.remove(playerId);
            }

            World world = Bukkit.getWorld(key.worldId());
            if (world == null || !isValidTarget(player.getWorld(), key)) continue;
            if (!world.isChunkLoaded(key.x() >> 4, key.z() >> 4)) continue;

            enqueue(player, key, world.getBlockAt(key.x(), key.y(), key.z()).getBlockData().clone());
        }
    }

    /**
     * Requeues tracked fake blocks in a chunk. Call this when you know the client
     * has received that chunk or a real block update may have overwritten an
     * overlay. This method does not change the tracked overlay state.
     */
    public static void resendChunk(Player player, World world, int chunkX, int chunkZ) {
        requireMainThread();
        if (player == null || world == null || !player.isOnline()) return;
        if (!player.getWorld().getUID().equals(world.getUID())) return;

        Map<BlockKey, BlockData> overlays = OVERLAYS.get(player.getUniqueId());
        if (overlays == null || overlays.isEmpty()) return;

        for (Map.Entry<BlockKey, BlockData> entry : overlays.entrySet()) {
            BlockKey key = entry.getKey();
            if (!key.worldId().equals(world.getUID())) continue;
            if ((key.x() >> 4) != chunkX || (key.z() >> 4) != chunkZ) continue;
            enqueue(player, key, entry.getValue().clone());
        }
    }

    /** Clears all pending and tracked fake blocks for a player, e.g. on quit. */
    public static void clearPlayer(Player player) {
        requireMainThread();
        if (player != null) clearPlayer(player.getUniqueId());
    }

    /** Clears all pending and tracked fake blocks for a player UUID. */
    public static void clearPlayer(UUID playerId) {
        requireMainThread();
        if (playerId == null) return;

        PENDING.remove(playerId);
        ROUND_ROBIN.removeIf(playerId::equals);
        OVERLAYS.remove(playerId);
    }

    /** Call from the plugin's onDisable(). */
    public static void shutdown() {
        requireMainThread();
        if (drainTask != null) {
            drainTask.cancel();
            drainTask = null;
        }
        PENDING.clear();
        ROUND_ROBIN.clear();
        OVERLAYS.clear();
    }

    private static void enqueue(Player player, BlockKey key, BlockData data) {
        PlayerQueue queue = PENDING.computeIfAbsent(player.getUniqueId(), ignored -> {
            ROUND_ROBIN.addLast(player.getUniqueId());
            return new PlayerQueue();
        });

        // Coalescing: the same player's same block position occupies one slot.
        queue.updates.put(key, data.clone());
        startTask();
    }

    private static void startTask() {
        if (drainTask != null) return;

        drainTask = ShenanigansCore.runTaskTimer(
                ClientBlockUtils::drain,
                1L,
                1L
        );
    }

    private static void drain() {
        int blocksDrained = 0;
        int batchesSent = 0;

        while (blocksDrained < BLOCKS_PER_TICK
                && batchesSent < BATCHES_PER_TICK
                && !ROUND_ROBIN.isEmpty()) {

            UUID playerId = ROUND_ROBIN.removeFirst();
            PlayerQueue playerQueue = PENDING.get(playerId);

            if (playerQueue == null || playerQueue.updates.isEmpty()) {
                PENDING.remove(playerId);
                continue;
            }

            Player player = Bukkit.getPlayer(playerId);
            Map<Location, BlockData> batch = new LinkedHashMap<>();
            int takenThisTurn = 0;

            while (takenThisTurn < MAX_UPDATES_PER_BATCH
                    && blocksDrained < BLOCKS_PER_TICK
                    && !playerQueue.updates.isEmpty()) {

                Iterator<Map.Entry<BlockKey, BlockData>> iterator =
                        playerQueue.updates.entrySet().iterator();
                Map.Entry<BlockKey, BlockData> entry = iterator.next();

                BlockKey key = entry.getKey();
                BlockData data = entry.getValue();
                iterator.remove();

                blocksDrained++;
                takenThisTurn++;

                if (player == null || !player.isOnline()) continue;
                if (!isValidTarget(player.getWorld(), key)) continue;

                World world = Bukkit.getWorld(key.worldId());
                if (world == null) continue;

                Location blockLocation = new Location(world, key.x(), key.y(), key.z());
                batch.put(blockLocation, data.clone());
            }

            if (!playerQueue.updates.isEmpty()) {
                ROUND_ROBIN.addLast(playerId);
            } else {
                PENDING.remove(playerId);
            }

            if (player != null && player.isOnline() && !batch.isEmpty()) {
                try {
                    transport.send(player, batch);
                } catch (RuntimeException exception) {
                    ShenanigansCore.getInstance().getLogger().log(
                            Level.WARNING,
                            "Failed to send fake-block batch to " + player.getName()
                                    + " (" + batch.size() + " changes)",
                            exception
                    );
                }
                batchesSent++;
            }
        }

        if (ROUND_ROBIN.isEmpty() && drainTask != null) {
            drainTask.cancel();
            drainTask = null;
        }
    }

    private static BlockKey keyOf(Location location) {
        return new BlockKey(
                location.getWorld().getUID(),
                location.getBlockX(),
                location.getBlockY(),
                location.getBlockZ()
        );
    }

    private static boolean isValidTarget(World playerWorld, BlockKey key) {
        if (playerWorld == null || key == null) return false;
        if (!playerWorld.getUID().equals(key.worldId())) return false;
        return key.y() >= playerWorld.getMinHeight()
                && key.y() < playerWorld.getMaxHeight();
    }

    private static void requireMainThread() {
        if (!Bukkit.isPrimaryThread()) {
            throw new IllegalStateException(
                    "ClientBlockUtils must only be called from the Bukkit main thread"
            );
        }
    }

    /** Default Paper implementation; replace via setTransport() for PacketEvents. */
    private static final class PaperTransport implements BlockChangeTransport {
        @Override
        public void send(Player player, Map<Location, BlockData> changes) {
            player.sendMultiBlockChange(changes);
        }
    }
}
