package com.hizeric.shenaniganscore.origins.jujutsukaisen.domainexpansions.unlimitedvoid;

import com.hizeric.shenaniganscore.ShenanigansCore;
import com.hizeric.shenaniganscore.core.display.ScreenOverlay;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Random;
import com.hizeric.shenaniganscore.core.util.MathUtil;

/**
 * The opening of the Unlimited Void.
 *
 * <p>A white-hot wavefront ({@link Lens}) races out of the caster. Every viewer's screen cuts to
 * white the instant the wavefront passes them (so the nearest victim goes first), and then runs
 * the same sequence on their own screen:</p>
 *
 * <pre>
 *   WHITE     0.00-0.35  pure white, hard cut; the world is repainting behind it
 *   SLICE     0.35-0.75  the white splits into horizontal bands that tint cyan/blue/violet/magenta
 *   STREAK    0.75-1.65  the bands slide sideways, accelerating, with RGB-split ghosts
 *   SPLATTER  1.65-2.15  the bands break into drops that scatter outward; in the world the same
 *                        colours launch from Gojo as streaks
 *   BLACK     2.15-2.55  near-black, almost silent
 *   REVEAL    2.55-3.05  the screen fades, and the void is there
 * </pre>
 *
 * <p>It runs on its own task, independent of the session, and cleans up after itself. Safe to
 * start from the debug command.</p>
 *
 * <p>Layers per viewer: 1 background + {@link #LINES} lines + {@link #GHOSTS} ghost copies.</p>
 */
public final class Cut {

    private static final int LINES = 28;
    private static final int GHOSTS = 10;
    /** Index of the background layer. Lines are 1..LINES, ghosts follow. */
    private static final int BG = 0;

    private static final double T_WHITE = 0.35;
    private static final double T_SLICE = 0.75;
    private static final double T_STREAK = 1.65;
    private static final double T_SPLAT = 2.15;
    private static final double T_BLACK = 2.55;
    private static final double T_REVEAL = 3.05;

    /**
     * Seconds the wavefront takes to reach the edge of the domain. The per-viewer screen-cut
     * delay below is derived from this constant as {@code delay = LENS_SECONDS * d / radius} and
     * stays correct ONLY because the wavefront's motes are interpolated <b>linearly</b> by the
     * client over the same duration (see {@code Lens.expand}). If the expansion easing ever
     * changes, this delay must be computed from the easing function or the screens will desync
     * from the wavefront.
     */
    public static final double LENS_SECONDS = 0.7;

    private static final Color[] COLORS = {
            Color.fromRGB(110, 235, 255),
            Color.fromRGB(70, 170, 255),
            Color.fromRGB(150, 120, 255),
            Color.fromRGB(215, 120, 255),
            Color.fromRGB(235, 245, 255),
            Color.fromRGB(255, 95, 190)
    };
    private static final Color GHOST_RED = Color.fromRGB(255, 60, 110);
    private static final Color GHOST_BLUE = Color.fromRGB(60, 120, 255);
    private static final Color DEEP = Color.fromRGB(4, 6, 18);

    /** One band's fixed look; generated once from a seed, so every viewer sees the same lines. */
    private static final class Line {
        float y, thick, width, speed, dropX, dropY;
        int dir;
        Color color;
    }

    /** One viewer's run of the sequence. */
    private static final class Run {
        final Player player;
        final ScreenOverlay screen;
        final double delay;
        boolean started;
        boolean done;
        float[] x = new float[LINES];

        Run(Player player, double delay) {
            this.player = player;
            this.screen = new ScreenOverlay(player);
            this.delay = delay;
        }
    }

    private final Location center;
    private final double radius;
    private final Streaks streaks;
    private final Lens lens;
    private final List<Run> runs = new ArrayList<>();
    private final Line[] lines = new Line[LINES];
    private long startMs;
    private boolean wavefrontSent;
    private boolean wavefrontDissolved;
    private boolean launched;
    private BukkitRunnable task;

