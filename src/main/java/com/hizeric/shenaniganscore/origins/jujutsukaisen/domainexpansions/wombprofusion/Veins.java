package com.hizeric.shenaniganscore.origins.jujutsukaisen.domainexpansions.wombprofusion;

import com.hizeric.shenaniganscore.core.display.DisplayUtils;
import com.hizeric.shenaniganscore.core.display.TransformUtils;
import org.bukkit.HeightMap;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Entity;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Raw veins crawling out of the tower's foot across the carpet, as if the ground were being wired
 * with something alive.
 *
 * <p>Each vein is a chain of thin bars that meanders outward and follows the terrain (heights are read
 * from loaded chunks only). Every bar is spawned once, at its final position and tiny, and is grown
 * with a short interpolated transform when the front reaches it, so a vein <i>extends</i> along its
 * length. No entity ever moves, so the whole effect costs a handful of metadata updates while it
 * grows and nothing afterwards.</p>
 */
public final class Veins {

    private static final float SEED = 0.001f;
    private static final int SEGMENTS = 10;
    private static final Vector3f Z_AXIS = new Vector3f(0, 0, 1);
    private static final Vector3f PIVOT = new Vector3f(0.5f, 0.5f, 0.5f);

    private static final class Segment {
        BlockDisplay display;
        Quaternionf rot;
        Vector3f size;
        boolean shown;
    }

    private final World world;
    private final Location center;
    private final double radius;
    private final Random rnd;
    private final List<Entity> sink;
    private final List<List<Segment>> veins = new ArrayList<>();
    private final int veinCount;
    private boolean closed;

    public Veins(Location center, double radius, double quality, long seed, List<Entity> sink) {
        this.center = center.clone();
        this.world = center.getWorld();
        this.radius = radius;
        this.rnd = new Random(seed ^ 0x7E1175L);
        this.sink = sink;
        this.veinCount = (int) Math.max(5, Math.min(20, Math.round(10 * quality)));
    }

    public int veinCount() {
        return veinCount;
    }

    public int builtCount() {
        return veins.size();
    }

    /** Builds one vein per call. Returns true once all are built. */
    public boolean buildStep() {
        if (closed || world == null) return true;
        if (veins.size() < veinCount) buildVein(veins.size());
        return veins.size() >= veinCount;
    }

    private double groundAt(double x, double z) {
        int bx = (int) Math.floor(x);
        int bz = (int) Math.floor(z);
        if (!world.isChunkLoaded(bx >> 4, bz >> 4)) return center.getY();
        return world.getHighestBlockYAt(bx, bz, HeightMap.MOTION_BLOCKING_NO_LEAVES) + 1.12;
    }

    private void buildVein(int index) {
        List<Segment> chain = new ArrayList<>();
        veins.add(chain);

        double angle = Math.PI * 2.0 * index / veinCount + (rnd.nextDouble() - 0.5) * 0.5;
        double step = radius * 0.86 / SEGMENTS;
        double x = center.getX() + Math.cos(angle) * radius * 0.07;
        double z = center.getZ() + Math.sin(angle) * radius * 0.07;
        Vector3f prev = new Vector3f((float) x, (float) groundAt(x, z), (float) z);

        for (int k = 0; k < SEGMENTS; k++) {
            angle += (rnd.nextDouble() - 0.5) * 0.55;
            x += Math.cos(angle) * step;
            z += Math.sin(angle) * step;
            Vector3f next = new Vector3f((float) x, (float) groundAt(x, z), (float) z);

            Vector3f dir = new Vector3f(next).sub(prev);
            float len = dir.length();
            if (len < 0.05f) {
                prev = next;
                continue;
            }
            Vector3f mid = new Vector3f(prev).add(next).mul(0.5f);
            float thickness = (float) (0.26 * (1.0 - 0.6 * k / SEGMENTS));

            Segment s = new Segment();
            s.rot = new Quaternionf().rotationTo(Z_AXIS, dir.normalize(new Vector3f()));
            s.size = new Vector3f(thickness, thickness, len * 1.08f);
            s.display = DisplayUtils.spawnBlock(new Location(world, mid.x, mid.y, mid.z),
                    (k % 3 == 0) ? Material.NETHER_WART_BLOCK : Material.RED_TERRACOTTA, 6.0f, 9);
            TransformUtils.applyTransform(s.display, new Vector3f(SEED), new Quaternionf(), PIVOT, 0);
            s.display.setTeleportDuration(0);
            if (sink != null) sink.add(s.display);
            chain.add(s);
            prev = next;
        }
    }

    /** 0 = no veins, 1 = fully grown. Raising it extends every vein outward. */
    public void grow(double progress) {
        if (closed) return;
        double p = Math.max(0.0, Math.min(1.0, progress));
        for (List<Segment> chain : veins) {
            int reach = (int) Math.floor(p * chain.size() + 0.0001);
            for (int k = 0; k < Math.min(reach, chain.size()); k++) {
                Segment s = chain.get(k);
                if (s.shown || !s.display.isValid()) continue;
                s.shown = true;
                TransformUtils.applyTransform(s.display, s.size, s.rot, PIVOT, 5);
            }
        }
    }

    /** The veins retract from their tips back toward the tower. */
    public void collapse(int ticks) {
        int t = Math.max(2, Math.min(59, ticks));
        for (List<Segment> chain : veins) {
            for (Segment s : chain) {
                if (s.display.isValid()) {
                    TransformUtils.applyTransform(s.display, new Vector3f(SEED), s.rot, PIVOT, t);
                }
            }
        }
    }

    public void close() {
        if (closed) return;
        closed = true;
        for (List<Segment> chain : veins) {
            for (Segment s : chain) if (s.display.isValid()) s.display.remove();
        }
        veins.clear();
    }
}
