package com.hizeric.shenaniganscore.core.util;

import org.bukkit.util.Vector;
import org.joml.Quaterniond;
import org.joml.Quaternionf;
import org.joml.Vector3d;
import org.joml.Vector3f;
import org.joml.Vector3fc;

public final class MathUtil {

    private static final double EPSILON = 1.0e-12;

    private MathUtil() {
    }

    // ----- Scalar helpers -----

    /**
     * Scale a base count by a quality factor, rounding to the nearest integer with a floor of 1.
     *
     * <p>Shared by technique builders whose projectile counts scale with cast quality. Kept as a
     * pure numeric helper so the core layer stays independent of any technique's domain types.</p>
     */
    public static int scaledCount(int base, double factor) {
        return (int) Math.max(1, Math.round(base * factor));
    }

    public static double clamp(double value, double min, double max) {
        requireFinite(value, "value");
        requireFinite(min, "min");
        requireFinite(max, "max");
        requireRange(min, max);

        return Math.max(min, Math.min(max, value));
    }

    public static float clamp(float value, float min, float max) {
        requireFinite(value, "value");
        requireFinite(min, "min");
        requireFinite(max, "max");
        requireRange(min, max);

        return Math.max(min, Math.min(max, value));
    }

    /** Clamped interpolation. */
    public static double lerp(double start, double end, double t) {
        return lerpUnclamped(start, end, clamp(t, 0.0, 1.0));
    }

    /** Unclamped interpolation; useful for controlled extrapolation. */
    public static double lerpUnclamped(double start, double end, double t) {
        requireFinite(start, "start");
        requireFinite(end, "end");
        requireFinite(t, "t");

        return start + (end - start) * t;
    }

    /** Returns where value lies between start and end, clamped to 0–1. */
    public static double inverseLerp(double start, double end, double value) {
        requireFinite(start, "start");
        requireFinite(end, "end");
        requireFinite(value, "value");

        if (Math.abs(end - start) <= EPSILON) {
            throw new IllegalArgumentException("start and end must differ");
        }

        return clamp((value - start) / (end - start), 0.0, 1.0);
    }

    /** Smooth interpolation from 0 to 1. */
    public static double smoothstep(double t) {
        t = clamp(t, 0.0, 1.0);
        return t * t * (3.0 - 2.0 * t);
    }

    /** Smooth interpolation between arbitrary edges. */
    public static double smoothstep(double edge0, double edge1, double value) {
        requireFinite(edge0, "edge0");
        requireFinite(edge1, "edge1");
        requireFinite(value, "value");

        if (edge0 >= edge1) {
            throw new IllegalArgumentException("edge0 must be less than edge1");
        }

        return smoothstep((value - edge0) / (edge1 - edge0));
    }

    /** Converts to the range [-180, 180). */
    public static double wrapDegrees(double degrees) {
        requireFinite(degrees, "degrees");

        double wrapped = degrees % 360.0;
        if (wrapped >= 180.0) wrapped -= 360.0;
        if (wrapped < -180.0) wrapped += 360.0;
        return wrapped;
    }

    public static double degreesToRadians(double degrees) {
        requireFinite(degrees, "degrees");
        return Math.toRadians(degrees);
    }

    public static double radiansToDegrees(double radians) {
        requireFinite(radians, "radians");
        return Math.toDegrees(radians);
    }

    // ----- Bukkit Vector helpers -----

    /**
     * Rotates a direction or offset around Y using Bukkit yaw convention.
     * Positive degrees rotate +Z toward -X.
     *
     * Use rotateAroundY for a point that must rotate around a specific pivot.
     */
    public static Vector rotateY(Vector vector, double degrees) {
        requireVector(vector, "vector");
        requireFinite(degrees, "degrees");

        Vector3d result = new Vector3d(vector.getX(), vector.getY(), vector.getZ());
        Quaterniond rotation = new Quaterniond()
                .rotateY(Math.toRadians(-degrees));

        result.rotate(rotation);
        return new Vector(result.x, result.y, result.z);
    }

    /** Rotates a point around a pivot, rather than around the world origin. */
    public static Vector rotateAroundY(Vector point, Vector pivot, double degrees) {
        requireVector(point, "point");
        requireVector(pivot, "pivot");

        Vector offset = point.clone().subtract(pivot);
        return rotateY(offset, degrees).add(pivot.clone());
    }

    /**
     * Builds a unit direction from Bukkit yaw and pitch, in degrees.
     * Positive pitch points downward, matching Minecraft's convention.
     */
    public static Vector directionFromYawPitch(double yaw, double pitch) {
        requireFinite(yaw, "yaw");
        requireFinite(pitch, "pitch");

        double yawRad = Math.toRadians(yaw);
        double pitchRad = Math.toRadians(pitch);
        double cosPitch = Math.cos(pitchRad);

        return new Vector(
                -cosPitch * Math.sin(yawRad),
                -Math.sin(pitchRad),
                cosPitch * Math.cos(yawRad)
        );
    }

