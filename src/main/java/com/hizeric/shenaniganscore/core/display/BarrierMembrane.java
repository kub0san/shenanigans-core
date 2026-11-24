package com.hizeric.shenaniganscore.core.display;

import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Display;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.Objects;
import com.hizeric.shenaniganscore.core.util.MathUtil;

/**
 * The barrier's leading edge: a <b>blinding wall of energy</b> riding the
 * exact latitude where the shell is being built.
 *
 * <p>A ring of interpolated {@link BlockDisplay} plates is stretched into a
 * tall, bright curtain instead of a single-block hoop. Each plate is rotated
 * with JOML so its face is <i>tangent to the sphere</i> — the curtain follows
 * the curvature of the barrier, leaning with the latitude it is climbing —
 * and every plate glows cyan, so the edge reads as a solid sheet of light
 * rather than a line of blocks.</p>
 *
 * <p>Two short-lived dust echoes trail behind the curtain, sparks stream along
 * its circumference and the wall "breathes" (small radius and height waves),
 * which is what stops it looking like a mechanically perfect ring. The plate
 * count is capped, so the effect stays cheap even on a 200-block domain.</p>
 *
 * <h3>Lifecycle</h3>
 * <p>All methods are main-thread only, ideally once per tick. Call
 * {@link #hide()} when the wall has not started climbing, and {@link #close()}
 * (after the interpolation settles) to remove the displays for good.</p>
 */
public final class BarrierMembrane {

    /** Matches the teleport/transform interpolation of the shell itself. */
    private static final int INTERPOLATION_TICKS = 2;

    /** How thick the curtain is, in blocks. */
    private static final float PLATE_THICKNESS = 0.18f;
    /** Base curtain height, in blocks. Individual plates breathe around it. */
    private static final float PLATE_HEIGHT = 3.2f;
    /** The curtain's bottom hangs this far below the advancing latitude. */
    private static final double PLATE_SKIRT = 0.55;
    /** Below this ring radius the dome has closed; the curtain stops drawing. */
    private static final double MIN_RING_RADIUS = 0.4;

    /** Blinding cyan outline, as requested. */
    private static final Color GLOW = Color.fromRGB(60, 235, 255);

    // Particle palette: hot white core, cyan halo, cold after-image.
    private static final Particle.DustOptions CORE_DUST =
            new Particle.DustOptions(Color.fromRGB(225, 255, 255), 1.35f);
    private static final Particle.DustOptions HALO_DUST =
            new Particle.DustOptions(Color.fromRGB(70, 205, 255), 0.95f);
    private static final Particle.DustOptions ECHO_DUST =
            new Particle.DustOptions(Color.fromRGB(35, 120, 210), 0.70f);

    private final World world;
    private final double cx;
    private final double cy;
    private final double cz;
    /** Used to work out how far the curtain must lean with the sphere. */
    private final double radius;
    private final BlockDisplay[] plates;

    private float phase;
    private Front previous;
    private Front older;
    private boolean hidden = true;
    private boolean closed;

    public BarrierMembrane(
            Location center,
            double radius,
            Material material,
            int plateCount,
            float viewRange
    ) {
        Objects.requireNonNull(center, "center");
        this.world = Objects.requireNonNull(center.getWorld(), "center world");
        Objects.requireNonNull(material, "material");
        if (!material.isBlock() || material.isAir()) {
            throw new IllegalArgumentException("membrane material must be a non-air block");
        }

        // Keep the exact center; rounding to block coordinates makes the
        // membrane visibly drift when centered on a non-integer location.
        this.cx = center.getX();
        this.cy = center.getY();
        this.cz = center.getZ();
        this.radius = Math.max(1.0, radius);

        int count = Math.max(12, Math.min(96, plateCount));
        this.plates = new BlockDisplay[count];

        Location spawnAt = new Location(world, cx, cy, cz);

        try {
            for (int i = 0; i < count; i++) {
                BlockDisplay plate = world.spawn(spawnAt, BlockDisplay.class, display -> {
                    display.setBlock(material.createBlockData());
                    display.setPersistent(false);
                    display.setViewRange(viewRange);

                    // Brightness raises the block's own light; the glow colour
                    // draws a solid cyan silhouette around it. Together they turn
                    // an ordinary block into a sheet of light.
                    display.setBrightness(new Display.Brightness(15, 15));
                    display.setGlowing(true);
                    display.setGlowColorOverride(GLOW);

                    display.setInterpolationDelay(0);
                    display.setInterpolationDuration(INTERPOLATION_TICKS);
                    display.setTeleportDuration(INTERPOLATION_TICKS);

                    TransformUtils.applyTransform(
                            display,
                            0.01f, 0.01f, 0.01f,
                            0.0f, 0.0f, 0.0f,
                            INTERPOLATION_TICKS
                    );
                });

                plates[i] = plate;
            }
        } catch (Throwable failure) {
            // A partial construction must not strand display entities in the world.
            for (BlockDisplay plate : plates) {
                if (plate != null) {
                    try { plate.remove(); } catch (Throwable ignored) { }
                }
            }
            throw failure;
        }
    }

