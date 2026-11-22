package com.hizeric.shenaniganscore.core.display;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextColor;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.TextDisplay;
import org.bukkit.util.Transformation;
import org.bukkit.util.Vector;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

import static com.hizeric.shenaniganscore.core.display.BlockDisplayOps.place;
import static com.hizeric.shenaniganscore.core.display.TechniqueLighting.BRIGHT;
import static com.hizeric.shenaniganscore.core.display.TechniqueLighting.TINY;
import static com.hizeric.shenaniganscore.core.display.TechniqueLighting.VIEW;

/**
 * Shared display/geometry helpers for technique builders.
 *
 * <p>These bodies were previously duplicated byte-for-byte across the technique builders. Keeping
 * one copy makes visual drift a single-file fix. The seam is deliberately small: only helpers that
 * every builder used identically live here; technique-specific construction stays in its builder.
 */
public final class TechniqueDisplayOps {

    private TechniqueDisplayOps() {}

    /** Spawn a tiny block display, register it on the rig, and park it at {@code at}. */
    public static BlockDisplay spawn(DisplayRig rig, Location at, Material material, int brightness) {
        BlockDisplay d = DisplayUtils.spawnBlock(at, material, VIEW, brightness);
        rig.entities().add(d);
        place(d, at, new Quaternionf(), TINY, TINY, TINY, 0, 0);
        return d;
    }

    /** Remove an entity from the rig and from the world, tolerating null/already-removed entities. */
    public static void discard(DisplayRig rig, Entity e) {
        if (e == null) return;
        rig.entities().remove(e);
        if (e.isValid()) e.remove();
    }

    /** Spawn a centre-billboarded text glyph with no background, shadow or persistence. */
    public static TextDisplay spawnGlyph(DisplayRig rig, Location at, String glyph,
                                         TextColor color, float scale) {
        TextDisplay td = at.getWorld().spawn(at, TextDisplay.class, t -> {
            t.text(Component.text(glyph, color));
            t.setBillboard(Display.Billboard.CENTER);
            t.setDefaultBackground(false);
            t.setBackgroundColor(Color.fromARGB(0, 0, 0, 0));
            t.setShadowed(false);
            t.setSeeThrough(false);
            t.setPersistent(false);
            t.setBrightness(new Display.Brightness(BRIGHT, BRIGHT));
            t.setViewRange(VIEW);
            t.setTransformation(scaleOnly(scale, 0f));
        });
        rig.entities().add(td);
        return td;
    }

    /** Teleport a display with one-tick interpolation and a scale/roll transformation. */
    public static void moveDisplay(Display d, Location to, float scale, float rollRad) {
        if (d == null || !d.isValid()) return;
        d.setInterpolationDelay(0);
        d.setInterpolationDuration(1);
        d.setTransformation(scaleOnly(scale, rollRad));
        d.setTeleportDuration(1);
        d.teleport(to);
    }

    /** Teleport a display over {@code ticks} with a scale/roll transformation. */
    public static void flingTo(Display d, Location to, float scale, float rollRad, int ticks) {
        if (d == null || !d.isValid()) return;
        d.setInterpolationDelay(0);
        d.setInterpolationDuration(ticks);
        d.setTransformation(scaleOnly(scale, rollRad));
        d.setTeleportDuration(ticks);
        d.teleport(to);
    }

    /** Set a front-anchored box transformation on a block display without teleporting it. */
    public static void beamBox(BlockDisplay d, Quaternionf q,
                               float sx, float sy, float sz, int interp, int delay) {
        if (d == null || !d.isValid()) return;
        Vector3f off = new Vector3f(-sx / 2f, -sy / 2f, 0f);
        q.transform(off);
        d.setInterpolationDelay(delay);
        d.setInterpolationDuration(interp);
        d.setTransformation(new Transformation(off, new Quaternionf(q),
                new Vector3f(sx, sy, sz), new Quaternionf()));
    }

    /** Uniform scale plus Z-roll, with no translation or post-rotation. */
    public static Transformation scaleOnly(float scale, float rollRad) {
        return new Transformation(new Vector3f(), new Quaternionf().rotateZ(rollRad),
                new Vector3f(scale, scale, scale), new Quaternionf());
    }

    /** Linear interpolation in world space, keeping {@code a}'s world. */
    public static Location lerp(Location a, Location b, double t) {
        return new Location(a.getWorld(),
                a.getX() + (b.getX() - a.getX()) * t,
                a.getY() + (b.getY() - a.getY()) * t,
                a.getZ() + (b.getZ() - a.getZ()) * t);
    }

    /** Orthonormal basis perpendicular to {@code dir}; falls back for near-vertical directions. */
    public static Vector[] basis(Vector dir) {
        Vector n = dir.clone().normalize();
        Vector u = n.clone().crossProduct(new Vector(0, 1, 0));
        if (u.lengthSquared() < 1.0e-4) u = new Vector(1, 0, 0);
        u.normalize();
        Vector v = u.clone().crossProduct(n).normalize();
        return new Vector[]{u, v};
    }

    /** Small random XYZ rotation, using the builder's existing thread-local random. */
    public static Quaternionf randomRot(ThreadLocalRandom r) {
        return new Quaternionf().rotateXYZ(
                (float) (r.nextDouble() * Math.PI),
                (float) (r.nextDouble() * Math.PI),
                (float) (r.nextDouble() * Math.PI));
    }

    /** Wilder random XYZ tumble in [-2.5, 2.5] radians, used for scattered debris. */
    public static Quaternionf tumble(ThreadLocalRandom r) {
        return new Quaternionf().rotateXYZ(
                (float) (r.nextDouble() * 5.0 - 2.5),
                (float) (r.nextDouble() * 5.0 - 2.5),
                (float) (r.nextDouble() * 5.0 - 2.5));
    }

    /** Pose a halo stack: first two large, the rest smaller, with alternating roll. */
    public static void poseHalos(List<BlockDisplay> halos, Location c, double size1, double size2) {
        for (int i = 0; i < halos.size(); i++) {
            float size = (float) Math.max(TINY, i < 2 ? size1 : size2);
            float roll = (i % 2 == 0) ? 0f : 45f;
            float thickness = 0.02f + 0.012f * i;
            place(halos.get(i), c, new Quaternionf().rotateZ((float) Math.toRadians(roll)),
                    size, size, thickness, 1, 1);
        }
    }

    /** Pose an orbiting glint around {@code c} at the given phase and speed. */
    public static void poseGlint(BlockDisplay glint, Location c, double tick, double radius, double speed) {
        if (glint == null) return;
        double a = tick * speed;
        double rg = radius * 1.6 + 0.05;
        Location p = c.clone().add(Math.cos(a) * rg, Math.sin(a * 1.3) * rg * 0.5, Math.sin(a) * rg);
        float s = (float) Math.max(TINY, radius * 0.3);
        place(glint, p, new Quaternionf(), s, s, s, 1, 1);
    }
}
