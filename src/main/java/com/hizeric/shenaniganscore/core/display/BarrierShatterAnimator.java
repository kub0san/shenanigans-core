package com.hizeric.shenaniganscore.core.display;

import com.hizeric.shenaniganscore.ShenanigansCore;
import com.hizeric.shenaniganscore.core.util.Fractures;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.scheduler.BukkitRunnable;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.function.BooleanSupplier;
import java.util.logging.Level;
import com.hizeric.shenaniganscore.core.util.MathUtil;

/**
 * Client-side choreography for the shatter that ends a closed domain.
 *
 * <p>This is a <b>presentation</b> layer, not a geometry layer. It reads
 * {@link Fractures} to decide <i>where</i> the twelve fracture fronts are, but it
 * never creates cracks itself: the fake blocks of {@link Barrier} are restored
 * by that class, and the real platform is restored by
 * {@code core.terrain.DomainFloor}. The animator only adds the beats around
 * those restores:</p>
 *
 * <ol>
 *     <li><b>Freeze</b> — the shell stays fully formed for a short hold.</li>
 *     <li><b>Ignite</b> — the twelve origins flash with particles and sound.</li>
 *     <li><b>Crack</b> — seam points brighten in fracture-arrival order.</li>
 *     <li><b>Separate</b> — the shell's own shatter starts, ghost fragments
 *         rotate and travel outward, and the floor fronts follow a few ticks
 *         later.</li>
 *     <li><b>Dissolve</b> — the fragments shrink away.</li>
 * </ol>
 *
 * <p>Everything here is visual. The only server-side entities are twelve
 * short-lived {@link BlockDisplay} fragments; no terrain is edited and no fake
 * block is sent by this class. All callbacks are invoked on the main thread and
 * must be cheap.</p>
 *
 * <p>Typical integration (the domain session owns the instance):</p>
 * <pre>{@code
 * BarrierShatterAnimator animator = new BarrierShatterAnimator(
 *         center, radius, Material.BLACK_CONCRETE.createBlockData(), floorY);
 * animator.play(shell::shatter, floor.platform()::shatter, this::close);
 * }</pre>
 *
 * <p>{@link #play} returns {@code false} when the presentation cannot start
 * (already running, already closed, or no plugin instance); the caller should
 * fall back to an immediate cleanup in that case.</p>
 */
public final class BarrierShatterAnimator implements AutoCloseable {

    /** Ticks the fully formed shell holds before anything moves. */
    public static final int FREEZE_TICKS = 6;
    /** Ticks the origins burn before cracks appear. */
    public static final int IGNITE_TICKS = 4;
    /** Ticks over which the seam network brightens. */
    public static final int CRACK_TICKS = 14;
    /** Ticks the shards travel outward and rotate. */
    public static final int SEPARATION_TICKS = 26;
    /** Ticks the ghost fragments take to shrink away. */
    public static final int DISSOLVE_TICKS = 8;
    /** Ticks between the shell's shatter and the floor's. */
    public static final int FLOOR_SHATTER_LAG = 4;

    /** Tick at which {@code shellShatter} is invoked. */
    public static final int SHELL_SHATTER_TICK = FREEZE_TICKS + IGNITE_TICKS + CRACK_TICKS;
    /** Tick at which {@code floorShatter} is invoked. */
    public static final int FLOOR_SHATTER_TICK = SHELL_SHATTER_TICK + FLOOR_SHATTER_LAG;
    /** Total presentation length in ticks. */
    public static final int DURATION_TICKS = SHELL_SHATTER_TICK + SEPARATION_TICKS + DISSOLVE_TICKS;

    private static final int SHELL_BANDS = 56;
    private static final double SHELL_SEAM_THRESHOLD = 0.82;
    /** Per-tick cap on crack particles, so a large shell cannot flood clients. */
    private static final int CRACK_PARTICLES_PER_TICK = 260;
    private static final float SHARD_VIEW_RANGE = 192.0f;
    private static final float SHARD_SCALE = 1.35f;

    private record CrackPoint(Location location, double arrival) {
    }

