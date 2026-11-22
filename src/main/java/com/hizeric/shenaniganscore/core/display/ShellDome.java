package com.hizeric.shenaniganscore.core.display;

import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Display;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import com.hizeric.shenaniganscore.core.util.MathUtil;

/**
 * The persistent half of a closed domain's shell.
 *
 * <p>The {@link BarrierMembrane} is the <i>leading edge</i> — a curtain that
 * rides the advancing front and hides again once the dome closes. By itself
 * that leaves the domain visually hollow for its entire live phase: nothing
 * encloses the caster, and the void reads as "sky and floor" instead of a
 * sealed plane.</p>
 *
 * <p>This dome fills that hole. It is a sparse shell of large, tangent
 * {@link BlockDisplay} plates on the upper hemisphere, styled after the
 * membrane (dark block, faint cyan glow) so the two read as one continuous
 * wall instead of two separate effects. It is deliberately cinema, not a fake
 * wall: gaps are acceptable, the silhouette is what matters. It costs a few
 * hundred displays, once per domain, instead of tens of thousands of fake
 * block packets.</p>
 *
 * <p>Main-thread only. Spawning is heavy enough to not want per-tick rebirth;
 * call {@link #show()} / {@link #hide()} for visibility and {@link #close()}
 * once the domain is over.</p>
 */
public final class ShellDome {

    /** Latitude rings, as {@code sin(latitude)} above the horizontal. */
    private static final double[] SIN_LAT = {-0.06, 0.16, 0.39, 0.61, 0.80, 0.945};

    /** Plates per ring cap; keeps huge radii from spawning thousands of displays. */
    private static final int MAX_PLATES_PER_RING = 48;
    private static final int MIN_PLATES_PER_RING = 14;
    private static final int TOTAL_PLATE_BUDGET = 280;

    /** Blinding edge of the light, deliberately dimmer than the membrane's. */
    private static final Color GLOW = Color.fromRGB(56, 168, 214);

    private final World world;
    private final double cx;
    private final double cy;
    private final double cz;
    private final double radius;
    private final Material material;
    private final List<BlockDisplay> plates = new ArrayList<>();

    private boolean spawned;
    private boolean shown;

    public ShellDome(Location center, double radius, Material material) {
        Objects.requireNonNull(center, "center");
        this.world = Objects.requireNonNull(center.getWorld(), "center world");
        Objects.requireNonNull(material, "material");
        if (!material.isBlock() || material.isAir()) {
            throw new IllegalArgumentException("shell dome material must be a non-air block");
        }
        // Keep exact center; block-aligned rounding visibly drifts the shell.
        this.cx = center.getX();
        this.cy = center.getY();
        this.cz = center.getZ();
        this.radius = Math.max(4.0, radius);
        this.material = material;
    }

    /** True once the dome plates have been spawned (they may still be scaled to zero). */
    public boolean spawned() {
        return spawned;
    }

    /** Shows the dome, spawning it first (bloom-in) if necessary. Idempotent. */
    public void show() {
        ensureSpawned();
        if (shown) return;
        shown = true;
        Vector3f pivot = new Vector3f(0.5f, 0.5f, 0.5f);
        for (int i = 0; i < plates.size(); i++) {
            BlockDisplay plate = plates.get(i);
            if (plate == null || !plate.isValid()) continue;
            TransformUtils.applyTransform(plate, TARGET_SCALES.get(i), ROTATIONS.get(i), pivot, 10);
        }
    }

    /** Hides the dome without deleting it (shrink-out). Idempotent. */
    public void hide() {
        if (!spawned || !shown) return;
        shown = false;
        Vector3f pivot = new Vector3f(0.5f, 0.5f, 0.5f);
        for (BlockDisplay plate : plates) {
            if (plate == null || !plate.isValid()) continue;
            TransformUtils.applyTransform(plate, new Vector3f(0.01f, 0.01f, 0.01f), new Quaternionf(), pivot, 6);
        }
    }

    /** Removes every plate for good. */
    public void close() {
        for (BlockDisplay plate : plates) {
            if (plate != null && plate.isValid()) plate.remove();
        }
        plates.clear();
        spawned = false;
        shown = false;
    }

    // ── internals ──────────────────────────────────────────────────────────

    private final List<Vector3f> TARGET_SCALES = new ArrayList<>();
    private final List<Quaternionf> ROTATIONS = new ArrayList<>();

    private void ensureSpawned() {
        if (spawned) return;
        spawned = true;
        Vector3f pivot = new Vector3f(0.5f, 0.5f, 0.5f);

        int budget = TOTAL_PLATE_BUDGET;
        for (double sinLat : SIN_LAT) {
            if (budget <= 0) break;
            double latitude = Math.asin(MathUtil.clamp(sinLat, -1.0, 1.0));
            double ringRadius = radius * Math.cos(latitude);
            double dy = radius * sinLat;
            if (ringRadius < 0.5) continue;

            int count = (int) Math.round((2.0 * Math.PI * ringRadius) / 6.5);
            count = Math.max(MIN_PLATES_PER_RING, Math.min(MAX_PLATES_PER_RING, count));
            count = Math.min(count, budget);
            budget -= count;

            float arc = (float) ((2.0 * Math.PI * ringRadius) / count);
            float length = arc * 1.18f;
            float height = (float) Math.max(2.2, Math.min(12.0, radius * 0.225));
            float thickness = 0.12f;
            // Lean with the sphere: plates at higher latitudes tip inward.
            float lean = (float) -latitude;

            for (int i = 0; i < count; i++) {
                double angle = (2.0 * Math.PI * i) / count;
                Location at = new Location(
                        world,
                        cx + Math.cos(angle) * ringRadius,
                        cy + dy,
                        cz + Math.sin(angle) * ringRadius);

                // Full blocks show every face; yaw only needs to keep the long
                // axis tangent so the overlap seam follows the ring.
                float yaw = (float) Math.toDegrees(angle) - 90.0f;
                Quaternionf rotation = new Quaternionf()
                        .rotateY((float) Math.toRadians(-yaw))
                        .rotateX(lean)
                        .normalize();

                Vector3f scale = new Vector3f(length, height, thickness);
                BlockDisplay plate = world.spawn(at, BlockDisplay.class, display -> {
                    display.setBlock(material.createBlockData());
                    display.setPersistent(false);
                    display.setViewRange((float) Math.max(64.0, radius + 32.0));
                    display.setBrightness(new Display.Brightness(13, 13));
                    display.setGlowing(true);
                    display.setGlowColorOverride(GLOW);
                    display.setInterpolationDelay(0);
                    display.setInterpolationDuration(10);
                    display.setTeleportDuration(0);
                    // Spawn flat-shrunk so the dome can bloom in instead of popping.
                    TransformUtils.applyTransform(display, new Vector3f(0.01f, 0.01f, 0.01f), rotation, pivot, 0);
                });

                TARGET_SCALES.add(scale);
                ROTATIONS.add(rotation);
                plates.add(plate);
            }
        }
        // Scale up over 10 ticks on the first show().
        shown = false;
    }
}
