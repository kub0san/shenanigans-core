package com.hizeric.shenaniganscore.origins.jujutsukaisen.domainexpansions.wombprofusion;

import com.hizeric.shenaniganscore.ShenanigansCore;
import com.hizeric.shenaniganscore.core.util.ClientBlockUtils;
import org.bukkit.Bukkit;
import org.bukkit.HeightMap;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * "Blanketing the countryside": the ground turns to living flesh, with the Womb Realm mandala laid
 * into it. Everything is client-side (fake blocks), so the real terrain is never touched; only the
 * <b>top surface</b> of each column is repainted, so the carpet follows hills.
 *
 * <h3>The mandala</h3>
 * <ul>
 *   <li><b>Centre</b>: an eight-petal lotus (red petals on pale flesh) around a bone-white hub, under
 *       the tower.</li>
 *   <li><b>Courts</b>: concentric ring lines and two square outlines.</li>
 *   <li><b>Edge</b>: a thick raw band at the domain's radius, which is also the edge of the blast's
 *       area, so players can see where it is safe to stand.</li>
 *   <li><b>Between</b>: patchy flesh from coherent noise, never per-block confetti.</li>
 * </ul>
 *
 * <h3>Behaviour</h3>
 * Tiles are sorted by distance from the centre. {@link #setTarget} moves a reveal pointer along that
 * order, so raising it spreads the carpet outward and lowering it recedes it <b>from the edge
 * inward</b> (the dispel). The session draws the leading edge with particles. Each viewer has their
 * own pointer, so late arrivals are caught up and leavers get the real world back.
 *
 * <p>Only loaded chunks are read, only full opaque surface blocks are repainted (so collision never
 * changes), and non-colliding clutter above the surface is hidden. Restoring always re-reads the real
 * world.</p>
 */
public final class Carpet {

    private static final int COLUMNS_PER_TICK = 600;
    private static final int PER_TICK = 2500;
    private static final double VIEW_RANGE = 110.0;

    private static final BlockData PINK = Material.PINK_TERRACOTTA.createBlockData();
    private static final BlockData WHITE = Material.WHITE_TERRACOTTA.createBlockData();
    private static final BlockData STEM = Material.MUSHROOM_STEM.createBlockData();
    private static final BlockData TAN = Material.TERRACOTTA.createBlockData();
    private static final BlockData RED = Material.RED_TERRACOTTA.createBlockData();
    private static final BlockData WART = Material.NETHER_WART_BLOCK.createBlockData();
    private static final BlockData BONE = Material.BONE_BLOCK.createBlockData();
    private static final BlockData BRICK = Material.RED_NETHER_BRICKS.createBlockData();
    private static final BlockData AIR = Material.AIR.createBlockData();

    private static final class Tile {
        final int x;
        final int y;
        final int z;
        final double dist;
        final BlockData data;
        final boolean clutterAbove;

        Tile(int x, int y, int z, double dist, BlockData data, boolean clutterAbove) {
            this.x = x;
            this.y = y;
            this.z = z;
            this.dist = dist;
            this.data = data;
            this.clutterAbove = clutterAbove;
        }
    }

    private final World world;
    private final Location center;
    private final double radius;
    private final long seed;
    private final List<Tile> tiles = new ArrayList<>();
    private final Map<UUID, Integer> sent = new HashMap<>();

    private int scanIndex;
    private int scanSize;
    private boolean prepared;
    private double target;
    private boolean closed;

    public Carpet(Location center, double radius, long seed) {
        this.center = center.clone();
        this.world = center.getWorld();
        this.radius = radius;
        this.seed = seed;
        this.scanSize = (int) Math.ceil(radius) * 2 + 1;
    }

    // ------------------------------------------------------------------
    // Scanning (spread over a few ticks, loaded chunks only)
    // ------------------------------------------------------------------

    /** Scans a slice of columns. Call each tick until it returns true. */
    public boolean prepareStep() {
        if (prepared || closed || world == null) return true;
        int total = scanSize * scanSize;
        int cx = center.getBlockX();
        int cz = center.getBlockZ();
        int half = scanSize / 2;

        for (int n = 0; n < COLUMNS_PER_TICK && scanIndex < total; n++, scanIndex++) {
            int dx = scanIndex % scanSize - half;
            int dz = scanIndex / scanSize - half;
            double d = Math.sqrt((double) dx * dx + (double) dz * dz);
            if (d > radius) continue;
            int x = cx + dx;
            int z = cz + dz;
            if (!world.isChunkLoaded(x >> 4, z >> 4)) continue;

            int y = world.getHighestBlockYAt(x, z, HeightMap.MOTION_BLOCKING_NO_LEAVES);
            Block surface = world.getBlockAt(x, y, z);
            Material m = surface.getType();
            // Full opaque blocks only: changing a slab or a path would change collision.
            if (!m.isOccluding()) continue;

            Material above = world.getBlockAt(x, y + 1, z).getType();
            boolean clutter = !above.isAir() && !above.isSolid()
                    && above != Material.WATER && above != Material.LAVA;
            tiles.add(new Tile(x, y, z, d, pattern(dx, dz, d), clutter));
        }

        if (scanIndex >= total) {
            tiles.sort(Comparator.comparingDouble(t -> t.dist));
            prepared = true;
        }
        return prepared;
    }

    public boolean isPrepared() {
        return prepared;
    }

    // ------------------------------------------------------------------
    // The mandala
    // ------------------------------------------------------------------

    private double noise(int x, int z, int scale) {
        int gx = Math.floorDiv(x, scale);
        int gz = Math.floorDiv(z, scale);
        double fx = (double) Math.floorMod(x, scale) / scale;
        double fz = (double) Math.floorMod(z, scale) / scale;
        double a = hash(gx, gz), b = hash(gx + 1, gz), c = hash(gx, gz + 1), d = hash(gx + 1, gz + 1);
        double sx = fx * fx * (3 - 2 * fx);
        double sz = fz * fz * (3 - 2 * fz);
        return (a + (b - a) * sx) + ((c + (d - c) * sx) - (a + (b - a) * sx)) * sz;
    }

    private double hash(int x, int z) {
        long h = x * 374761393L + z * 668265263L + seed * 0x9E3779B97F4A7C15L;
        h = (h ^ (h >>> 13)) * 1274126177L;
        return ((h ^ (h >>> 16)) & 0xFFFF) / 65535.0;
    }

    /** Which block of the carpet sits at this offset from the centre. */
    private BlockData pattern(int dx, int dz, double d) {
        double r = radius;
        double u = d / r;                       // 0 at the tower, 1 at the edge
        double theta = Math.atan2(dz, dx);
        int cheb = Math.max(Math.abs(dx), Math.abs(dz));

        // The edge of the area of effect: a thick raw band, studded with bone.
        if (d >= r - 1.6) {
            return ((dx + dz) & 3) == 0 ? BONE : WART;
        }

        // The hub and the eight-petal lotus.
        if (u < 0.05) return BONE;
        if (u < 0.24) {
            double petal = 0.235 * Math.pow(Math.abs(Math.cos(4.0 * theta)), 0.55);
            if (u < petal && u > 0.07) return RED;
            if (u < petal + 0.012) return WART;
        }

        // Concentric ring lines.
        for (double ring : new double[]{0.32, 0.5, 0.7}) {
            if (Math.abs(d - ring * r) < 0.7) return BRICK;
        }
        // Two square courts.
        for (double court : new double[]{0.42, 0.62}) {
            if (Math.abs(cheb - court * r) < 0.7) return BONE;
        }

        // Between the lines: patchy flesh, with raw veins.
        double n = noise(dx, dz, 6) * 0.65 + noise(dx, dz, 3) * 0.35;
        double vein = noise(dx + 91, dz - 47, 4);
        if (vein > 0.8) return RED;
        if (n < 0.3) return WHITE;
        if (n < 0.5) return STEM;
        if (n < 0.75) return PINK;
        return TAN;
    }

    // ------------------------------------------------------------------
    // Reveal / recede
    // ------------------------------------------------------------------

    /** 0 = no carpet, 1 = all of it. Raising spreads outward; lowering recedes from the edge. */
    public void setTarget(double front) {
        this.target = Math.max(0.0, Math.min(1.0, front));
    }

    /** The radius the carpet has reached, in blocks. */
    public double reachedRadius() {
        return target * radius;
    }

    private int indexFor(double front) {
        if (tiles.isEmpty()) return 0;
        double r = front * radius;
        int lo = 0, hi = tiles.size();
        while (lo < hi) {
            int mid = (lo + hi) >>> 1;
            if (tiles.get(mid).dist <= r) lo = mid + 1;
            else hi = mid;
        }
        return lo;
    }

    /** Call once per tick: moves every viewer's pointer toward the target. */
    public void tick() {
        if (closed || !prepared || world == null) return;
        flush(PER_TICK);
    }

    private void flush(int budget) {
        sent.keySet().removeIf(id -> {
            Player p = Bukkit.getPlayer(id);
            return p == null || !p.isOnline() || !p.getWorld().equals(world);
        });

        int want = closed ? 0 : indexFor(target);
        double rangeSq = VIEW_RANGE * VIEW_RANGE;
        Map<Long, Map<Location, BlockData>> cache = new HashMap<>();

        for (Player p : world.getPlayers()) {
            UUID id = p.getUniqueId();
            boolean near = !closed && p.getLocation().distanceSquared(center) <= rangeSq;
            int goal = near ? want : 0;
            int have = sent.getOrDefault(id, 0);
            if (have == goal) {
                if (have == 0) sent.remove(id);
                continue;
            }
            if (have < goal) {
                int to = (int) Math.min((long) goal, (long) have + budget);
                send(p, cache, have, to, true);
                sent.put(id, to);
            } else {
                int from = (int) Math.max((long) goal, (long) have - budget);
                send(p, cache, from, have, false);
                sent.put(id, from);
            }
        }
    }

    private void send(Player p, Map<Long, Map<Location, BlockData>> cache, int from, int to, boolean reveal) {
        long key = (((long) from << 32) | to) ^ (reveal ? 0x4000000000000000L : 0L);
        Map<Location, BlockData> batch = cache.computeIfAbsent(key, k -> build(from, to, reveal));
        if (!batch.isEmpty()) ClientBlockUtils.sendFakeBlocks(p, batch);
    }

    private Map<Location, BlockData> build(int from, int to, boolean reveal) {
        Map<Location, BlockData> map = new HashMap<>(Math.max(16, (to - from) * 2));
        for (int i = from; i < to; i++) {
            Tile t = tiles.get(i);
            if (reveal) {
                map.put(new Location(world, t.x, t.y, t.z), t.data);
                if (t.clutterAbove) map.put(new Location(world, t.x, t.y + 1, t.z), AIR);
            } else {
                if (!world.isChunkLoaded(t.x >> 4, t.z >> 4)) continue;
                map.put(new Location(world, t.x, t.y, t.z), world.getBlockAt(t.x, t.y, t.z).getBlockData());
                if (t.clutterAbove) {
                    map.put(new Location(world, t.x, t.y + 1, t.z), world.getBlockAt(t.x, t.y + 1, t.z).getBlockData());
                }
            }
        }
        return map;
    }

    // ------------------------------------------------------------------
    // Shutdown
    // ------------------------------------------------------------------

    /** Gives every viewer the real world back, spread over a few ticks if the plugin is still up. */
    public void close() {
        if (closed) return;
        closed = true;
        target = 0.0;
        if (sent.isEmpty()) return;

        ShenanigansCore plugin = ShenanigansCore.getInstance();
        if (plugin != null && plugin.isEnabled()) {
            try {
                new BukkitRunnable() {
                    int guard = 0;

                    @Override
                    public void run() {
                        flush(PER_TICK);
                        if (sent.isEmpty()) {
                            cancel();
                        } else if (++guard > 400) {
                            cancel();
                            finishNow();
                        }
                    }
                }.runTaskTimer(plugin, 0L, 1L);
                return;
            } catch (Throwable ignored) {
                // fall through to the synchronous path
            }
        }
        finishNow();
    }

    private void finishNow() {
        int guard = 0;
        while (!sent.isEmpty() && guard++ < 1000) flush(Integer.MAX_VALUE / 2);
    }
}
