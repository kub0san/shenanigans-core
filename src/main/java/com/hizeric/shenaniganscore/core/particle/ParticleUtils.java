package com.hizeric.shenaniganscore.core.particle;

import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;

public final class ParticleUtils {

    private ParticleUtils() {}

    // Pre-cached dust options used across all domains to prevent Garbage Collection lag
    public static final Particle.DustOptions WHITE_DUST = new Particle.DustOptions(Color.WHITE, 1.0f);
    public static final Particle.DustOptions BLACK_DUST = new Particle.DustOptions(Color.fromRGB(10, 10, 16), 1.4f);
    public static final Particle.DustOptions RED_DUST = new Particle.DustOptions(Color.RED, 1.0f);
    public static final Particle.DustOptions BLOOD_DUST = new Particle.DustOptions(Color.fromRGB(140, 0, 0), 1.2f);
    public static final Particle.DustOptions BLOOD_FINE_DUST = new Particle.DustOptions(Color.fromRGB(175, 8, 20), 0.65f);
    public static final Particle.DustOptions CYAN_DUST = new Particle.DustOptions(Color.fromRGB(100, 255, 255), 1.0f);

    // Extended domain palette (Unlimited Void keeps its exact colours)
    public static final Particle.DustOptions ICE_DUST = new Particle.DustOptions(Color.fromRGB(175, 225, 255), 0.95f);
    public static final Particle.DustOptions DIM_BLUE_DUST = new Particle.DustOptions(Color.fromRGB(90, 130, 210), 0.5f);
    public static final Particle.DustOptions BLUE_DUST = new Particle.DustOptions(Color.fromRGB(70, 120, 255), 0.8f);
    public static final Particle.DustOptions DEEP_DUST = new Particle.DustOptions(Color.fromRGB(40, 80, 200), 0.9f);
    public static final Particle.DustOptions PALE_DUST = new Particle.DustOptions(Color.fromRGB(215, 238, 255), 0.65f);

    // Layered-particle palette (Unlimited Void visual upgrade)
    public static final Particle.DustOptions AQUA_DUST = new Particle.DustOptions(Color.fromRGB(110, 205, 255), 0.85f);
    public static final Particle.DustOptions VOID_PURPLE_DUST = new Particle.DustOptions(Color.fromRGB(130, 60, 255), 1.5f);
    public static final Particle.DustOptions NEBULA_BLUE_DUST = new Particle.DustOptions(Color.fromRGB(60, 120, 255), 1.8f);
    public static final Particle.DustOptions ORANGE_DUST = new Particle.DustOptions(Color.fromRGB(255, 120, 20), 1.0f);

    /** Violet/blue/teal clouds of the nebula. */
    public static final Particle.DustOptions[] NEBULA_DUST = {
            new Particle.DustOptions(Color.fromRGB(150, 110, 255), 2.2f),
            new Particle.DustOptions(Color.fromRGB(205, 120, 255), 2.0f),
            new Particle.DustOptions(Color.fromRGB(60, 90, 230), 2.4f),
            new Particle.DustOptions(Color.fromRGB(80, 190, 230), 1.9f)
    };

    /** Spawns a flat circle of particles at the given location. */
    public static void spawnCircle(Location center, double radius, int points, Particle particle, Object data) {
        if (center.getWorld() == null) return;
        for (int i = 0; i < points; i++) {
            double angle = 2 * Math.PI * i / points;
            double x = center.getX() + Math.cos(angle) * radius;
            double z = center.getZ() + Math.sin(angle) * radius;
            center.getWorld().spawnParticle(particle, x, center.getY(), z, 1, 0, 0, 0, 0, data);
        }
    }
}
