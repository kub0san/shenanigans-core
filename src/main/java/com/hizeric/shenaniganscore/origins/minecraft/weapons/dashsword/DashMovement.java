package com.hizeric.shenaniganscore.origins.minecraft.weapons.dashsword;

import org.bukkit.util.Vector;

/**
 * Pure movement math for the Rift Cleave dash: steering blend, speed and decay.
 *
 * <p>No Bukkit state, scheduler or player access. Values are provisional until playtest;
 * the dash currently uses zero steering and zero decay, i.e. a locked straight line.</p>
 */
public final class DashMovement {

    /** Provisional forward speed (blocks/tick). */
    public static final double DEFAULT_MOMENTUM = 2.6;
    /** Provisional steering blend toward the look direction (0 = locked line). */
    public static final double DEFAULT_STEERING_ALPHA = 0.0;
    /** Provisional speed decay per tick (0 = constant speed). */
    public static final double DEFAULT_DECAY_PER_TICK = 0.0;

    private DashMovement() { }

    /** Blend current direction toward look direction by alpha in [0,1]. */
    public static Vector steer(Vector currentDirection, Vector lookDirection, double alpha) {
        double blend = Math.max(0.0, Math.min(1.0, alpha));
        Vector current = currentDirection == null ? new Vector(0, 0, 1) : currentDirection.clone();
        Vector look = lookDirection == null ? current.clone() : lookDirection.clone();
        if (current.lengthSquared() < 1.0e-8) current = new Vector(0, 0, 1);
        if (look.lengthSquared() < 1.0e-8) look = current.clone();
        current.normalize();
        look.normalize();
        Vector next = current.clone().multiply(1.0 - blend).add(look.clone().multiply(blend));
        if (next.lengthSquared() < 1.0e-8) return current.clone();
        return next.normalize();
    }

    /** Speed after {@code tick} ticks of decay, floored at zero. */
    public static double speed(double initial, double decayPerTick, int tick) {
        double factor = 1.0 - Math.max(0.0, Math.min(1.0, decayPerTick));
        double result = initial * Math.pow(factor, Math.max(0, tick));
        return Math.max(0.0, result);
    }

    /** Direction scaled to the given speed. */
    public static Vector velocity(Vector direction, double speed) {
        if (direction == null || direction.lengthSquared() < 1.0e-8) {
            return new Vector(0, 0, 0);
        }
        return direction.clone().normalize().multiply(Math.max(0.0, speed));
    }
}
