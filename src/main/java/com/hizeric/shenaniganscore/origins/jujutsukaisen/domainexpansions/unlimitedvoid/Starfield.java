package com.hizeric.shenaniganscore.origins.jujutsukaisen.domainexpansions.unlimitedvoid;

import com.hizeric.shenaniganscore.ShenanigansCore;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Transformation;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Small points of light suspended at different depths in Unlimited Void.
 *
 * <p>These are not placed on a spherical dome: their varied distances keep the field
 * from reading as a star-covered ceiling. ItemDisplay models are centered on their
 * entity location, so their transforms use zero translation rather than BlockDisplay
 * unit-cube pivot compensation.</p>
 */
public final class Starfield {

    private static final int BASE_COUNT = 64;
    private static final float SEED_SCALE = 0.001f;

    // Item-display scale: small enough to read as points, but not all identical.
    private static final float MIN_SCALE = 0.045f;
    private static final float MAX_SCALE = 0.085f;

    private static final Color[] GLOW_COLORS = {
            Color.fromRGB(220, 240, 255),
            Color.fromRGB(145, 205, 255),
            Color.fromRGB(185, 160, 255)
    };

    private static final class Star {
        private final ItemDisplay display;
        private final float scale;
        private boolean twinkling;

        private Star(ItemDisplay display, float scale) {
            this.display = display;
            this.scale = scale;
        }
    }

    private final World world;
    private final Random random = new Random();
    private final List<Star> stars = new ArrayList<>();
    private final List<Entity> sink;

    private boolean ignited;
    private boolean collapsing;
    private boolean closed;

    public Starfield(Location center, double radius, double quality, List<Entity> sink) {
        this.world = center.getWorld();
        this.sink = sink;

        if (world == null || !Double.isFinite(radius) || radius <= 0.0) {
            return;
        }

        int count = (int) Math.max(24, Math.min(120,
                Math.round(BASE_COUNT * Math.max(0.25, quality))));

        Location origin = center.clone();

        // Keep the immediate space around the caster relatively clear.
        double outerRadius = radius * 0.92;
        double innerRadius = Math.min(outerRadius * 0.45, Math.max(1.5, radius * 0.12));

        for (int i = 0; i < count; i++) {
            Location at = randomPointInShell(origin, innerRadius, outerRadius);

            float scale = MIN_SCALE + random.nextFloat() * (MAX_SCALE - MIN_SCALE);
            ItemDisplay display = world.spawn(at, ItemDisplay.class, d -> {
                d.setItemStack(new ItemStack(Material.NETHER_STAR));
                d.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.FIXED);
                d.setBillboard(Display.Billboard.CENTER);
                d.setBrightness(new Display.Brightness(15, 15));
                d.setViewRange(6.0f);
                d.setPersistent(false);
                d.setShadowRadius(0.0f);
                d.setShadowStrength(0.0f);

                // ItemDisplay models are centered at the entity, so translation is zero.
                setScale(d, SEED_SCALE, 0);
                d.setTeleportDuration(0);

                // Keep most stars as clean points; a few get a colored edge.
                if (random.nextInt(5) == 0) {
                    d.setGlowing(true);
                    d.setGlowColorOverride(
                            GLOW_COLORS[random.nextInt(GLOW_COLORS.length)]
                    );
                }
            });

            if (sink != null) {
                sink.add(display);
            }
            stars.add(new Star(display, scale));
        }
    }

    /**
     * Places points uniformly by volume between two radii, rather than on one shell.
     */
    private Location randomPointInShell(Location origin, double innerRadius, double outerRadius) {
        double y = random.nextDouble() * 2.0 - 1.0;
        double angle = random.nextDouble() * Math.PI * 2.0;
        double horizontal = Math.sqrt(Math.max(0.0, 1.0 - y * y));

        double inner3 = innerRadius * innerRadius * innerRadius;
        double outer3 = outerRadius * outerRadius * outerRadius;
        double distance = Math.cbrt(inner3 + random.nextDouble() * (outer3 - inner3));

        double x = horizontal * Math.cos(angle);
        double z = horizontal * Math.sin(angle);

        return origin.clone().add(x * distance, y * distance, z * distance);
    }

    /**
     * Makes the field appear in staggered groups instead of all at once.
     */
    public void ignite() {
        if (closed || collapsing || ignited) {
            return;
        }
        ignited = true;

        for (Star star : stars) {
            if (!star.display.isValid()) {
                continue;
            }

            int delay = random.nextInt(18);
            int duration = 8 + random.nextInt(13);

            star.display.setInterpolationDelay(delay);
            setScale(star.display, star.scale, duration);
        }
    }

    /**
     * Pulse a few stars. Call roughly every 12–16 ticks, not every tick.
     */
    public void twinkle() {
        if (closed || collapsing || !ignited || stars.isEmpty()) {
            return;
        }

        int pulses = Math.min(2, stars.size());
        for (int i = 0; i < pulses; i++) {
            Star star = stars.get(random.nextInt(stars.size()));

            if (star.twinkling || !star.display.isValid()) {
                continue;
            }

            star.twinkling = true;
            star.display.setInterpolationDelay(0);
            setScale(star.display, star.scale * 1.3f, 3);

            ShenanigansCore.runTaskLater(
                    () -> {
                        if (star.display.isValid() && !closed) {
                            star.display.setInterpolationDelay(0);
                            setScale(star.display, star.scale, 6);
                        }
                        star.twinkling = false;
                    },
                    4L
            );
        }
    }

    /**
     * Draw the stars back into tiny points, then remove them when the animation ends.
     */
    public void collapse(int ticks) {
        if (closed || collapsing) {
            return;
        }
        collapsing = true;

        int duration = Math.max(2, Math.min(59, ticks));
        for (Star star : stars) {
            if (star.display.isValid()) {
                star.display.setInterpolationDelay(0);
                setScale(star.display, SEED_SCALE, duration);
            }
        }

        ShenanigansCore.runTaskLater(
                this::close,
                duration + 2L
        );
    }

    private static void setScale(ItemDisplay display, float scale, int duration) {
        display.setInterpolationDelay(0);
        display.setInterpolationDuration(duration);
        display.setTransformation(new Transformation(
                new Vector3f(),
                new Quaternionf(),
                new Vector3f(scale, scale, scale),
                new Quaternionf()
        ));
    }

    public void close() {
        if (closed) {
            return;
        }
        closed = true;

        for (Star star : stars) {
            if (star.display.isValid()) {
                star.display.remove();
            }
        }
        stars.clear();
    }

    /**
     * This changes the player's local time; it does not by itself create a blank sky.
     * Track and restore any pre-existing player-time override in your domain manager.
     */
    public static void applyVoidSky(Player player) {
        player.setPlayerTime(18000L, false);
    }

    public static void resetSky(Player player) {
        player.resetPlayerTime();
    }
}