    private final ShenanigansCore plugin;
    private final World world;
    private final Location center;
    private final double radius;
    /** The platform's surface Y, or {@code Double.NaN} when there is none. */
    private final double floorY;
    private final BlockData shardBlock;

    /** Sorted shell seam samples, ready to be revealed in arrival order. */
    private final double[] crackX;
    private final double[] crackY;
    private final double[] crackZ;
    private final double[] crackArrival;

    private BlockDisplay[] shards;
    private BukkitRunnable task;
    private int tick;
    private int crackCursor;
    private boolean shellStarted;
    private boolean floorStarted;
    private boolean running;
    private boolean finished;
    private boolean closed;

    /**
     * @param center     barrier centre; must belong to a loaded world
     * @param radius     barrier radius, between 1 and 200
     * @param shardBlock block used for the twelve ghost fragments
     * @param floorY     the platform surface Y, or {@code Double.NaN} for no floor
     */
    public BarrierShatterAnimator(Location center, double radius,
                                  BlockData shardBlock, double floorY) {
        Objects.requireNonNull(center, "center");
        if (center.getWorld() == null) {
            throw new IllegalArgumentException("Shatter animator centre must belong to a world");
        }
        if (!Double.isFinite(center.getX()) || !Double.isFinite(center.getY())
                || !Double.isFinite(center.getZ())) {
            throw new IllegalArgumentException("Shatter animator centre coordinates must be finite");
        }
        if (!Double.isFinite(radius) || radius < 1.0 || radius > 200.0) {
            throw new IllegalArgumentException("Shatter animator radius must be between 1 and 200");
        }
        Objects.requireNonNull(shardBlock, "shardBlock");

        this.plugin = ShenanigansCore.getInstance();
        this.world = center.getWorld();
        this.center = center.clone();
        this.radius = radius;
        this.floorY = floorY;
        this.shardBlock = shardBlock;

        List<CrackPoint> points = new ArrayList<>();
        for (int band = 0; band < SHELL_BANDS; band++) {
            double ny = -1.0 + (2.0 * (band + 0.5)) / SHELL_BANDS;
            double ring = Math.sqrt(Math.max(0.0, 1.0 - ny * ny));
            int steps = Math.max(8, (int) Math.ceil(ring * 88.0));
            for (int step = 0; step < steps; step++) {
                double angle = (2.0 * Math.PI * step) / steps;
                double nx = ring * Math.cos(angle);
                double nz = ring * Math.sin(angle);
                Fractures.Sample sample = Fractures.sample(nx, ny, nz);
                if (sample.seamProximity() < SHELL_SEAM_THRESHOLD) continue;

                Location point = center.clone().add(nx * radius, ny * radius, nz * radius);
                if (Double.isFinite(floorY) && point.getY() < floorY + 0.5) continue;
                points.add(new CrackPoint(point, sample.arrivalFraction()));
            }
        }
        points.sort(Comparator.comparingDouble(CrackPoint::arrival));

        int count = points.size();
        crackX = new double[count];
        crackY = new double[count];
        crackZ = new double[count];
        crackArrival = new double[count];
        for (int i = 0; i < count; i++) {
            CrackPoint point = points.get(i);
            crackX[i] = point.location().getX();
            crackY[i] = point.location().getY();
            crackZ[i] = point.location().getZ();
            crackArrival[i] = point.arrival();
        }
    }

    /**
     * Starts the presentation.
     *
     * @param shellShatter  called at {@link #SHELL_SHATTER_TICK}; should return
     *                      whether the barrier accepted the shatter
     * @param floorShatter  called at {@link #FLOOR_SHATTER_TICK}; should return
     *                      whether the platform accepted the shatter
     * @param onFinished    called once at the end (or on failure); used to run
     *                      the caller's cleanup
     * @return true when the presentation was accepted
     */
    public boolean play(BooleanSupplier shellShatter, BooleanSupplier floorShatter,
                        Runnable onFinished) {
        if (running || closed || plugin == null || !plugin.isEnabled()) {
            return false;
        }
        running = true;
        tick = 0;
        try {
            task = new BukkitRunnable() {
                @Override
                public void run() {
                    step(shellShatter, floorShatter, onFinished);
                }
            };
            task.runTaskTimer(plugin, 0L, 1L);
        } catch (RuntimeException exception) {
            plugin.getLogger().log(Level.WARNING,
                    "Could not schedule the barrier shatter presentation.", exception);
            running = false;
            if (onFinished != null) {
                onFinished.run();
            }
            return false;
        }
        return true;
    }

