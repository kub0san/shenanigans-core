package com.hizeric.shenaniganscore.origins.jujutsukaisen.domainexpansions.timecellmoonpalace;

import com.hizeric.shenaniganscore.core.terrain.DomainFloor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.World;

import java.util.Random;

/**
 * Time Cell Moon Palace's ground: a <b>real</b>, flat black stage.
 *
 * <p>The time cell used to black out every exposed block inside the sphere —
 * up to sixty thousand fake blocks per viewer, which was the single most
 * expensive thing the plugin did and still left the real terrain underneath
 * for everybody to stand on.</p>
 *
 * <p>Now the studio has a real floor: a flat, black platform of actual server
 * blocks. It costs a fraction of the blackout, it has collision, it hides the
 * terrain completely and it gives the flesh path and the projector something
 * solid to sit on. The shell above it only has to close the walls now.</p>
 *
 * <p>Anything the platform swallows is lifted onto it; when the time cell
 * ends, the world is handed back exactly as it was.</p>
 */
final class Floor {

    private static final int MAX_FLOOR_RADIUS = 48;
    private static final Random RANDOM = new Random();

    private final Session session;
    private final DomainFloor floor;

    Floor(Session session) {
        this.session = session;
        int radius = DomainFloor.resolveFloorRadius(session.getRadius(), MAX_FLOOR_RADIUS);
        // The film stage reads better in a mid gray than straight-up black.
        Material baseBlock = Material.GRAY_CONCRETE;
        this.floor = new DomainFloor(session.getCenter(), session.getRadius(),
                baseBlock, radius, DomainFloor.clampBudget(DomainFloor.DEFAULT_BUDGET));
        this.floor.setSessionId(session.getCaster().getUniqueId());
        this.floor.setRimSkirt(12);
    }

    /** Measures the terrain and plans the stage. */
    void prepare() {
        floor.prepare();
    }

    /** Grows the stage towards the given progress (0–1). */
    void expand(double progress) {
        floor.build(progress);
    }

    /** Finishes the stage in one go (safety net). */
    void finish() {
        floor.build(1.0);
    }

    /** Hands the world back from the rim inwards. */
    void collapse(double progress) {
        floor.recede(progress);
    }

    /** Gives the world back for good. */
    void close() {
        floor.close();
    }

    /** The platform itself, for the session's cleanup and the shell's anchor. */
    DomainFloor platform() {
        return floor;
    }

    /** The standing height of the stage. */
    int surfaceY() {
        return floor.surfaceY();
    }

    /** How far the stage has really been built, in blocks. */
    double builtRadius() {
        return floor.builtRadius();
    }

    /** Dust and ash drifting over the stage. */
    void ambience() {
        World world = session.world();
        if (world == null) return;

        Location origin = session.origin();
        double radius = Math.min(floor.builtRadius(), 12.0);
        if (radius <= 0.5) return;

        for (int i = 0; i < 3; i++) {
            double angle = RANDOM.nextDouble() * Math.PI * 2.0;
            double distance = RANDOM.nextDouble() * radius;
            world.spawnParticle(Particle.WHITE_ASH,
                    origin.getX() + Math.cos(angle) * distance,
                    floor.surfaceY() + 0.2 + RANDOM.nextDouble() * 1.4,
                    origin.getZ() + Math.sin(angle) * distance,
                    1, 0, 0, 0, 0);
        }
    }
}
