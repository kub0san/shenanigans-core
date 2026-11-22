package com.hizeric.shenaniganscore.origins.jujutsukaisen.domainexpansions.unlimitedvoid;

import com.hizeric.shenaniganscore.core.terrain.DomainFloor;
import org.bukkit.Material;
import org.bukkit.block.data.BlockData;

/**
 * Unlimited Void's ground: the same <b>real</b>, flat platform as before, now a plain,
 * featureless black.
 *
 * <p>The void must have no visible floor, wall or horizon, so the hexagonal Six Eyes sigil is gone
 * and the platform is a single black surface that melts into the black dome. It keeps everything
 * that made the platform worth having: real collision, a flat plane for everyone to stand on, and
 * an exact restore on close (it snapshots every block it touches).</p>
 *
 * <p>The default block budget is higher than the platform's own, so it can keep pace with the
 * opening wavefront.</p>
 */
public final class Floor {

    private static final BlockData BASE = Material.BLACK_CONCRETE.createBlockData();

    private static final int MAX_FLOOR_RADIUS = 48;
    private static final int DEFAULT_BLOCKS_PER_TICK = 2400;

    private final DomainFloor floor;
    private final int floorRadius;

    public Floor(Session session) {
        this.floorRadius = DomainFloor.resolveFloorRadius(session.getRadius(), MAX_FLOOR_RADIUS);
        Material baseBlock = Material.BLACK_CONCRETE;
        this.floor = new DomainFloor(session.getCenter(), session.getRadius(),
                baseBlock, floorRadius, DomainFloor.clampBudget(DEFAULT_BLOCKS_PER_TICK));
        this.floor.setSessionId(session.getCaster().getUniqueId());
        this.floor.setRimSkirt(12);
    }

    /** Measures the terrain and plans the platform. Call once, early. */
    public void prepare() {
        floor.prepare();
    }

    /** Grows the platform towards the given progress (0-1). */
    public void expand(double progress) {
        floor.build(progress);
    }

    /** Finishes the platform in one go (safety net for a cut-short lead-in). */
    public void finish() {
        floor.build(1.0);
    }

    /** Hands the world back from the rim inwards. */
    public void collapse(double progress) {
        floor.recede(progress);
    }

    /** Gives the world back for good. */
    public void close() {
        floor.close();
    }

    /** The standing height of the platform (the Y of its top face). */
    public int surfaceY() {
        return floor.surfaceY();
    }

    /** How far the platform has really been built, in blocks. */
    public double builtRadius() {
        return floor.builtRadius();
    }

    /** The real platform itself, for the session's cleanup. */
    public DomainFloor platform() {
        return floor;
    }

    /** Where the ground is: the platform's surface, or the fallback if there is none. */
    public static double groundY(Session session, double x, double z, double fallback) {
        Floor f = session.floor;
        return f == null ? fallback : f.surfaceY();
    }
}