    /**
     * Moves the curtain to the shell's current leading edge.
     *
     * @param frontDy    vertical offset from the barrier centre, in blocks
     * @param ringRadius horizontal radius of the ring at that height
     */
    public void update(double frontDy, double ringRadius) {
        if (closed) return;

        if (!(ringRadius > MIN_RING_RADIUS)) {
            // The dome has closed over: there is no edge left to draw.
            hide();
            return;
        }

        hidden = false;

        Front current = new Front(frontDy, ringRadius);

        // Fading echoes where the ring was on recent updates give the front
        // momentum instead of letting it snap forward one latitude at a time.
        if (older != null) {
            drawDustRing(older, ECHO_DUST, 4, 0.97);
        }
        if (previous != null) {
            drawDustRing(previous, HALO_DUST, 3, 0.99);
        }

        phase += 0.21f;

        int count = plates.length;
        double baseArc = (2.0 * Math.PI * ringRadius) / count;

        // Curtain plates lean with the sphere: at the equator they stand
        // vertical, higher up they tip inward with the closing dome.
        double latitude = Math.asin(MathUtil.clamp(frontDy / radius, -1.0, 1.0));
        float lean = (float) -latitude;

        Vector3f pivot = new Vector3f(0.5f, 0.5f, 0.5f);

        for (int i = 0; i < count; i++) {
            BlockDisplay plate = plates[i];
            if (plate == null || !plate.isValid()) continue;

            double angle = (2.0 * Math.PI * i) / count;

            // The "breathing" wave keeps the circumference from reading as a
            // mechanically perfect circle, without making it visibly lumpy.
            double wave = Math.sin(phase + angle * 3.0) * 0.055;
            double localRadius = Math.max(0.05, ringRadius + wave);
            double localDy = frontDy + Math.sin(phase * 0.7 + angle) * 0.03;

            // Every few plates the curtain surges taller, giving the edge a
            // ragged, flame-like crown instead of a flat ribbon.
            double surge = 0.5 + 0.5 * Math.sin(phase * 1.3 + angle * 2.0);
            float height = PLATE_HEIGHT * (0.78f + 0.42f * (float) surge);

            Location target = new Location(
                    world,
                    cx + Math.cos(angle) * localRadius,
                    // The block is centered on the display, so lift it by half
                    // its height to keep its bottom on the advancing latitude.
                    cy + localDy - PLATE_SKIRT + height * 0.5,
                    cz + Math.sin(angle) * localRadius
            );

            // Slight overlap between neighbours prevents gaps in the curtain.
            float length = (float) Math.max(0.45, baseArc * 1.14);

            // Rotate the plate so its face is tangent to the sphere:
            //   yaw   -> local X lies along the ring tangent, local Z points
            //            radially outward;
            //   lean  -> tip local Z up to the sphere's surface normal.
            float yaw = (float) Math.toDegrees(angle) - 90.0f;
            Quaternionf rotation = new Quaternionf()
                    .rotateY((float) Math.toRadians(-yaw))
                    .rotateX(lean);

            TransformUtils.applyTransform(
                    plate,
                    new Vector3f(length, height, PLATE_THICKNESS),
                    rotation,
                    pivot,
                    INTERPOLATION_TICKS
            );

            plate.setTeleportDuration(INTERPOLATION_TICKS);
            plate.teleport(target);

            spawnEdgeParticles(i, target.getX(), cy + localDy + 0.1, target.getZ(), angle);
        }

        // Save a short history so the next updates can draw trailing echoes.
        older = previous;
        previous = current;
    }

