package com.hizeric.shenaniganscore.core.network;

import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.protocol.world.states.WrappedBlockState;
import com.github.retrooper.packetevents.util.Vector3i;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerBlockChange;
import com.hizeric.shenaniganscore.core.util.ClientBlockUtils;
import org.bukkit.Location;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Player;

import java.util.Map;
import java.util.Objects;

/**
 * Sends queued fake-block changes as targeted PacketEvents block-change packets.
 *
 * <p>The packet shape is the one verified against PacketEvents 2.10.0 in this project:
 * {@code new WrapperPlayServerBlockChange(new Vector3i(x, y, z),
 * WrappedBlockState.getByString(state))}, delivered through
 * {@code PacketEvents.getAPI().getPlayerManager().sendPacket(player, packet)}. The plugin-owned
 * singleton is only ever read; this class never installs the singleton itself.</p>
 *
 * <p>{@link #toBlockChange(Location, BlockData)} is a deliberately PacketEvents-free projection so
 * the coordinate and state mapping can be unit-tested: {@code WrappedBlockState}'s static
 * initialization needs the live API singleton, which a unit test must never install.</p>
 *
 * <p>The guarded send path is split into two package-private seams so a serverless test can observe
 * it: a {@link ReadinessGuard} must run before any packet work, then every projected change is
 * handed to a {@link PacketSink}. The no-arg constructor wires the real guard and the real
 * wrapper/send implementation; tests inject recorders instead.</p>
 */
public final class PacketEventsTransport implements ClientBlockUtils.BlockChangeTransport {

    /** The fail-closed guard that must succeed before any packet work. */
    @FunctionalInterface
    interface ReadinessGuard {
        void requireReady();
    }

    /** Terminal send step, expressed in server-independent projected fields so tests can record it. */
    @FunctionalInterface
    interface PacketSink {
        void send(Player player, BlockChange change);
    }

    /**
     * Server-independent projection of one queued change onto the fields of a block-change packet.
     *
     * @param x     Bukkit block X (already floored by {@link Location#getBlockX()})
     * @param y     Bukkit block Y
     * @param z     Bukkit block Z (already floored by {@link Location#getBlockZ()})
     * @param state Paper's {@link BlockData#getAsString()} state key, as consumed by
     *              {@code WrappedBlockState.getByString(String)}
     */
    public record BlockChange(int x, int y, int z, String state) {
        public BlockChange {
            Objects.requireNonNull(state, "state");
        }
    }

    private final ReadinessGuard readiness;
    private final PacketSink packetSink;

    /** Production wiring: real readiness guard and the real PacketEvents wrapper/send path. */
    public PacketEventsTransport() {
        this(PacketEventsBridge::requireReady, PacketEventsTransport::sendThroughPacketEvents);
    }

    /** Test seam: recorders replace the guard and the terminal send step. */
    PacketEventsTransport(ReadinessGuard readiness, PacketSink packetSink) {
        this.readiness = Objects.requireNonNull(readiness, "readiness");
        this.packetSink = Objects.requireNonNull(packetSink, "packetSink");
    }

    @Override
    public void send(Player player, Map<Location, BlockData> changes) {
        // Fail closed before touching the API or the batch. The enable gate guarantees readiness
        // in production; a later API teardown must not send half a batch or mask the real cause.
        readiness.requireReady();
        if (player == null || changes == null || changes.isEmpty()) return;

        for (Map.Entry<Location, BlockData> entry : changes.entrySet()) {
            Location location = entry.getKey();
            BlockData data = entry.getValue();
            if (location == null || data == null || location.getWorld() == null) continue;

            packetSink.send(player, toBlockChange(location, data));
        }
    }

    /** The real terminal send step: projected fields → state → packet → player manager. */
    private static void sendThroughPacketEvents(Player player, BlockChange change) {
        WrappedBlockState state = WrappedBlockState.getByString(change.state());
        if (state == null) {
            throw new IllegalStateException(
                    "PacketEvents could not map block state: " + change.state());
        }

        WrapperPlayServerBlockChange packet = new WrapperPlayServerBlockChange(
                new Vector3i(change.x(), change.y(), change.z()), state);
        PacketEvents.getAPI().getPlayerManager().sendPacket(player, packet);
    }

    /** Pure mapping from a queued change to packet fields; needs no live server or API singleton. */
    public static BlockChange toBlockChange(Location location, BlockData data) {
        Objects.requireNonNull(location, "location");
        Objects.requireNonNull(data, "data");
        return new BlockChange(
                location.getBlockX(),
                location.getBlockY(),
                location.getBlockZ(),
                data.getAsString());
    }
}
