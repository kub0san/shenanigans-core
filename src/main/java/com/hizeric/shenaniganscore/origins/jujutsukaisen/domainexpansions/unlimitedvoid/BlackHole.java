package com.hizeric.shenaniganscore.origins.jujutsukaisen.domainexpansions.unlimitedvoid;

import com.hizeric.shenaniganscore.core.display.DisplayUtils;
import com.hizeric.shenaniganscore.core.display.QuadUtils;
import com.hizeric.shenaniganscore.core.display.TransformUtils;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.TextDisplay;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Random;

/**
 * The black hole behind Gojo: the quiet hero of the void.
 *
 * <p>Composition: it hangs behind and above the caster, so victims facing Gojo see him framed
 * against its ring. It is built entirely from display entities:</p>
 * <ul>
 *   <li><b>Core</b>: three overlapping black cubes, a dark silhouette.</li>
 *   <li><b>Photon ring</b>: a thin ring of white-hot cubes facing the arena, with a glow outline.
 *       It <i>draws itself on</i>, cube by cube, as the hole ignites.</li>
 *   <li><b>Lensing arcs</b>: two bright arcs above and below the core, the light of the far side
 *       of the disk bent around it.</li>
 *   <li><b>Accretion disk</b>: ~120 small glowing debris cubes in a tilted annulus, white-hot
 *       inside, cooling through ice-blue to violet. They orbit (inner faster than outer).</li>
 *   <li><b>Halo</b>: stacked translucent quads that fake a soft bloom.</li>
 * </ul>
 *
 * <h3>Cost</h3>
 * The disk never moves per tick. Each cube re-teleports once every {@code period} ticks, with the
 * teleport interpolated over the same {@code period}, so the client draws smooth orbits while the
 * server sends only a handful of updates per tick. A cube's period is chosen so it turns about
 * 0.4 rad per step: small enough that the chord looks like the arc.
 */
public final class BlackHole {

    private static final float SEED = 0.01f;
    private static final Random RANDOM = new Random(0xB1AC4L);

    private static final Material[] DISK_MATERIALS = {
            Material.WHITE_CONCRETE, Material.WHITE_CONCRETE,
            Material.LIGHT_BLUE_CONCRETE, Material.LIGHT_BLUE_CONCRETE,
            Material.BLUE_CONCRETE, Material.PURPLE_CONCRETE, Material.MAGENTA_CONCRETE
    };

    private static final Color RING_GLOW = Color.fromRGB(200, 235, 255);
    private static final Color ARC_GLOW = Color.fromRGB(120, 200, 255);

    private static final class Debris {
        BlockDisplay display;
        double rho;
        double phi;
        double omega;
        float size;
        int period;
        int phase;
        boolean shown;
    }

    private static final class Piece {
        BlockDisplay display;
        float size;
        boolean shown;
    }

    private static final class Glow {
        TextDisplay quad;
        float size;
        int alpha;
        float roll;
        Color color;
    }

    private final World world;
    private final Location center;
    private final double core;          // core radius, in blocks
    private final List<Entity> sink;
    private final Vector right;
    private final Vector up = new Vector(0, 1, 0);
    private final Vector diskA;
    private final Vector diskB;

    private final List<BlockDisplay> coreCubes = new ArrayList<>();
    /** Per core cube: {side, yaw, leanX, leanZ}. */
    private final float[][] coreScale = new float[3][];
    private final List<Piece> ring = new ArrayList<>();
    private final List<Piece> arcs = new ArrayList<>();
    private final List<Debris> disk = new ArrayList<>();
    private final List<Glow> halo = new ArrayList<>();

    private int tickCount;
    private int igniteStart = -1;
    private int igniteTicks = 40;
    private boolean coreShown;
    private boolean haloShown;
    private boolean collapsed;
    private boolean closed;

    /**
     * @param center  where the black hole hangs
     * @param facing  horizontal unit vector from the black hole toward the arena (the way it faces)
     * @param core    radius of the black core, in blocks
     * @param sink    every entity is registered here so the session can clean it up
     */
    public BlackHole(Location center, Vector facing, double core, double quality, List<Entity> sink) {
        this.center = center.clone();
        this.world = center.getWorld();
        this.core = Math.max(1.0, core);
        this.sink = sink;

        Vector f = facing.clone().setY(0);
        if (f.lengthSquared() < 1.0e-6) f = new Vector(0, 0, 1);
        f.normalize();
        this.right = f.clone().crossProduct(up).normalize();

        // The disk is nearly horizontal, tilted slightly toward the viewers.
        double tilt = Math.toRadians(14.0);
        Vector n = up.clone().multiply(Math.cos(tilt)).add(f.clone().multiply(Math.sin(tilt))).normalize();
        this.diskA = right.clone();
        this.diskB = n.clone().crossProduct(diskA).normalize();

        if (world == null) return;
        buildCore(f);
        buildRing(f, 48);
        buildArcs(f, 22);
        buildDisk((int) Math.max(40, Math.min(200, Math.round(120 * quality))));
        buildHalo(f);
    }

    // ------------------------------------------------------------------
    // Build (everything starts as an invisible seed)
    // ------------------------------------------------------------------