    /** Sparse but bright: sparks ride the edge, motes drift off it. */
    private void spawnEdgeParticles(int index, double x, double y, double z, double angle) {
        // Hot core: every other plate contributes a blinding white mote.
        if ((index & 1) == 0) {
            world.spawnParticle(
                    Particle.DUST,
                    x, y, z,
                    1,
                    0.04, 0.10, 0.04,
                    0.0,
                    CORE_DUST
            );
        }

        // Cyan halo, slightly wider and a touch dimmer than the core.
        if (index % 3 == 0) {
            world.spawnParticle(
                    Particle.DUST,
                    x, y, z,
                    1,
                    0.14, 0.20, 0.14,
                    0.0,
                    HALO_DUST
            );
        }

        // Streaming sparks along the circumference.
        if (index % 5 == 0) {
            world.spawnParticle(
                    Particle.ELECTRIC_SPARK,
                    x, y + 0.35, z,
                    1,
                    0.10, 0.14, 0.10,
                    0.02
            );
        }

        // Cold flame flecks make the edge feel like it is burning outward.
        if (index % 7 == 0) {
            double outward = 0.18;
            world.spawnParticle(
                    Particle.SOUL_FIRE_FLAME,
                    x + Math.cos(angle) * outward,
                    y + 0.30,
                    z + Math.sin(angle) * outward,
                    1,
                    0.02, 0.06, 0.02,
                    0.002
            );
        }

        // Bright sparks that jump ahead of the wall.
        if (index % 9 == 0) {
            world.spawnParticle(
                    Particle.END_ROD,
                    x + Math.cos(angle) * 0.10,
                    y + 0.24,
                    z + Math.sin(angle) * 0.10,
                    1,
                    0.015, 0.04, 0.015,
                    0.004
            );
        }
    }

    private void drawDustRing(
            Front front,
            Particle.DustOptions dust,
            int stride,
            double radiusScale
    ) {
        int count = plates.length;
        double ring = Math.max(0.0, front.radius * radiusScale);

        for (int i = 0; i < count; i += stride) {
            double angle = (2.0 * Math.PI * i) / count;

            Location point = new Location(
                    world,
                    cx + Math.cos(angle) * ring,
                    cy + front.dy + PLATE_HEIGHT * 0.35,
                    cz + Math.sin(angle) * ring
            );

            world.spawnParticle(
                    Particle.DUST,
                    point,
                    1,
                    0.0, 0.0, 0.0,
                    0.0,
                    dust
            );
        }
    }

    /**
     * Collapses the curtain to nothing. Used while the floor lead-in owns the
     * screen; repeat calls are ignored until the next {@link #update(double, double)}.
     */
    public void hide() {
        if (closed || hidden) return;
        hidden = true;

        // History would otherwise let a stale echo flash when the wall starts.
        previous = null;
        older = null;

        Location center = new Location(world, cx, cy, cz);

        for (BlockDisplay plate : plates) {
            if (plate == null || !plate.isValid()) continue;

            TransformUtils.applyTransform(
                    plate,
                    0.01f, 0.01f, 0.01f,
                    0.0f, 0.0f, 0.0f,
                    INTERPOLATION_TICKS
            );

            plate.setTeleportDuration(INTERPOLATION_TICKS);
            plate.teleport(center);
        }
    }

    /** Removes every display. Idempotent. */
    public void close() {
        if (closed) return;
        closed = true;

        for (BlockDisplay plate : plates) {
            if (plate != null && plate.isValid()) {
                plate.remove();
            }
        }

        previous = null;
        older = null;
    }

    private record Front(double dy, double radius) {
    }
}
