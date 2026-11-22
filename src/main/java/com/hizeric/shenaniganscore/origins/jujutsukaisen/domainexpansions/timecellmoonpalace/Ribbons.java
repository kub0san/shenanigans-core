package com.hizeric.shenaniganscore.origins.jujutsukaisen.domainexpansions.timecellmoonpalace;

import com.hizeric.shenaniganscore.core.display.DisplayUtils;
import com.hizeric.shenaniganscore.core.display.TransformUtils;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Display;

import java.util.ArrayList;
import java.util.List;

/**
 * Film curtains hanging in the void: the domain's Projection Sorcery motif made physical.
 *
 * <p>4-8 long black strips (scaled to the radius) circle the arena at about two thirds of the
 * radius. Each carries a column of white frame windows.</p>
 *
 * <ul>
 *   <li><b>Unroll</b> (expansion): a strip drops from the crown to full length, its windows
 *       blinking into existence as the leading edge passes them.</li>
 *   <li><b>Run</b> (domain): a chase light travels down every strip two film frames at a time,
 *       as if the film were being pulled through a gate. Strips sway in <i>whole frames</i>
 *       (a snap every 6 frames, no easing): stop-motion, on purpose, against the smooth
 *       breathing of the flesh.</li>
 *   <li><b>Snap</b> (collapse): the strips drop away and shrink one after another.</li>
 * </ul>
 *
 * <p>Cost: about 55 displays at the default radius. Nothing is moved per tick: sway is a
 * transform change, the chase is a brightness change, and the server only sends data
 * that actually changed.</p>
 */
final class Ribbons {

    private static final float SEED = 0.01f;
    private static final float BAR_W = 1.5f, BAR_T = 0.06f;
    private static final float WIN_W = 0.95f, WIN_H = 0.75f, WIN_T = 0.10f;
    private static final double PITCH = 1.5;

    private static final Display.Brightness DIM = new Display.Brightness(4, 4);
    private static final Display.Brightness LIT = new Display.Brightness(15, 15);

    private static final class Ribbon {
        final int index;
        final BlockDisplay bar;
        final List<BlockDisplay> windows = new ArrayList<>();
        final Location center;
        final float yaw;
        final float length;
        final double at;
        final double phase;
        boolean unrolled;
        boolean dropped;
        int windowsShown;

        Ribbon(int index, BlockDisplay bar, Location center, float yaw, float length, double at, double phase) {
            this.index = index;
            this.bar = bar;
            this.center = center;
            this.yaw = yaw;
            this.length = length;
            this.at = at;
            this.phase = phase;
        }
    }

    private final Session s;
    private final List<Ribbon> ribbons = new ArrayList<>();
    private boolean built;
    private long lastStep = -1;
    private long lastSway = -1;

    Ribbons(Session s) {
        this.s = s;
    }

    private BlockDisplay spawn(Material m, Location loc, float yaw, int bright) {
        BlockDisplay d = DisplayUtils.spawnBlock(loc, m, 2.0f, bright);
        TransformUtils.applyTransform(d, SEED, SEED, SEED, yaw, 0f, 0f, 0);
        s.spawnedDisplays.add(d);
        return d;
    }