    private BlockDisplay seed(Location at, Material m, int brightness) {
        BlockDisplay d = DisplayUtils.spawnBlock(at, m, 4.0f, brightness);
        TransformUtils.applyTransform(d, SEED, SEED, SEED, 0f, 0f, 0f, 0);
        d.setTeleportDuration(0);
        if (sink != null) sink.add(d);
        return d;
    }

    private void buildCore(Vector f) {
        float s = (float) (core * 1.5);
        float[][] orient = {{0f, 0f, 0f}, {45f, 35f, 0f}, {20f, 0f, 55f}};
        for (float[] o : orient) {
            BlockDisplay d = seed(center, Material.BLACK_CONCRETE, 0);
            coreCubes.add(d);
            coreScale[coreCubes.size() - 1] = new float[]{s, o[0], o[1], o[2]};
        }
    }

    private Location onRing(double radiusBlocks, double angle) {
        Vector offset = right.clone().multiply(Math.cos(angle) * radiusBlocks)
                .add(up.clone().multiply(Math.sin(angle) * radiusBlocks));
        return center.clone().add(offset);
    }

    private void buildRing(Vector f, int count) {
        double r = core * 1.18;
        float size = (float) Math.max(0.2, 2.0 * Math.PI * r / count * 1.15);
        for (int i = 0; i < count; i++) {
            double a = Math.PI * 2.0 * i / count;
            BlockDisplay d = seed(onRing(r, a), Material.WHITE_CONCRETE, 15);
            d.setGlowing(true);
            d.setGlowColorOverride(RING_GLOW);
            Piece p = new Piece();
            p.display = d;
            p.size = size;
            ring.add(p);
        }
    }

    private void buildArcs(Vector f, int perArc) {
        double r = core * 1.72;
        float size = (float) Math.max(0.18, core * 0.2);
        for (int arc = 0; arc < 2; arc++) {
            double from = arc == 0 ? Math.toRadians(40) : Math.toRadians(220);
            double span = Math.toRadians(100);
            for (int i = 0; i < perArc; i++) {
                double a = from + span * i / (perArc - 1);
                BlockDisplay d = seed(onRing(r, a), Material.LIGHT_BLUE_CONCRETE, 15);
                d.setGlowing(true);
                d.setGlowColorOverride(ARC_GLOW);
                Piece p = new Piece();
                p.display = d;
                p.size = size * (0.7f + 0.5f * (float) Math.sin(Math.PI * i / (perArc - 1)));
                arcs.add(p);
            }
        }
    }

    private Location diskPoint(double rho, double phi, double lift) {
        Vector v = diskA.clone().multiply(Math.cos(phi) * rho)
                .add(diskB.clone().multiply(Math.sin(phi) * rho));
        Vector n = diskA.clone().crossProduct(diskB);
        return center.clone().add(v).add(n.multiply(lift));
    }

    private void buildDisk(int count) {
        double inner = core * 1.45;
        double outer = core * 3.5;
        for (int i = 0; i < count; i++) {
            Debris d = new Debris();
            double u = RANDOM.nextDouble();
            d.rho = inner * Math.pow(outer / inner, u * u * 0.6 + u * 0.4);
            d.phi = RANDOM.nextDouble() * Math.PI * 2.0;
            double q = (d.rho - inner) / (outer - inner);
            int mi = (int) Math.min(DISK_MATERIALS.length - 1,
                    Math.max(0, Math.floor(q * DISK_MATERIALS.length + RANDOM.nextDouble() * 1.5 - 0.5)));
            d.size = (float) (core * (0.12 + RANDOM.nextDouble() * 0.2) * Math.sqrt(inner / d.rho) * 1.6);
            d.omega = 1.0 * Math.pow(inner / d.rho, 1.5);
            d.period = (int) Math.max(8, Math.min(40, Math.round(0.4 / d.omega * 20.0)));
            d.phase = RANDOM.nextInt(d.period);
            double lift = (RANDOM.nextDouble() - 0.5) * 0.1 * d.rho;
            d.display = seed(diskPoint(d.rho, d.phi, lift), DISK_MATERIALS[mi], q < 0.5 ? 15 : 12);
            disk.add(d);
        }
        disk.sort(Comparator.comparingDouble(x -> x.rho));
    }

    private void buildHalo(Vector f) {
        float[] sizes = {3.2f, 4.8f, 7.0f, 10.0f};
        int[] alphas = {26, 18, 11, 6};
        Color[] colors = {
                Color.fromRGB(170, 200, 255), Color.fromRGB(130, 150, 255),
                Color.fromRGB(110, 110, 255), Color.fromRGB(90, 80, 220)};
        Location at = center.clone().add(f.clone().multiply(-0.5 * core));
        for (int l = 0; l < sizes.length; l++) {
            for (int k = 0; k < 3; k++) {
                Glow g = new Glow();
                g.quad = QuadUtils.spawn(at, Display.Billboard.CENTER, 6.0f, false);
                g.size = (float) (sizes[l] * core);
                g.alpha = alphas[l];
                g.roll = k * 30.0f;
                g.color = colors[l];
                if (sink != null) sink.add(g.quad);
                halo.add(g);
            }
        }
    }

