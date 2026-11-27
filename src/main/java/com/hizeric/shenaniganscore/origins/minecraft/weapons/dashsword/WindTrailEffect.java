package com.hizeric.shenaniganscore.origins.minecraft.weapons.dashsword;

import com.hizeric.shenaniganscore.core.display.DisplayUtils;
import com.hizeric.shenaniganscore.core.display.TransformUtils;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Display;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Vector;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Display-entity wind/breeze visuals for the Rift Cleave dash.
 *
 * <p>Particles are the first thing players disable for performance, so the wind cues here are
 * real display entities — they survive a clientside {@code particles=off} setting. All spawns go
 * through {@link DisplayUtils}/{@link TransformUtils}, are tracked internally, and are removed by
 * {@link #close()}. The caller drives animation by passing its timeline tick to {@link #tick(int)}.</p>
 */
public final class WindTrailEffect {

    private static final int LAUNCH_RING_COUNT = 16;
    private static final int LAUNCH_GUST_COUNT = 6;
    private static final int STREAK_COUNT = 2;

    private static final int RING_LIFE = 14;
    private static final int GUST_LIFE = 12;
    private static final int STREAK_LIFE = 6;

    private static final double RING_START_RADIUS = 0.7;
    private static final double RING_RADIUS_GAIN = 0.16;
    private static final float RING_START_SCALE = 0.95f;
    private static final float RING_SCALE_GAIN = -0.04f;

    private static final double GUST_START_RADIUS = 0.9;
    private static final double GUST_RADIUS_GAIN = 0.22;
    private static final Vector3f GUST_START_SCALE = new Vector3f(0.10f, 0.10f, 1.30f);
    private static final Vector3f GUST_SCALE_GAIN = new Vector3f(-0.005f, -0.005f, 0.08f);

    private static final double STREAK_START_RADIUS = 0.12;
    private static final double STREAK_RADIUS_GAIN = 0.05;
    private static final Vector3f STREAK_START_SCALE = new Vector3f(0.08f, 0.08f, 0.60f);
    private static final Vector3f STREAK_SCALE_GAIN = new Vector3f(0.0f, 0.0f, 0.04f);

    private static final double RING_FORWARD_DRIFT = 0.08;
    private static final double GUST_FORWARD_DRIFT = 0.18;
    private static final double STREAK_FORWARD_DRIFT = 0.12;

    private static final Material[] GUST_MATERIALS = {
            Material.WHITE_STAINED_GLASS, Material.POWDER_SNOW, Material.COBWEB
    };
    private static final Material[] STREAK_MATERIALS = {
            Material.WHITE_STAINED_GLASS, Material.POWDER_SNOW
    };

    private static final Vector3f PIVOT = new Vector3f(0.5f, 0.5f, 0.5f);

    private enum Type { RING, GUST, STREAK }

    private static final class Tracked {
        final Display display;
        final Type type;
        final int bornTick;
        final int lifeTicks;
        final Location anchor;
        final Vector outward;
        final double startRadius;
        final double radiusGain;
        final Vector3f startScale;
        final Vector3f scaleGain;
        final double forwardDrift;
        /** Facing captured at spawn; already-spawned planes are never retro-rotated. */
        final Vector3f facing;

        Tracked(Display display, Type type, int bornTick, int lifeTicks, Location anchor,
                Vector outward, double startRadius, double radiusGain, Vector3f startScale,
                Vector3f scaleGain, double forwardDrift, Vector3f facing) {
            this.display = display;
            this.type = type;
            this.bornTick = bornTick;
            this.lifeTicks = lifeTicks;
            this.anchor = anchor;
            this.outward = outward;
            this.startRadius = startRadius;
            this.radiusGain = radiusGain;
            this.startScale = startScale;
            this.scaleGain = scaleGain;
            this.forwardDrift = forwardDrift;
            this.facing = facing;
        }
    }

    private final Player caster;
    private final Location origin;
    private final Vector forward;
    private final Vector right;
    private final Vector up;
    private final Vector3f facing;
    private final List<Tracked> tracked = new ArrayList<>();
    private boolean closed;

    /**
     * @param caster  the dashing player (captured for ownership)
     * @param origin  launch origin; the ring and gusts expand around this point
     * @param forward dash direction; normalized internally
     */
    public WindTrailEffect(Player caster, Location origin, Vector forward) {
        this.caster = caster;
        this.origin = origin.clone();
        Vector heading = forward.clone();
        if (heading.lengthSquared() < 1.0e-6) heading = new Vector(0, 0, 1);
        this.forward = heading.normalize();
        this.right = DashAnimation.rightOf(this.forward);
        this.up = this.right.clone().crossProduct(this.forward).normalize();
        this.facing = new Vector3f(
                (float) this.forward.getX(), (float) this.forward.getY(), (float) this.forward.getZ());
    }

    /** Dramatic launch visuals: a large WIND_CHARGE ring plus stretched white wind gusts. */
    public void launchBurst(int tick) {
        if (closed) return;

        for (int i = 0; i < LAUNCH_RING_COUNT; i++) {
            double angle = (Math.PI * 2.0 * i) / LAUNCH_RING_COUNT;
            Vector outward = radial(angle);
            Location at = origin.clone()
                    .add(outward.clone().multiply(RING_START_RADIUS))
                    .add(forward.clone().multiply(RING_FORWARD_DRIFT));
            ItemDisplay display = DisplayUtils.spawnItem(
                    at, new ItemStack(Material.WIND_CHARGE), itemOptions());
            TransformUtils.applyTransform(display, RING_START_SCALE, RING_START_SCALE,
                    RING_START_SCALE, (float) Math.toDegrees(angle), 0f, 0f, 1);
            tracked.add(new Tracked(display, Type.RING, tick, RING_LIFE, origin.clone(), outward,
                    RING_START_RADIUS, RING_RADIUS_GAIN, new Vector3f(RING_START_SCALE),
                    new Vector3f(RING_SCALE_GAIN), RING_FORWARD_DRIFT, facing));
        }

        for (int i = 0; i < LAUNCH_GUST_COUNT; i++) {
            double angle = (Math.PI * 2.0 * i) / LAUNCH_GUST_COUNT + 0.35;
            Vector outward = radial(angle);
            Location at = origin.clone()
                    .add(outward.clone().multiply(GUST_START_RADIUS))
                    .add(forward.clone().multiply(GUST_FORWARD_DRIFT));
            Material material = GUST_MATERIALS[i % GUST_MATERIALS.length];
            BlockDisplay display = DisplayUtils.spawnBlock(at, material, blockOptions());
            TransformUtils.applyFacing(display, new Vector3f(GUST_START_SCALE), facing, PIVOT, 1);
            tracked.add(new Tracked(display, Type.GUST, tick, GUST_LIFE, origin.clone(), outward,
                    GUST_START_RADIUS, GUST_RADIUS_GAIN, new Vector3f(GUST_START_SCALE),
                    new Vector3f(GUST_SCALE_GAIN), GUST_FORWARD_DRIFT, facing));
        }
    }

    /**
     * Sparse, short-lived wind streaks around an echo-beat {@code point}, drifting outward. The
     * {@code heading} is the path tangent at this beat, so new streaks face along the turn.
     */
    public void trailStreak(Location point, Vector heading, int tick) {
        if (closed || point == null || point.getWorld() == null) return;

        Vector3f streakFacing = facingOf(heading);
        for (int i = 0; i < STREAK_COUNT; i++) {
            double angle = (Math.PI * 2.0 * i) / STREAK_COUNT + tick * 0.6;
            Vector outward = radial(angle);
            Location at = point.clone()
                    .add(outward.clone().multiply(STREAK_START_RADIUS));
            Material material = STREAK_MATERIALS[i % STREAK_MATERIALS.length];
            BlockDisplay display = DisplayUtils.spawnBlock(at, material, blockOptions());
            TransformUtils.applyFacing(display, new Vector3f(STREAK_START_SCALE), streakFacing, PIVOT, 1);
            tracked.add(new Tracked(display, Type.STREAK, tick, STREAK_LIFE, point.clone(), outward,
                    STREAK_START_RADIUS, STREAK_RADIUS_GAIN, new Vector3f(STREAK_START_SCALE),
                    new Vector3f(STREAK_SCALE_GAIN), STREAK_FORWARD_DRIFT, streakFacing));
        }
    }

    /**
     * Expands/stretches tracked displays and removes any that have expired. {@code heading} is the
     * live path tangent: it steers each display's forward drift so the wind follows the turn, while
     * a display's facing stays as captured at spawn (no retroactive rotation).
     */
    public void tick(int currentTick, Vector heading) {
        if (closed) return;

        Vector drift = driftDirection(heading);
        Iterator<Tracked> it = tracked.iterator();
        while (it.hasNext()) {
            Tracked entry = it.next();
            int age = currentTick - entry.bornTick;
            if (age < 0) continue;
            if (age >= entry.lifeTicks || !entry.display.isValid()) {
                remove(entry.display);
                it.remove();
                continue;
            }

            double radius = entry.startRadius + entry.radiusGain * age;
            Location at = entry.anchor.clone()
                    .add(entry.outward.clone().multiply(radius))
                    .add(drift.clone().multiply(entry.forwardDrift * age));
            entry.display.teleport(at);

            float sx = Math.max(0.02f, entry.startScale.x + entry.scaleGain.x * age);
            float sy = Math.max(0.02f, entry.startScale.y + entry.scaleGain.y * age);
            float sz = Math.max(0.02f, entry.startScale.z + entry.scaleGain.z * age);

            if (entry.type == Type.RING) {
                TransformUtils.applyTransform(entry.display, sx, sy, sz,
                        age * 18.0f, 0f, 0f, 1);
            } else {
                TransformUtils.applyFacing(
                        entry.display, new Vector3f(sx, sy, sz), entry.facing, PIVOT, 1);
            }
        }
    }

    /** Removes every display this effect spawned. Idempotent. */
    public void close() {
        if (closed) return;
        closed = true;
        for (Tracked entry : tracked) remove(entry.display);
        tracked.clear();
    }

    /** Unit radial direction in the plane perpendicular to the dash direction. */
    private Vector radial(double angle) {
        return right.clone().multiply(Math.cos(angle))
                .add(up.clone().multiply(Math.sin(angle)));
    }

    /** Facing vector for a beat heading, falling back to the cast facing if degenerate. */
    private Vector3f facingOf(Vector heading) {
        if (heading == null || heading.lengthSquared() < 1.0e-6) return facing;
        return new Vector3f((float) heading.getX(), (float) heading.getY(), (float) heading.getZ());
    }

    /** Live drift direction for this tick, falling back to the cast forward if degenerate. */
    private Vector driftDirection(Vector heading) {
        if (heading == null || heading.lengthSquared() < 1.0e-6) return forward;
        return heading.clone().normalize();
    }

    private static void remove(Display display) {
        if (display != null && display.isValid()) display.remove();
    }

    private static DisplayUtils.Options itemOptions() {
        return DisplayUtils.Options.builder()
                .viewRange(9.0f)
                .brightness(15)
                .shadow(0.0f, 0.0f)
                .interpolation(0, 1)
                .teleportDuration(1)
                .cullingBounds(3.0f, 3.0f)
                .build();
    }

    private static DisplayUtils.Options blockOptions() {
        return DisplayUtils.Options.builder()
                .viewRange(10.0f)
                .brightness(15)
                .shadow(0.0f, 0.0f)
                .interpolation(0, 1)
                .teleportDuration(1)
                .cullingBounds(4.0f, 4.0f)
                .build();
    }
}
