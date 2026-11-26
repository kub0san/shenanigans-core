package com.hizeric.shenaniganscore.core.display;

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
 * membrane (dark block, lit at full brightness) so the two read as one
 * continuous wall instead of two separate effects. It is deliberately cinema,
 * not a fake wall: gaps are acceptable, the silhouette is what matters. It costs a few
 * hundred displays, once per domain, instead of tens of thousands of fake
 * block packets. The plate plan is computed once; the entities themselves are
 * spawned in small batches across successive ticks so one tick never creates
 * the whole dome.</p>
 *
 * <p>Main-thread only. Call {@link #show()} / {@link #hide()} for visibility and
 * {@link #close()} once the domain is over.</p>
 */
public final class ShellDome {

    /** Latitude rings, as {@code sin(latitude)} above the horizontal. */
    private static final double[] SIN_LAT = {-0.06, 0.16, 0.39, 0.61, 0.80, 0.945};

    /** Plates per ring cap; keeps huge radii from spawning thousands of displays. */
    private static final int MAX_PLATES_PER_RING = 48;
    private static final int MIN_PLATES_PER_RING = 14;
    private static final int TOTAL_PLATE_BUDGET = 280;

    private static final Vector3f TINY_SCALE = new Vector3f(0.01f, 0.01f, 0.01f);
    private static final Quaternionf NO_ROTATION = new Quaternionf();
    private static final Vector3f PIVOT = new Vector3f(0.5f, 0.5f, 0.5f);

    private final World world;
    private final double radius;
    private final Material material;
    /** Immutable geometry plan; spawning it is spread across ticks. */
    private final List<Plate> plan = new ArrayList<>();
    private final List<BlockDisplay> plates = new ArrayList<>();

    private int spawnedCount;
    private boolean shown;

    private record Plate(Location at, Vector3f scale, Quaternionf rotation) { }

    public ShellDome(Location center, double radius, Material material) {
        Objects.requireNonNull(center, "center");
        this.world = Objects.requireNonNull(center.getWorld(), "center world");
        Objects.requireNonNull(material, "material");
        if (!material.isBlock() || material.isAir()) {
            throw new IllegalArgumentException("shell dome material must be a non-air block");
        }
        // Keep exact center; block-aligned rounding visibly drifts the shell.
        double cx = center.getX();
        double cy = center.getY();
        double cz = center.getZ();
        this.radius = Math.max(4.0, radius);
        this.material = material;
        buildPlan(cx, cy, cz, this.radius);
    }

    /** True once every plate in the plan has been spawned (they may still be scaled to zero). */
    public boolean spawned() {
        return spawnedCount >= plan.size();
    }

    /**
     * Shows the dome, spawning its next batch of plates (bloom-in) as needed.
     * Idempotent for plates already shown; repeated calls finish the remaining
     * batches without changing their final geometry.
     */
    public void show() {
        boolean firstShow = !shown;
        shown = true;

        int added = spawnNextBatch();
        if (added == 0 && !firstShow) return;

        int from = firstShow ? 0 : plates.size() - added;
        for (int i = from; i < plates.size(); i++) {
            BlockDisplay plate = plates.get(i);
            if (plate == null || !plate.isValid()) continue;
            Plate spec = plan.get(i);
            TransformUtils.applyTransform(plate, spec.scale(), spec.rotation(), PIVOT, 10);
        }
    }

    /** Hides the dome without deleting it (shrink-out). Idempotent. */
    public void hide() {
        if (!shown) return;
        shown = false;
        for (BlockDisplay plate : plates) {
            if (plate == null || !plate.isValid()) continue;
            TransformUtils.applyTransform(plate, TINY_SCALE, NO_ROTATION, PIVOT, 6);
        }
    }

    /** Removes every plate for good. */
    public void close() {
        for (BlockDisplay plate : plates) {
            if (plate != null && plate.isValid()) plate.remove();
        }
        plates.clear();
        spawnedCount = 0;
        shown = false;
    }

    // ── internals ──────────────────────────────────────────────────────────

    /** Computes the same plate layout the one-tick spawn used to create. */
    private void buildPlan(double cx, double cy, double cz, double radius) {
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

                plan.add(new Plate(at, new Vector3f(length, height, thickness), rotation));
            }
        }
    }

    /** Materialises at most {@link ShellDomePlan#PLATES_PER_TICK} plates. */
    private int spawnNextBatch() {
        int count = ShellDomePlan.batch(plan.size(), spawnedCount);
        for (int i = 0; i < count; i++) {
            Plate spec = plan.get(spawnedCount + i);
            BlockDisplay plate = world.spawn(spec.at(), BlockDisplay.class, display -> {
                display.setBlock(material.createBlockData());
                display.setPersistent(false);
                display.setViewRange((float) Math.max(64.0, radius + 32.0));
                display.setBrightness(new Display.Brightness(13, 13));
                display.setInterpolationDelay(0);
                display.setInterpolationDuration(10);
                display.setTeleportDuration(0);
                // Spawn flat-shrunk so the dome can bloom in instead of popping.
                TransformUtils.applyTransform(display, TINY_SCALE, spec.rotation(), PIVOT, 0);
            });
            plates.add(plate);
        }
        spawnedCount += count;
        return count;
    }

}
