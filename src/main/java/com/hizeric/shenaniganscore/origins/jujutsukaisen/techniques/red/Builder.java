package com.hizeric.shenaniganscore.origins.jujutsukaisen.techniques.red;

import com.hizeric.shenaniganscore.core.display.TechniqueLighting;
import com.hizeric.shenaniganscore.ShenanigansCore;
import com.hizeric.shenaniganscore.core.display.DisplayUtils;
import com.hizeric.shenaniganscore.core.sound.SoundUtils;
import com.hizeric.shenaniganscore.core.util.ClientBlockUtils;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.FluidCollisionMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.TileState;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.type.Light;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Transformation;
import org.bukkit.util.Vector;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.IntConsumer;
import com.hizeric.shenaniganscore.core.util.MathUtil;
import com.hizeric.shenaniganscore.core.display.DisplayRig;
import static com.hizeric.shenaniganscore.core.display.TechniqueLighting.lightable;
import static com.hizeric.shenaniganscore.core.display.TechniqueLighting.LIGHT_RANGE_SQ;
import static com.hizeric.shenaniganscore.core.display.TechniqueLighting.TINY;
import static com.hizeric.shenaniganscore.core.display.TechniqueLighting.BRIGHT;
import static com.hizeric.shenaniganscore.core.display.TechniqueLighting.VIEW;
import static com.hizeric.shenaniganscore.core.display.BlockDisplayOps.blockLoc;
import static com.hizeric.shenaniganscore.core.display.BlockDisplayOps.facing;
import static com.hizeric.shenaniganscore.core.display.BlockDisplayOps.place;
import static com.hizeric.shenaniganscore.core.display.BlockDisplayOps.randomUnit;
import static com.hizeric.shenaniganscore.core.display.BlockDisplayOps.shrink;
import static com.hizeric.shenaniganscore.core.display.BlockDisplayOps.line;
import static com.hizeric.shenaniganscore.core.display.TechniqueDisplayOps.basis;
import static com.hizeric.shenaniganscore.core.display.TechniqueDisplayOps.discard;
import static com.hizeric.shenaniganscore.core.display.TechniqueDisplayOps.randomRot;
import static com.hizeric.shenaniganscore.core.display.TechniqueDisplayOps.spawn;
import static com.hizeric.shenaniganscore.core.display.TechniqueDisplayOps.tumble;
import static com.hizeric.shenaniganscore.core.util.MathUtil.scaledCount;

/**
 * Every visual of Cursed Technique Reversal: Red.
 *
 * <p><b>Visual idea: divergence.</b> Fuga is fire and smoke; Red is the opposite — clean,
 * crimson and white-hot, with nothing burning. Everything in it <i>pushes away</i>:</p>
 * <ul>
 *   <li><b>The orb</b> — a white-hot core wrapped in layered crimson glass, with a crown of thin
 *       red rays that pulse outward (the space around it being forced apart). Rings of sparks
 *       counter-rotate around it and the air at the caster's feet is shoved outward.</li>
 *   <li><b>Flight</b> — expanding ripples of light are shed behind the orb like shockwaves,
 *       with electric sparks and a fading crimson ribbon wake and a travelling light.</li>
 *   <li><b>Impact</b> — the orb <i>collapses</i> for two ticks, then diverges: a flash, sonic-boom
 *       rings, a spherical shock shell, rays of red shooting out in every direction, and
 *       a faceted white/crimson fireball-sphere. Sparks and puffs are flung radially.</li>
 *   <li><b>The ground</b> — real terrain blocks around the impact are copied into displays
 *       and thrown outward on arcs (the real blocks are hidden on clients for a moment and
 *       restored), and fault-line cracks split the earth and fade out later.</li>
 * </ul>
 *
 * <p>Nothing real is ever changed: displays, particles and short-lived client-side blocks only.
 * Everything scales with {@code jujutsukaisen.techniques.red.quality}.</p>
 */
public final class Builder {

    private Builder() {}

    private static final double CRATER_RANGE_SQ = 96.0 * 96.0;

    private static final Particle.DustTransition RED_EDGE =
            new Particle.DustTransition(Color.fromRGB(255, 70, 50), Color.fromRGB(90, 0, 10), 1.6f);
    private static final Particle.DustTransition HOT_EDGE =
            new Particle.DustTransition(Color.fromRGB(255, 240, 235), Color.fromRGB(255, 50, 40), 1.4f);


    // ─────────────────────────────────────────────────────────────────────────
    // Data carried by the projectile
    // ─────────────────────────────────────────────────────────────────────────

    /** One layer of the orb: rel = size relative to the orb radius, spin = angular speed. */
    private record Piece(BlockDisplay display, Quaternionf rot, float rel, float spin) {}

    /** One crown ray of the orb. */
    private record Ray(BlockDisplay display, Vector dir, double phase) {}

    /** One ray of the divergence burst. */
    private record Spike(BlockDisplay display, Vector dir, double len) {}

