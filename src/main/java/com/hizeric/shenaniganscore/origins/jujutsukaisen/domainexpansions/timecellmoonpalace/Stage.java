package com.hizeric.shenaniganscore.origins.jujutsukaisen.domainexpansions.timecellmoonpalace;

import com.hizeric.shenaniganscore.ShenanigansCore;
import com.hizeric.shenaniganscore.core.display.DisplayUtils;
import com.hizeric.shenaniganscore.core.display.TransformUtils;
import com.hizeric.shenaniganscore.core.util.MathUtil;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Display;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.ThreadLocalRandom;

/**
 * The main stage: <b>a straight path of flesh that leads to a large eye behind the caster</b>.
 * Everything is a {@code BlockDisplay} in the shrine's local frame (+z = the way the caster
 * faced, x = across).
 *
 * <h3>Animation language</h3>
 * Organic things move <b>smoothly</b> (unfurling, lids parting, tendrils). Film things move
 * in <b>whole frames</b> (gaze snaps, the heartbeat, pupil flares), on the 24 FPS clock.
 *
 * <ul>
 *   <li><b>Path</b>: unfurls toward the eye with a lit leading edge. In the domain a
 *       <i>peristaltic wave</i> (glow + ribs swelling) runs from the enemy side into the eye
 *       every 2.75s; when it arrives the pupil constricts and the flesh jolts.</li>
 *   <li><b>Lobes</b>: womb-like masses beating a stepped lub-dub on a 24-frame cycle,
 *       synced to the heartbeat sound, dripping blood.</li>
 *   <li><b>Eye</b>: lid, sclera, veins, two-tone iris and pupil built from bars. It opens in
 *       two beats (a slit of light, then the lids part), snaps its gaze toward the nearest
 *       victim, blinks by flattening to a slit, and its pupil flares when a victim freezes
 *       or loses a limb.</li>
 * </ul>
 */
final class Stage {

    private static final float SEED = 0.01f;
    private static final float SLIT = 0.12f;

    private static final Display.Brightness[] LIGHT = new Display.Brightness[16];

    static {
        for (int i = 0; i < 16; i++) LIGHT[i] = new Display.Brightness(i, i);
    }

    private static Display.Brightness light(int b) {
        return LIGHT[(int) MathUtil.clamp(b, 0, 15)];
    }

    private static final BlockData BLOOD_CRUMB = Material.REDSTONE_BLOCK.createBlockData();

    private static final class Part {
        final BlockDisplay d;
        final float sx, sy, sz, yaw;
        final double at;
        final int base;
        final boolean glow;
        boolean shown;
        double settle;

        Part(BlockDisplay d, float sx, float sy, float sz, float yaw, double at, int base, boolean glow) {
            this.d = d;
            this.sx = sx;
            this.sy = sy;
            this.sz = sz;
            this.yaw = yaw;
            this.at = at;
            this.base = base;
            this.glow = glow;
        }
    }

    /** One slice of the path: what the peristaltic wave lights and swells. */
    private static final class Seg {
        final Part base, rib, vein, nodL, nodR;
        boolean dead;

        Seg(Part base, Part rib, Part vein, Part nodL, Part nodR) {
            this.base = base;
            this.rib = rib;
            this.vein = vein;
            this.nodL = nodL;
            this.nodR = nodR;
        }
    }

    /** One bar of the eye. layer: 0 lid, 1 sclera, 2 iris, 3 inner iris, 4 pupil, 5 vein. */
    private static final class Row {
        final BlockDisplay d;
        final Location home;
        final Location center;
        final float sx, sy, sz, yaw, lean;
        final int layer;

        Row(BlockDisplay d, Location home, Location center, float sx, float sy, float sz,
            float yaw, float lean, int layer) {
            this.d = d;
            this.home = home;
            this.center = center;
            this.sx = sx;
            this.sy = sy;
            this.sz = sz;
            this.yaw = yaw;
            this.lean = lean;
            this.layer = layer;
        }
    }

    private final Session s;
    private final List<Part> parts = new ArrayList<>();
    private final List<Part> lobes = new ArrayList<>();
    private final List<Seg> segs = new ArrayList<>();
    private final List<Row> rows = new ArrayList<>();

