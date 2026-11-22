package com.hizeric.shenaniganscore.core.session;

import com.hizeric.shenaniganscore.core.domain.DomainRegistry;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import com.hizeric.shenaniganscore.core.util.MathUtil;
import static com.hizeric.shenaniganscore.core.util.BlockKey.pack;

/**
 * Handles animated under-plane entry for captives captured below the domain plane.
 *
 * <p>For {@link DomainSession.VerticalClass#BELOW_CLEAR} captives an elevator platform
 * is built under their feet and lifted toward {@link DomainSession#getPlaneY()}.
 * Platform blocks are claimed in {@link DomainRegistry} while active and unclaimed
 * as they are removed. When the rise completes the captive is placed at/above the
 * plane to merge into the domain.</p>
 *
 * <p>For {@link DomainSession.VerticalClass#BELOW_OBSTRUCTED} captives a guided
 * particle-corridor glide moves them upward along a safe path (never through solid
 * blocks, never suffocating) into the domain interior.</p>
 */
public final class DomainVerticalHandler {

    private static final int DEFAULT_RISE_TICKS = 30;
    private static final int DEFAULT_GLIDE_TICKS = 24;

    private final DomainSession session;
    private final Map<UUID, Rise> rises = new HashMap<>();
    private final Map<UUID, Glide> glides = new HashMap<>();
    private final Set<UUID> handled = new HashSet<>();
    private boolean initialized;

    public DomainVerticalHandler(DomainSession session) {
        this.session = session;
    }

    /** Initializes handling for below-plane captives once. Call from session tick/start. */
    public void ensureInitialized() {
        if (initialized || session == null) return;
        initialized = true;
        for (DomainSession.CaptiveRecord record : session.getCaptives().values()) {
            if (record == null || record.uuid() == null) continue;
            if (handled.contains(record.uuid())) continue;
            DomainSession.VerticalClass cls = record.verticalClass();
            if (cls == DomainSession.VerticalClass.BELOW_CLEAR
                    && session.underLevelMode() != UnderLevelMode.GLIDE) {
                startRise(record);
            } else if (cls == DomainSession.VerticalClass.BELOW_OBSTRUCTED
                    || (cls == DomainSession.VerticalClass.BELOW_CLEAR
                    && session.underLevelMode() == UnderLevelMode.GLIDE)) {
                startGlide(record);
            }
        }
    }

    /** Ticks active rises and glides. Safe to call every tick. */
    public void tick() {
        ensureInitialized();
        tickRises();
        tickGlides();
    }

    public boolean isBusy(UUID uuid) {
        return uuid != null && (rises.containsKey(uuid) || glides.containsKey(uuid));
    }

    public boolean isAnyBusy() {
        return !rises.isEmpty() || !glides.isEmpty();
    }

    /** Cleans up any active platform blocks/glides (e.g. on abrupt end). */
    public void cleanup() {
        for (Rise rise : rises.values()) {
            removePlatform(rise);
        }
        rises.clear();
        glides.clear();
    }

    private void startRise(DomainSession.CaptiveRecord record) {
        Player player = Bukkit.getPlayer(record.uuid());
        if (player == null || !player.isOnline()) return;
        Location loc = player.getLocation();
        World world = loc.getWorld();
        if (world == null || session.getCenter() == null) return;
        if (session.getCenter().getWorld() != world) return;

        double targetY = session.getPlaneY();
        double startY = Math.min(loc.getY(), targetY - 0.01);
        int ticks = Math.max(5, session.underlevelRiseTicks());
        Rise rise = new Rise(record.uuid(), startY, targetY, ticks, loc.clone());
        rises.put(record.uuid(), rise);
        handled.add(record.uuid());
        buildOrMovePlatform(rise, startY);
    }

    private void startGlide(DomainSession.CaptiveRecord record) {
        Player player = Bukkit.getPlayer(record.uuid());
        if (player == null || !player.isOnline()) return;
        Location from = player.getLocation();
        Location target = findGlideTarget(from);
        if (target == null) {
            // Fallback: try rise target above plane
            target = from.clone();
            target.setY(Math.min(session.getPlaneY() + 1.0, from.getWorld().getMaxHeight() - 2));
        }
        int ticks = Math.max(5, session.underlevelGlideTicks());
        glides.put(record.uuid(), new Glide(record.uuid(), from.clone(), target.clone(), ticks));
        handled.add(record.uuid());
    }

