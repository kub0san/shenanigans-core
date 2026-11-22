package com.hizeric.shenaniganscore.core.model;

import com.hizeric.shenaniganscore.core.display.TransformUtils;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * A model made of display entities, positioned and animated as one unit.
 *
 * <p>Every {@link Part} stores a <i>local</i> position, rotation and size (in blocks, relative to the
 * rig's origin). {@link #place} composes those with the rig's root yaw and scale and pushes the result
 * to the entities in one interpolated move, so the client animates a whole model growing, shrinking
 * or swinging while the server sends two packets per part.</p>
 *
 * <h3>Cheap animation</h3>
 * <ul>
 *   <li>{@link #place(float, int)}: scales/moves the whole model (growing out of the ground, collapsing).</li>
 *   <li>{@link #pulse(float, int)}: swells every part about <i>its own</i> centre. Transformation
 *       only, no teleport, so a breathing mass costs one metadata update per part.</li>
 *   <li>{@link #setFactor}, {@link #setRotation}: blink an eye, twitch a face.</li>
 * </ul>
 *
 * <p>Conventions: the root yaw uses Minecraft's yaw (so it matches {@link TransformUtils}), the pivot of
 * a block display is its centre (0.5), of an item display its origin (0), and of a skull a point above
 * its origin ({@link Parts#headCenterY()}).</p>
 *
 * <p>Main-thread only.</p>
 */
public final class Rig {

    private static final float SEED = 0.001f;

    /** One display entity of the model. */
    public static final class Part {
        public final Display display;
        final Vector3f pos;
        final Quaternionf rot;
        final Quaternionf baseRot;
        final Vector3f scale;
        final Vector3f pivot;
        final boolean billboard;
        float factor = 1.0f;

        /** The rotation this part was built with (a copy), for twitching and restoring. */
        public Quaternionf baseRotation() {
            return new Quaternionf(baseRot);
        }

        Part(Display display, Vector3f pos, Quaternionf rot, Vector3f scale, Vector3f pivot, boolean billboard) {
            this.display = display;
            this.pos = pos;
            this.rot = rot;
            this.baseRot = new Quaternionf(rot);
            this.scale = scale;
            this.pivot = pivot;
            this.billboard = billboard;
        }
    }

    private final World world;
    private final Vector3f origin;
    private final float yaw;
    private final List<Entity> sink;
    private final List<Part> parts = new ArrayList<>();
    private float rootScale = 1.0f;

    public Rig(Location origin, float yawDegrees, List<Entity> sink) {
        this.world = origin.getWorld();
        this.origin = new Vector3f((float) origin.getX(), (float) origin.getY(), (float) origin.getZ());
        this.yaw = yawDegrees;
        this.sink = sink;
    }

    public Location origin() {
        return new Location(world, origin.x, origin.y, origin.z);
    }

    public float rootScale() {
        return rootScale;
    }

    public List<Part> parts() {
        return Collections.unmodifiableList(parts);
    }

    /**
     * Adds a display to the model. The display should have been spawned anywhere (it is moved on the
     * first {@link #place}); it is shrunk to a speck until then.
     *
     * @param pos   local position of the part's centre, in blocks
     * @param rot   local rotation
     * @param scale local size, in blocks (per axis, as the display's scale)
     * @param pivot the display-space point that stays fixed at {@code pos} (0.5 for blocks)
     */
    public Part add(Display display, Vector3f pos, Quaternionf rot, Vector3f scale, Vector3f pivot,
                    boolean billboard) {
        Part part = new Part(display, new Vector3f(pos), new Quaternionf(rot), new Vector3f(scale),
                new Vector3f(pivot), billboard);
        parts.add(part);
        if (sink != null) sink.add(display);
        display.setTeleportDuration(0);
        TransformUtils.applyTransform(display, new Vector3f(SEED), new Quaternionf(), new Vector3f(pivot), 0);
        return part;
    }

    private static int clampTicks(int ticks) {
        return Math.max(0, Math.min(59, ticks));
    }

    private Quaternionf yawRotation() {
        return new Quaternionf().rotateY((float) -Math.toRadians(yaw));
    }

    /** Moves and scales every part for the given root scale, interpolated over {@code ticks}. */
    public void place(float newRootScale, int ticks) {
        this.rootScale = Math.max(SEED, newRootScale);
        Quaternionf yawQ = yawRotation();
        int t = clampTicks(ticks);
        for (Part part : parts) {
            if (!part.display.isValid()) continue;
            apply(part, yawQ, t, true);
        }
    }

    /** Swells or relaxes every part about its own centre. No part moves, so this is cheap. */
    public void pulse(float factor, int ticks) {
        Quaternionf yawQ = yawRotation();
        int t = clampTicks(ticks);
        for (Part part : parts) {
            if (!part.display.isValid() || part.billboard) continue;
            part.factor = factor;
            apply(part, yawQ, t, false);
        }
    }

    /** Scales one part about its own centre (a blink, a swell). */
    public void setFactor(Part part, float factor, int ticks) {
        if (!part.display.isValid()) return;
        part.factor = Math.max(SEED, factor);
        apply(part, yawRotation(), clampTicks(ticks), false);
    }

    /** Turns one part to a new local rotation (a twitch). */
    public void setRotation(Part part, Quaternionf localRot, int ticks) {
        if (!part.display.isValid()) return;
        part.rot.set(localRot);
        apply(part, yawRotation(), clampTicks(ticks), false);
    }

    /** Returns a part to the rotation it was built with. */
    public void restoreRotation(Part part, int ticks) {
        setRotation(part, part.baseRot, ticks);
    }

    private void apply(Part part, Quaternionf yawQ, int ticks, boolean move) {
        Quaternionf worldRot = part.billboard ? new Quaternionf() : new Quaternionf(yawQ).mul(part.rot);
        Vector3f worldScale = new Vector3f(part.scale).mul(rootScale * part.factor);
        TransformUtils.applyTransform(part.display, worldScale, worldRot, part.pivot, ticks);
        if (move) {
            Vector3f offset = new Vector3f(part.pos).mul(rootScale).rotate(yawQ);
            part.display.setTeleportDuration(ticks);
            part.display.teleport(new Location(world,
                    origin.x + offset.x, origin.y + offset.y, origin.z + offset.z));
        }
    }

    /** Removes every display. Safe to call more than once. */
    public void remove() {
        for (Part part : parts) {
            if (part.display.isValid()) part.display.remove();
        }
        parts.clear();
    }
}
