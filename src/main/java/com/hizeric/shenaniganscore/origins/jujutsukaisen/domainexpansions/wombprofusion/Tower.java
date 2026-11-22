package com.hizeric.shenaniganscore.origins.jujutsukaisen.domainexpansions.wombprofusion;

import com.hizeric.shenaniganscore.core.model.Parts.HeadType;
import com.hizeric.shenaniganscore.core.model.Rig;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.function.Supplier;

/**
 * The totem of Womb Profusion: a tower of merged cursed-spirit faces, stitched seams, headless
 * corpses and reaching branches, with crouching figures at its foot.
 *
 * <h3>Structure</h3>
 * <ul>
 *   <li><b>Tiers</b> (5-11, tapering): each is a {@link Rig} of overlapping flesh lumps, a ring of
 *       skull faces, loose eyes, a corpse and stitched seams. Each tier is its own rig, so it can
 *       erupt on its own.</li>
 *   <li><b>Branches</b>: arcing limbs growing out of the trunk, ending in hands or small faces.</li>
 *   <li><b>Figures</b>: crouching humanoids around the base.</li>
 * </ul>
 *
 * <h3>Lifecycle</h3>
 * <ol>
 *   <li>{@link #buildStep()}: spawns one rig per call (so no single tick spawns hundreds of
 *       entities). Everything starts as an invisible speck.</li>
 *   <li>{@link #erupt()}: tiers grow out of the ground one after another with an overshoot, then the
 *       branches reach out and the figures unfold.</li>
 *   <li>{@link #tick()}: idle life. The mass breathes (a slow swell), eyes blink, faces twitch.</li>
 *   <li>{@link #collapse(int)}: everything shrinks away; {@link #close()} removes the displays.</li>
 * </ol>
 *
 * <p>The layout is seeded, so the same seed always grows the same tower. Entity count at the default
 * quality is roughly 400 (scale it with {@code quality}).</p>
 */
public final class Tower {

    private static final int TIER_STAGGER = 8;

    private final Location base;
    private final double height;
    private final double baseR;
    private final double topR;
    private final double quality;
    private final Random rnd;
    private final List<Entity> sink;

    private final List<Supplier<Rig>> jobs = new ArrayList<>();
    private final List<Rig> tiers = new ArrayList<>();
    private final List<Rig> branches = new ArrayList<>();
    private final List<Integer> branchTier = new ArrayList<>();
    private final List<Rig> figures = new ArrayList<>();

    private record Living(Rig rig, Rig.Part part) {}

    private record Pending(int due, Runnable action) {}

    private final List<Living> eyes = new ArrayList<>();
    private final List<Living> faces = new ArrayList<>();
    private final List<Pending> pending = new ArrayList<>();

    private int builtJobs;
    private int tick;
    private int screamUntil = -1;
    private int eruptTick = -1;
    private boolean closed;

    private Location[] tierOrigins;
    private double tierHeight;
    private int tierCount;

    /**
     * @param base    the ground point at the centre of the tower's foot
     * @param radius  the domain radius; the tower is about 0.8 of it tall
     * @param quality scales detail and entity count (0.4-2.0)
     * @param sink    every display is registered here for guaranteed cleanup
     */
    public Tower(Location base, double radius, double quality, long seed, List<Entity> sink) {
        this.base = base.clone();
        this.height = Math.max(8.0, radius * 0.8);
        this.baseR = Math.max(2.0, radius * 0.16);
        this.topR = baseR * 0.42;
        this.quality = Math.max(0.4, Math.min(2.0, quality));
        this.rnd = new Random(seed);
        this.sink = sink;
        plan();
    }

    // ------------------------------------------------------------------
    // Planning (no entities)
    // ------------------------------------------------------------------

    private static double lerp(double a, double b, double t) {
        return a + (b - a) * t;
    }

    private static int clamp(int v, int lo, int hi) {
        return Math.max(lo, Math.min(hi, v));
    }

