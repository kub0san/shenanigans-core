package com.hizeric.shenaniganscore.core.display;

import com.hizeric.shenaniganscore.ShenanigansCore;
import com.hizeric.shenaniganscore.core.util.Fractures;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.block.data.BlockData;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.Objects;
import java.util.function.Consumer;
import java.util.logging.Level;
import com.hizeric.shenaniganscore.core.util.MathUtil;

/**
 * Visual-only closed-domain barrier shell.
 *
 * <p>Closed domains draw no server blocks for the shell. The rim is
 * represented by display-entity cinema (membrane/particles) plus an optional,
 * closed-domain-only client-side blackout lip via PacketEvents
 * ({@code <domain>.overlay.blackout-lip}). Real server blocks are used only by
 * {@code DomainFloor} for the platform beneath the domain.</p>
 *
 * <p>The public API is preserved for existing domain sessions (formation
 * progress, floor lead-in timing, shell/floor progress, front offset, and
 * close/shatter lifecycle).</p>
 */
public class Barrier implements AutoCloseable {

    private static final int MAX_RADIUS = 200;
    /** Portion of the intro reserved for the domain-specific floor/base animation. */
    public static final double FLOOR_LEAD_IN = 0.36;

    private final World world;
    private final int cx;
    private final int cy;
    private final int cz;
    private final int radius;
    private final boolean blackoutInterior;
    private final BlockData shellBlock;
    private final BlockData interiorBlock;
    private final Material membraneMaterial;

    /** The smooth leading edge that rides the wall's front. */
    private BarrierMembrane membrane;

    /** The persistent dome body (visible once the shell is closed). */
    private ShellDome dome;

    /** Cheap rim shimmer while the shell is fully formed. */
    private int fullRimTicks;

    /** The block offset (from the centre) at which the shell starts forming. */
    private double formationStartDy;
    /** True once the shell has been anchored on a floor. */
    private boolean anchoredOnFloor;
    /** The radius the domain's floor has reached. */
    private double floorFrontRadius;

    private boolean prepared;
    private boolean scanComplete = true;
    private boolean closed;
    private boolean shattering;

    private double target;

    public Barrier(Location center, double radius, boolean blackoutInterior,
                   Material shellMat, Material interiorMat) {
        this(center, radius, blackoutInterior, shellMat, interiorMat, null);
    }

    public Barrier(Location center, double radius, boolean blackoutInterior,
                   Material shellMat, Material interiorMat, Material membraneMat) {
        Objects.requireNonNull(center, "center");
        if (center.getWorld() == null) {
            throw new IllegalArgumentException("Barrier centre must belong to a world");
        }
        if (!Double.isFinite(center.getX()) || !Double.isFinite(center.getY())
                || !Double.isFinite(center.getZ())) {
            throw new IllegalArgumentException("Barrier centre coordinates must be finite");
        }
        if (!Double.isFinite(radius) || radius < 1.0 || radius > MAX_RADIUS) {
            throw new IllegalArgumentException("radius must be between 1 and " + MAX_RADIUS);
        }

        this.world = center.getWorld();
        this.cx = center.getBlockX();
        this.cy = center.getBlockY();
        this.cz = center.getBlockZ();
        this.radius = (int) Math.floor(radius);
        this.blackoutInterior = blackoutInterior;
        this.formationStartDy = -this.radius;
        this.shellBlock = createBlockData(shellMat, "shellMat");
        this.interiorBlock = createBlockData(interiorMat, "interiorMat");

        // Use an explicit membrane material if provided; otherwise fall back to shell material
        // for a continuous visual curtain.
        if (membraneMat != null && membraneMat.isBlock() && !membraneMat.isAir()) {
            this.membraneMaterial = membraneMat;
        } else if (shellMat != null && shellMat.isBlock() && !shellMat.isAir()) {
            this.membraneMaterial = shellMat;
        } else {
            this.membraneMaterial = Material.BLACK_CONCRETE;
        }
    }

