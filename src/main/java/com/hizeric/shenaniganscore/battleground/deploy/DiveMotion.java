package com.hizeric.shenaniganscore.battleground.deploy;

/**
 * Pure dive kinematics. The airdrop is a strictly vertical descent: the per-tick velocity has zero
 * horizontal components so the player lands on the column the selector scored. Kept arithmetic-only
 * so the fall feel is unit-testable without a live world.
 */
public final class DiveMotion {

    /** Starting downward speed and the per-tick acceleration applied before the cap. */
    public static final double BASE_DOWN_SPEED = 0.5;
    public static final double DOWN_SPEED_RAMP = 0.12;

    /** A drift-free velocity: only the downward component is ever non-zero. */
    public record Velocity(double x, double y, double z) { }

    private DiveMotion() { }

    /** Downward speed at {@code tick}: starts at {@code 0.5 + 0.12} and never exceeds the cap. */
    public static double downSpeed(int tick, double maxDownSpeed) {
        return Math.min(maxDownSpeed, BASE_DOWN_SPEED + tick * DOWN_SPEED_RAMP);
    }

    /** The velocity {@code SkyDrop} applies at {@code tick}: straight down, no horizontal drift. */
    public static Velocity forTick(int tick, double maxDownSpeed) {
        return new Velocity(0.0, -downSpeed(tick, maxDownSpeed), 0.0);
    }
}