    private void plan() {
        tierCount = clamp((int) Math.round(height / 3.6), 5, 11);
        tierHeight = height / tierCount;
        tierOrigins = new Location[tierCount];
        double[] radii = new double[tierCount];

        for (int i = 0; i < tierCount; i++) {
            double t = tierCount == 1 ? 0 : i / (double) (tierCount - 1);
            radii[i] = lerp(baseR, topR, Math.pow(t, 0.85)) * (0.92 + rnd.nextDouble() * 0.2);
            // the whole column leans and sways a little
            double sx = Math.sin(i * 0.8) * baseR * 0.1;
            double sz = Math.cos(i * 0.6) * baseR * 0.1;
            tierOrigins[i] = base.clone().add(sx, i * tierHeight, sz);
        }

        for (int i = 0; i < tierCount; i++) {
            final int index = i;
            final double r = radii[i];
            final double t = tierCount == 1 ? 0 : i / (double) (tierCount - 1);
            jobs.add(() -> buildTier(index, t, r));
        }

        int branchCount = clamp((int) Math.round(9 * quality), 4, 18);
        for (int b = 0; b < branchCount; b++) {
            final int attach = 1 + rnd.nextInt(Math.max(1, tierCount - 2));
            final double angle = rnd.nextDouble() * Math.PI * 2.0;
            final double radius = radii[Math.min(attach, tierCount - 1)];
            jobs.add(() -> buildBranch(attach, angle, radius));
        }

        int figureCount = clamp((int) Math.round(6 * quality), 3, 10);
        for (int f = 0; f < figureCount; f++) {
            final double angle = Math.PI * 2.0 * f / figureCount + rnd.nextDouble() * 0.5;
            jobs.add(() -> buildFigure(angle));
        }
    }

    // ------------------------------------------------------------------
    // Building (one rig per call)
    // ------------------------------------------------------------------

    /** Builds the next rig. Returns true once everything is built. */
    public boolean buildStep() {
        if (closed) return true;
        if (builtJobs < jobs.size()) {
            Rig rig = jobs.get(builtJobs++).get();
            if (rig != null) rig.place(0.001f, 0);
        }
        return builtJobs >= jobs.size();
    }

    public int jobCount() {
        return jobs.size();
    }

    public int builtCount() {
        return builtJobs;
    }

    private Rig buildTier(int i, double t, double r) {
        Rig rig = new Rig(tierOrigins[i], 0f, sink);
        tiers.add(rig);
        float th = (float) tierHeight;
        float rr = (float) r;

        // The trunk: three big lumps at the centre, so the column is never hollow.
        for (int k = 0; k < 3; k++) {
            double a = rnd.nextDouble() * Math.PI * 2.0;
            Vector3f p = new Vector3f((float) (Math.cos(a) * r * 0.3), th * (0.45f + rnd.nextFloat() * 0.2f),
                    (float) (Math.sin(a) * r * 0.3));
            Models.lump(rig, rnd, p, rr * 0.85f, Models.flesh(rnd));
        }

        // Outer lumps: the lumpy surface.
        int outer = clamp((int) Math.round(5 * quality), 3, 8);
        for (int k = 0; k < outer; k++) {
            double a = Math.PI * 2.0 * k / outer + rnd.nextDouble() * 0.6;
            double rad = r * (0.85 + rnd.nextDouble() * 0.25);
            Vector3f p = new Vector3f((float) (Math.cos(a) * rad), th * (0.2f + rnd.nextFloat() * 0.7f),
                    (float) (Math.sin(a) * rad));
            Models.lump(rig, rnd, p, rr * (0.45f + rnd.nextFloat() * 0.35f), Models.flesh(rnd));
        }

        // Faces pressed into the mass, some with a smaller one half-hidden behind.
        int faceCount = clamp((int) Math.round(lerp(6, 4, t) * quality), 3, 8);
        double offset = rnd.nextDouble() * Math.PI * 2.0;
        for (int j = 0; j < faceCount; j++) {
            double a = offset + Math.PI * 2.0 * j / faceCount + (rnd.nextDouble() - 0.5) * 0.4;
            double rad = r * (1.1 + rnd.nextDouble() * 0.12);
            Vector3f p = new Vector3f((float) (Math.cos(a) * rad), th * (0.25f + rnd.nextFloat() * 0.6f),
                    (float) (Math.sin(a) * rad));
            float size = rr * (0.55f + rnd.nextFloat() * 0.35f);
            HeadType type = Models.headType(rnd);
            Rig.Part face = Models.face(rig, rnd, p, (float) Math.cos(a), (float) Math.sin(a), size, type);
            faces.add(new Living(rig, face));

            if (rnd.nextInt(10) < 3) {
                double a2 = a + (rnd.nextBoolean() ? 0.5 : -0.5);
                Vector3f p2 = new Vector3f((float) (Math.cos(a2) * rad * 0.94), p.y + size * 0.55f,
                        (float) (Math.sin(a2) * rad * 0.94));
                Models.face(rig, rnd, p2, (float) Math.cos(a2), (float) Math.sin(a2), size * 0.6f, Models.headType(rnd));
            }
        }

        // Loose eyes scattered over the surface.
        int eyeCount = clamp((int) Math.round(6 * quality), 3, 9);
        for (int k = 0; k < eyeCount; k++) {
            double a = rnd.nextDouble() * Math.PI * 2.0;
            Vector3f p = new Vector3f((float) (Math.cos(a) * r * 1.25), th * (0.1f + rnd.nextFloat() * 0.85f),
                    (float) (Math.sin(a) * r * 1.25));
            Rig.Part eye = Models.eye(rig, rnd, p, 0.35f + rnd.nextFloat() * 0.5f);
            eyes.add(new Living(rig, eye));
        }

        // A headless corpse, embedded or sagging out of the side.
        if (i % 2 == 0 || rnd.nextInt(10) < 4) {
            double a = rnd.nextDouble() * Math.PI * 2.0;
            Vector3f p = new Vector3f((float) (Math.cos(a) * r * 0.95), th * 0.1f, (float) (Math.sin(a) * r * 0.95));
            float facing = (float) Math.atan2(Math.cos(a), Math.sin(a));
            Quaternionf pose = new Quaternionf()
                    .rotateY(facing)
                    .rotateX((float) Math.toRadians(-50 + rnd.nextFloat() * 80f))
                    .rotateZ((float) Math.toRadians(rnd.nextFloat() * 140f - 70f));
            float scale = (float) Math.max(1.1, Math.min(3.0, r * 0.45));
            Models.corpse(rig, rnd, p, pose, scale);
        }

        // Seams stitched across the surface.
        for (int k = 0; k < 3; k++) {
            double a1 = rnd.nextDouble() * Math.PI * 2.0;
            double a2 = a1 + 0.35 + rnd.nextDouble() * 0.3;
            float y = th * (0.15f + rnd.nextFloat() * 0.7f);
            double rad = r * 1.25;
            Vector3f a = new Vector3f((float) (Math.cos(a1) * rad), y, (float) (Math.sin(a1) * rad));
            Vector3f b = new Vector3f((float) (Math.cos(a2) * rad), y + (rnd.nextFloat() - 0.5f) * th * 0.3f,
                    (float) (Math.sin(a2) * rad));
            Vector3f outward = new Vector3f((a.x + b.x) * 0.5f, 0, (a.z + b.z) * 0.5f);
            Models.stitch(rig, rnd, a, b, outward);
        }
        return rig;
    }