    private void tickRises() {
        Iterator<Map.Entry<UUID, Rise>> it = rises.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<UUID, Rise> entry = it.next();
            Rise rise = entry.getValue();
            Player player = Bukkit.getPlayer(rise.uuid);
            if (player == null || !player.isOnline()) {
                removePlatform(rise);
                it.remove();
                continue;
            }
            rise.tick++;
            double p = MathUtil.clamp(rise.tick / (double) rise.totalTicks, 0.0, 1.0);
            double eased = MathUtil.smoothstep(p);
            double y = MathUtil.lerp(rise.startY, rise.targetY, eased);

            // Move platform and rider up together.
            buildOrMovePlatform(rise, y);
            Location dest = player.getLocation().clone();
            dest.setY(y);
            safeTeleport(player, dest);
            spawnRiseParticles(dest);

            if (rise.tick >= rise.totalTicks) {
                // Merge onto plane/floor: remove platform and place player on plane.
                removePlatform(rise);
                Location end = player.getLocation().clone();
                end.setY(Math.max(rise.targetY, session.getPlaneY()));
                safeTeleport(player, end);
                it.remove();
            }
        }
    }

    private void tickGlides() {
        Iterator<Map.Entry<UUID, Glide>> it = glides.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<UUID, Glide> entry = it.next();
            Glide glide = entry.getValue();
            Player player = Bukkit.getPlayer(glide.uuid);
            if (player == null || !player.isOnline()) {
                it.remove();
                continue;
            }
            glide.tick++;
            double p = MathUtil.clamp(glide.tick / (double) glide.totalTicks, 0.0, 1.0);
            double eased = MathUtil.smoothstep(p);
            double x = MathUtil.lerp(glide.from.getX(), glide.to.getX(), eased);
            double y = MathUtil.lerp(glide.from.getY(), glide.to.getY(), eased);
            double z = MathUtil.lerp(glide.from.getZ(), glide.to.getZ(), eased);
            Location step = new Location(glide.to.getWorld(), x, y, z, glide.from.getYaw(), glide.from.getPitch());

            if (!isSpaceSafe(step)) {
                // Nudge up to nearest safe Y in same column to avoid suffocation/solids.
                step = nudgeUpSafe(step);
            }
            safeTeleport(player, step);
            spawnGlideParticles(step);

            if (glide.tick >= glide.totalTicks) {
                Location end = glide.to.clone();
                if (!isSpaceSafe(end)) end = nudgeUpSafe(end);
                safeTeleport(player, end);
                it.remove();
            }
        }
    }

    private void buildOrMovePlatform(Rise rise, double feetY) {
        Player player = Bukkit.getPlayer(rise.uuid);
        if (player == null || !player.isOnline()) return;
        World world = player.getWorld();
        int baseY = (int) Math.floor(feetY - 1.0);
        int cx = player.getLocation().getBlockX();
        int cz = player.getLocation().getBlockZ();

        Set<Long> wanted = new HashSet<>();
        List<Block> wantedBlocks = new ArrayList<>();
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                int x = cx + dx;
                int z = cz + dz;
                wanted.add(pack(x, baseY, z));
                wantedBlocks.add(world.getBlockAt(x, baseY, z));
            }
        }

        // Remove blocks no longer needed.
        Iterator<Map.Entry<Long, BlockState>> bit = rise.blocks.entrySet().iterator();
        while (bit.hasNext()) {
            Map.Entry<Long, BlockState> e = bit.next();
            if (wanted.contains(e.getKey())) continue;
            restoreBlock(e.getValue());
            bit.remove();
        }

        // Place/update required platform blocks.
        for (Block block : wantedBlocks) {
            long key = pack(block.getX(), block.getY(), block.getZ());
            if (rise.blocks.containsKey(key)) continue;
            if (block.getY() < world.getMinHeight() || block.getY() >= world.getMaxHeight()) continue;
            if (!block.getType().isAir() && !block.isPassable()) continue;
            BlockState state = new BlockState(block.getWorld(), block.getX(), block.getY(), block.getZ(),
                    block.getBlockData().clone());
            DomainRegistry.get().claim(session.getUniqueId(), block);
            block.setType(Material.BARRIER, false);
            rise.blocks.put(key, state);
        }
    }

    private void removePlatform(Rise rise) {
        for (BlockState state : rise.blocks.values()) {
            restoreBlock(state);
        }
        rise.blocks.clear();
    }

    private void restoreBlock(BlockState state) {
        if (state == null || state.world == null) return;
        Block block = state.world.getBlockAt(state.x, state.y, state.z);
        try {
            block.setBlockData(state.data, false);
        } catch (Throwable ignored) {
            // Teardown-time state restore; an unloaded chunk mid-teardown is not worth crashing a domain over.
        }
        DomainRegistry.get().unclaim(session.getUniqueId(), block);
    }

    private Location findGlideTarget(Location from) {
        if (from == null || from.getWorld() == null) return null;
        World world = from.getWorld();
        Location target = from.clone();
        target.setY(Math.min(session.getPlaneY() + 0.5, world.getMaxHeight() - 2));
        // Search upward in same column for a safe standable space at/above plane.
        int x = from.getBlockX();
        int z = from.getBlockZ();
        int startY = Math.max(from.getBlockY(), (int) Math.floor(session.getPlaneY()));
        int maxY = world.getMaxHeight() - 2;
        for (int y = startY; y <= maxY; y++) {
            Location probe = new Location(world, from.getX(), y, from.getZ(), from.getYaw(), from.getPitch());
            if (isSpaceSafe(probe)) {
                return probe;
            }
        }
        return nudgeUpSafe(target);
    }

    private boolean isSpaceSafe(Location feet) {
        if (feet == null || feet.getWorld() == null) return false;
        World world = feet.getWorld();
        int y = feet.getBlockY();
        if (y < world.getMinHeight() + 1 || y >= world.getMaxHeight() - 1) return false;
        Block feetBlock = world.getBlockAt(feet.getBlockX(), y, feet.getBlockZ());
        Block headBlock = world.getBlockAt(feet.getBlockX(), y + 1, feet.getBlockZ());
        return (feetBlock.isPassable() || !feetBlock.getType().isSolid())
                && (headBlock.isPassable() || !headBlock.getType().isSolid());
    }

    private Location nudgeUpSafe(Location loc) {
        if (loc == null || loc.getWorld() == null) return loc;
        World world = loc.getWorld();
        Location p = loc.clone();
        int maxY = world.getMaxHeight() - 2;
        for (int i = 0; i < 16 && p.getBlockY() <= maxY; i++) {
            if (isSpaceSafe(p)) return p;
            p.add(0, 1.0, 0);
        }
        p.setY(Math.min(p.getY(), maxY));
        return p;
    }

    private void spawnRiseParticles(Location loc) {
        World world = loc == null ? null : loc.getWorld();
        if (world == null) return;
        world.spawnParticle(Particle.END_ROD, loc.clone().add(0, 0.2, 0), 4, 0.35, 0.1, 0.35, 0.01);
    }

    private void spawnGlideParticles(Location loc) {
        World world = loc == null ? null : loc.getWorld();
        if (world == null) return;
        world.spawnParticle(Particle.CLOUD, loc.clone().add(0, 1.0, 0), 6, 0.25, 0.35, 0.25, 0.01);
        world.spawnParticle(Particle.END_ROD, loc.clone().add(0, 0.5, 0), 2, 0.15, 0.2, 0.15, 0.0);
    }

    private static void safeTeleport(Player player, Location loc) {
        if (player == null || !player.isOnline() || loc == null || loc.getWorld() == null) return;
        try {
            player.teleport(loc);
            player.setFallDistance(0.0f);
        } catch (Throwable ignored) {
            // A failed return teleport must never abort domain teardown; the player stays where they are.
        }
    }
    private static final class Rise {
        final UUID uuid;
        final double startY;
        final double targetY;
        final int totalTicks;
        final Location startLoc;
        final Map<Long, BlockState> blocks = new HashMap<>();
        int tick;

        Rise(UUID uuid, double startY, double targetY, int totalTicks, Location startLoc) {
            this.uuid = uuid;
            this.startY = startY;
            this.targetY = targetY;
            this.totalTicks = totalTicks;
            this.startLoc = startLoc;
        }
    }

    private static final class Glide {
        final UUID uuid;
        final Location from;
        final Location to;
        final int totalTicks;
        int tick;

        Glide(UUID uuid, Location from, Location to, int totalTicks) {
            this.uuid = uuid;
            this.from = from;
            this.to = to;
            this.totalTicks = totalTicks;
        }
    }

    private static final class BlockState {
        final World world;
        final int x;
        final int y;
        final int z;
        final BlockData data;

        BlockState(World world, int x, int y, int z, BlockData data) {
            this.world = world;
            this.x = x;
            this.y = y;
            this.z = z;
            this.data = data;
        }
    }
}
