package com.hizeric.shenaniganscore.core.display;

import org.bukkit.Bukkit;
import org.bukkit.entity.Display;
import org.bukkit.util.Transformation;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.Objects;

/**
 * Helpers for validated, pivot-aware display transformations.
 *
 * <p>All methods must be called on the Bukkit main thread.</p>
 *
 * <p>Display transformations and entity teleports are separate client-side
 * interpolation channels. This class configures transformation interpolation
 * only; callers should coordinate it with teleport interpolation when they
 * change both the entity location and its transformation.</p>
 */
public final class TransformUtils {

    private static final float MIN_LENGTH_SQUARED = 1.0e-8f;

    private TransformUtils() {}

    /**
     * Applies scale and rotation about the center of a unit-block model.
     *
     * <p>The yaw convention deliberately preserves this project's existing
     * sign: yaw is applied as {@code rotateY(-yaw)}. Angles are in degrees.</p>
     */
    public static void applyTransform(
            Display display,
            float scaleX,
            float scaleY,
            float scaleZ,
            float yaw,
            float leanX,
            float leanZ,
            int duration
    ) {
        validateScale(scaleX, "scaleX");
        validateScale(scaleY, "scaleY");
        validateScale(scaleZ, "scaleZ");
        validateFinite(yaw, "yaw");
        validateFinite(leanX, "leanX");
        validateFinite(leanZ, "leanZ");

        Quaternionf rotation = new Quaternionf()
                .rotateY((float) Math.toRadians(-yaw))
                .rotateX((float) Math.toRadians(leanX))
                .rotateZ((float) Math.toRadians(leanZ))
                .normalize();

        applyTransform(
                display,
                new Vector3f(scaleX, scaleY, scaleZ),
                rotation,
                new Vector3f(0.5f, 0.5f, 0.5f),
                duration
        );
    }

    /**
     * Applies a transform around a pivot in the model's local coordinates.
     * The pivot remains fixed as the model scales and rotates.
     *
     * <p>The supplied vectors and quaternion are copied; this method does not
     * modify objects owned by the caller.</p>
     */
    public static void applyTransform(
            Display display,
            Vector3f scale,
            Quaternionf rotation,
            Vector3f pivot,
            int duration
    ) {
        requireMainThread();
        Objects.requireNonNull(display, "display");
        Objects.requireNonNull(scale, "scale");
        Objects.requireNonNull(rotation, "rotation");
        Objects.requireNonNull(pivot, "pivot");
        validateDuration(duration);

        validateVector(scale, "scale");
        validateVector(pivot, "pivot");
        validateScale(scale.x, "scale.x");
        validateScale(scale.y, "scale.y");
        validateScale(scale.z, "scale.z");
        validateQuaternion(rotation);

        Vector3f safeScale = new Vector3f(scale);
        Quaternionf safeRotation = new Quaternionf(rotation).normalize();

        // Transform the pivot by scale, then rotation. Translation must offset
        // that movement while leaving the pivot at its original position.
        Vector3f transformedPivot = new Vector3f(pivot)
                .mul(safeScale)
                .rotate(safeRotation);

        Vector3f translation = new Vector3f(pivot).sub(transformedPivot);

        setTransformation(
                display,
                new Transformation(
                        translation,
                        safeRotation,
                        safeScale,
                        new Quaternionf()
                ),
                duration
        );
    }

    /**
     * Rotates the display's local +Z axis toward {@code facing}, while keeping
     * the given local pivot fixed.
     *
     * @param facing finite, non-zero direction vector
     */
    public static void applyFacing(
            Display display,
            Vector3f scale,
            Vector3f facing,
            Vector3f pivot,
            int duration
    ) {
        Objects.requireNonNull(facing, "facing");
        validateVector(facing, "facing");

        float lengthSquared = facing.lengthSquared();
        if (!Float.isFinite(lengthSquared) || lengthSquared < MIN_LENGTH_SQUARED) {
            throw new IllegalArgumentException("facing must be finite and non-zero");
        }

        Vector3f direction = new Vector3f(facing).normalize();
        Quaternionf rotation = new Quaternionf().rotationTo(
                new Vector3f(0.0f, 0.0f, 1.0f),
                direction
        );

        applyTransform(display, scale, rotation, pivot, duration);
    }

    /** Resets the transformation to identity, optionally interpolating. */
    public static void resetTransform(Display display, int duration) {
        requireMainThread();
        Objects.requireNonNull(display, "display");
        validateDuration(duration);

        setTransformation(
                display,
                new Transformation(
                        new Vector3f(),
                        new Quaternionf(),
                        new Vector3f(1.0f),
                        new Quaternionf()
                ),
                duration
        );
    }

    private static void setTransformation(
            Display display,
            Transformation transformation,
            int duration
    ) {
        display.setInterpolationDelay(0);
        display.setInterpolationDuration(duration);
        display.setTransformation(transformation);
    }

    private static void validateScale(float value, String name) {
        if (!Float.isFinite(value) || value < 0.0f) {
            throw new IllegalArgumentException(
                    name + " must be finite and non-negative"
            );
        }
    }

    private static void validateFinite(float value, String name) {
        if (!Float.isFinite(value)) {
            throw new IllegalArgumentException(name + " must be finite");
        }
    }

    private static void validateVector(Vector3f vector, String name) {
        if (!Float.isFinite(vector.x)
                || !Float.isFinite(vector.y)
                || !Float.isFinite(vector.z)) {
            throw new IllegalArgumentException(
                    name + " components must be finite"
            );
        }
    }

    private static void validateQuaternion(Quaternionf quaternion) {
        if (!Float.isFinite(quaternion.x)
                || !Float.isFinite(quaternion.y)
                || !Float.isFinite(quaternion.z)
                || !Float.isFinite(quaternion.w)) {
            throw new IllegalArgumentException(
                    "rotation components must be finite"
            );
        }

        float lengthSquared = quaternion.lengthSquared();
        if (!Float.isFinite(lengthSquared) || lengthSquared < MIN_LENGTH_SQUARED) {
            throw new IllegalArgumentException(
                    "rotation must be a non-zero quaternion"
            );
        }
    }

    private static void validateDuration(int duration) {
        if (duration < 0) {
            throw new IllegalArgumentException("duration cannot be negative");
        }
    }

    private static void requireMainThread() {
        if (!Bukkit.isPrimaryThread()) {
            throw new IllegalStateException(
                    "TransformUtils must only be called from the Bukkit main thread"
            );
        }
    }
}