    /** One cube of the blast sphere. */
    private record Cube(BlockDisplay display, Quaternionf rot, float size) {}

    /** A real terrain block thrown outward. */
    private record Chunk(BlockDisplay display, Location start, Location apex, Location land) {}

    /** An entity with a lifetime and an optional per-tick callback. */
    private static final class Timed {
        final Entity entity;
        final int born;
        final int life;
        final IntConsumer onAge;

        Timed(Entity entity, int born, int life, IntConsumer onAge) {
            this.entity = entity;
            this.born = born;
            this.life = life;
            this.onAge = onAge;
        }
    }

    /** Everything Red spawns, plus the little state the animation needs. */
    public static final class Rig implements DisplayRig {
        final List<Entity> entities = new ArrayList<>();

        @Override
        public List<Entity> entities() {
            return entities;
        }
        final List<Piece> pieces = new ArrayList<>();
        final List<Ray> rays = new ArrayList<>();
        final List<Cube> cubes = new ArrayList<>();
        final List<Spike> burst = new ArrayList<>();
        final List<Chunk> chunks = new ArrayList<>();
        final List<Timed> timed = new ArrayList<>();
        final List<Entity> scars = new ArrayList<>();
        final Map<Location, Integer> lights = new HashMap<>();
        final List<Location> craterBlocks = new ArrayList<>();
        final Set<UUID> craterViewers = new HashSet<>();
        float radius = 0.5f;
        Location center;
        double groundY;
        boolean hasGround;
    }