    private boolean built, eyeOpen, closed, collapseInit, eyeGone, lobesGone;
    private double eyeW, eyeH, eyeCy, eyeZ;
    private final Vector gaze = new Vector(0, 0, 0);

    private float lobeScale = 1.0f;
    private float pupilFactor = 1.0f;
    private float flareFactor = 1.0f;
    private long flareUntil, joltUntil;
    private long lastCycle = -1, lastGaze = -1, lastDrip = -1;
    private int waveHi = -99;

    Stage(Session s) {
        this.s = s;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Reactions (called by Rule24)
    // ─────────────────────────────────────────────────────────────────────────

    /** A victim's cells froze: the pupil dilates wide and the flesh swells. */
    void onFreeze() {
        flare(14, 1.45f);
        jolt(6);
    }

    /** A limb came off: the pupil snaps to a pinprick and the flesh convulses. */
    void onSever() {
        flare(10, 0.45f);
        jolt(10);
    }

    private void flare(int frames, float factor) {
        flareUntil = s.frame() + frames;
        flareFactor = factor;
    }

    private void jolt(int frames) {
        joltUntil = s.frame() + frames;
    }

    Location pupilLocation() {
        return s.local(0, eyeCy, eyeZ + 1.0).add(gaze);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Build
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Builds the stage.
     *
     * <p>The expansion is split like the barrier around it: the flesh path is
     * ground-level and belongs to the floor lead-in, while the lobes and the eye
     * belong to the shell phase that follows it.</p>
     *
     * @param floorSeconds seconds of the expansion reserved for the floor lead-in
     * @param shellSeconds seconds of the expansion spent on the rising shell
     */
    void build(double floorSeconds, double shellSeconds) {
        if (built) return;
        built = true;
        double R = s.getRadius();
        eyeZ = -R * 0.6;
        buildPath(R, floorSeconds);
        buildLobes(R, floorSeconds, shellSeconds);
        buildEye(R);
    }

    private BlockDisplay seed(Material m, Location loc, float yaw, int bright) {
        BlockDisplay d = DisplayUtils.spawnBlock(loc, m, 2.0f, (int) MathUtil.clamp(bright, 0, 15));
        TransformUtils.applyTransform(d, SEED, SEED, SEED, yaw, 0f, 0f, 0);
        s.spawnedDisplays.add(d);
        return d;
    }

    private Part part(Material m, Location loc, float sx, float sy, float sz, float yawOffset,
                      double at, int bright, boolean glow) {
        float yaw = s.getCasterYaw() + yawOffset;
        Part p = new Part(seed(m, loc, yaw, bright), sx, sy, sz, yaw, at, bright, glow);
        parts.add(p);
        return p;
    }

    private static final Material[] PATH_FLESH = {Material.PINK_TERRACOTTA, Material.PINK_TERRACOTTA, Material.TERRACOTTA};
    private static final Material[] LOBE_FLESH = {Material.PINK_TERRACOTTA, Material.MUSHROOM_STEM, Material.RED_TERRACOTTA};

    /** Local-frame point on the ground: y is relative to the caster's feet. */
    private Location onGround(double x, double z, double lift) {
        Location flat = s.local(x, 0, z);
        double rel = s.groundY(flat.getX(), flat.getZ()) - s.origin().getY();
        return s.local(x, rel + lift, z);
    }

    private void buildPath(double R, double floorSeconds) {
        double zFront = R * 0.85;
        double zBack = eyeZ + 0.6;
        double length = zFront - zBack;
        double seg = Math.max(1.7, length / 36.0);
        int n = (int) Math.ceil(length / seg);
        // The path lies on the ground, so it unfurls inside the floor lead-in.
        double stagger = (floorSeconds * 0.8) / Math.max(1, n);

        for (int i = 0; i < n; i++) {
            double z = zFront - (i + 0.5) * seg;
            double at = 0.05 + i * stagger;

            Part base = part(PATH_FLESH[i % PATH_FLESH.length], onGround(0, z, 0.07),
                    4.4f, 0.14f, (float) (seg * 1.02), 0f, at, 10, true);
            Part rib = part(Material.NETHER_WART_BLOCK, onGround(0, z - seg / 2, 0.10),
                    4.7f, 0.18f, 0.22f, 0f, at, 9, true);
            Part vein = part(Material.RED_CONCRETE, onGround(0, z, 0.14),
                    0.16f, 0.10f, (float) (seg * 0.95), 0f, at, 11, true);
            Material nod = (i % 2 == 0) ? Material.NETHER_WART_BLOCK : Material.RED_TERRACOTTA;
            Part nl = part(nod, onGround(-2.45, z, 0.2), 0.55f, 0.45f, 0.9f, 0f, at, 9, true);
            Part nr = part(nod, onGround(2.45, z, 0.2), 0.55f, 0.45f, 0.9f, 0f, at, 9, true);
            segs.add(new Seg(base, rib, vein, nl, nr));
        }
    }

    private void buildLobes(double R, double floorSeconds, double shellSeconds) {
        Random rnd = new Random(s.casterPlayer().getUniqueId().getMostSignificantBits() ^ 0x24L);
        for (int i = 0; i < 10; i++) {
            double z = MathUtil.lerp(R * 0.55, -R * 0.45, i / 9.0) + (rnd.nextDouble() - 0.5) * 3.0;
            double side = (i % 2 == 0 ? 1 : -1) * R * (0.26 + rnd.nextDouble() * 0.16);
            float size = (float) (2.4 + rnd.nextDouble() * 2.6);
            // The lobes stand above the floor, so they bloom once the floor
            // lead-in is over and the shell is rising behind them.
            double at = floorSeconds + shellSeconds * 0.12 + i * 0.12;
            Material m = LOBE_FLESH[i % LOBE_FLESH.length];
            Location base = onGround(side, z, size * 0.42);

            // two cubes, one turned 45 degrees: reads as a rounded lump
            lobes.add(part(m, base, size, size, size, 0f, at, 8, false));
            lobes.add(part(m, base, size * 0.95f, size * 0.95f, size * 0.95f, 45f, at, 8, false));
        }
    }

    // ── The eye ──

    private void buildEye(double R) {
        eyeW = Math.min(R * 0.7, 34.0);
        eyeH = eyeW * 0.52;
        double ground = onGround(0, eyeZ, 0).getY() - s.origin().getY();
        eyeCy = ground + 1.2 + eyeH / 2.0;
        float yaw = s.getCasterYaw();

        int lidN = 17;
        for (int i = 0; i < lidN; i++) {
            double v = -1.0 + 2.0 * (i + 0.5) / lidN;
            double w = eyeW * Math.pow(1.0 - v * v, 0.7) + 1.3;
            double h = eyeH / lidN * 1.1 + 0.35;
            row(Material.NETHER_WART_BLOCK, 0, 0, v * eyeH / 2.0, eyeZ - 0.55, (float) w, (float) h, 1.2f, yaw, 0f);
        }
        for (int i = 0; i < lidN; i++) {
            double v = -1.0 + 2.0 * (i + 0.5) / lidN;
            double w = eyeW * Math.pow(1.0 - v * v, 0.7);
            double h = eyeH / lidN * 1.08;
            row(Material.WHITE_CONCRETE, 1, 0, v * eyeH / 2.0, eyeZ, (float) Math.max(0.3, w), (float) h, 1.0f, yaw, 0f);
        }

        // iris: a red outer disc and a hotter inner ring
        double ri = eyeH * 0.40;
        int irisN = 13;
        for (int i = 0; i < irisN; i++) {
            double v = -1.0 + 2.0 * (i + 0.5) / irisN;
            double w = 2.0 * ri * Math.sqrt(Math.max(0.0, 1.0 - v * v));
            double h = 2.0 * ri / irisN * 1.1;
            row(Material.RED_CONCRETE, 2, 0, v * ri, eyeZ + 0.6, (float) Math.max(0.25, w), (float) h, 0.2f, yaw, 0f);
        }
        double ri2 = ri * 0.62;
        int inN = 9;
        for (int i = 0; i < inN; i++) {
            double v = -1.0 + 2.0 * (i + 0.5) / inN;
            double w = 2.0 * ri2 * Math.sqrt(Math.max(0.0, 1.0 - v * v));
            double h = 2.0 * ri2 / inN * 1.12;
            row(Material.ORANGE_CONCRETE, 3, 0, v * ri2, eyeZ + 0.68, (float) Math.max(0.2, w), (float) h, 0.2f, yaw, 0f);
        }

        // pupil: a tall slit
        double rp = ri * 0.5;
        int pupN = 9;
        for (int i = 0; i < pupN; i++) {
            double v = -1.0 + 2.0 * (i + 0.5) / pupN;
            double w = 2.0 * rp * 0.62 * Math.sqrt(Math.max(0.0, 1.0 - v * v));
            double h = 2.0 * rp * 1.25 / pupN * 1.12;
            row(Material.BLACK_CONCRETE, 4, 0, v * rp * 1.25, eyeZ + 0.78, (float) Math.max(0.2, w), (float) h, 0.2f, yaw, 0f);
        }

        // veins creeping out from the iris. Bars are long along z: yaw - 90 lays them across
        // the eye, and lean (pitch) rotates them within the plane of the eye.
        Random rnd = new Random(s.casterPlayer().getUniqueId().getLeastSignificantBits() ^ 0x77L);
        int made = 0;
        for (int tries = 0; made < 10 && tries < 60; tries++) {
            double a = rnd.nextDouble() * Math.PI * 2.0;
            double len = eyeW * (0.10 + 0.12 * rnd.nextDouble());
            double rc = ri * 1.05 + len / 2.0;
            double x = Math.cos(a) * rc * 1.6;
            double y = Math.sin(a) * rc;
            double v = y / (eyeH / 2.0);
            if (Math.abs(v) > 0.8) continue;
            double halfW = (eyeW / 2.0) * Math.pow(1.0 - v * v, 0.7);
            if (Math.abs(x) + len * 0.5 > halfW * 0.9) continue;
            float lean = (float) Math.toDegrees(Math.atan2(Math.sin(a), Math.cos(a) * 1.6));
            row(Material.RED_CONCRETE, 5, x, y, eyeZ + 0.54, 0.07f, 0.07f, (float) len, yaw - 90f, lean);
            made++;
        }
    }

    private void row(Material m, int layer, double x, double yOff, double z,
                     float sx, float sy, float sz, float yaw, float lean) {
        Location home = s.local(x, eyeCy + yOff, z);
        // veins grow where they lie; every other bar starts on the eye's centre line
        Location center = layer == 5 ? home : s.local(x, eyeCy, z);
        BlockDisplay d = seed(m, center, yaw, 15);
        rows.add(new Row(d, home, center, sx, sy, sz, yaw, lean, layer));
    }

    private float scaleX(Row r) {
        return r.layer == 4 ? r.sx * pupilFactor : r.sx;
    }

    /** Stage one: a slit of light. Stage two (10 ticks later): the lids part. */
    private void openEye() {
        eyeOpen = true;
        World w = s.world();
        Location eye = pupilLocation();

        for (Row r : rows) {
            if (r.layer == 5 || !r.d.isValid()) continue;
            TransformUtils.applyTransform(r.d, r.sx, SLIT, r.sz, r.yaw, r.lean, 0f, 8);
        }
        if (w != null) {
            w.playSound(eye, Sound.BLOCK_SCULK_SHRIEKER_SHRIEK, 2.5f, 0.45f);
            w.playSound(eye, Sound.BLOCK_CHAIN_PLACE, 2.0f, 0.4f);
        }

        ShenanigansCore.runTaskLater( () -> {
            if (!s.isActive() || closed) return;
            for (Row r : rows) {
                if (!r.d.isValid()) continue;
                r.d.setTeleportDuration(14);
                r.d.teleport(r.layer >= 2 && r.layer <= 4 ? r.home.clone().add(gaze) : r.home);
                TransformUtils.applyTransform(r.d, scaleX(r), r.sy, r.sz, r.yaw, r.lean, 0f, 14);
            }
            World ww = s.world();
            if (ww != null) {
                ww.playSound(eye, Sound.ENTITY_ELDER_GUARDIAN_CURSE, 3.0f, 0.5f);
            }
            Projector.flash(eye);
        }, 10L);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Expansion: the stage unfurls
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Expansion: the stage unfurls.
     *
     * @param t             seconds since the expansion began
     * @param floorSeconds  length of the floor lead-in
     * @param shellSeconds  length of the shell phase that follows it
     */
    void tickExpand(double t, double floorSeconds, double shellSeconds) {
        for (Part p : parts) {
            if (!p.shown) {
                if (p.at <= t) grow(p, 6, t);
            } else if (p.settle > 0.0 && t >= p.settle) {
                p.settle = 0.0;
                if (p.d.isValid()) p.d.setBrightness(light(p.base));
            }
        }
        // The eye is the tallest part of the stage: it opens once the shell is
        // most of the way up.
        if (!eyeOpen && t >= floorSeconds + shellSeconds * 0.45) openEye();
    }

    void finishExpand() {
        for (Part p : parts) {
            if (!p.shown) grow(p, 4, -1.0);
            if (p.settle > 0.0 || (p.glow && p.d.isValid())) {
                p.settle = 0.0;
                if (p.d.isValid()) p.d.setBrightness(light(p.base));
            }
        }
        if (!eyeOpen) openEye();
    }

    /** Growing parts of the path flare bright, then settle: a lit leading edge. */
    private void grow(Part p, int ticks, double t) {
        p.shown = true;
        if (!p.d.isValid()) return;
        TransformUtils.applyTransform(p.d, p.sx, p.sy, p.sz, p.yaw, 0f, 0f, ticks);
        if (p.glow && t >= 0.0) {
            p.d.setBrightness(light(15));
            p.settle = t + 0.35;
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Domain: the 24 FPS clock drives everything here
    // ─────────────────────────────────────────────────────────────────────────

    void tickDomain() {
        if (closed) return;
        long f = s.frame();
        World w = s.world();
        if (w == null) return;

        // 1) Flesh heartbeat: lub-dub on a 24-frame cycle, stepped, synced to the sound
        long ph = f % 24;
        float m = ph <= 1 ? 1.10f : (ph >= 5 && ph <= 6 ? 1.06f : 1.0f);
        if (f < joltUntil) m = Math.max(m, 1.16f);
        if (m != lobeScale) {
            lobeScale = m;
            for (Part p : lobes) {
                if (p.d.isValid()) {
                    TransformUtils.applyTransform(p.d, p.sx * m, p.sy * m, p.sz * m, p.yaw, 0f, 0f, 0);
                }
            }
        }
        long cyc = f / 24;
        if (cyc != lastCycle) {
            lastCycle = cyc;
            w.playSound(pupilLocation(), Sound.ENTITY_WARDEN_HEARTBEAT, 2.0f, 0.55f);
        }

        // 2) Pupil flares and constrictions land in a single frame
        float pf = f < flareUntil ? flareFactor : 1.0f;
        if (pf != pupilFactor && eyeOpen) {
            pupilFactor = pf;
            for (Row r : rows) {
                if (r.layer == 4 && r.d.isValid()) {
                    TransformUtils.applyTransform(r.d, scaleX(r), r.sy, r.sz, r.yaw, 0f, 0f, 0);
                }
            }
        }

        // 3) Gaze snaps every 12 frames; the eye blinks every 14th snap
        long g = f / 12;
        if (g != lastGaze) {
            lastGaze = g;
            lookAtNearest();
            if (g % 14 == 7) blink();
        }

        // 4) The peristaltic wave
        tickWave(f);

        // 5) Drips
        long dr = f / 9;
        if (dr != lastDrip) {
            lastDrip = dr;
            drip(w);
        }
    }

    // ── peristaltic wave ──

    private void tickWave(long f) {
        if (!eyeOpen || segs.isEmpty()) return;
        int n = segs.size();
        long ph = f % 66;                        // 36 frames of travel, 30 at rest
        int hi = ph < 36 ? (int) (ph / 36.0 * (n + 3)) : n + 3;
        if (hi == waveHi) return;

        int prev = waveHi;
        waveHi = hi;
        int lo = Math.max(0, Math.min(prev, hi) - 3);
        int top = Math.min(n - 1, Math.max(prev, hi));
        for (int k = lo; k <= top; k++) {
            boolean on = k <= hi && k >= hi - 1;
            boolean tail = k == hi - 2;
            setSeg(segs.get(k), on, tail);
        }
        if (hi >= n && prev < n) arrive();
    }

    private void setSeg(Seg g, boolean on, boolean tail) {
        int b = on ? 15 : tail ? 12 : -1;
        for (Part p : new Part[]{g.base, g.rib, g.vein, g.nodL, g.nodR}) {
            if (p.d.isValid() && p.shown) p.d.setBrightness(light(b < 0 ? p.base : b));
        }
        // the ribs and nodules swell as the wave passes
        float rs = on ? 2.8f : 1.0f;
        float ns = on ? 1.5f : 1.0f;
        if (g.rib.d.isValid() && g.rib.shown) {
            TransformUtils.applyTransform(g.rib.d, g.rib.sx, g.rib.sy * rs, g.rib.sz, g.rib.yaw, 0f, 0f, 0);
        }
        for (Part nd : new Part[]{g.nodL, g.nodR}) {
            if (nd.d.isValid() && nd.shown) {
                TransformUtils.applyTransform(nd.d, nd.sx, nd.sy * ns, nd.sz, nd.yaw, 0f, 0f, 0);
            }
        }
    }

    /** The wave reaches the eye. */
    private void arrive() {
        flare(8, 0.6f);
        jolt(5);
        World w = s.world();
        if (w == null) return;
        Location eye = pupilLocation();
        w.spawnParticle(Particle.END_ROD, eye, 10, eyeW * 0.04, eyeH * 0.06, 0.1, 0.02);
        w.playSound(eye, Sound.BLOCK_SCULK_CATALYST_BLOOM, 1.4f, 0.6f);
    }

    // ── gaze / blink ──

    /** The pupil snaps one frame at a time toward the nearest victim: watchful, never smooth. */
    private void lookAtNearest() {
        if (!eyeOpen || closed) return;
        double gx, gy = 0;
        LivingEntity nearest = null;
        double best = Double.MAX_VALUE;
        Location eye = s.local(0, eyeCy, eyeZ);
        for (LivingEntity le : s.rule.victims()) {
            double d = le.getLocation().distanceSquared(eye);
            if (d < best) {
                best = d;
                nearest = le;
            }
        }
        if (nearest != null) {
            double[] lz = s.toLocal(nearest.getLocation());
            double R = s.getRadius();
            gx = MathUtil.clamp(lz[0] / R * eyeW * 0.25, -eyeW * 0.12, eyeW * 0.12);
            double dy = nearest.getLocation().getY() - eye.getY();
            gy = MathUtil.clamp(dy / R * eyeH * 0.5, -eyeH * 0.06, eyeH * 0.06);
        } else {
            gx = (Math.random() - 0.5) * eyeW * 0.1;
        }
        double rad = Math.toRadians(s.getCasterYaw());
        Vector next = new Vector(Math.cos(rad) * gx, gy, Math.sin(rad) * gx);
        if (next.distanceSquared(gaze) < 1.0e-4) return;
        gaze.copy(next);

        for (Row r : rows) {
            if (r.layer < 2 || r.layer > 4 || !r.d.isValid()) continue;
            r.d.setTeleportDuration(0);
            r.d.teleport(r.home.clone().add(gaze));
        }
    }

    /** Lids close to a slit, then reopen. */
    private void blink() {
        if (!eyeOpen || closed) return;
        for (Row r : rows) {
            if (!r.d.isValid()) continue;
            if (r.layer == 5) {
                TransformUtils.applyTransform(r.d, SEED, SEED, SEED, r.yaw, r.lean, 0f, 2);
                continue;
            }
            r.d.setTeleportDuration(2);
            r.d.teleport(r.center);
            TransformUtils.applyTransform(r.d, r.sx, SLIT, r.sz, r.yaw, r.lean, 0f, 2);
        }
        ShenanigansCore.runTaskLater( () -> {
            if (!s.isActive() || closed) return;
            for (Row r : rows) {
                if (!r.d.isValid()) continue;
                if (r.layer != 5) {
                    r.d.setTeleportDuration(2);
                    r.d.teleport(r.layer >= 2 && r.layer <= 4 ? r.home.clone().add(gaze) : r.home);
                }
                TransformUtils.applyTransform(r.d, scaleX(r), r.sy, r.sz, r.yaw, r.lean, 0f, 2);
            }
        }, 4L);
    }

    private void drip(World w) {
        ThreadLocalRandom r = ThreadLocalRandom.current();
        if (eyeOpen) {
            double x = (r.nextDouble() - 0.5) * eyeW * 0.5;
            Location l = s.local(x, eyeCy - eyeH * 0.5 + 0.25, eyeZ + 0.8);
            w.spawnParticle(Particle.FALLING_DUST, l, 1, 0.1, 0, 0.1, 0, BLOOD_CRUMB);
        }
        if (!lobes.isEmpty()) {
            Part p = lobes.get(r.nextInt(lobes.size()));
            if (p.d.isValid() && p.shown) {
                w.spawnParticle(Particle.FALLING_DUST, p.d.getLocation().add(0, p.sy * 0.4, 0), 1,
                        p.sx * 0.3, 0, p.sz * 0.3, 0, BLOOD_CRUMB);
            }
        }
    }

    /** A few floating cells in the black, sparse on purpose. */
    void ambient() {
        World w = s.world();
        if (w == null) return;
        double r2 = s.getRadius() * s.getRadius();
        for (Player p : w.getPlayers()) {
            if (p.getLocation().distanceSquared(s.origin()) > r2) continue;
            Location l = p.getLocation().add(0, 1.5, 0);
            w.spawnParticle(Particle.WHITE_ASH, l, 3, 7, 3, 7, 0);
            w.spawnParticle(Particle.SPORE_BLOSSOM_AIR, l, 2, 8, 4, 8, 0);
            w.spawnParticle(Particle.END_ROD, l, 1, 6, 3, 6, 0.004);
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Collapse: the lids close and the path winds back into the eye
    // ─────────────────────────────────────────────────────────────────────────

    void tickCollapse(double t, double collapseSeconds) {
        if (!collapseInit) {
            collapseInit = true;
            closed = true;
            for (Row r : rows) {
                if (!r.d.isValid()) continue;
                if (r.layer == 5) {
                    TransformUtils.applyTransform(r.d, SEED, SEED, SEED, r.yaw, r.lean, 0f, 6);
                } else {
                    r.d.setTeleportDuration(10);
                    r.d.teleport(r.center);
                    TransformUtils.applyTransform(r.d, r.sx, SLIT, r.sz, r.yaw, r.lean, 0f, 10);
                }
            }
            World w = s.world();
            if (w != null) w.playSound(pupilLocation(), Sound.BLOCK_BEACON_DEACTIVATE, 2.5f, 0.5f);
        }

        // the last sliver of light goes out
        if (!eyeGone && t >= 0.55) {
            eyeGone = true;
            for (Row r : rows) {
                if (r.d.isValid()) TransformUtils.applyTransform(r.d, SEED, SEED, SEED, r.yaw, r.lean, 0f, 5);
            }
            Projector.flash(pupilLocation());
        }

        // winding the film back: the enemy end of the path goes first, the eye's end last
        int n = segs.size();
        for (int k = 0; k < n; k++) {
            Seg g = segs.get(k);
            if (g.dead) continue;
            double at = 0.1 + k * (collapseSeconds * 0.6 / Math.max(1, n));
            if (t < at) break;
            g.dead = true;
            for (Part p : new Part[]{g.base, g.rib, g.vein, g.nodL, g.nodR}) {
                if (p.d.isValid()) TransformUtils.applyTransform(p.d, SEED, SEED, SEED, p.yaw, 0f, 0f, 6);
            }
        }

        if (!lobesGone && t >= 0.3) {
            lobesGone = true;
            for (Part p : lobes) {
                if (p.d.isValid()) TransformUtils.applyTransform(p.d, SEED, SEED, SEED, p.yaw, 0f, 0f, 12);
            }
        }
    }

    void clear() {
        for (Part p : parts) {
            if (p.d.isValid()) p.d.remove();
        }
        for (Row r : rows) {
            if (r.d.isValid()) r.d.remove();
        }
        parts.clear();
        lobes.clear();
        segs.clear();
        rows.clear();
    }
}
