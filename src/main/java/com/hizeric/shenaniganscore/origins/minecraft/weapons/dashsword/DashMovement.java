package com.hizeric.shenaniganscore.origins.minecraft.weapons.dashsword;

import org.bukkit.util.Vector;

/**
 * Pure movement math for the Rift Cleave dash: steering blend, speed, decay and curved path.
 *
 * <p>No Bukkit state, scheduler or player access. The dash flies a 28-tick window: it keeps the
 * launch pitch, drifts its yaw by {@link #YAW_DRIFT_DEGREES_PER_TICK} degrees every tick, and
 * loses speed as it goes.</p>
 */
public final class DashMovement {

    /** Forward speed at launch (blocks/tick). */
    public static final double DEFAULT_MOMENTUM = 2.4;
    /** Steering blend toward the look direction (0 = locked line). */
    public static final double DEFAULT_STEERING_ALPHA = 0.0;
    /** Fraction of speed lost per tick (0 = constant speed). */
    public static final double DEFAULT_DECAY_PER_TICK = 0.035;
    /** Yaw the path drifts per tick, in degrees (sign/handedness deferred to playtest). */
    public static final double YAW_DRIFT_DEGREES_PER_TICK = 1.6;

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

    /**
     * Flight heading at {@code tick}: the normalized launch direction, yawed about world up by
     * {@link #YAW_DRIFT_DEGREES_PER_TICK} degrees per tick. Pitch is preserved — only the
     * horizontal heading rotates. A vertical (or missing) launch direction, whose horizontal
     * component is degenerate, falls back to {@code (1,0,0)} before the same yaw is applied.
     */
    public static Vector headingAt(Vector launchForward, int tick) {
        Vector base = launchForward == null ? new Vector(1, 0, 0) : launchForward.clone();
        if (base.lengthSquared() < 1.0e-8) base = new Vector(1, 0, 0);
        base.normalize();
        double horizontal = base.getX() * base.getX() + base.getZ() * base.getZ();
        if (horizontal < 1.0e-8) base = new Vector(1, 0, 0);
        double radians = Math.toRadians(YAW_DRIFT_DEGREES_PER_TICK * Math.max(0, tick));
        double cos = Math.cos(radians);
        double sin = Math.sin(radians);
        double x = base.getX() * cos + base.getZ() * sin;
        double z = base.getZ() * cos - base.getX() * sin;
        return new Vector(x, base.getY(), z).normalize();
    }

    /** Total distance covered over {@code ticks} ticks of decayed flight. */
    public static double distanceAfter(int ticks, double momentum, double decayPerTick) {
        double total = 0.0;
        for (int tick = 1; tick <= ticks; tick++) {
            total += speed(momentum, decayPerTick, tick);
        }
        return total;
    }
}
