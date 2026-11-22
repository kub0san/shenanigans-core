package com.hizeric.shenaniganscore.origins.jujutsukaisen.domainexpansions.unlimitedvoid;

import com.hizeric.shenaniganscore.ShenanigansCore;
import com.hizeric.shenaniganscore.core.display.DisplayUtils;
import com.hizeric.shenaniganscore.core.display.QuadUtils;
import com.hizeric.shenaniganscore.core.display.TransformUtils;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Display;
import org.bukkit.entity.TextDisplay;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * THE WAVEFRONT: a white-hot burst that races out of Gojo as the domain opens.
 *
 * <p>Redesigned from the old &quot;120 independently-rendered glass plates&quot; bubble, which
 * could never tile a sphere evenly and read as a lumpy, faceted cage (Fibonacci points are not a
 * square tiling, and glass + fullbright + overlapping faces guarantees seams). The wavefront is
 * three display-entity beats, all seeded at the caster and revealed by a single interpolated
 * move — no per-tick work, no physics, no tiling:</p>
 *
 * <ol>
 *   <li><b>PUNCH</b> (t&nbsp;=&nbsp;0): one {@code CENTER}-billboard white quad at Gojo blooms
 *       from nothing to a huge white pop while it fades, layered with a wide horizontal
 *       <i>Infinity streak</i> (a VERTICAL-billboard quad: white core over an ice-blue rim) and
 *       one faint violet vertical twin. This is the white flash viewers on top of Gojo see; the
 *       per-viewer screen cut (driven by {@code Cut}) carries the same beat for everyone
 *       else.</li>
 *   <li><b>SHELL</b> (t&nbsp;=&nbsp;0): a sparse sphere of fullbright concrete motes (white /
 *       ice-blue / violet) on Fibonacci directions, seeded collapsed at the caster and sent out
 *       in one interpolated teleport — the client interpolates linearly, which is exactly what
 *       {@code Cut}'s per-viewer screen-cut delay assumes (see {@code Cut.LENS_SECONDS}). An
 *       equatorial ring of larger, white-glowing motes sells the &quot;racing outward&quot;
 *       plane. Dots, not plates: nothing tiles, nothing facets.</li>
 *   <li><b>FIZZLE</b> ({@link #dissolve(int)}): motes shrink to nothing in place; quads shrink
 *       and go transparent; every entity is removed automatically a couple of ticks later.
 *       Dissolving can no longer leak remnants — {@link #dissolve(int)} schedules
 *       {@link #close()} itself, so callers no longer need to remember it.</li>
 * </ol>
 *
 * <p>Entity budget: {@value #BASE_MOTES} motes (scaled by {@code quality}, clamped to
 * 60–300) + {@value #EQUATOR_MOTES} equatorial motes + 4 quads ≈ 205–235 transient entities,
 * created once, moved once, removed once. The wavefront never ticks.</p>
 */
public final class Lens {

    private static final float SEED = 0.01f;
    private static final int BASE_MOTES = 170;
    private static final int EQUATOR_MOTES = 28;
    /** Punch / streak reveal times, in ticks. */
    private static final int PUNCH_TICKS = 6;
    private static final int STREAK_TICKS = 8;
    /** Ticks to wait after the dissolve shrink before every entity is removed. */
    private static final int AUTO_CLOSE_DELAY = 2;
    private static final float VIEW_RANGE = 4.0f;

    private static final Color ICE = Color.fromRGB(150, 215, 255);
    private static final Color VIOLET = Color.fromRGB(170, 120, 255);
    private static final Color RING_GLOW = Color.fromRGB(200, 235, 255);

    private final World world;
    private final Location center;
    private final double maxRadius;
    private final List<BlockDisplay> motes = new ArrayList<>();
    private final List<double[]> dirs = new ArrayList<>();
    private final List<Float> sizes = new ArrayList<>();
    private TextDisplay punch;
    private TextDisplay streakRim;
    private TextDisplay streakCore;
    private TextDisplay streakVertical;
    private final List<TextDisplay> quads = new ArrayList<>();
    private double radius;
    private boolean closed;

    public Lens(Location center, double maxRadius, double quality) {
        this.center = center.clone();
        this.world = center.getWorld();
        this.maxRadius = Math.max(4.0, maxRadius);
        int moteCount = (int) Math.max(60, Math.min(300, Math.round(BASE_MOTES * quality)));
        seed(moteCount);
    }

    // ------------------------------------------------------------------
    // Build (everything starts as an invisible seed at the caster)
    // ------------------------------------------------------------------

    private void seed(int shellMotes) {
        if (world == null) return;
        Random rnd = new Random(0xC07L);

        // Beat 2, part 1: the Fibonacci sphere of sparse motes.
        double golden = Math.PI * (3.0 - Math.sqrt(5.0));
        for (int i = 0; i < shellMotes; i++) {
            double y = 1.0 - (2.0 * i + 1.0) / shellMotes;
            double r = Math.sqrt(Math.max(0.0, 1.0 - y * y));
            double theta = golden * i;
            dirs.add(new double[]{Math.cos(theta) * r, y, Math.sin(theta) * r});
            sizes.add(0.18f + rnd.nextFloat() * 0.32f);

            int roll = rnd.nextInt(100);
            Material mat = roll < 60 ? Material.WHITE_CONCRETE
                    : roll < 85 ? Material.LIGHT_BLUE_CONCRETE : Material.PURPLE_CONCRETE;
            BlockDisplay mote = DisplayUtils.spawnBlock(center, mat, VIEW_RANGE, 15);
            seedMote(mote);
            motes.add(mote);
        }

        // Beat 2, part 2: the equatorial shockwave ring — same Fibonacci trick, plane y = 0,
        // larger and white-glowing, so the expansion reads as one racing wavefront.
        for (int i = 0; i < EQUATOR_MOTES; i++) {
            double a = Math.PI * 2.0 * i / EQUATOR_MOTES;
            dirs.add(new double[]{Math.cos(a), 0.0, Math.sin(a)});
            sizes.add(0.6f + rnd.nextFloat() * 0.25f);
            BlockDisplay mote = DisplayUtils.spawnBlock(center, Material.WHITE_CONCRETE, VIEW_RANGE, 15);
            mote.setGlowing(true);
            mote.setGlowColorOverride(RING_GLOW);
            seedMote(mote);
            motes.add(mote);
        }

        // Beat 1: the punch and the Infinity streaks. All four quads start transparent and tiny.
        punch = QuadUtils.spawn(center, Display.Billboard.CENTER, VIEW_RANGE, false);
        QuadUtils.setColor(punch, Color.WHITE, 235);
        quads.add(punch);

        streakRim = QuadUtils.spawn(center, Display.Billboard.VERTICAL, VIEW_RANGE, false);
        QuadUtils.setColor(streakRim, ICE, 110);
        quads.add(streakRim);

        streakCore = QuadUtils.spawn(center, Display.Billboard.VERTICAL, VIEW_RANGE, false);
        QuadUtils.setColor(streakCore, Color.WHITE, 235);
        quads.add(streakCore);

        streakVertical = QuadUtils.spawn(center, Display.Billboard.VERTICAL, VIEW_RANGE, false);
        QuadUtils.setColor(streakVertical, VIOLET, 115);
        quads.add(streakVertical);
    }

    private void seedMote(BlockDisplay mote) {
        TransformUtils.applyTransform(mote, SEED, SEED, SEED, 0f, 0f, 0f, 0);
        mote.setTeleportDuration(0);
    }

    // ------------------------------------------------------------------
    // Beats 1 + 2: fire everything (single interpolated move)
    // ------------------------------------------------------------------

    /**
     * Sends the punch, the streaks and every mote to their final state, interpolated over
     * {@code ticks} (1-59). The shell expands linearly with the client-side teleport
     * interpolation; {@code Cut}'s per-viewer screen-cut delay is derived from the same constant,
     * so the two stay in step as long as the motion stays linear.
     */
    public void expand(double targetRadius, int ticks) {
        if (closed || world == null) return;
        int t = Math.max(1, Math.min(59, ticks));
        double r = Math.max(0.5, Math.min(maxRadius, targetRadius));
        radius = r;

        // Beat 1 — PUNCH: a white pop washing over the arena.
        float punchSize = (float) Math.max(24.0, r * 1.6);
        QuadUtils.apply(punch, punchSize, punchSize, 0f, 0f, 0f, 0f, PUNCH_TICKS);
        // Infinity streak: white core over an ice-blue rim, then the faint violet vertical twin.
        float streak = (float) (r * 2.1);
        QuadUtils.apply(streakRim, streak * 1.03f, 0.75f, 0f, 0f, 0f, 0f, STREAK_TICKS);
        QuadUtils.apply(streakCore, streak, 0.42f, 0f, 0f, 0f, 0f, STREAK_TICKS);
        QuadUtils.apply(streakVertical, 0.4f, (float) (r * 1.05), 0f, 0f, 0f, 0f, STREAK_TICKS);

        // Beat 2 — SHELL: one interpolated teleport per mote. Nothing ticks.
        for (int i = 0; i < motes.size(); i++) {
            BlockDisplay mote = motes.get(i);
            if (!mote.isValid()) continue;
            double[] d = dirs.get(i);
            Location target = center.clone().add(d[0] * r, d[1] * r, d[2] * r);
            float size = sizes.get(i);

            TransformUtils.applyTransform(mote, size, size, size, 0f, 0f, 0f, t);
            mote.setTeleportDuration(t);
            mote.teleport(target);
        }
    }

    /**
     * Shrinks the wavefront to nothing in place over {@code ticks}, then removes every entity
     * automatically a couple of ticks later. This method owns the full lifecycle: callers
     * ({@code Cut}) must not remove anything themselves.
     */
    public void dissolve(int ticks) {
        if (closed) return;
        int t = Math.max(1, Math.min(59, ticks));
        for (BlockDisplay mote : motes) {
            if (mote.isValid()) TransformUtils.applyTransform(mote, SEED, SEED, SEED, 0f, 0f, 0f, t);
        }
        for (TextDisplay quad : quads) {
            if (!quad.isValid()) continue;
            QuadUtils.apply(quad, 0.01f, 0.01f, 0f, 0f, 0f, 0f, t);
            QuadUtils.setColor(quad, Color.WHITE, 0);
        }
        ShenanigansCore.runTaskLater( this::close, t + AUTO_CLOSE_DELAY);
    }

    public double radius() {
        return radius;
    }

    /** Removes every entity. Safe to call more than once. */
    public void close() {
        if (closed) return;
        closed = true;
        for (BlockDisplay mote : motes) {
            if (mote != null && mote.isValid()) mote.remove();
        }
        for (TextDisplay quad : quads) {
            if (quad != null && quad.isValid()) quad.remove();
        }
        motes.clear();
        dirs.clear();
        sizes.clear();
        quads.clear();
    }
}