    /**
     * @param streaks the world-space streaks to launch at the splatter (may be null)
     */
    public Cut(Location center, double radius, double quality, Collection<Player> viewers, Streaks streaks) {
        this.center = center.clone();
        this.radius = Math.max(4.0, radius);
        this.streaks = streaks;
        this.lens = new Lens(center, radius * 1.12, quality);

        Random rnd = new Random(0xC07L);
        for (int i = 0; i < LINES; i++) {
            Line l = new Line();
            l.y = -0.85f + 1.7f * (i + 0.5f) / LINES + (rnd.nextFloat() - 0.5f) * 0.03f;
            float[] thicknesses = {0.006f, 0.012f, 0.02f, 0.035f, 0.06f, 0.1f};
            l.thick = thicknesses[rnd.nextInt(thicknesses.length)];
            l.width = 0.5f + rnd.nextFloat() * 1.1f;
            l.speed = 2.0f + rnd.nextFloat() * 7.0f;
            l.dir = (i % 3 == 0) ? -1 : 1;
            l.color = COLORS[rnd.nextInt(COLORS.length)];
            double ang = rnd.nextDouble() * Math.PI * 2.0;
            l.dropX = (float) Math.cos(ang);
            l.dropY = (float) Math.sin(ang);
            lines[i] = l;
        }

        for (Player p : viewers) {
            if (p == null || !p.isOnline() || p.getWorld() != center.getWorld()) continue;
            double d = p.getLocation().distance(center);
            // Linear delay: the wavefront's motes interpolate linearly on the client over
            // LENS_SECONDS (see Lens.expand), so the screen cut lands when the wavefront reaches
            // the viewer. Do not ease the shell without retuning this.
            double delay = Math.min(LENS_SECONDS, LENS_SECONDS * d / this.radius);
            runs.add(new Run(p, delay));
        }
    }

    /** Starts the sequence. */
    public void start() {
        startMs = System.currentTimeMillis();
        World world = center.getWorld();
        if (world != null) {
            world.playSound(center, Sound.BLOCK_BEACON_DEACTIVATE, 2.0f, 1.9f);
            world.playSound(center, Sound.ENTITY_LIGHTNING_BOLT_THUNDER, 0.8f, 1.9f);
            world.playSound(center, Sound.ENTITY_WARDEN_SONIC_BOOM, 0.7f, 1.8f);
        }

        task = new BukkitRunnable() {
            @Override
            public void run() {
                try {
                    double t = (System.currentTimeMillis() - startMs) / 1000.0;
                    step(t);
                    if (t > LENS_SECONDS + T_REVEAL + 0.3) {
                        cancel();
                        close();
                    }
                } catch (Throwable ex) {
                    ShenanigansCore.getInstance().getLogger().warning("Void Cut failed: " + ex);
                    cancel();
                    close();
                }
            }
        };
        task.runTaskTimer(ShenanigansCore.getInstance(), 0L, 1L);
    }

    /** Stops the sequence and removes every entity. Safe to call more than once. */
    public void close() {
        if (task != null && !task.isCancelled()) task.cancel();
        for (Run r : runs) r.screen.close();
        runs.clear();
        lens.close();
    }

    // ------------------------------------------------------------------
    // The world-space half: the wavefront
    // ------------------------------------------------------------------

    private void step(double t) {
        if (!wavefrontSent) {
            wavefrontSent = true;
            lens.expand(radius * 1.12, (int) Math.round(LENS_SECONDS * 20.0));
        }
        if (!wavefrontDissolved && t >= LENS_SECONDS + 0.1) {
            wavefrontDissolved = true;
            // dissolve() shrinks the wavefront AND removes every entity itself a couple of ticks
            // later; lens.close() below (called by close()) stays as an idempotent safety net.
            lens.dissolve(10);
        }

        for (Run r : runs) {
            if (r.done) continue;
            if (!r.player.isOnline()) {
                r.screen.close();
                r.done = true;
                continue;
            }
            double local = t - r.delay;
            if (local < 0) continue;
            if (!r.started) {
                r.started = true;
                r.screen.open(1 + LINES + GHOSTS);
                r.screen.cover(BG);
                r.screen.color(BG, Color.WHITE, 255);
                r.player.playSound(r.player.getLocation(), Sound.BLOCK_AMETHYST_BLOCK_RESONATE, 1.6f, 1.9f);
                r.player.playSound(r.player.getLocation(), Sound.ENTITY_ENDER_DRAGON_FLAP, 0.6f, 2.0f);
            }
            r.screen.tickFollow();
            runViewer(r, local);
            if (local > T_REVEAL) {
                r.screen.close();
                r.done = true;
            }
        }

        // The splatter, in the world: the colours rush out of Gojo.
        if (!launched && t >= LENS_SECONDS + T_STREAK) {
            launched = true;
            if (streaks != null) streaks.launch(24);
            World world = center.getWorld();
            if (world != null) {
                world.playSound(center, Sound.BLOCK_AMETHYST_CLUSTER_BREAK, 1.8f, 0.6f);
            }
        }
    }

    // ------------------------------------------------------------------
    // The screen-space half: one viewer's sequence
    // ------------------------------------------------------------------