    private void step(BooleanSupplier shellShatter, BooleanSupplier floorShatter,
                      Runnable onFinished) {
        try {
            int t = tick++;

            if (t == 0) {
                freezeHold();
            } else if (t == FREEZE_TICKS) {
                igniteOrigins();
            }

            if (t > FREEZE_TICKS && t <= SHELL_SHATTER_TICK) {
                drawCracks((t - FREEZE_TICKS) / (double) CRACK_TICKS);
            }

            if (t == SHELL_SHATTER_TICK && !shellStarted) {
                shellStarted = true;
                boolean accepted = safe(shellShatter);
                if (accepted) {
                    spawnShards();
                }
            }

            if (t == FLOOR_SHATTER_TICK && !floorStarted) {
                floorStarted = true;
                if (safe(floorShatter)) {
                    floorOrigins();
                }
            }

            if (t >= SHELL_SHATTER_TICK) {
                double separation = Math.min(1.0,
                        (t - SHELL_SHATTER_TICK) / (double) SEPARATION_TICKS);
                moveShards(separation);
            }

            if (t >= DURATION_TICKS) {
                finish(onFinished);
            }
        } catch (Throwable throwable) {
            plugin.getLogger().log(Level.WARNING,
                    "Barrier shatter presentation failed; closing the domain now.", throwable);
            finish(onFinished);
        }
    }

    private boolean safe(BooleanSupplier action) {
        if (action == null) return false;
        try {
            return action.getAsBoolean();
        } catch (RuntimeException exception) {
            plugin.getLogger().log(Level.WARNING, "A barrier shatter callback failed.", exception);
            return false;
        }
    }

    /** The hold: the shell is still whole, but the world goes quiet around it. */
    private void freezeHold() {
        world.playSound(center, Sound.BLOCK_BEACON_POWER_SELECT, 1.0f, 0.45f);
        world.playSound(center, Sound.ENTITY_WARDEN_HEARTBEAT, 0.8f, 0.55f);
        world.spawnParticle(Particle.SQUID_INK, center, 24, radius * 0.5, radius * 0.5,
                radius * 0.5, 0.01);
    }

    /** Flashes at the twelve fracture origins; no shard fragments yet. */
    private void igniteOrigins() {
        for (Location origin : Fractures.origins(center, radius)) {
            world.spawnParticle(Particle.END_ROD, origin, 10, 0.3, 0.3, 0.3, 0.02);
            world.spawnParticle(Particle.ELECTRIC_SPARK, origin, 8, 0.35, 0.35, 0.35, 0.01);
            world.spawnParticle(Particle.SMOKE, origin, 5, 0.28, 0.28, 0.28, 0.008);
        }
        world.playSound(center, Sound.BLOCK_GLASS_BREAK, 0.8f, 0.7f);
    }

    /** Reveals seam points whose fracture arrival has passed. */
    private void drawCracks(double progress) {
        int spawned = 0;
        while (crackCursor < crackArrival.length
                && crackArrival[crackCursor] <= progress
                && spawned < CRACK_PARTICLES_PER_TICK) {
            Location point = new Location(world, crackX[crackCursor], crackY[crackCursor],
                    crackZ[crackCursor]);
            world.spawnParticle(Particle.END_ROD, point, 1, 0.05, 0.05, 0.05, 0.0);
            world.spawnParticle(Particle.ELECTRIC_SPARK, point, 1, 0.08, 0.08, 0.08, 0.004);
            crackCursor++;
            spawned++;
        }
    }

