package com.hizeric.shenaniganscore.origins.jujutsukaisen.domainexpansions.timecellmoonpalace;

import com.hizeric.shenaniganscore.core.particle.ParticleUtils;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.util.Vector;

/**
 * Light effects. The eye is a <i>projector</i>: Projection Sorcery, literally.
 *
 * <ul>
 *   <li><b>beam</b>: a flickering cone of light from the pupil down the path. One frame in
 *       four is dark, so it reads as film passing a lamp, not a spotlight.</li>
 *   <li><b>scanLine</b>: a curtain of alternating white/black frames sweeping from the eye
 *       across the arena at domain start; victims are injected as it passes them.</li>
 *   <li><b>shockRing</b>: a ring on the floor that advances in whole frames.</li>
 *   <li><b>flash</b>: a single bright frame.</li>
 * </ul>
 *
 * <p>All stateless, particle-only, and bounded per call (a beam pulse is ~80 particles).</p>
 */
final class Projector {

    private Projector() {}

    /** One pulse of the projector beam from {@code from} (the pupil) toward the front of the path. */
    static void beam(Session s, Location from, long frame) {
        if (frame % 4 == 3) return; // the dark frame between two lit ones
        World w = from.getWorld();
        if (w == null) return;

        Location to = s.local(0, 0.6, s.getRadius() * 0.8);
        Vector dir = to.toVector().subtract(from.toVector());
        double len = dir.length();
        if (len < 2.0) return;
        dir.normalize();

        Vector right = dir.clone().crossProduct(new Vector(0, 1, 0));
        if (right.lengthSquared() < 1.0e-6) return;
        right.normalize();
        Vector up = right.clone().crossProduct(dir).normalize();

        double spin = frame * 0.35;
        int steps = 12;
        for (int k = 1; k <= steps; k++) {
            double d = len * k / steps;
            double rad = d * 0.20;
            Location c = from.clone().add(dir.clone().multiply(d));
            int ring = 4 + k / 2;
            for (int j = 0; j < ring; j++) {
                double a = spin + j * (Math.PI * 2 / ring);
                w.spawnParticle(Particle.DUST,
                        c.getX() + (right.getX() * Math.cos(a) + up.getX() * Math.sin(a)) * rad,
                        c.getY() + (right.getY() * Math.cos(a) + up.getY() * Math.sin(a)) * rad,
                        c.getZ() + (right.getZ() * Math.cos(a) + up.getZ() * Math.sin(a)) * rad,
                        1, 0, 0, 0, 0, ParticleUtils.PALE_DUST);
            }
            if ((k & 1) == 0) {
                w.spawnParticle(Particle.DUST, c, 1, 0.05, 0.05, 0.05, 0, ParticleUtils.WHITE_DUST);
            }
        }
        // dust motes caught in the light
        Location mid = from.clone().add(dir.clone().multiply(len * 0.5));
        w.spawnParticle(Particle.WHITE_ASH, mid, 6, len * 0.12, len * 0.06, len * 0.12, 0);
    }

    /**
     * A curtain of frames across the arena at local depth {@code lineZ}, clipped to the sphere.
     * Alternating white/black blocks swap every frame.
     */
    static void scanLine(Session s, double lineZ, long frame) {
        World w = s.world();
        if (w == null) return;
        double R = s.getRadius();
        double half = Math.sqrt(Math.max(0.0, R * R - lineZ * lineZ)) - 1.5;
        if (half < 1.0) return;

        int pts = (int) Math.min(60, Math.max(8, half * 1.2));
        for (int i = 0; i <= pts; i++) {
            double x = -half + 2.0 * half * i / pts;
            Location base = s.local(x, 0, lineZ);
            double gy = s.groundY(base.getX(), base.getZ());
            Particle.DustOptions c = (((i + frame) & 1L) == 0L) ? ParticleUtils.WHITE_DUST : ParticleUtils.BLACK_DUST;
            for (int row = 0; row < 3; row++) {
                w.spawnParticle(Particle.DUST, base.getX(), gy + 0.2 + row * 1.0, base.getZ(), 1, 0, 0, 0, 0, c);
            }
        }
    }

    /** A ground ring of alternating frames. Call each tick with a frame-stepped radius. */
    static void shockRing(Session s, double radius, long frame) {
        World w = s.world();
        if (w == null || radius < 1.0) return;
        int pts = (int) Math.min(90, Math.max(24, radius * 2.4));
        Location o = s.origin();
        for (int i = 0; i < pts; i++) {
            double a = (Math.PI * 2 * i) / pts;
            double x = o.getX() + Math.cos(a) * radius;
            double z = o.getZ() + Math.sin(a) * radius;
            double y = s.groundY(x, z) + 0.15;
            Particle.DustOptions c = (((i + frame) & 1L) == 0L) ? ParticleUtils.WHITE_DUST : ParticleUtils.BLACK_DUST;
            w.spawnParticle(Particle.DUST, x, y, z, 1, 0, 0, 0, 0, c);
        }
    }

    /** One blown-out frame. FLASH needs colour data on newer servers and none on older ones. */
    static void flash(Location l) {
        World w = l.getWorld();
        if (w == null) return;
        try {
            w.spawnParticle(Particle.FLASH, l, 1, 0, 0, 0, 0, Color.WHITE);
        } catch (Throwable t) {
            try {
                w.spawnParticle(Particle.FLASH, l, 1, 0, 0, 0, 0);
            } catch (Throwable ignored) {
                w.spawnParticle(Particle.END_ROD, l, 16, 0.4, 0.4, 0.4, 0.15);
            }
        }
    }
}