    /** Returns a normalized copy, or zero if the input has effectively zero length. */
    public static Vector normalizeOrZero(Vector vector) {
        requireVector(vector, "vector");

        double lengthSquared = vector.lengthSquared();
        if (lengthSquared <= EPSILON) {
            return new Vector();
        }

        return vector.clone().multiply(1.0 / Math.sqrt(lengthSquared));
    }

    /** Moves current toward target by at most maxDistance. */
    public static Vector moveTowards(Vector current, Vector target, double maxDistance) {
        requireVector(current, "current");
        requireVector(target, "target");
        requireFinite(maxDistance, "maxDistance");

        if (maxDistance < 0.0) {
            throw new IllegalArgumentException("maxDistance must be non-negative");
        }

        Vector delta = target.clone().subtract(current);
        double distanceSquared = delta.lengthSquared();

        if (distanceSquared <= EPSILON || distanceSquared <= maxDistance * maxDistance) {
            return target.clone();
        }

        return current.clone().add(delta.multiply(maxDistance / Math.sqrt(distanceSquared)));
    }

    // ----- JOML helpers -----

    /** Returns a normalized copy, or a zero vector if the input is effectively zero. */
    public static Vector3f normalizeOrZero(Vector3fc vector) {
        requireVector(vector, "vector");

        Vector3f result = new Vector3f(vector);
        float lengthSquared = result.lengthSquared();

        if (lengthSquared <= (float) EPSILON) {
            return new Vector3f();
        }

        return result.normalize();
    }

    /** Returns the angle between two non-zero vectors, in radians. */
    public static float angleBetween(Vector3fc a, Vector3fc b) {
        requireVector(a, "a");
        requireVector(b, "b");

        float aLength = a.length();
        float bLength = b.length();

        if (aLength <= 1.0e-6f || bLength <= 1.0e-6f) {
            throw new IllegalArgumentException("Vectors must have non-zero length");
        }

        float cosine = a.dot(b) / (aLength * bLength);
        cosine = clamp(cosine, -1.0f, 1.0f);
        return (float) Math.acos(cosine);
    }

    /**
     * Returns the shortest-arc rotation from one direction to another.
     * The returned quaternion is a new object; the inputs are not modified.
     */
    public static Quaternionf rotationBetween(Vector3fc from, Vector3fc to) {
        requireVector(from, "from");
        requireVector(to, "to");

        Vector3f a = new Vector3f(from);
        Vector3f b = new Vector3f(to);

        if (a.lengthSquared() <= 1.0e-12f || b.lengthSquared() <= 1.0e-12f) {
            throw new IllegalArgumentException("Directions must have non-zero length");
        }

        a.normalize();
        b.normalize();

        return new Quaternionf().rotationTo(a, b).normalize();
    }

    /** Spherically interpolates between rotations without mutating either input. */
    public static Quaternionf slerp(Quaternionf from, Quaternionf to, float t) {
        if (from == null || to == null) {
            throw new IllegalArgumentException("Quaternions cannot be null");
        }
        requireFinite(t, "t");

        return new Quaternionf(from).normalize()
                .slerp(new Quaternionf(to).normalize(), clamp(t, 0.0f, 1.0f))
                .normalize();
    }

    // ----- Validation -----

    private static void requireVector(Vector vector, String name) {
        if (vector == null
                || !Double.isFinite(vector.getX())
                || !Double.isFinite(vector.getY())
                || !Double.isFinite(vector.getZ())) {
            throw new IllegalArgumentException(name + " must be non-null and finite");
        }
    }

    private static void requireVector(Vector3fc vector, String name) {
        if (vector == null
                || !Float.isFinite(vector.x())
                || !Float.isFinite(vector.y())
                || !Float.isFinite(vector.z())) {
            throw new IllegalArgumentException(name + " must be non-null and finite");
        }
    }

    private static void requireRange(double min, double max) {
        if (min > max) {
            throw new IllegalArgumentException("min must be <= max");
        }
    }

    private static void requireRange(float min, float max) {
        if (min > max) {
            throw new IllegalArgumentException("min must be <= max");
        }
    }

    private static void requireFinite(double value, String name) {
        if (!Double.isFinite(value)) {
            throw new IllegalArgumentException(name + " must be finite");
        }
    }

    private static void requireFinite(float value, String name) {
        if (!Float.isFinite(value)) {
            throw new IllegalArgumentException(name + " must be finite");
        }
    }
}