    private Rig buildBranch(int attachTier, double angle, double tierRadius) {
        Location o = tierOrigins[Math.min(attachTier, tierCount - 1)].clone()
                .add(Math.cos(angle) * tierRadius * 0.95, tierHeight * 0.5, Math.sin(angle) * tierRadius * 0.95);
        Rig rig = new Rig(o, 0f, sink);
        branches.add(rig);
        branchTier.add(attachTier);

        float length = (float) (height * (0.16 + rnd.nextDouble() * 0.24));
        double t = attachTier / (double) Math.max(1, tierCount - 1);
        float thickness = (float) Math.max(0.35, Math.min(1.2, baseR * 0.16 * (1.0 - 0.4 * t)));
        Models.branch(rig, rnd, new Vector3f(), new Vector3f((float) Math.cos(angle), 0, (float) Math.sin(angle)),
                length, thickness);
        return rig;
    }

    private Rig buildFigure(double angle) {
        double rad = baseR * (1.7 + rnd.nextDouble() * 0.6);
        Location o = base.clone().add(Math.cos(angle) * rad, 0, Math.sin(angle) * rad);
        Rig rig = new Rig(o, 0f, sink);
        figures.add(rig);

        // Facing outward, toward whoever comes near.
        Quaternionf pose = new Quaternionf().rotateY((float) Math.atan2(Math.cos(angle), Math.sin(angle)));
        Models.crouching(rig, rnd, new Vector3f(), pose, 1.6f + rnd.nextFloat() * 0.8f);
        return rig;
    }

    // ------------------------------------------------------------------
    // Lifecycle
    // ------------------------------------------------------------------

    /** The mid-height point of the tower, where the core orb gathers. */
    public Location coreLocation() {
        return base.clone().add(0, height * 0.55, 0);
    }

    public double height() {
        return height;
    }

    public double baseRadius() {
        return baseR;
    }

    /** Starts the eruption: tiers grow in order, then the branches and figures follow. */
    public void erupt() {
        // tick() increments first, so the first tick after this call is since == 0
        if (eruptTick < 0) eruptTick = tick + 1;
    }

    /** Ticks from {@link #erupt()} until the last tier has landed. */
    public int eruptDuration() {
        return tierCount * TIER_STAGGER + 30;
    }

