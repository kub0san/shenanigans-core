package com.hizeric.shenaniganscore.origins.jujutsukaisen.domainexpansions.malevolentshrine;

import com.hizeric.shenaniganscore.ShenanigansCore;
import com.hizeric.shenaniganscore.core.terrain.DomainFloor;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.block.data.BlockData;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import com.hizeric.shenaniganscore.core.util.MathUtil;

/**
 * The Shrine's ground: a <b>real</b>, flat platform of cursed stone.
 *
 * <h3>Why it is a platform and not a tide</h3>
 * <p>The shrine used to paint its ground with fake blocks — one packet per
 * column of terrain, following the terrain's own height. On flat ground that
 * looked fine; on a hillside the shrine's terrace ended up hanging over a
 * slope, its pillars grew to different lengths to reach the ground, and the
 * whole structure looked like it had been dropped onto the landscape from
 * above.</p>
 *
 * <p>Now the shrine stands on a platform that is genuinely flat: the terrain
 * inside the shrine's footprint is measured, cut away above the shrine's level
 * and filled in below it, and the ground is rebuilt out of real server
 * blocks. The temple is therefore always level, its pillars are always the
 * same length, and its terrace always meets the ground.</p>
 *
 * <h3>The tide is still a tide</h3>
 * <p>Everything that made the old ground worth looking at is still there, it
 * is just made of real blocks now:</p>
 * <ul>
 *   <li><b>Zones, not dice.</b> The surface is chosen from coherent value-noise,
 *   so the courtyard (polished blackstone with bone patches), the ossuary ring
 *   (bone fading into the dark) and the tide (black concrete, obsidian, frozen
 *   ripple rings) are patches and drifts instead of confetti.</li>
 *   <li><b>Magma veins.</b> Eight jagged cracks radiate from the shrine and are
 *   built <i>ahead</i> of the tide, so they shoot outward first and the dark
 *   floods in behind them.</li>
 *   <li><b>Scars.</b> {@link #scar(double, double, boolean)} lets Dismantle
 *   carve real cuts into the platform: white-hot, then cooling to cracked
 *   stone or magma. The ground is genuinely gouged now.</li>
 *   <li><b>Recede.</b> {@link #recede(double)} plays the whole thing backwards on
 *   collapse: the platform dissolves from the rim inwards and the real terrain
 *   returns.</li>
 * </ul>
 *
 * <p>Because the platform is real, everybody in the shrine stands on the same
 * plane — and the shrine's own geometry can finally assume a level datum.</p>
 */
public final class Floor {

    /** Hard cap: a 200m shrine must not turn the whole map into a platform. */
    private static final int MAX_FLOOR_RADIUS = 64;
    private static final int SPOKES = 8;
    private static final double VIEW_RANGE_SQ = 110.0 * 110.0;

    // ── Palette (cached once) ──
    private static BlockData bd(Material m) { return m.createBlockData(); }

    private static final BlockData BONE = bd(Material.BONE_BLOCK);
    private static final BlockData POLISHED_BRICKS = bd(Material.POLISHED_BLACKSTONE_BRICKS);
    private static final BlockData CRACKED_BRICKS = bd(Material.CRACKED_POLISHED_BLACKSTONE_BRICKS);
    private static final BlockData POLISHED = bd(Material.POLISHED_BLACKSTONE);
    private static final BlockData BLACKSTONE = bd(Material.BLACKSTONE);
    private static final BlockData BLACK_CONCRETE = bd(Material.BLACK_CONCRETE);
    private static final BlockData OBSIDIAN = bd(Material.OBSIDIAN);
    private static final BlockData NETHER_BRICKS = bd(Material.NETHER_BRICKS);
    private static final BlockData RED_BRICKS = bd(Material.RED_NETHER_BRICKS);
    private static final BlockData MAGMA = bd(Material.MAGMA_BLOCK);
    private static final BlockData SCAR_HOT = bd(Material.WHITE_CONCRETE);
    private static final BlockData SCAR_COOL = bd(Material.CRACKED_DEEPSLATE_BRICKS);

    private static final Particle.DustTransition RIPPLE =
            new Particle.DustTransition(Color.fromRGB(205, 20, 20), Color.fromRGB(30, 0, 0), 1.3f);
    private static final Particle.DustOptions EMBER =
            new Particle.DustOptions(Color.fromRGB(190, 40, 10), 1.0f);

