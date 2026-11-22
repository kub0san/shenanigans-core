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

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * The restrained background of the void: translucent haze bands, a few soft white patches and two
 * faint, distant galaxies. Deliberately dim: it should hint at depth, never compete with the
 * black hole or read as a dense nebula.
 *
 * <ul>
 *   <li><b>Bands</b>: thin, wide, very low-alpha quads (5-12%) in cool blues and violets, drifting
 *       slowly back and forth. One 59-tick interpolated move per band per cycle.</li>
 *   <li><b>White patches</b>: larger, fainter squares (the lore's "white patches").</li>
 *   <li><b>Galaxies</b>: two small two-armed spirals of dim cubes that turn very slowly.</li>
 * </ul>
 */
public final class Haze {

    private static final float SEED = 0.01f;
    private static final int DRIFT = 59;
    private static final Random RANDOM = new Random(0x4A2EL);

    private static final Color[] BAND_COLORS = {
            Color.fromRGB(120, 170, 255), Color.fromRGB(150, 130, 255),
            Color.fromRGB(100, 140, 230), Color.fromRGB(185, 150, 255)};

    private static final class Band {
        TextDisplay quad;
        double theta;
        double rho;
        double height;
        double drift;
        float w;
        float h;
        int alpha;
        Color color;
        int phase;
        int flip = 1;
    }

    private static final class Star {
        BlockDisplay display;
        double rho;
        double phi;
        double omega;
        float size;
        int period;
        int phase;
    }

    private static final class Spiral {
        final List<Star> stars = new ArrayList<>();
        Location center;
        double[] axisA;
        double[] axisB;
    }

    private final World world;
    private final Location center;
    private final double radius;
    private final List<Entity> sink;
    private final List<Band> bands = new ArrayList<>();
    private final List<Spiral> spirals = new ArrayList<>();
    private int tickCount;
    private boolean shown;
    private boolean closed;

    public Haze(Location center, double radius, double quality, List<Entity> sink) {
        this.center = center.clone();
        this.world = center.getWorld();
        this.radius = radius;
        this.sink = sink;
        if (world == null) return;

        int bandCount = (int) Math.max(6, Math.min(24, Math.round(14 * quality)));
        for (int i = 0; i < bandCount; i++) buildBand(false);
        for (int i = 0; i < 4; i++) buildBand(true);
        buildSpiral(0.9, 0.5, 0.95);
        buildSpiral(-2.2, 0.35, 0.8);
    }

    private double[] dome(double theta, double rho, double height) {
        return new double[]{Math.cos(theta) * rho, height, Math.sin(theta) * rho};
    }

    private void buildBand(boolean patch) {
        Band b = new Band();
        b.theta = RANDOM.nextDouble() * Math.PI * 2.0;
        b.rho = radius * (0.7 + RANDOM.nextDouble() * 0.2);
        b.height = radius * (0.04 + RANDOM.nextDouble() * 0.6);
        b.drift = (RANDOM.nextDouble() - 0.5) * 0.18;
        if (patch) {
            b.w = (float) (radius * (0.18 + RANDOM.nextDouble() * 0.12));
            b.h = b.w * (0.5f + RANDOM.nextFloat() * 0.5f);
            b.alpha = 9;
            b.color = Color.fromRGB(235, 245, 255);
        } else {
            b.w = (float) (radius * (0.4 + RANDOM.nextDouble() * 0.45));
            b.h = 0.8f + RANDOM.nextFloat() * 2.8f;
            b.alpha = 7 + RANDOM.nextInt(7);
            b.color = BAND_COLORS[RANDOM.nextInt(BAND_COLORS.length)];
        }
        b.phase = RANDOM.nextInt(DRIFT);
        double[] p = dome(b.theta, b.rho, b.height);
        b.quad = QuadUtils.spawn(center.clone().add(p[0], p[1], p[2]), Display.Billboard.CENTER, 6.0f, false);
        if (sink != null) sink.add(b.quad);
        bands.add(b);
    }

    private void buildSpiral(double theta, double elevation, double distance) {
        Spiral sp = new Spiral();
        double rho = radius * distance;
        sp.center = center.clone().add(Math.cos(theta) * rho * Math.cos(elevation),
                Math.sin(elevation) * rho, Math.sin(theta) * rho * Math.cos(elevation));
        double tilt = RANDOM.nextDouble() * Math.PI;
        sp.axisA = new double[]{Math.cos(tilt), 0.0, Math.sin(tilt)};
        sp.axisB = new double[]{-Math.sin(tilt) * 0.5, 0.86, Math.cos(tilt) * 0.5};

        double reach = radius * 0.085;
        int perArm = 18;
        for (int arm = 0; arm < 2; arm++) {
            for (int i = 0; i < perArm; i++) {
                Star s = new Star();
                double u = (i + 1.0) / perArm;
                s.rho = reach * u;
                s.phi = arm * Math.PI + u * 3.4 + (RANDOM.nextDouble() - 0.5) * 0.35;
                s.omega = 0.1 * (1.2 - u * 0.6);
                s.size = (float) (0.12 + RANDOM.nextDouble() * 0.2) * (1.15f - (float) u * 0.5f);
                s.period = 40;
                s.phase = RANDOM.nextInt(40);
                Location at = starPoint(sp, s);
                int brightness = 8 + RANDOM.nextInt(4);
                s.display = DisplayUtils.spawnBlock(at, RANDOM.nextInt(4) == 0
                        ? Material.PURPLE_CONCRETE : Material.WHITE_CONCRETE, 6.0f, brightness);
                TransformUtils.applyTransform(s.display, SEED, SEED, SEED, 0f, 0f, 0f, 0);
                s.display.setTeleportDuration(0);
                if (sink != null) sink.add(s.display);
                sp.stars.add(s);
            }
        }
        spirals.add(sp);
    }

    private Location starPoint(Spiral sp, Star s) {
        double c = Math.cos(s.phi) * s.rho;
        double d = Math.sin(s.phi) * s.rho;
        return sp.center.clone().add(
                sp.axisA[0] * c + sp.axisB[0] * d,
                sp.axisA[1] * c + sp.axisB[1] * d,
                sp.axisA[2] * c + sp.axisB[2] * d);
    }

    /** Fades the haze in over {@code ticks}: quads grow from nothing, the stars appear. */
    public void show(int ticks) {
        if (shown || closed) return;
        shown = true;
        int t = Math.max(4, Math.min(59, ticks));
        for (Band b : bands) {
            QuadUtils.setColor(b.quad, b.color, b.alpha);
            QuadUtils.apply(b.quad, b.w, b.h, 0f, 0f, 0f, 0f, t);
        }
        for (Spiral sp : spirals) {
            for (Star s : sp.stars) {
                if (s.display.isValid()) {
                    TransformUtils.applyTransform(s.display, s.size, s.size, s.size, 0f, 0f, 0f, t);
                }
            }
        }
    }

    /** Call every tick. */
    public void tick() {
        if (closed || !shown) return;
        tickCount++;

        for (Band b : bands) {
            if ((tickCount + b.phase) % DRIFT != 0 || !b.quad.isValid()) continue;
            b.theta += b.drift * b.flip;
            b.flip = -b.flip;
            double[] p = dome(b.theta, b.rho, b.height);
            b.quad.setTeleportDuration(DRIFT);
            b.quad.teleport(center.clone().add(p[0], p[1], p[2]));
        }
        for (Spiral sp : spirals) {
            for (Star s : sp.stars) {
                if ((tickCount + s.phase) % s.period != 0 || !s.display.isValid()) continue;
                s.phi += s.omega * s.period / 20.0;
                s.display.setTeleportDuration(s.period);
                s.display.teleport(starPoint(sp, s));
            }
        }
    }

    /** Everything fades back where it stands. */
    public void collapse(int ticks) {
        if (closed) return;
        int t = Math.max(2, Math.min(59, ticks));
        shown = false;
        for (Band b : bands) QuadUtils.apply(b.quad, 0.01f, 0.01f, 0f, 0f, 0f, 0f, t);
        for (Spiral sp : spirals) {
            for (Star s : sp.stars) {
                if (s.display.isValid()) TransformUtils.applyTransform(s.display, SEED, SEED, SEED, 0f, 0f, 0f, t);
            }
        }
    }

    public void close() {
        if (closed) return;
        closed = true;
        for (Band b : bands) if (b.quad.isValid()) b.quad.remove();
        for (Spiral sp : spirals) for (Star s : sp.stars) if (s.display.isValid()) s.display.remove();
        bands.clear();
        spirals.clear();
    }
}