    public void tick() {
        if (closed) return;
        tick++;
        World world = base.getWorld();
        if (world == null) return;

        runPending();
        if (eruptTick < 0) return;
        int since = tick - eruptTick;
        if (since < 0) return;

        // Eruption.
        for (int i = 0; i < tiers.size(); i++) {
            int start = i * TIER_STAGGER;
            if (since == start) {
                tiers.get(i).place(1.12f, 7);
                Location at = tierOrigins[i];
                world.playSound(at, Sound.BLOCK_SLIME_BLOCK_PLACE, 1.6f, 0.5f);
                world.playSound(at, Sound.ENTITY_PHANTOM_AMBIENT, 0.9f, 0.5f);
                world.spawnParticle(Particle.BLOCK, at, 24, 1.5, 0.3, 1.5, 0.1,
                        Material.PINK_TERRACOTTA.createBlockData());
            } else if (since == start + 7) {
                tiers.get(i).place(1.0f, 6);
            }
        }
        for (int b = 0; b < branches.size(); b++) {
            int start = branchTier.get(b) * TIER_STAGGER + 10;
            if (since == start) branches.get(b).place(1.0f, 14);
        }
        for (int f = 0; f < figures.size(); f++) {
            if (since == 2 + f * 3) figures.get(f).place(1.0f, 16);
        }

        if (since < eruptDuration()) return;
        idle();
    }

    /** Breathing, blinking, twitching: the tower is alive. */
    private void idle() {
        // Breathing: every rig swells and relaxes on its own 40-tick cycle.
        for (int k = 0; k < tiers.size(); k++) {
            int phase = tick + k * 3;
            if (phase % 20 == 0) tiers.get(k).pulse(((phase / 20) % 2 == 0) ? 1.025f : 1.0f, 20);
        }
        for (int k = 0; k < branches.size(); k++) {
            int phase = tick + k * 5;
            if (phase % 20 == 0) branches.get(k).pulse(((phase / 20) % 2 == 0) ? 1.03f : 1.0f, 20);
        }

        // Blinking: one eye closes briefly every few ticks.
        if (tick % 3 == 0 && !eyes.isEmpty()) {
            Living e = eyes.get(rnd.nextInt(eyes.size()));
            e.rig().setFactor(e.part(), 0.05f, 2);
            pending.add(new Pending(tick + 5, () -> e.rig().setFactor(e.part(), 1.0f, 2)));
        }

        // Screaming: all faces lurch together every few ticks.
        if (tick < screamUntil && tick % 6 == 0) {
            for (Living f : faces) {
                Quaternionf lurch = f.part().baseRotation()
                        .rotateX((float) Math.toRadians((rnd.nextFloat() - 0.5f) * 30f))
                        .rotateZ((float) Math.toRadians((rnd.nextFloat() - 0.5f) * 30f));
                f.rig().setFactor(f.part(), 1.16f, 2);
                f.rig().setRotation(f.part(), lurch, 2);
                pending.add(new Pending(tick + 3, () -> {
                    f.rig().setFactor(f.part(), 1.0f, 3);
                    f.rig().restoreRotation(f.part(), 3);
                }));
            }
        }

        // Twitching: two faces shudder and settle.
        if (tick % 12 == 0 && !faces.isEmpty()) {
            for (int k = 0; k < 2; k++) {
                Living f = faces.get(rnd.nextInt(faces.size()));
                Quaternionf twitched = f.part().baseRotation()
                        .rotateX((float) Math.toRadians((rnd.nextFloat() - 0.5f) * 20f))
                        .rotateZ((float) Math.toRadians((rnd.nextFloat() - 0.5f) * 20f));
                f.rig().setRotation(f.part(), twitched, 4);
                pending.add(new Pending(tick + 8, () -> f.rig().restoreRotation(f.part(), 6)));
            }
        }
    }

    private void runPending() {
        if (pending.isEmpty()) return;
        List<Pending> due = new ArrayList<>();
        for (Pending p : pending) {
            if (tick >= p.due()) due.add(p);
        }
        pending.removeAll(due);
        for (Pending p : due) p.action().run();
    }

    /**
     * Every face swells and shudders in unison for {@code ticks}, in time with the heartbeat: the tower
     * screaming as the charge builds. (Vanilla skulls cannot open their mouths, so the face lurches.)
     */
    public void scream(int ticks) {
        screamUntil = tick + Math.max(6, ticks);
    }

    /** Everything shrinks to nothing over {@code ticks}. */
    public void collapse(int ticks) {
        int t = Math.max(2, Math.min(59, ticks));
        pending.clear();
        for (Rig rig : tiers) rig.place(0.001f, t);
        for (Rig rig : branches) rig.place(0.001f, t);
        for (Rig rig : figures) rig.place(0.001f, t);
    }

    public void close() {
        if (closed) return;
        closed = true;
        pending.clear();
        for (Rig rig : tiers) rig.remove();
        for (Rig rig : branches) rig.remove();
        for (Rig rig : figures) rig.remove();
        tiers.clear();
        branches.clear();
        figures.clear();
        eyes.clear();
        faces.clear();
    }
}