    /**
     * Builds the ribbons.
     *
     * <p>The strips hang from the crown like the walls of the domain, so they
     * unroll while the shell rises and not during the floor lead-in.</p>
     *
     * @param floorSeconds length of the floor lead-in
     * @param shellSeconds length of the shell phase that follows it
     */
    void build(double floorSeconds, double shellSeconds) {
        if (built) return;
        built = true;

        double R = s.getRadius();
        int n = (int) Math.max(4, Math.min(8, Math.round(R / 5.0)));
        float len = (float) Math.min(R * 0.38, 16.0);
        double rho = R * 0.66;
        double yTop = R * 0.5;
        int wins = Math.max(2, (int) ((len - 0.8) / PITCH));

        for (int i = 0; i < n; i++) {
            double th = Math.PI * 2.0 * i / n + 0.4;
            double x = Math.cos(th) * rho;
            double z = Math.sin(th) * rho;
            // local frame: the strip's flat face points at the arena centre
            float yaw = s.getCasterYaw() + (float) (Math.toDegrees(th) + 90.0);

            Location top = s.local(x, yTop, z);
            Location mid = s.local(x, yTop - len / 2.0, z);
            BlockDisplay bar = spawn(Material.BLACK_CONCRETE, top, yaw, DIM.getBlockLight());
            Ribbon r = new Ribbon(i, bar, mid, yaw, len,
                    floorSeconds + shellSeconds * 0.1 + i * 0.18, th * 1.7);
            for (int j = 0; j < wins; j++) {
                r.windows.add(spawn(Material.WHITE_CONCRETE, s.local(x, yTop - 0.8 - j * PITCH, z), yaw, DIM.getBlockLight()));
            }
            ribbons.add(r);
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Expansion: unroll from the crown
    // ─────────────────────────────────────────────────────────────────────────

    void tickExpand(double t) {
        World w = s.world();
        for (Ribbon r : ribbons) {
            if (!r.unrolled && t >= r.at) {
                r.unrolled = true;
                if (r.bar.isValid()) {
                    // centre slides down while the length grows: the top edge stays pinned to the crown
                    r.bar.setTeleportDuration(12);
                    r.bar.teleport(r.center);
                    TransformUtils.applyTransform(r.bar, BAR_W, r.length, BAR_T, r.yaw, 0f, 0f, 12);
                }
                if (w != null) w.playSound(r.center, Sound.BLOCK_CHAIN_PLACE, 1.0f, 0.5f);
            }
            if (r.unrolled) {
                int should = (int) Math.min(r.windows.size(), Math.max(0.0, (t - r.at - 0.1) / 0.09));
                while (r.windowsShown < should) {
                    BlockDisplay win = r.windows.get(r.windowsShown++);
                    if (win.isValid()) TransformUtils.applyTransform(win, WIN_W, WIN_H, WIN_T, r.yaw, 0f, 0f, 4);
                }
            }
        }
    }

    void finishExpand() {
        for (Ribbon r : ribbons) {
            if (!r.unrolled) {
                r.unrolled = true;
                if (r.bar.isValid()) {
                    r.bar.setTeleportDuration(4);
                    r.bar.teleport(r.center);
                    TransformUtils.applyTransform(r.bar, BAR_W, r.length, BAR_T, r.yaw, 0f, 0f, 4);
                }
            }
            while (r.windowsShown < r.windows.size()) {
                BlockDisplay win = r.windows.get(r.windowsShown++);
                if (win.isValid()) TransformUtils.applyTransform(win, WIN_W, WIN_H, WIN_T, r.yaw, 0f, 0f, 4);
            }
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Domain: the film runs
    // ─────────────────────────────────────────────────────────────────────────

    void tickDomain() {
        long f = s.frame();

        // Chase light: advances every 2 frames, travels down the strip
        long step = f / 2;
        if (step != lastStep) {
            lastStep = step;
            for (Ribbon r : ribbons) {
                for (int j = 0; j < r.windows.size(); j++) {
                    BlockDisplay win = r.windows.get(j);
                    if (!win.isValid()) continue;
                    boolean lit = Math.floorMod(step - j + r.index * 3L, 5L) == 0L;
                    win.setBrightness(lit ? LIT : DIM);
                }
            }
        }

        // Sway: a hard snap every 6 frames, never eased
        long sway = f / 6;
        if (sway != lastSway) {
            lastSway = sway;
            for (Ribbon r : ribbons) {
                float yaw = r.yaw + (float) (Math.sin(sway * 0.55 + r.phase) * 12.0);
                if (r.bar.isValid()) TransformUtils.applyTransform(r.bar, BAR_W, r.length, BAR_T, yaw, 0f, 0f, 0);
                for (BlockDisplay win : r.windows) {
                    if (win.isValid()) TransformUtils.applyTransform(win, WIN_W, WIN_H, WIN_T, yaw, 0f, 0f, 0);
                }
            }
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Collapse: the film snaps and falls
    // ─────────────────────────────────────────────────────────────────────────

    void tickCollapse(double t) {
        for (Ribbon r : ribbons) {
            if (r.dropped || t < 0.15 + r.index * 0.06) continue;
            r.dropped = true;
            drop(r.bar, r.yaw);
            for (BlockDisplay win : r.windows) drop(win, r.yaw);
        }
    }

    private void drop(BlockDisplay d, float yaw) {
        if (!d.isValid()) return;
        Location l = d.getLocation().add(0, -5.0, 0);
        d.setTeleportDuration(10);
        d.teleport(l);
        TransformUtils.applyTransform(d, SEED, SEED, SEED, yaw, 0f, 0f, 10);
    }

    void clear() {
        for (Ribbon r : ribbons) {
            if (r.bar.isValid()) r.bar.remove();
            for (BlockDisplay win : r.windows) {
                if (win.isValid()) win.remove();
            }
        }
        ribbons.clear();
    }
}