    // ------------------------------------------------------------------
    // Lifecycle
    // ------------------------------------------------------------------

    public Location center() {
        return center;
    }

    /** Begins the slow ignition: the ring draws on, the disk spins up, the halo blooms. */
    public void ignite(int ticks) {
        if (igniteStart >= 0 || closed) return;
        igniteStart = tickCount;
        igniteTicks = Math.max(10, ticks);
    }

    /** Call every tick. */
    public void tick() {
        if (closed || collapsed || world == null) return;
        tickCount++;

        if (igniteStart >= 0) {
            double p = Math.min(1.0, (tickCount - igniteStart) / (double) igniteTicks);

            if (!coreShown && p > 0.0) {
                coreShown = true;
                for (int i = 0; i < coreCubes.size(); i++) {
                    float[] c = coreScale[i];
                    TransformUtils.applyTransform(coreCubes.get(i), c[0], c[0], c[0], c[1], c[2], c[3], 24);
                }
            }
            reveal(ring, p, 0.08, 0.6, 4);
            reveal(arcs, p, 0.55, 0.8, 8);
            revealDisk(p);
            if (!haloShown && p >= 0.6) {
                haloShown = true;
                for (Glow g : halo) {
                    QuadUtils.setColor(g.quad, g.color, g.alpha);
                    QuadUtils.apply(g.quad, g.size, g.size, 0f, 0f, 0f, g.roll, 40);
                }
            }
        }

        // Orbits: each shown cube re-teleports once per period, interpolated across it.
        for (Debris d : disk) {
            if (!d.shown || !d.display.isValid()) continue;
            if ((tickCount + d.phase) % d.period != 0) continue;
            d.phi += d.omega * d.period / 20.0;
            d.display.setTeleportDuration(d.period);
            d.display.teleport(diskPoint(d.rho, d.phi, 0.0));
        }
    }

    private void reveal(List<Piece> pieces, double p, double from, double to, int dur) {
        int n = pieces.size();
        for (int i = 0; i < n; i++) {
            Piece piece = pieces.get(i);
            if (piece.shown) continue;
            if (p < from + (to - from) * i / Math.max(1, n)) break;
            piece.shown = true;
            if (piece.display.isValid()) {
                TransformUtils.applyTransform(piece.display, piece.size, piece.size, piece.size, 0f, 0f, 0f, dur);
            }
        }
    }

    private void revealDisk(double p) {
        int n = disk.size();
        for (int i = 0; i < n; i++) {
            Debris d = disk.get(i);
            if (d.shown) continue;
            if (p < 0.3 + 0.55 * i / Math.max(1, n)) break; // sorted by radius: inner first
            d.shown = true;
            if (d.display.isValid()) {
                TransformUtils.applyTransform(d.display, d.size, d.size, d.size, 0f, 0f, 0f, 6);
            }
        }
    }

    /** Pulls everything into the core and shrinks it. */
    public void collapse(int ticks) {
        if (closed || collapsed) return;
        collapsed = true;
        int t = Math.max(2, Math.min(59, ticks));
        for (Debris d : disk) {
            if (!d.display.isValid()) continue;
            d.display.setTeleportDuration(t);
            d.display.teleport(center);
            TransformUtils.applyTransform(d.display, SEED, SEED, SEED, 0f, 0f, 0f, t);
        }
        for (Piece p : ring) shrink(p, t);
        for (Piece p : arcs) shrink(p, t);
        for (Glow g : halo) QuadUtils.apply(g.quad, 0.01f, 0.01f, 0f, 0f, 0f, g.roll, t);
        for (BlockDisplay c : coreCubes) {
            if (c.isValid()) TransformUtils.applyTransform(c, SEED, SEED, SEED, 0f, 0f, 0f, t);
        }
    }

    private void shrink(Piece p, int t) {
        if (p.display.isValid()) {
            p.display.setTeleportDuration(t);
            p.display.teleport(center);
            TransformUtils.applyTransform(p.display, SEED, SEED, SEED, 0f, 0f, 0f, t);
        }
    }

    // ------------------------------------------------------------------
    // Sources for the victims' information streams
    // ------------------------------------------------------------------

    public int sourceCount() {
        return disk.size();
    }

    /** A live point on the disk, for streams flying into a victim's head. */
    public Location sourcePoint(int index) {
        if (disk.isEmpty()) return center.clone();
        Debris d = disk.get(Math.floorMod(index, disk.size()));
        return d.display.isValid() ? d.display.getLocation() : center.clone();
    }

    public void close() {
        if (closed) return;
        closed = true;
        for (BlockDisplay c : coreCubes) if (c.isValid()) c.remove();
        for (Piece p : ring) if (p.display.isValid()) p.display.remove();
        for (Piece p : arcs) if (p.display.isValid()) p.display.remove();
        for (Debris d : disk) if (d.display.isValid()) d.display.remove();
        for (Glow g : halo) if (g.quad.isValid()) g.quad.remove();
        coreCubes.clear();
        ring.clear();
        arcs.clear();
        disk.clear();
        halo.clear();
    }
}