    private void runViewer(Run r, double t) {
        ScreenOverlay s = r.screen;

        if (t < T_WHITE) {
            // Pure white with a faint shimmer.
            s.color(BG, (((int) (t * 40)) & 1) == 0 ? Color.WHITE : Color.fromRGB(238, 247, 255), 255);
            return;
        }

        if (t < T_STREAK) {
            double sliceP = MathUtil.clamp((t - T_WHITE) / (T_SLICE - T_WHITE), 0.0, 1.0);
            // The white background drains to deep blue-black as the lines appear.
            double drain = MathUtil.clamp((t - T_WHITE) / (T_STREAK - T_WHITE), 0.0, 1.0);
            s.color(BG, mix(Color.WHITE, DEEP, drain * drain), 255);
            drawLines(r, t, sliceP);
            return;
        }

        if (t < T_SPLAT) {
            s.color(BG, DEEP, 255);
            drawDrops(r, MathUtil.clamp((t - T_STREAK) / (T_SPLAT - T_STREAK), 0.0, 1.0));
            return;
        }

        if (t < T_BLACK) {
            s.color(BG, DEEP, 255);
            hideLines(s);
            return;
        }

        // Reveal: the background fades out.
        double fade = MathUtil.clamp((t - T_BLACK) / (T_REVEAL - T_BLACK), 0.0, 1.0);
        s.color(BG, DEEP, (int) Math.round(255.0 * (1.0 - fade)));
    }

    /** The slice and the streak: white splits into bands that tint and slide. */
    private void drawLines(Run r, double t, double sliceP) {
        ScreenOverlay s = r.screen;
        double streakP = MathUtil.clamp((t - T_SLICE) / (T_STREAK - T_SLICE), 0.0, 1.0);
        double accel = streakP * streakP;

        for (int i = 0; i < LINES; i++) {
            Line l = lines[i];
            int layer = 1 + i;

            // Each band appears a little after its neighbour, outward from the middle.
            double stagger = Math.abs(l.y) * 0.25;
            double p = MathUtil.clamp((sliceP - stagger) / (1.0 - stagger), 0.0, 1.0);
            if (p <= 0.0) {
                s.hide(layer);
                continue;
            }

            // Width: full screen at first, shortening to its own length as it starts to slide.
            float w = (float) MathUtil.lerp(2.4, l.width, MathUtil.clamp(streakP * 2.0, 0.0, 1.0));
            // Position: slides with acceleration.
            r.x[i] += l.dir * l.speed * (float) accel * 0.05f * (float) (0.4 + streakP);
            float limit = 1.0f + w * 0.5f;
            if (r.x[i] > limit) r.x[i] = -limit;
            if (r.x[i] < -limit) r.x[i] = limit;
            boolean wrapped = Math.abs(r.x[i]) >= limit - 0.05f;

            s.rect(layer, r.x[i], l.y, w, l.thick * (float) (0.4 + 0.6 * p), wrapped ? 0 : 2);
            // White at first, tinting to its own colour as the slice completes.
            s.color(layer, mix(Color.WHITE, l.color, p), 255);

            // RGB-split ghosts on the first few lines, once they are moving.
            if (i < GHOSTS && streakP > 0.05) {
                int gl = 1 + LINES + i;
                float off = 0.03f + 0.06f * (float) streakP;
                s.rect(gl, r.x[i] + l.dir * off, l.y + 0.004f, w, l.thick * 0.9f, wrapped ? 0 : 2);
                s.color(gl, (i % 2 == 0) ? GHOST_RED : GHOST_BLUE, 150);
            }
        }
    }

    /** The splatter: every band breaks into a drop that flies outward and shrinks. */
    private void drawDrops(Run r, double p) {
        ScreenOverlay s = r.screen;
        double eased = 1.0 - Math.pow(1.0 - p, 2.2);
        for (int i = 0; i < LINES; i++) {
            Line l = lines[i];
            int layer = 1 + i;
            float size = (float) Math.max(0.01, 0.16 * (1.0 - eased) * (0.4 + l.thick * 8));
            float cx = r.x[i] * (float) (1.0 - eased) + l.dropX * (float) (eased * 2.4);
            float cy = l.y * (float) (1.0 - eased) + l.dropY * (float) (eased * 1.6);
            s.rect(layer, cx, cy, size, size, (float) (eased * 140.0 * l.dir), 3);
            s.color(layer, l.color, (int) Math.round(255.0 * (1.0 - eased * 0.6)));

            if (i < GHOSTS) s.hide(1 + LINES + i);
        }
    }

    private void hideLines(ScreenOverlay s) {
        for (int i = 0; i < LINES + GHOSTS; i++) s.hide(1 + i);
    }

    // ------------------------------------------------------------------
    // Small helpers
    // ------------------------------------------------------------------

    private static Color mix(Color a, Color b, double t) {
        double k = MathUtil.clamp(t, 0.0, 1.0);
        return Color.fromRGB(
                (int) Math.round(a.getRed() + (b.getRed() - a.getRed()) * k),
                (int) Math.round(a.getGreen() + (b.getGreen() - a.getGreen()) * k),
                (int) Math.round(a.getBlue() + (b.getBlue() - a.getBlue()) * k));
    }
}