    /** Anchors the shell on a real floor. Call before {@link #prepare()} when possible. */
    public void setShellFloor(int floorSurfaceY) {
        requireMainThread();
        if (closed) return;
        int dy = Math.max(-radius, Math.min(radius, floorSurfaceY - cy));
        this.formationStartDy = dy;
        this.anchoredOnFloor = true;
    }

    /** Records how far the domain's real floor has reached. */
    public void setFloorFrontRadius(double floorRadius) {
        if (!Double.isFinite(floorRadius) || floorRadius < 0.0) {
            return;
        }
        this.floorFrontRadius = floorRadius;
    }

    /** The last floor front radius reported by the domain. */
    public double floorFrontRadius() {
        return floorFrontRadius;
    }

    /** True once the barrier can be animated (visual-only: immediately ready). */
    public boolean ready() {
        return scanComplete;
    }

    /** Current normalized formation progress, in the range 0–1. */
    public double progress() {
        return target;
    }

    /** Normalized 0–1 progress for the domain-specific floor/base animation. */
    public double floorProgress() {
        return MathUtil.clamp(target / FLOOR_LEAD_IN, 0.0, 1.0);
    }

    /** Normalized 0–1 progress for the spherical shell after the floor lead-in. */
    public double shellProgress() {
        return MathUtil.clamp((target - FLOOR_LEAD_IN) / (1.0 - FLOOR_LEAD_IN), 0.0, 1.0);
    }

    /** Sets the normalized formation front. Zero is hidden; one is fully formed. */
    public void setTarget(double front) {
        requireMainThread();
        if (closed) return;
        if (!Double.isFinite(front)) {
            throw new IllegalArgumentException("front must be finite");
        }
        this.target = MathUtil.clamp(front, 0.0, 1.0);
    }

    /** Alias that makes the intended use clearer in session timelines. */
    public void setFormationProgress(double progress) {
        setTarget(progress);
    }

    /** The latitude of the wall's leading edge, as a block offset from the centre. */
    public double frontOffset() {
        return frontDy(shellProgress());
    }

    private double frontDy(double shellProgress) {
        double p = MathUtil.clamp(shellProgress, 0.0, 1.0);
        if (anchoredOnFloor) {
            double theta0 = Math.asin(MathUtil.clamp(formationStartDy / (double) radius, -1.0, 1.0));
            double span = Math.PI / 2.0 - theta0;
            if (span <= 1.0e-6) {
                return radius;
            }
            return radius * Math.sin(theta0 + span * p);
        }
        return -radius * Math.cos(Math.PI * p);
    }

    /**
     * Prepares the visual shell. No terrain scan/fake-block capture is performed.
     */
    public void prepare() {
        requireMainThread();
        if (prepared || closed) {
            return;
        }
        prepared = true;
        scanComplete = true;
        ensureMembrane();
    }

    /** Called once per session tick while the barrier is active. */
    public void tick() {
        requireMainThread();
        if (closed || shattering) {
            return;
        }
        if (!prepared) {
            prepare();
        }
        updateMembrane();
        spawnShellAtmosphere();
    }

    private void ensureMembrane() {
        if (membrane != null || closed || world == null) return;
        try {
            int plates = (int) Math.ceil((2.0 * Math.PI * this.radius) / 6.0);
            plates = Math.max(16, Math.min(96, plates));
            Location center = new Location(world, cx, cy, cz);
            membrane = new BarrierMembrane(center, this.radius, membraneMaterial, plates,
                    (float) (this.radius + 32));
        } catch (Throwable t) {
            ShenanigansCore plugin = ShenanigansCore.getInstance();
            if (plugin != null) {
                plugin.getLogger().log(Level.WARNING, "Failed to create barrier membrane visuals", t);
            }
            membrane = null;
        }
        ensureDome();
    }