    private final Session session;
    private final List<Scar> cooling = new ArrayList<>();

    /** The real platform. Created in {@link #prepare()}, once the shrine's
     *  position is known. */
    private DomainFloor platform;
    private int floorRadius;
    private long seed;
    private int tickCount;
    private double[] spokeBase = new double[SPOKES];
    private double[] spokeReach = new double[SPOKES];
    /** Lowest and highest build key, so the front's radius can be reported. */
    private double minKey;
    private double maxKey;

    /** A cut that is still cooling down. */
    private record Scar(int x, int z, boolean glow, int coolAt) {}

    public Floor(Session session) {
        this.session = session;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Build
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Measures the terrain, plans the platform and decides every column's look.
     * Call once, early — the title hides the work.
     */
    public void prepare() {
        World world = session.getShrineCenter().getWorld();
        if (world == null) return;

        ThreadLocalRandom rng = ThreadLocalRandom.current();
        seed = rng.nextLong();

        int maxFloor = MAX_FLOOR_RADIUS;
        floorRadius = (int) Math.min(session.getRadius(), Math.max(8, maxFloor));
        floorRadius = Math.max(8, Math.min(MAX_FLOOR_RADIUS, floorRadius));

        for (int j = 0; j < SPOKES; j++) {
            spokeBase[j] = j * (Math.PI * 2 / SPOKES) + (rng.nextDouble() - 0.5) * 0.35;
            spokeReach[j] = floorRadius * (0.62 + 0.38 * rng.nextDouble()) + 6.0;
        }

        // The platform's centre is the shrine itself, but its level is the
        // caster's footing: the temple and the ground share one datum.
        Location center = session.getShrineCenter().clone();
        center.setY(session.getShrineCenter().getY() - 1.0);

        platform = new DomainFloor(center, floorRadius, Material.BLACK_CONCRETE,
                floorRadius, DomainFloor.clampBudget(DomainFloor.DEFAULT_BUDGET));
        platform.setSessionId(session.getCaster().getUniqueId());
        // Carry the rim down towards the terrain so the tide slab reads as a
        // carved terrace from outside, with ember brick instead of a bare slab.
        platform.setRimSkirt(12, Material.NETHER_BRICKS);
        platform.setPattern(this::patternAt);
        platform.setOrder(this::orderKey);
        platform.prepare();

        // The platform normalizes the keys, so the same range is used here to
        // turn a build progress back into a radius for the tide ring.
        minKey = platform.minKey();
        maxKey = platform.maxKey();
        if (maxKey <= minKey) {
            minKey = 0.0;
            maxKey = 1.0;
        }
    }

    /** True once the platform has been planned. */
    public boolean ready() {
        return platform != null;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // The surface
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * The block a column gets.
     *
     * <p>{@code dx}/{@code dz} are the column's offset from the shrine — the
     * platform's centre — so the zones read as rings around the temple.</p>
     */
    private BlockData patternAt(int dx, int dz) {
        double r = Math.sqrt((double) dx * dx + (double) dz * dz);
        double ang = Math.atan2(dz, dx);

        // The outermost ring is a burning rim, so the platform never just
        // stops in the middle of the grass.
        if (r > floorRadius - 1.5) {
            return ((dx + dz) & 1) == 0 ? MAGMA : RED_BRICKS;
        }

        int crack = crackLevel(r, ang);
        if (crack == 2) return MAGMA;
        if (crack == 1) return RED_BRICKS;

        return zoneBlock(dx, dz, r);
    }

    /**
     * The build order of a column.
     *
     * <p>The magma veins lead: they are built at 45 % of their distance, so
     * they shoot outward ahead of the tide that floods in behind them.</p>
     */
    private double orderKey(int dx, int dz) {
        double r = Math.sqrt((double) dx * dx + (double) dz * dz);
        double ang = Math.atan2(dz, dx);
        if (crackLevel(r, ang) > 0) {
            return r * 0.45;
        }
        return r + (noise(dx * 0.22, dz * 0.22, seed + 5) - 0.5) * 6.0;
    }

    /** Courtyard, ossuary ring, then the open tide. */
    private BlockData zoneBlock(int dx, int dz, double r) {
        if (r < 13.0) {
            // Courtyard: polished stone, cracked patches, bone drifts
            double n = noise(dx * 0.5, dz * 0.5, seed);
            if (n > 0.64) return BONE;
            if (n < 0.28) return CRACKED_BRICKS;
            return POLISHED_BRICKS;
        }
        if (r < 25.0) {
            // Ossuary ring: bone dissolves into darkness with distance
            double fade = (r - 13.0) / 12.0;
            double n = noise(dx * 0.35, dz * 0.35, seed + 1);
            if (n > 0.52 + fade * 0.36) return BONE;
            return noise(dx * 0.6, dz * 0.6, seed + 2) > 0.55 ? POLISHED : BLACKSTONE;
        }
        // The tide: black glass-like plain with frozen ripple rings and rare blood veins
        double band = Math.sin(r * 0.75 + noise(dx * 0.12, dz * 0.12, seed + 2) * 5.0);
        if (band > 0.5 && noise(dx * 0.2, dz * 0.2, seed + 3) > 0.78) return NETHER_BRICKS;
        if (band > 0.82) return OBSIDIAN;
        if (band < -0.93) return POLISHED;
        return BLACK_CONCRETE;
    }

    /** 0 = no crack, 1 = scorched edge, 2 = magma core. */
    private int crackLevel(double r, double ang) {
        if (r < 11.5) return 0;
        int best = 0;
        for (int j = 0; j < SPOKES; j++) {
            if (r > spokeReach[j]) continue;
            double wob = 0.16 * Math.sin(r * 0.33 + j * 2.1) + 0.09 * Math.sin(r * 0.81 + j);
            double d = ang - (spokeBase[j] + wob);
            d = Math.atan2(Math.sin(d), Math.cos(d));
            if (Math.abs(d) > Math.PI / 2) continue;
            double perp = Math.abs(Math.sin(d)) * r;
            double width = 0.6 + 1.1 * (1.0 - r / spokeReach[j]);
            if (perp < width * 0.5) return 2;
            if (perp < width) best = 1;
        }
        return best;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Expansion / recede
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Grows the platform.
     *
     * @param progress 0–1 through the tide
     */
    public void expand(double progress) {
        if (platform == null) return;
        platform.build(MathUtil.clamp(progress, 0.0, 1.0));
    }

    /**
     * The radius the tide's front currently sits at, in blocks from the shrine.
     *
     * <p>Used to draw the glowing rim that rides the wavefront.</p>
     */
    public double frontRadius(double progress) {
        double p = MathUtil.clamp(progress, 0.0, 1.0);
        return minKey + p * (maxKey - minKey);
    }

    /**
     * Hands the world back from the rim inwards.
     *
     * @param progress 0 = the platform is still whole, 1 = it is gone
     */
    public void recede(double progress) {
        if (platform == null) return;
        platform.recede(MathUtil.clamp(progress, 0.0, 1.0));
    }

    /** Gives the world back for good. */
    public void restoreAll() {
        if (platform == null) return;
        platform.close();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Dismantle hooks
    // ─────────────────────────────────────────────────────────────────────────

    /** Carves a cut into the platform under (wx, wz): white-hot, then it cools. */
    public void scar(double wx, double wz, boolean hot) {
        if (platform == null) return;
        int x = (int) Math.floor(wx);
        int z = (int) Math.floor(wz);
        if (!platform.setSurface(x, z, hot ? SCAR_HOT : SCAR_COOL)) return;

        boolean glow = crackLevel(distanceFromShrine(x, z), angleFromShrine(x, z)) > 0
                || ThreadLocalRandom.current().nextInt(4) == 0;
        cooling.add(new Scar(x, z, glow, tickCount + 5));
    }

    private double distanceFromShrine(int x, int z) {
        Location shrine = session.getShrineCenter();
        return Math.hypot(x + 0.5 - shrine.getX(), z + 0.5 - shrine.getZ());
    }

    private double angleFromShrine(int x, int z) {
        Location shrine = session.getShrineCenter();
        return Math.atan2(z + 0.5 - shrine.getZ(), x + 0.5 - shrine.getX());
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Height queries
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * The standing height of the ground at x/z.
     *
     * <p>On the platform that is the flat surface; outside it, the real
     * terrain.</p>
     */
    public double groundY(double x, double z, double fallback) {
        int bx = (int) Math.floor(x);
        int bz = (int) Math.floor(z);
        if (platform != null && platform.contains(bx, bz)) {
            return platform.surfaceY();
        }

        World world = session.getShrineCenter().getWorld();
        if (world == null || !world.isChunkLoaded(bx >> 4, bz >> 4)) {
            return fallback;
        }
        return world.getHighestBlockYAt(bx, bz) + 1.0;
    }

    /**
     * How far the local ground sits above or below the shrine's datum.
     *
     * <p>On the platform this is always zero — that is the whole point: the
     * temple's pillars are all the same length, whatever the terrain under the
     * shrine used to do.</p>
     */
    public double terrainOffset(double worldX, double worldZ) {
        int bx = (int) Math.floor(worldX);
        int bz = (int) Math.floor(worldZ);
        if (platform != null && platform.contains(bx, bz)) {
            return 0.0;
        }
        World world = session.getShrineCenter().getWorld();
        if (world == null || !world.isChunkLoaded(bx >> 4, bz >> 4)) {
            return 0.0;
        }
        return Builder.terrainSurfaceY(world, bx, bz) - session.getShrineCenter().getY();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Ambience
    // ─────────────────────────────────────────────────────────────────────────

    /** A ring of drifting blood-dust that rolls across the settled floor. */
    public void ripple(double ringRadius) {
        World world = session.getShrineCenter().getWorld();
        if (world == null || ringRadius <= 0) return;

        Location sc = session.getShrineCenter();
        int pts = (int) Math.min(110, Math.max(16, ringRadius * 2.2));
        for (int i = 0; i < pts; i++) {
            double a = (Math.PI * 2 * i) / pts;
            double x = sc.getX() + Math.cos(a) * ringRadius;
            double z = sc.getZ() + Math.sin(a) * ringRadius;
            world.spawnParticle(Particle.DUST_COLOR_TRANSITION, x, groundY(x, z, sc.getY()) + 0.12, z,
                    1, 0, 0, 0, 0, RIPPLE);
        }
    }

    /** The burning edge that eats the platform away on collapse. */
    public void recedeRim(double radius) {
        World world = session.getShrineCenter().getWorld();
        if (world == null || radius <= 0) return;

        Location sc = session.getShrineCenter();
        int pts = (int) Math.min(90, Math.max(18, radius * 2.4));
        for (int i = 0; i < pts; i++) {
            double a = (Math.PI * 2 * i) / pts;
            double x = sc.getX() + Math.cos(a) * radius;
            double z = sc.getZ() + Math.sin(a) * radius;
            double y = groundY(x, z, sc.getY()) + 0.1;
            world.spawnParticle(Particle.DUST, x, y, z, 2, 0.2, 0.05, 0.2, 0.01, EMBER);
            if (i % 5 == 0) {
                world.spawnParticle(Particle.LARGE_SMOKE, x, y + 0.2, z, 1, 0.1, 0.1, 0.1, 0.01);
            }
        }
    }

    /** Call once per tick: cools the scars and spends the platform's budget. */
    public void tick() {
        tickCount++;
        if (platform != null) {
            platform.tick();
        }

        for (int i = cooling.size() - 1; i >= 0; i--) {
            Scar scar = cooling.get(i);
            if (tickCount < scar.coolAt()) continue;
            cooling.remove(i);
            if (platform != null) {
                platform.setSurface(scar.x(), scar.z(), scar.glow() ? MAGMA : SCAR_COOL);
            }
        }
    }

    // ── tiny value-noise ──
    private static long hash(int x, int z, long seed) {
        long n = x * 374761393L + z * 668265263L + seed * 1442695040888963407L;
        n = (n ^ (n >>> 13)) * 1274126177L;
        n ^= (n >>> 16);
        return n & 0xFFFFFF;
    }

    private static double noise(double x, double z, long seed) {
        int x0 = (int) Math.floor(x);
        int z0 = (int) Math.floor(z);
        double fx = x - x0;
        double fz = z - z0;
        double sx = fx * fx * (3 - 2 * fx);
        double sz = fz * fz * (3 - 2 * fz);
        double a = hash(x0, z0, seed) / (double) 0x1000000;
        double b = hash(x0 + 1, z0, seed) / (double) 0x1000000;
        double c = hash(x0, z0 + 1, seed) / (double) 0x1000000;
        double d = hash(x0 + 1, z0 + 1, seed) / (double) 0x1000000;
        double top = a + (b - a) * sx;
        double bot = c + (d - c) * sx;
        return top + (bot - top) * sz;
    }
}
