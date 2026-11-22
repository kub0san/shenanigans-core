package com.hizeric.shenaniganscore.origins.jujutsukaisen.domainexpansions.unlimitedvoid;

import com.hizeric.shenaniganscore.core.display.QuadUtils;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Display;
import org.bukkit.entity.TextDisplay;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Quiet, long information-like streaks drifting through Unlimited Void.
 *
 * <p>Streaks remain alive for the duration of the domain. They travel along slow,
 * gently wavering paths rather than shrinking, teleporting, and respawning.</p>
 */
public final class Streaks {

    private static final int BASE_COUNT = 20;
    private static final int UPDATE_EVERY = 4;

    // Mostly near-white, with a few muted cool tones.
    private static final Color[] PALETTE = {
            Color.fromRGB(235, 244, 255),
            Color.fromRGB(235, 244, 255),
            Color.fromRGB(235, 244, 255),
            Color.fromRGB(185, 220, 255),
            Color.fromRGB(125, 175, 230),
            Color.fromRGB(185, 175, 235)
    };

    private static final class Streak {
        private TextDisplay quad;

        private double theta;
        private double rho;
        private double height;
        private double angularSpeed;
        private double phase;

        private float length;
        private float thickness;
        private Color color;
        private int alpha;

        private long launchTick;
        private boolean launched;
    }

    private final World world;
    private final Location center;
    private final double radius;
    private final Random random = new Random();
    private final List<Streak> streaks = new ArrayList<>();

    private long tick;
    private boolean closed;

    public Streaks(Location center, double radius, double quality) {
        this.center = center.clone();
        this.world = center.getWorld();
        this.radius = Math.max(2.0, radius);

        if (world == null) {
            return;
        }

        int count = (int) Math.max(12, Math.min(36,
                Math.round(BASE_COUNT * Math.max(0.25, Math.min(1.75, quality)))));

        for (int i = 0; i < count; i++) {
            Streak streak = createStreak();

            // Start invisible and collapsed so nothing flashes at the caster.
            streak.quad = QuadUtils.spawn(
                    this.center,
                    Display.Billboard.VERTICAL,
                    4.0f,
                    false
            );
            QuadUtils.setColor(streak.quad, streak.color, 0);
            QuadUtils.apply(
                    streak.quad,
                    0.001f,
                    streak.thickness,
                    0f, 0f, 0f, 0f,
                    0
            );

            streaks.add(streak);
        }
    }

    private Streak createStreak() {
        Streak streak = new Streak();

        streak.theta = random.nextDouble() * Math.PI * 2.0;

        // Spread them through the void, not just around a single dome edge.
        streak.rho = radius * (0.22 + random.nextDouble() * 0.58);
        streak.height = radius * (-0.55 + random.nextDouble() * 1.10);

        // A full circuit takes a long time. Some drift clockwise, some counterclockwise.
        double speed = 0.0012 + random.nextDouble() * 0.0016;
        streak.angularSpeed = random.nextBoolean() ? speed : -speed;

        streak.phase = random.nextDouble() * Math.PI * 2.0;
        streak.length = (float) (radius * (0.08 + random.nextDouble() * 0.16));
        streak.thickness = 0.025f + random.nextFloat() * 0.035f;
        streak.color = PALETTE[random.nextInt(PALETTE.length)];
        streak.alpha = 38 + random.nextInt(55);

        return streak;
    }

    /**
     * Sends the streaks outward from the caster. Call once when the domain reveals.
     */
    public void launch(int ticks) {
        if (closed) {
            return;
        }

        int baseDuration = Math.max(2, Math.min(59, ticks));

        for (Streak streak : streaks) {
            if (!streak.quad.isValid()) {
                continue;
            }

            streak.launched = true;
            streak.launchTick = tick;

            // Vary the travel time slightly so the wave doesn't look perfectly uniform.
            int duration = Math.max(2,
                    baseDuration - random.nextInt(Math.max(1, baseDuration / 4)));

            Location target = positionAt(streak, 0);

            QuadUtils.setColor(streak.quad, streak.color, streak.alpha);
            streak.quad.setTeleportDuration(duration);
            streak.quad.teleport(target);

            QuadUtils.apply(
                    streak.quad,
                    streak.length,
                    streak.thickness,
                    0f, 0f, 0f, 0f,
                    duration
            );
        }
    }

    /**
     * Call every tick from the domain manager. Actual position updates are throttled
     * internally, and the client interpolates between those positions.
     */
    public void tick() {
        if (closed) {
            return;
        }

        tick++;

        if (tick % UPDATE_EVERY != 0) {
            return;
        }

        for (Streak streak : streaks) {
            if (!streak.launched || !streak.quad.isValid()) {
                continue;
            }

            long elapsed = tick - streak.launchTick;

            streak.quad.setTeleportDuration(UPDATE_EVERY);
            streak.quad.teleport(positionAt(streak, elapsed));
        }
    }

    private Location positionAt(Streak streak, long elapsedTicks) {
        double t = elapsedTicks;

        // Slow angular drift, plus a small independent wobble in depth and height.
        double theta = streak.theta + streak.angularSpeed * t;
        double rho = streak.rho
                + radius * 0.025 * Math.sin(t * 0.003 + streak.phase);
        double y = streak.height
                + radius * 0.035 * Math.sin(t * 0.0023 + streak.phase * 1.7);

        return center.clone().add(
                Math.cos(theta) * rho,
                y,
                Math.sin(theta) * rho
        );
    }

    /**
     * Draws the streaks back toward the caster and shrinks them.
     * Call close() once the collapse animation has finished.
     */
    public void collapse(int ticks, Location to) {
        if (closed) {
            return;
        }

        int duration = Math.max(2, Math.min(59, ticks));

        for (Streak streak : streaks) {
            if (!streak.quad.isValid()) {
                continue;
            }

            streak.launched = false;
            streak.quad.setTeleportDuration(duration);
            streak.quad.teleport(to);

            QuadUtils.apply(
                    streak.quad,
                    0.001f,
                    streak.thickness,
                    0f, 0f, 0f, 0f,
                    duration
            );
        }
    }

    public void close() {
        if (closed) {
            return;
        }
        closed = true;

        for (Streak streak : streaks) {
            if (streak.quad != null && streak.quad.isValid()) {
                streak.quad.remove();
            }
        }
        streaks.clear();
    }
}