    /** Creates the persistent dome shell. Missing this is the whole "no barrier" bug. */
    private void ensureDome() {
        if (dome != null || closed || world == null) return;
        try {
            Location center = new Location(world, cx, cy, cz);
            dome = new ShellDome(center, this.radius, shellMaterial());
        } catch (Throwable t) {
            ShenanigansCore plugin = ShenanigansCore.getInstance();
            if (plugin != null) {
                plugin.getLogger().log(Level.WARNING, "Failed to create shell dome visuals", t);
            }
            dome = null;
        }
    }

    /** The block the shell is built from; membranes use their own, but the dome "keeps" this one. */
    private Material shellMaterial() {
        return shellBlock == null ? Material.BLACK_CONCRETE : shellBlock.getMaterial();
    }

    private void updateMembrane() {
        double shell = shellProgress();
        if (shell <= 0.0) {
            if (membrane != null) membrane.hide();
            if (dome != null) dome.hide();
            return;
        }
        if (membrane != null) {
            double dy = frontDy(shell);
            double inner = Math.max(0.0, radius - 1.8);
            double ring = Math.sqrt(Math.max(0.0, inner * inner - dy * dy));
            membrane.update(dy, ring);
        }

        // The dome owns the enclosure once the shell is complete; the membrane
        // front retires to the crown by then anyway.
        if (dome != null) {
            if (shell >= 0.98) {
                dome.show();
            } else {
                dome.hide();
            }
        }
    }

    /**
     * Lightweight visual atmosphere to imply a closed shell boundary without
     * fake terrain blocks. Kept intentionally sparse for performance.
     */
    private void spawnShellAtmosphere() {
        if (world == null) return;
        double shell = shellProgress();
        if (shell <= 0.02) return;

        if (shell >= 0.995) {
            // The dome owns the enclosure; keep a cheap breathing rim so the
            // boundary feels alive while the domain is open.
            if ((fullRimTicks++ % 14) == 0) {
                Particle.DustOptions dim = new Particle.DustOptions(Color.fromRGB(40, 120, 180), 0.7f);
                int points = 8;
                double t = (System.currentTimeMillis() / 700.0) % (Math.PI * 2.0);
                for (int i = 0; i < points; i++) {
                    double a = t + (Math.PI * 2.0 * i) / points;
                    world.spawnParticle(Particle.DUST,
                            cx + Math.cos(a) * radius * 0.985,
                            cy + 0.4,
                            cz + Math.sin(a) * radius * 0.985,
                            1, 0.02, 0.06, 0.02, 0.0, dim);
                }
            }
            return;
        }
        fullRimTicks = 0;

        double dy = frontDy(shell);
        double ringR = Math.sqrt(Math.max(0.0, (double) radius * radius - dy * dy));
        if (ringR < 1.0) return;

        // A few particles along the active front and occasional boundary hints.
        int points = Math.max(8, Math.min(24, (int) (ringR / 3.0)));
        double baseAngle = (System.currentTimeMillis() / 180.0) % (Math.PI * 2.0);
        for (int i = 0; i < points; i++) {
            double a = baseAngle + (Math.PI * 2.0 * i) / points;
            double x = cx + Math.cos(a) * ringR;
            double z = cz + Math.sin(a) * ringR;
            double y = cy + dy + 0.2;
            world.spawnParticle(Particle.END_ROD, x, y, z, 1, 0.02, 0.05, 0.02, 0.0);
            if ((i & 3) == 0) {
                world.spawnParticle(Particle.DUST, x, y, z, 1, 0.0, 0.0, 0.0, 0.0,
                        new Particle.DustOptions(Color.fromRGB(80, 220, 255), 0.9f));
            }
        }

        // When the shell is mostly formed, hint at a dome boundary with sparse dust.
        if (shell > 0.85) {
            int halo = 10;
            for (int i = 0; i < halo; i++) {
                double a = baseAngle * 0.7 + (Math.PI * 2.0 * i) / halo;
                double rr = radius * 0.98;
                double px = cx + Math.cos(a) * rr * 0.7;
                double pz = cz + Math.sin(a) * rr * 0.7;
                double py = cy + Math.sin(a * 1.3) * rr * 0.25;
                world.spawnParticle(Particle.DUST, px, py, pz, 1, 0.0, 0.0, 0.0, 0.0,
                        new Particle.DustOptions(Color.fromRGB(40, 120, 180), 0.7f));
            }
        }
    }