    /** Spawns one ghost fragment per shard, sitting on the shell's surface. */
    private void spawnShards() {
        shards = new BlockDisplay[Fractures.FRONT_COUNT];
        Quaternionf identity = new Quaternionf();

        for (int i = 0; i < Fractures.FRONT_COUNT; i++) {
            Location origin = Fractures.origin(center, radius, i);
            if (Double.isFinite(floorY) && origin.getY() < floorY) {
                continue;
            }

            BlockDisplay display = DisplayUtils.spawnBlock(
                    origin, shardBlock.getMaterial(), SHARD_VIEW_RANGE, 12);
            display.setGlowing(true);
            display.setGlowColorOverride(Color.fromRGB(0x4D, 0xF0, 0x8A));
            TransformUtils.applyTransform(display,
                    new Vector3f(SHARD_SCALE, SHARD_SCALE, SHARD_SCALE),
                    identity,
                    new Vector3f(0.5f, 0.5f, 0.5f),
                    0);
            shards[i] = display;
        }
    }

    /** Dust and sparks where the real platform's twelve fronts begin. */
    private void floorOrigins() {
        if (!Double.isFinite(floorY)) return;
        for (Location origin : Fractures.floorOrigins(center, radius)) {
            Location point = new Location(world, origin.getX(), floorY + 0.15, origin.getZ());
            world.spawnParticle(Particle.SQUID_INK, point, 8, 0.4, 0.2, 0.4, 0.01);
            world.spawnParticle(Particle.ELECTRIC_SPARK, point, 5, 0.35, 0.2, 0.35, 0.008);
        }
        world.playSound(new Location(world, center.getX(), floorY, center.getZ()),
                Sound.BLOCK_GLASS_BREAK, 1.0f, 0.6f);
    }

    /**
     * Moves each fragment outward along its shard direction while it rotates
     * around the shard's tangent axis. Ghosts are removed by {@link #finish}.
     */
    private void moveShards(double separation) {
        if (shards == null) return;

        double eased = MathUtil.smoothstep(separation);
        int dissolveStart = DURATION_TICKS - DISSOLVE_TICKS;
        double dissolve = Math.min(1.0,
                Math.max(0.0, (tick - dissolveStart) / (double) DISSOLVE_TICKS));

        for (int i = 0; i < shards.length; i++) {
            BlockDisplay display = shards[i];
            if (display == null || !display.isValid()) continue;

            org.bukkit.util.Vector direction = Fractures.direction(i);
            org.bukkit.util.Vector tangent = Fractures.tangentDirection(i);

            double distance = radius * (1.0 + eased * 0.45);
            Location target = center.clone()
                    .add(direction.getX() * distance, direction.getY() * distance,
                            direction.getZ() * distance)
                    .add(tangent.getX() * Math.sin(eased * Math.PI) * 0.6,
                            tangent.getY() * Math.sin(eased * Math.PI) * 0.6,
                            tangent.getZ() * Math.sin(eased * Math.PI) * 0.6);
            display.teleport(target);

            float angle = (float) Math.toDegrees(
                    Fractures.rotationSign(i) * eased * 110.0);
            Quaternionf rotation = new Quaternionf().rotateAxis((float) Math.toRadians(angle),
                    (float) tangent.getX(), (float) tangent.getY(), (float) tangent.getZ());
            float scale = (float) Math.max(0.05, SHARD_SCALE * (1.0 - dissolve));
            TransformUtils.applyTransform(display,
                    new Vector3f(scale, scale, scale),
                    rotation,
                    new Vector3f(0.5f, 0.5f, 0.5f),
                    0);

            if (dissolve <= 0.0 && i % 2 == 0) {
                world.spawnParticle(Particle.END_ROD, target, 1, 0.15, 0.15, 0.15, 0.0);
            }
        }
    }

    private void finish(Runnable onFinished) {
        if (finished) return;
        finished = true;
        running = false;
        if (task != null) {
            task.cancel();
            task = null;
        }
        removeShards();
        if (onFinished != null) {
            onFinished.run();
        }
    }

    private void removeShards() {
        if (shards == null) return;
        for (BlockDisplay display : shards) {
            if (display != null && display.isValid()) {
                display.remove();
            }
        }
        shards = null;
    }

    @Override
    public void close() {
        closed = true;
        running = false;
        if (task != null) {
            task.cancel();
            task = null;
        }
        removeShards();
    }
}
