package com.hizeric.shenaniganscore.battleground.deploy;

/**
 * Synchronizes concurrent deploys: all members of a wave use the same fall duration, so their
 * release heights are solved from that duration and (when they start on the same tick) they land on
 * the same tick. Pure arithmetic, so the relationship is unit-testable without a server.
 */
public final class DropWave {

    private DropWave() { }

    /** Distance covered in {@code ticks} ticks at the {@link DiveMotion} ramp, capped by maxSpeed. */
    public static double fallDistance(int ticks, double maxSpeed) {
        double distance = 0;
        for (int tick = 1; tick <= Math.max(0, ticks); tick++) {
            distance += DiveMotion.downSpeed(tick, maxSpeed);
        }
        return distance;
    }

    /** Release Y for a given target land-tick count, clamped to the arena roof. */
    public static int releaseY(double landY, int targetTicks, double maxSpeed, int regionMaxY) {
        int desired = (int) Math.ceil(fallDistance(targetTicks, maxSpeed));
        int release = (int) Math.floor(landY) + Math.max(3, desired);
        return Math.min(regionMaxY - 2, release);
    }
}