    /**
     * Starts a visual shatter outro. No fake blocks are restored because none are used.
     */
    public boolean shatter() {
        return shatter(null);
    }

    /**
     * Starts the visual fracture outro and optionally invokes an effect at each of the
     * twelve fracture origins.
     */
    public boolean shatter(Consumer<Location> fractureOriginEffect) {
        requireMainThread();
        if (closed || shattering) {
            return false;
        }
        closed = true;
        shattering = true;
        target = 0.0;

        runFractureOriginEffect(fractureOriginEffect);
        spawnShatterBurst();

        if (membrane != null) {
            membrane.hide();
        }
        if (dome != null) {
            dome.hide();
        }

        // Let particles/membrane interpolation settle briefly, then fully close visuals.
        ShenanigansCore plugin = ShenanigansCore.getInstance();
        if (plugin == null || !plugin.isEnabled()) {
            finishShatter();
            return true;
        }
        try {
            new BukkitRunnable() {
                @Override
                public void run() {
                    finishShatter();
                }
            }.runTaskLater(plugin, 8L);
        } catch (RuntimeException ex) {
            finishShatter();
        }
        return true;
    }

    private void runFractureOriginEffect(Consumer<Location> fractureOriginEffect) {
        if (fractureOriginEffect == null || world == null) return;
        ShenanigansCore plugin = ShenanigansCore.getInstance();
        Location center = new Location(world, cx, cy, cz);
        for (Location origin : Fractures.origins(center, radius)) {
            try {
                fractureOriginEffect.accept(origin);
            } catch (RuntimeException exception) {
                if (plugin != null) {
                    plugin.getLogger().log(Level.WARNING,
                            "A barrier fracture-origin effect failed.", exception);
                }
            }
        }
    }

    private void spawnShatterBurst() {
        if (world == null) return;
        Location center = new Location(world, cx, cy, cz);
        for (int i = 0; i < 12; i++) {
            Location origin = Fractures.origin(center, radius, i);
            world.spawnParticle(Particle.END_ROD, origin, 10, 0.3, 0.3, 0.3, 0.02);
            world.spawnParticle(Particle.ELECTRIC_SPARK, origin, 8, 0.35, 0.35, 0.35, 0.01);
            world.spawnParticle(Particle.SMOKE, origin, 6, 0.25, 0.25, 0.25, 0.01);
        }
        world.spawnParticle(Particle.FLASH, center, 1, 0, 0, 0, 0);
    }

    private void finishShatter() {
        if (membrane != null) {
            membrane.close();
            membrane = null;
        }
        if (dome != null) {
            dome.close();
            dome = null;
        }
        shattering = false;
    }

    /**
     * Starts a visual close. Idempotent.
     */
    @Override
    public void close() {
        requireMainThread();
        if (closed && membrane == null) {
            return;
        }
        closed = true;
        target = 0.0;
        if (membrane != null) {
            membrane.close();
            membrane = null;
        }
        if (dome != null) {
            dome.close();
            dome = null;
        }
        shattering = false;
    }

    private static BlockData createBlockData(Material material, String parameterName) {
        Objects.requireNonNull(material, parameterName);
        if (!material.isBlock() || material.isAir()) {
            throw new IllegalArgumentException(parameterName + " must be a non-air block material");
        }
        return material.createBlockData();
    }

    private static void requireMainThread() {
        if (!Bukkit.isPrimaryThread()) {
            throw new IllegalStateException("Barrier lifecycle methods must be called on the server thread");
        }
    }
}