    /** Where the orb is (the release point). */
    static Location centerOf(Rig rig) {
        return rig.center == null ? null : rig.center.clone();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Small helpers
    // ─────────────────────────────────────────────────────────────────────────

    /** Spawns a fullbright block display (tiny) and tracks it for cleanup. */
    /**
     * Puts a box of the given size (rotated by q) so that its CENTER sits on {@code center}.
     * interp = ticks the client takes to animate size/rotation, tele = ticks for the move.
     */
    /** A thin glowing beam between two points. */
    /** A flat strip lying on the ground between two points (cracks). */
    private static void strip(BlockDisplay d, Location a, Location b, float width, float height) {
        Vector delta = b.toVector().subtract(a.toVector());
        double len = delta.length();
        if (len < 0.05) return;
        Location mid = a.clone().add(delta.clone().multiply(0.5));
        place(d, mid, facing(delta.multiply(1.0 / len)), width, height, (float) len, 0, 0);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Client-side light (a real light source that rides the orb)
    // ─────────────────────────────────────────────────────────────────────────

    /** Makes the visible fake light blocks match {@code want}, sending only differences. */
    private static void lightPair(Rig rig, World w, Location a, int levelA, Location b, int levelB) {
        Map<Location, Integer> want = new HashMap<>();
        if (a != null) {
            Location la = blockLoc(a);
            if (lightable(w, la)) want.put(la, levelA);
        }
        if (b != null) {
            Location lb = blockLoc(b);
            if (lightable(w, lb)) want.merge(lb, levelB, Math::max);
        }
        TechniqueLighting.setLights(rig.lights, w, want);
    }

    private static void lightBurst(Rig rig, World w, Location centre, double R, int level) {
        Map<Location, Integer> want = new HashMap<>();
        double d = R * 0.45;
        double[][] offsets = {{0, 0, 0}, {d, 0, 0}, {-d, 0, 0}, {0, d, 0}, {0, -d, 0}, {0, 0, d}, {0, 0, -d}};
        for (double[] o : offsets) {
            Location l = blockLoc(centre.clone().add(o[0], o[1], o[2]));
            if (lightable(w, l)) want.put(l, level);
        }
        TechniqueLighting.setLights(rig.lights, w, want);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // CHARGE — the orb condenses
    // ─────────────────────────────────────────────────────────────────────────

    /** Builds the orb (tiny; {@link #tickCharge} grows it). */
    public static void spawnOrb(Projectile p) {
        Location at = p.caster().getEyeLocation();
        ThreadLocalRandom r = ThreadLocalRandom.current();

        Rig rig = new Rig();
        rig.radius = (float) (0.45 + 0.10 * p.power());
        rig.center = at.clone();

        // White-hot core, wrapped in layered crimson glass (two cubes per layer, so it reads as a sphere).
        rig.pieces.add(new Piece(spawn(rig, at, Material.WHITE_CONCRETE, BRIGHT), randomRot(r), 0.9f, 0.0f));
        rig.pieces.add(new Piece(spawn(rig, at, Material.RED_STAINED_GLASS, BRIGHT), randomRot(r), 1.55f, 0.22f));
        rig.pieces.add(new Piece(spawn(rig, at, Material.RED_STAINED_GLASS, BRIGHT), randomRot(r), 1.55f, -0.17f));
        rig.pieces.add(new Piece(spawn(rig, at, Material.RED_STAINED_GLASS, BRIGHT), randomRot(r), 2.2f, 0.12f));
        rig.pieces.add(new Piece(spawn(rig, at, Material.RED_STAINED_GLASS, BRIGHT), randomRot(r), 2.2f, -0.09f));

        // The crown: thin rays that pulse outward — the space around the orb being forced apart.
        int n = Math.max(6, Math.min(16, scaledCount(10, p.quality())));
        double golden = Math.PI * (3.0 - Math.sqrt(5.0));
        for (int i = 0; i < n; i++) {
            double y = 1.0 - 2.0 * (i + 0.5) / n;
            double h = Math.sqrt(Math.max(0.0, 1.0 - y * y));
            double th = golden * i;
            Vector dir = new Vector(Math.cos(th) * h, y, Math.sin(th) * h);
            rig.rays.add(new Ray(spawn(rig, at, Material.RED_CONCRETE, BRIGHT), dir, r.nextDouble() * Math.PI * 2.0));
        }
        p.setRig(rig);
    }

    /** Poses every layer and ray of the orb around a centre. */
    private static void poseOrb(Rig rig, Location c, double tick, double charge, double rayScale) {
        float r = (float) (rig.radius * charge);
        double pulse = 1.0 + 0.08 * Math.sin(tick * 0.9);

        for (Piece pc : rig.pieces) {
            Quaternionf q = new Quaternionf(pc.rot())
                    .rotateY((float) (tick * pc.spin()))
                    .rotateX((float) (tick * pc.spin() * 0.6));
            float s = (float) Math.max(TINY, r * pc.rel() * pulse);
            place(pc.display(), c, q, s, s, s, 1, 1);
        }

        float th = (float) (0.05 + 0.03 * rig.radius);
        for (Ray ray : rig.rays) {
            double len = r * (1.3 + 1.1 * (0.5 + 0.5 * Math.sin(tick * 0.55 + ray.phase()))) * rayScale;
            if (len < 0.05) {
                place(ray.display(), c, new Quaternionf(), TINY, TINY, TINY, 1, 1);
                continue;
            }
            Location a = c.clone().add(ray.dir().clone().multiply(r * 0.9));
            Location b = a.clone().add(ray.dir().clone().multiply(len));
            line(ray.display(), a, b, th, 1);
        }
    }

    /** Called every charge tick. progress runs 0..1. */
    public static void tickCharge(Projectile p, double progress) {
        Rig rig = p.rig();
        if (rig == null) return;

        Player c = p.caster();
        World w = c.getWorld();
        ThreadLocalRandom r = ThreadLocalRandom.current();
        int tick = p.stateTicks();
        double e = MathUtil.smoothstep(progress);

        Location eye = c.getEyeLocation();
        Vector fwd = eye.getDirection().normalize();
        Vector right = fwd.clone().crossProduct(new Vector(0, 1, 0));
        if (right.lengthSquared() < 1.0e-4) right = new Vector(1, 0, 0);
        right.normalize();
        Vector up = right.clone().crossProduct(fwd).normalize();

        Location grip = eye.clone()
                .add(fwd.clone().multiply(1.7))
                .add(right.clone().multiply(0.5))
                .subtract(up.clone().multiply(0.1));
        rig.center = grip.clone();

        double charge = 0.15 + 0.85 * e;
        poseOrb(rig, grip, tick, charge, 0.4 + 0.6 * e);
        lightPair(rig, w, grip, (int) (6 + 8 * e), null, 0);

        double orb = rig.radius * charge;

        // Positive energy crackles off the orb; the air is pushed away from it.
        w.spawnParticle(Particle.ELECTRIC_SPARK, grip, 3, orb * 1.5, orb * 1.5, orb * 1.5, 0.1);
        for (int i = 0; i < 4; i++) {
            Vector v = randomUnit(r);
            w.spawnParticle(Particle.END_ROD, grip, 0, v.getX(), v.getY(), v.getZ(), 0.1 + 0.1 * charge);
        }

        // Two counter-rotating rings of light around the orb.
        double ringR = orb * 2.8 + 0.2;
        for (int i = 0; i < 12; i++) {
            double a = Math.PI * 2.0 * i / 12.0 + progress * 6.0;
            double ca = Math.cos(a) * ringR, sa = Math.sin(a) * ringR;
            Location p1 = grip.clone().add(right.clone().multiply(ca)).add(up.clone().multiply(sa));
            w.spawnParticle(Particle.DUST_COLOR_TRANSITION, p1, 1, 0, 0, 0, 0, RED_EDGE);
            double b = -a * 1.3;
            Location p2 = grip.clone().add(right.clone().multiply(Math.cos(b) * ringR))
                    .add(fwd.clone().multiply(Math.sin(b) * ringR));
            w.spawnParticle(Particle.DUST_COLOR_TRANSITION, p2, 1, 0, 0, 0, 0, HOT_EDGE);
        }

        // Air shoved outward across the ground around his feet.
        Location feet = c.getLocation();
        double sweep = 0.8 + 3.0 * ((tick % 8) / 8.0);
        for (int i = 0; i < 16; i++) {
            double a = Math.PI * 2.0 * i / 16.0;
            w.spawnParticle(Particle.CLOUD, feet.getX() + Math.cos(a) * sweep, feet.getY() + 0.1,
                    feet.getZ() + Math.sin(a) * sweep, 0, Math.cos(a), 0.02, Math.sin(a), 0.08);
        }

        // Sound: positive energy charging.
        if (tick == 1) {
            SoundUtils.play(feet, Sound.BLOCK_RESPAWN_ANCHOR_CHARGE, 1.3f, 0.6f);
            SoundUtils.play(feet, Sound.ENTITY_WARDEN_HEARTBEAT, 1.2f, 0.8f);
        } else if (tick % 3 == 0) {
            SoundUtils.play(grip, Sound.BLOCK_AMETHYST_BLOCK_RESONATE, 1.0f, (float) (0.6 + 0.9 * progress));
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // RELEASE + FLIGHT
    // ─────────────────────────────────────────────────────────────────────────

    /** The throw: a ring of light off the orb and a crack of sound. */
    public static void release(Projectile p) {
        Rig rig = p.rig();
        if (rig == null) return;
        Location at = p.currentLocation();
        World w = at.getWorld();
        if (w == null) return;

        Vector[] bs = basis(p.direction());
        for (int i = 0; i < 28; i++) {
            double a = Math.PI * 2.0 * i / 28.0;
            Vector out = bs[0].clone().multiply(Math.cos(a)).add(bs[1].clone().multiply(Math.sin(a)));
            w.spawnParticle(Particle.END_ROD, at, 0, out.getX(), out.getY(), out.getZ(), 0.4);
            if (i % 2 == 0) w.spawnParticle(Particle.CLOUD, at, 0, out.getX(), out.getY(), out.getZ(), 0.3);
        }
        w.spawnParticle(Particle.FLASH, at, 1, 0, 0, 0, 0, Color.WHITE);
        SoundUtils.play(at, Sound.ENTITY_WARDEN_SONIC_BOOM, 1.2f, 1.4f);
        SoundUtils.play(at, Sound.ITEM_TRIDENT_THUNDER, 0.9f, 1.8f);
    }

    /** A fading crimson ribbon segment left behind the orb. */
    private static void ribbon(Rig rig, Location a, Location b, Material m, float thickness, int now) {
        final BlockDisplay d = spawn(rig, a, m, BRIGHT);
        line(d, a, b, thickness, 1);
        rig.timed.add(new Timed(d, now, 9, age -> {
            if (age == 1) shrink(d, 7);
        }));
    }

    /** Moves the orb: ripples shed behind it, sparks, ribbon wake, travelling light. */
    public static void tickFlight(Projectile p, Location from, Location to) {
        Rig rig = p.rig();
        World w = to.getWorld();
        if (rig == null || w == null) return;

        Vector dir = p.direction();
        ThreadLocalRandom r = ThreadLocalRandom.current();
        int tick = p.flightTicks();
        Vector[] bs = basis(dir);

        rig.center = to.clone();
        poseOrb(rig, to, p.ticksLived(), 1.0, 0.55);
        lightPair(rig, w, to, 15, to.clone().subtract(dir.clone().multiply(4.0)), 11);

        // Ripples shed behind the orb: rings of light that expand outward.
        if (tick % 2 == 0) {
            Location tail = to.clone().subtract(dir.clone().multiply(0.5));
            for (int i = 0; i < 14; i++) {
                double a = Math.PI * 2.0 * i / 14.0;
                Vector out = bs[0].clone().multiply(Math.cos(a)).add(bs[1].clone().multiply(Math.sin(a)));
                w.spawnParticle(Particle.END_ROD, tail, 0, out.getX(), out.getY(), out.getZ(), 0.22);
            }
        }
        w.spawnParticle(Particle.ELECTRIC_SPARK, to, 3, 0.5, 0.5, 0.5, 0.15);

        Vector step = to.toVector().subtract(from.toVector());
        double len = step.length();
        int n = Math.max(1, (int) Math.ceil(len / 0.7));
        for (int i = 1; i <= n; i++) {
            Location pt = from.clone().add(step.clone().multiply(i / (double) n));
            w.spawnParticle(Particle.DUST_COLOR_TRANSITION, pt, 1, 0.12, 0.12, 0.12, 0,
                    r.nextBoolean() ? RED_EDGE : HOT_EDGE);
        }

        if (len > 0.1) {
            float k = (float) (0.6 + 0.2 * p.power());
            int now = p.ticksLived();
            ribbon(rig, from, to, Material.RED_STAINED_GLASS, 0.45f * k, now);
            if (p.quality() >= 0.7) ribbon(rig, from, to, Material.WHITE_CONCRETE, 0.12f * k, now);
        }

        if (tick % 3 == 1) SoundUtils.play(to, Sound.BLOCK_BEACON_AMBIENT, 1.4f, 1.6f);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // IMPACT
    // ─────────────────────────────────────────────────────────────────────────

    /** The two-tick anticipation: the orb collapses and the air rushes in. */
    public static void tickCompress(Projectile p, int t) {
        Rig rig = p.rig();
        if (rig == null) return;
        Location c = p.impactLocation();
        World w = c.getWorld();
        if (w == null) return;
        ThreadLocalRandom r = ThreadLocalRandom.current();
        double R = p.impactRadius();

        rig.center = c.clone();
        poseOrb(rig, c, p.ticksLived(), 1.0 - 0.55 * t / 2.0, 0.15);

        for (int i = 0; i < 10; i++) {
            Vector v = randomUnit(r);
            Location from = c.clone().add(v.clone().multiply(R * 0.7));
            w.spawnParticle(Particle.CLOUD, from, 0, -v.getX(), -v.getY(), -v.getZ(), 0.35);
        }
        lightPair(rig, w, c, 15, null, 0);
        if (t == 1) SoundUtils.play(c, Sound.ENTITY_WARDEN_HEARTBEAT, 1.6f, 1.6f);
    }

    private static void discardOrb(Rig rig) {
        for (Piece pc : rig.pieces) discard(rig, pc.display());
        for (Ray ray : rig.rays) discard(rig, ray.display());
        rig.pieces.clear();
        rig.rays.clear();
    }

    /** Frame zero of the divergence: flash, rings, sphere, rays, terrain, cracks. */
    public static void createBlast(Projectile p) {
        Location loc = p.impactLocation();
        World w = loc.getWorld();
        if (w == null) return;

        Rig rig = p.rig();
        if (rig == null) {
            rig = new Rig();
            p.setRig(rig);
        }
        double R = p.impactRadius();
        boolean max = p.isMax();
        ThreadLocalRandom r = ThreadLocalRandom.current();

        discardOrb(rig);

        RayTraceResult down = w.rayTraceBlocks(loc, new Vector(0, -1, 0), 10.0, FluidCollisionMode.NEVER, true);
        rig.hasGround = down != null;
        rig.groundY = down != null ? down.getHitPosition().getY() : loc.getY() - 1.0;

        // ── Sound: space being forced apart ──
        SoundUtils.play(loc, Sound.ENTITY_GENERIC_EXPLODE, 3.0f, 0.8f);
        SoundUtils.play(loc, Sound.ENTITY_WARDEN_SONIC_BOOM, 2.5f, 0.6f);
        SoundUtils.play(loc, Sound.ENTITY_WARDEN_SONIC_BOOM, 2.0f, 1.1f);
        SoundUtils.play(loc, Sound.ITEM_TRIDENT_THUNDER, 2.0f, 1.2f);
        if (max) SoundUtils.play(loc, Sound.ENTITY_LIGHTNING_BOLT_THUNDER, 1.5f, 1.4f);

        // ── Flash + sonic-boom rings ──
        w.spawnParticle(Particle.FLASH, loc, 1, 0, 0, 0, 0, Color.WHITE);
        w.spawnParticle(Particle.SONIC_BOOM, loc, 1, 0, 0, 0, 0);
        int booms = max ? 4 : 2;
        for (int i = 0; i < booms; i++) {
            w.spawnParticle(Particle.SONIC_BOOM, loc.clone().add((r.nextDouble() - 0.5) * R * 0.3,
                    (r.nextDouble() - 0.5) * R * 0.2, (r.nextDouble() - 0.5) * R * 0.3), 1, 0, 0, 0, 0);
        }
        w.spawnParticle(Particle.EXPLOSION_EMITTER, loc, 1, 0, 0, 0, 0);

        // ── Radial pressure burst: sparks and puffs flung outward ──
        int bursts = (int) Math.max(40, Math.min(200, (40 + R * 8) * Math.max(0.6, p.quality())));
        double speedScale = Math.max(0.8, Math.min(1.6, R / 5.0));
        for (int i = 0; i < bursts; i++) {
            Vector v = randomUnit(r);
            double sp = (0.5 + r.nextDouble() * 1.0) * speedScale;
            w.spawnParticle(Particle.END_ROD, loc, 0, v.getX(), v.getY(), v.getZ(), sp);
            if (i % 3 == 0) w.spawnParticle(Particle.CLOUD, loc, 0, v.getX(), v.getY(), v.getZ(), sp * 0.6);
            if (i % 5 == 0) w.spawnParticle(Particle.ELECTRIC_SPARK, loc, 0, v.getX(), v.getY(), v.getZ(), sp * 0.8);
        }

        // ── Blast sphere: white-hot core, two crimson glass layers (two rotated cubes each) ──
        double fr = Math.min(R * 0.5, 8.0);
        float[] sizes = {(float) fr, (float) (fr * 1.6), (float) (fr * 2.2)};
        Material[] mats = {Material.WHITE_CONCRETE, Material.RED_STAINED_GLASS, Material.RED_STAINED_GLASS};
        for (int layer = 0; layer < 3; layer++) {
            for (int k = 0; k < 2; k++) {
                BlockDisplay d = spawn(rig, loc, mats[layer], BRIGHT);
                rig.cubes.add(new Cube(d, randomRot(r), sizes[layer]));
            }
        }

        // ── Divergence rays: red lances shooting out in every direction ──
        int rays = Math.max(10, scaledCount(18, p.quality()));
        for (int i = 0; i < rays; i++) {
            Vector dir = randomUnit(r);
            rig.burst.add(new Spike(spawn(rig, loc, Material.RED_CONCRETE, BRIGHT), dir, R * (0.9 + 0.5 * r.nextDouble())));
        }

        if (rig.hasGround) {
            if (p.crater()) buildTerrain(rig, p, w, loc, R, r);
            buildCracks(rig, p, w, loc, R, r);
        }
    }

    /** Copies real terrain blocks into displays that get thrown outward; hides the real ones briefly. */
    private static void buildTerrain(Rig rig, Projectile p, World w, Location loc, double R, ThreadLocalRandom r) {
        int want = Math.max(8, (int) Math.round(24 * p.quality()));
        Set<Long> seen = new HashSet<>();
        Map<Location, BlockData> hide = new HashMap<>();
        BlockData air = Material.AIR.createBlockData();

        for (int tries = 0; tries < want * 3 && rig.chunks.size() < want; tries++) {
            double ang = r.nextDouble() * Math.PI * 2.0;
            double d = Math.sqrt(r.nextDouble()) * R * 0.9;
            int bx = (int) Math.floor(loc.getX() + Math.cos(ang) * d);
            int bz = (int) Math.floor(loc.getZ() + Math.sin(ang) * d);
            if (!w.isChunkLoaded(bx >> 4, bz >> 4)) continue;

            Location from = new Location(w, bx + 0.5, Math.floor(loc.getY()) + 4.0, bz + 0.5);
            RayTraceResult hit = w.rayTraceBlocks(from, new Vector(0, -1, 0), 12.0, FluidCollisionMode.NEVER, true);
            if (hit == null || hit.getHitBlock() == null) continue;

            Block b = hit.getHitBlock();
            Material m = b.getType();
            if (!m.isSolid() || m.getHardness() < 0 || b.getState() instanceof TileState) continue;
            long key = ((long) b.getX() << 40) ^ ((long) b.getZ() << 16) ^ (b.getY() & 0xFFFFL);
            if (!seen.add(key)) continue;

            final BlockData data = b.getBlockData();
            Location start = b.getLocation().add(0.5, 0.5, 0.5);
            Vector out = new Vector(start.getX() - loc.getX(), 0, start.getZ() - loc.getZ());
            if (out.lengthSquared() < 0.01) out = new Vector(Math.cos(ang), 0, Math.sin(ang));
            out.normalize();

            double near = 1.0 - Math.min(1.0, d / (R * 0.9));
            double fling = R * (0.5 + 0.9 * near) * (0.6 + 0.8 * r.nextDouble());
            Location apex = start.clone().add(out.clone().multiply(fling * 0.5))
                    .add(0, R * (0.4 + 0.5 * near) + 0.5, 0);
            Location land = start.clone().add(out.clone().multiply(fling));
            land.setY(rig.groundY + 0.5);

            BlockDisplay disp = w.spawn(start, BlockDisplay.class, t -> {
                t.setBlock(data);
                t.setPersistent(false);
                t.setViewRange(VIEW);
                t.setInterpolationDuration(0);
                t.setTeleportDuration(0);
            });
            rig.entities.add(disp);
            place(disp, start, new Quaternionf(), 1f, 1f, 1f, 0, 0);
            rig.chunks.add(new Chunk(disp, start, apex, land));

            Location blockKey = b.getLocation();
            hide.put(blockKey, air);
            rig.craterBlocks.add(blockKey);
        }

        // Hide the real blocks for nearby players so the crater is visible as the chunks fly.
        if (!hide.isEmpty()) {
            for (Player pl : w.getPlayers()) {
                if (pl.getLocation().distanceSquared(loc) <= CRATER_RANGE_SQ) {
                    ClientBlockUtils.sendFakeBlocks(pl, hide);
                    rig.craterViewers.add(pl.getUniqueId());
                }
            }
        }
    }

    /** Fault lines that split the ground outward from the impact and fade out later. */
    private static void buildCracks(Rig rig, Projectile p, World w, Location loc, double R, ThreadLocalRandom r) {
        int cracks = Math.max(6, scaledCount(10, p.quality()));
        double gy = rig.groundY + 0.03;
        for (int i = 0; i < cracks; i++) {
            double ang = (i + r.nextDouble() * 0.6) * Math.PI * 2.0 / cracks;
            Vector out = new Vector(Math.cos(ang), 0, Math.sin(ang));
            double len = R * (0.6 + 0.8 * r.nextDouble());
            Location a = new Location(w, loc.getX() + out.getX() * 0.8, gy, loc.getZ() + out.getZ() * 0.8);
            Location b = new Location(w, loc.getX() + out.getX() * len, gy, loc.getZ() + out.getZ() * len);

            BlockDisplay dark = DisplayUtils.spawnBlock(a, Material.BLACK_CONCRETE, VIEW, 4);
            strip(dark, a, b, 0.30f, 0.04f);
            BlockDisplay glow = DisplayUtils.spawnBlock(a, Material.RED_CONCRETE, VIEW, BRIGHT);
            strip(glow, a.clone().add(0, 0.012, 0), b.clone().add(0, 0.012, 0), 0.10f, 0.05f);
            rig.scars.add(dark);
            rig.scars.add(glow);
        }
    }

    private static void restoreCrater(Rig rig) {
        if (rig.craterBlocks.isEmpty()) return;
        for (UUID id : rig.craterViewers) {
            Player pl = Bukkit.getPlayer(id);
            if (pl != null && pl.isOnline()) ClientBlockUtils.revertBlocks(pl, rig.craterBlocks);
        }
        rig.craterBlocks.clear();
        rig.craterViewers.clear();
    }

    /** Animates the divergence. b is 1 on the first blast tick. */
    public static void tickBlast(Projectile p, int b) {
        Rig rig = p.rig();
        if (rig == null) return;
        Location loc = p.impactLocation();
        World w = loc.getWorld();
        if (w == null) return;

        double R = p.impactRadius();
        boolean max = p.isMax();
        ThreadLocalRandom r = ThreadLocalRandom.current();
        double gy = rig.hasGround ? rig.groundY : loc.getY();

        // ── Light: the scene is lit white-hot, then dims ──
        if (b <= 24) {
            lightBurst(rig, w, loc, R, b <= 4 ? 15 : Math.max(0, 15 - (b - 4)));
        } else if (b == 25) {
            TechniqueLighting.setLights(rig.lights, w, new HashMap<>());
        }

        // ── Blast sphere: swell fast, hold, shrink away ──
        if (b == 1) {
            for (Cube c : rig.cubes) place(c.display(), loc, c.rot(), c.size(), c.size(), c.size(), 3, 0);
        } else if (b == 6) {
            for (Cube c : rig.cubes) place(c.display(), loc, c.rot(), TINY, TINY, TINY, 8, 0);
        } else if (b == 16) {
            for (Cube c : rig.cubes) discard(rig, c.display());
            rig.cubes.clear();
        }

        // ── Divergence rays: lance out, then retract ──
        float th = (float) (0.12 + 0.03 * p.power());
        if (b == 1) {
            for (Spike s : rig.burst) {
                line(s.display(), loc, loc.clone().add(s.dir().clone().multiply(s.len())), th, 2);
            }
        } else if (b == 4) {
            for (Spike s : rig.burst) shrink(s.display(), 6);
        } else if (b == 12) {
            for (Spike s : rig.burst) discard(rig, s.display());
            rig.burst.clear();
        }

        // ── Shock shell (a sphere of light) and the ground ring ──
        if (b <= 10) {
            double rr = R * 1.5 * (1.0 - Math.pow(1.0 - b / 10.0, 2.2));
            int n = (int) Math.max(30, Math.min(120, R * 10.0 * p.quality()));
            double golden = Math.PI * (3.0 - Math.sqrt(5.0));
            double spin = r.nextDouble() * Math.PI * 2.0;
            for (int i = 0; i < n; i++) {
                double y = 1.0 - 2.0 * (i + 0.5) / n;
                double h = Math.sqrt(Math.max(0.0, 1.0 - y * y));
                double th2 = golden * i + spin;
                double px = loc.getX() + Math.cos(th2) * h * rr;
                double py = loc.getY() + y * rr;
                double pz = loc.getZ() + Math.sin(th2) * h * rr;
                if (py < gy) continue;
                if (i % 3 == 0) {
                    w.spawnParticle(Particle.END_ROD, px, py, pz, 0, Math.cos(th2) * h, y, Math.sin(th2) * h, 0.05);
                } else {
                    w.spawnParticle(Particle.DUST_COLOR_TRANSITION, px, py, pz, 1, 0, 0, 0, 0,
                            i % 2 == 0 ? RED_EDGE : HOT_EDGE);
                }
            }

            double ring = R * 1.8 * (1.0 - Math.pow(1.0 - b / 10.0, 2.2));
            int pts = (int) Math.max(24, Math.min(90, ring * 6.0));
            for (int i = 0; i < pts; i++) {
                double a = Math.PI * 2.0 * i / pts;
                double cx = Math.cos(a), sz = Math.sin(a);
                double x = loc.getX() + cx * ring;
                double z = loc.getZ() + sz * ring;
                w.spawnParticle(Particle.DUST_COLOR_TRANSITION, x, gy + 0.2, z, 1, 0, 0, 0, 0, RED_EDGE);
                if (i % 2 == 0) w.spawnParticle(Particle.CLOUD, x, gy + 0.25, z, 0, cx, 0.12, sz, 0.2);
            }
        }
        // Max output: a second, later shockwave.
        if (max && b >= 4 && b <= 14) {
            double ring = R * 2.3 * (1.0 - Math.pow(1.0 - (b - 3) / 11.0, 2.2));
            int pts = (int) Math.max(30, Math.min(110, ring * 6.0));
            for (int i = 0; i < pts; i++) {
                double a = Math.PI * 2.0 * i / pts;
                w.spawnParticle(Particle.END_ROD, loc.getX() + Math.cos(a) * ring, gy + 0.3,
                        loc.getZ() + Math.sin(a) * ring, 0, Math.cos(a), 0.1, Math.sin(a), 0.1);
            }
        }

        // ── Terrain: up, outward, down, gone — then the real blocks come back ──
        if (b == 1) {
            for (Chunk c : rig.chunks) {
                place(c.display(), c.apex(), tumble(r), 1f, 1f, 1f, 6, 6);
            }
        } else if (b == 7) {
            for (Chunk c : rig.chunks) {
                place(c.display(), c.land(), tumble(r), 1f, 1f, 1f, 8, 8);
            }
        } else if (b == 15) {
            for (Chunk c : rig.chunks) {
                place(c.display(), c.land(), new Quaternionf(), TINY, TINY, TINY, 4, 0);
            }
        } else if (b == 20) {
            for (Chunk c : rig.chunks) discard(rig, c.display());
            rig.chunks.clear();
        }
        if (b == 24) restoreCrater(rig);

        // ── Lingering static ──
        if (b >= 8 && b <= 34 && b % 2 == 0) {
            for (int k = 0; k < 4; k++) {
                double a = r.nextDouble() * Math.PI * 2.0;
                double d = r.nextDouble() * R * 0.8;
                Location spot = new Location(w, loc.getX() + Math.cos(a) * d, gy + 0.3, loc.getZ() + Math.sin(a) * d);
                w.spawnParticle(Particle.ELECTRIC_SPARK, spot, 6, 0.6, 0.4, 0.6, 0.15);
                w.spawnParticle(Particle.CLOUD, spot, 2, 0.5, 0.1, 0.5, 0.01);
            }
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Ambient upkeep + cleanup
    // ─────────────────────────────────────────────────────────────────────────

    /** Ages timed entities (the ribbon): shrinks and removes them on schedule. */
    public static void tickAmbient(Projectile p) {
        Rig rig = p.rig();
        if (rig == null || rig.timed.isEmpty()) return;
        int now = p.ticksLived();

        Iterator<Timed> it = rig.timed.iterator();
        while (it.hasNext()) {
            Timed t = it.next();
            if (t.entity == null || !t.entity.isValid()) {
                it.remove();
                continue;
            }
            int age = now - t.born;
            if (age >= t.life) {
                discard(rig, t.entity);
                it.remove();
                continue;
            }
            if (t.onAge != null) t.onAge.accept(age);
        }
    }

    /** Removes every display, light and hidden block. The ground cracks fade out later on their own. */
    public static void cleanup(Projectile p) {
        Rig rig = p.rig();
        if (rig == null) return;

        try {
            restoreCrater(rig);
            World w = p.currentLocation() != null && p.currentLocation().getWorld() != null
                    ? p.currentLocation().getWorld() : p.caster().getWorld();
            if (w != null && !rig.lights.isEmpty()) TechniqueLighting.setLights(rig.lights, w, new HashMap<>());
        } catch (Throwable ignored) {
            // Restoration is cosmetic; never block the cleanup.
        }

        for (Entity e : new ArrayList<>(rig.entities)) {
            if (e != null && e.isValid()) e.remove();
        }
        rig.entities.clear();
        rig.pieces.clear();
        rig.rays.clear();
        rig.cubes.clear();
        rig.burst.clear();
        rig.chunks.clear();
        rig.timed.clear();

        List<Entity> scars = new ArrayList<>(rig.scars);
        rig.scars.clear();
        if (!scars.isEmpty()) {
            int delay = p.scarTicks();
            try {
                ShenanigansCore plugin = ShenanigansCore.getInstance();
                Bukkit.getScheduler().runTaskLater(plugin, () -> {
                    for (Entity e : scars) {
                        if (e instanceof BlockDisplay d && d.isValid()) shrink(d, 20);
                    }
                }, delay);
                Bukkit.getScheduler().runTaskLater(plugin, () -> {
                    for (Entity e : scars) {
                        if (e != null && e.isValid()) e.remove();
                    }
                }, delay + 22L);
            } catch (Throwable t) {
                for (Entity e : scars) {
                    if (e != null && e.isValid()) e.remove();
                }
            }
        }
        p.setRig(null);
    }
}
