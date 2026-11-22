package com.hizeric.shenaniganscore.origins.jujutsukaisen.domainexpansions.wombprofusion;

import com.hizeric.shenaniganscore.core.display.TransformUtils;
import com.hizeric.shenaniganscore.core.model.Parts;
import com.hizeric.shenaniganscore.core.model.Parts.HeadType;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ItemDisplay;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * The uzumaki: cursed spirits spiralling into the tower's core to be compressed into the blast.
 *
 * <p>About 30 small skull-faced displays start in a wide ring around the tower and each follows a
 * <b>logarithmic-style spiral</b> inward and upward, shrinking as it arrives. Every spirit re-teleports
 * once every {@code STEP} ticks with the move interpolated over the same {@code STEP}, so the client
 * draws smooth arcs while the server sends only a few updates per tick. Spirits start at staggered
 * delays so they arrive as a continuous stream, not a clump.</p>
 */
public final class Spirits {

    private static final int STEP = 10;
    private static final Random RANDOM = new Random(0x5B1A7L);
    private static final HeadType[] TYPES = {HeadType.ZOMBIE, HeadType.PIGLIN, HeadType.SKELETON, HeadType.WITHER};
    private static final Color GLOW = Color.fromRGB(190, 150, 255);

    private static final class Spirit {
        ItemDisplay display;
        double theta0;
        double r0;
        double y0;
        double turns;
        double sizeBlocks;
        int delay;
        boolean done;
    }

    private final World world;
    private final Location core;
    private final List<Entity> sink;
    private final double ground;
    private final List<Spirit> spirits = new ArrayList<>();
    private int duration;
    private int age = -1;
    private boolean closed;

    public Spirits(Location core, double radius, double quality, List<Entity> sink) {
        this.core = core.clone();
        this.world = core.getWorld();
        this.sink = sink;
        this.ground = world == null ? core.getY() : world.getHighestBlockYAt(core.getBlockX(), core.getBlockZ()) + 1.0;
        if (world == null) return;

        int count = (int) Math.max(12, Math.min(70, Math.round(30 * quality)));
        for (int i = 0; i < count; i++) {
            Spirit s = new Spirit();
            s.theta0 = RANDOM.nextDouble() * Math.PI * 2.0;
            s.r0 = radius * (0.55 + RANDOM.nextDouble() * 0.4);
            s.y0 = RANDOM.nextDouble() * 5.0 + 0.5;
            s.turns = 1.2 + RANDOM.nextDouble() * 0.9;
            s.sizeBlocks = 0.6 + RANDOM.nextDouble() * 0.7;
            s.delay = RANDOM.nextInt(Math.max(1, STEP * 2));
            Location start = pointOn(s, 0.0);
            s.display = Parts.head(start, TYPES[RANDOM.nextInt(TYPES.length)], 12, 6.0f);
            s.display.setGlowing(true);
            s.display.setGlowColorOverride(GLOW);
            s.display.setTeleportDuration(0);
            shape(s, 0.001, 0.0, 0);
            if (sink != null) sink.add(s.display);
            spirits.add(s);
        }
    }

    /** A point on the spirit's spiral; {@code u} is 0 at the start and 1 at the core. */
    private Location pointOn(Spirit s, double u) {
        double radius = s.r0 * Math.pow(1.0 - u, 1.35) + 0.8;
        double theta = s.theta0 + s.turns * Math.PI * 2.0 * u;
        double yy = ground + s.y0 + (core.getY() - ground - s.y0) * Math.pow(u, 1.1)
                + Math.sin(u * Math.PI) * 1.5;
        return new Location(world, core.getX() + Math.cos(theta) * radius, yy, core.getZ() + Math.sin(theta) * radius);
    }

    private void shape(Spirit s, double scaleFactor, double spin, int ticks) {
        float scale = (float) (s.sizeBlocks * scaleFactor / Parts.headSize());
        Quaternionf rot = new Quaternionf().rotateY((float) spin).rotateX(0.25f);
        TransformUtils.applyTransform(s.display, new Vector3f(Math.max(0.001f, scale)), rot,
                new Vector3f(0, Parts.headCenterY(), 0), ticks);
    }

    /** Starts the inflow; it takes {@code ticks} for a spirit to reach the core. */
    public void start(int ticks) {
        if (age >= 0 || closed) return;
        duration = Math.max(STEP * 2, ticks);
        age = 0;
    }

    public void tick() {
        if (closed || age < 0) return;
        age++;
        for (Spirit s : spirits) {
            if (s.done || !s.display.isValid()) continue;
            int local = age - s.delay;
            if (local < 0 || local % STEP != 0) continue;

            double u = Math.min(1.0, (local + STEP) / (double) duration);
            if (local + STEP >= duration) s.done = true;

            s.display.setTeleportDuration(STEP);
            s.display.teleport(pointOn(s, u));
            // shrink as it nears the core; swell a little first, so they look like they strain
            double scaleFactor = u > 0.85 ? Math.max(0.05, (1.0 - u) / 0.15) : 1.0 + 0.3 * Math.sin(u * Math.PI);
            shape(s, scaleFactor, u * Math.PI * 4.0, STEP);
        }
    }

    public boolean finished() {
        return age >= 0 && age > duration + STEP * 3;
    }

    public void close() {
        if (closed) return;
        closed = true;
        for (Spirit s : spirits) {
            if (s.display != null && s.display.isValid()) s.display.remove();
        }
        spirits.clear();
    }
}
