package com.hizeric.shenaniganscore.origins.jujutsukaisen.techniques.blue;

import com.hizeric.shenaniganscore.core.display.TechniqueLighting;
import com.hizeric.shenaniganscore.ShenanigansCore;
import com.hizeric.shenaniganscore.core.display.DisplayUtils;
import com.hizeric.shenaniganscore.core.sound.SoundUtils;
import com.hizeric.shenaniganscore.core.util.ClientBlockUtils;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextColor;
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
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.inventory.ItemStack;
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
import static com.hizeric.shenaniganscore.core.display.TechniqueDisplayOps.discard;
import static com.hizeric.shenaniganscore.core.display.TechniqueDisplayOps.moveDisplay;
import static com.hizeric.shenaniganscore.core.display.TechniqueDisplayOps.randomRot;
import static com.hizeric.shenaniganscore.core.display.TechniqueDisplayOps.scaleOnly;
import static com.hizeric.shenaniganscore.core.display.TechniqueDisplayOps.spawn;
import static com.hizeric.shenaniganscore.core.display.BlockDisplayOps.line;
import static com.hizeric.shenaniganscore.core.display.TechniqueDisplayOps.spawnGlyph;
import static com.hizeric.shenaniganscore.core.display.TechniqueDisplayOps.poseGlint;
import static com.hizeric.shenaniganscore.core.display.TechniqueDisplayOps.poseHalos;

/**
 * Every visual of Cursed Technique Lapse: Blue, built from display entities.
 *
 * <p><b>Visual idea: convergence.</b> Red pushes everything away and is clean and bright; Blue is its
 * mirror — everything is <i>drawn in</i>, spirals, and is ground into a point. So the whole effect is
 * a small black-hole system made of displays:</p>
 *
 * <ul>
 *   <li><b>The core (BlockDisplay)</b> — a black cube in three layers of counter-rotating blue glass, with a
 *       white glint orbiting it. The "event horizon".</li>
 *   <li><b>The halo (BlockDisplay with billboard)</b> — four flat discs that always face the viewer, in
 *       light-blue and white glass, pulse around the core as a bloom / lensing glow.</li>
 *   <li><b>The accretion disc (BlockDisplay)</b> — dozens of small cubes orbit the core in a slowly precessing,
 *       tilted plane, the inner ones faster than the outer ones, like a real accretion disc.</li>
 *   <li><b>Suction streams (ItemDisplay)</b> — lapis-lazuli sprites spiral in from the edge of the pull radius,
 *       speeding up and shrinking as they fall into the core.</li>
 *   <li><b>Tendrils (BlockDisplay, moved in two steps)</b> — thin streaks are placed far out, then glide into
 *       the core while thinning, again and again.</li>
 *   <li><b>Lifted terrain (BlockDisplay with real block data)</b> — blocks around the well are copied into
 *       displays that spiral up and in and are crushed at the core; the real blocks are hidden for a
 *       moment on clients and restored. The pool recycles, so the ground is steadily eaten.</li>
 *   <li><b>TextDisplay</b> — a giant 蒼 seal when the well opens.</li>
 *   <li><b>Client-side light</b> — the core glows; ground scars (concentric grooves) are left behind.</li>
 * </ul>
 *
 * <p>Nothing real is ever changed. Everything scales with {@code jujutsukaisen.techniques.blue.quality}.</p>
 */
public final class BlueBuilder {

    private BlueBuilder() {}

    private static final double HIDE_RANGE_SQ = 96.0 * 96.0;

    private static final Particle.DustTransition BLUE_EDGE =
            new Particle.DustTransition(Color.fromRGB(120, 190, 255), Color.fromRGB(20, 40, 160), 1.5f);
    private static final Particle.DustTransition WHITE_BLUE =
            new Particle.DustTransition(Color.fromRGB(255, 255, 255), Color.fromRGB(80, 160, 255), 1.3f);
    private static final TextColor BLUE_TEXT = TextColor.color(110, 190, 255);
    private static final BlockData AIR = Material.AIR.createBlockData();


    // ─────────────────────────────────────────────────────────────────────────
    // Data carried by the technique
    // ─────────────────────────────────────────────────────────────────────────

    /** One layer of the core: rel = size relative to the core radius, spin = angular speed. */
    private record Piece(BlockDisplay display, Quaternionf rot, float rel, float spin) {}

    /** One cube of the accretion disc. */
    private record Orbiter(BlockDisplay display, double r, double a0, double speed, double yoff,
                           float size, Quaternionf rot) {}

    /** One lapis sprite spiralling in from the edge of the pull radius. */
    private record Suck(ItemDisplay display, double phase, double speed, double a0, double r0, double ytilt) {}

    /** A thin streak that is placed far out, then glides into the core. */
    private static final class Streak {
        final BlockDisplay display;
        Quaternionf rot;
        Location end;
        int stage;
        int next;

        Streak(BlockDisplay display, int next) {
            this.display = display;
            this.next = next;
        }
    }

    /** A real terrain block lifted into the well. */
    private static final class Lift {
        final BlockDisplay display;
        final Location blockLoc;
        final Quaternionf rot;
        final double r0;
        final double a0;
        final double h0;
        final int born;
        int life;

        Lift(BlockDisplay display, Location blockLoc, Quaternionf rot, double r0, double a0, double h0,
             int born, int life) {
            this.display = display;
            this.blockLoc = blockLoc;
            this.rot = rot;
            this.r0 = r0;
            this.a0 = a0;
            this.h0 = h0;
            this.born = born;
            this.life = life;
        }
    }

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

    /** Everything the technique spawns, plus the little state the animation needs. */
    public static final class Rig implements DisplayRig {
        final List<Entity> entities = new ArrayList<>();

        @Override
        public List<Entity> entities() {
            return entities;
        }
        final List<Piece> core = new ArrayList<>();
        final List<BlockDisplay> halos = new ArrayList<>();
        BlockDisplay glint;
        final List<Orbiter> orbiters = new ArrayList<>();
        final List<Suck> sucks = new ArrayList<>();
        final List<Streak> streaks = new ArrayList<>();
        final List<Lift> lifts = new ArrayList<>();
        final Set<Long> usedBlocks = new HashSet<>();
        final Set<UUID> viewers = new HashSet<>();
        final List<Entity> scars = new ArrayList<>();
        final List<Timed> timed = new ArrayList<>();
        final Map<Location, Integer> lights = new HashMap<>();
        float radius = 0.4f;
        Location center;
        double groundY;
        boolean hasGround;
        /** 0 while the well is open, rising to 1 as the collapse crushes everything in. */
        double crush;
    }

    /** Where the point is (the release point). */
    static Location centerOf(Rig rig) {
        return rig.center == null ? null : rig.center.clone();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Small helpers
    // ─────────────────────────────────────────────────────────────────────────

    private static int scaled(BlueProjectile b, int base) {
        return (int) Math.max(1, Math.round(base * b.quality()));
    }

    /** Spawns a fullbright block display (tiny) and tracks it for cleanup. */
    /** A lapis-lazuli sprite (a blue gem) that always faces the viewer. */
    private static ItemDisplay spawnSprite(Rig rig, Location at) {
        ItemDisplay d = DisplayUtils.spawnItem(at, new ItemStack(Material.LAPIS_LAZULI), VIEW, BRIGHT);
        d.setBillboard(Display.Billboard.CENTER);
        d.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.FIXED);
        d.setTransformation(scaleOnly(TINY, 0f));
        rig.entities.add(d);
        return d;
    }

    /** A glowing kanji that always faces the viewer. */
    /** Box of the given size (rotated by q) whose CENTER sits on {@code center}. */
    /** A thin glowing beam between two points. */
    /** A flat strip lying on the ground between two points. */
    private static void strip(Rig rig, Location a, Location b, float width, float height, Material m, int brightness) {
        Vector delta = b.toVector().subtract(a.toVector());
        double len = delta.length();
        if (len < 0.05) return;
        Location mid = a.clone().add(delta.clone().multiply(0.5));
        BlockDisplay d = DisplayUtils.spawnBlock(mid, m, VIEW, brightness);
        place(d, mid, facing(delta.multiply(1.0 / len)), width, height, (float) len * 1.08f, 0, 0);
        rig.scars.add(d);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Client-side light and hidden blocks
    // ─────────────────────────────────────────────────────────────────────────

    private static void lightPair(Rig rig, World w, Location a, int levelA) {
        Map<Location, Integer> want = new HashMap<>();
        if (a != null && levelA > 0) {
            Location la = blockLoc(a);
            if (lightable(w, la)) want.put(la, levelA);
        }
        TechniqueLighting.setLights(rig.lights, w, want);
    }

    /** The well's light: the core plus four points around it. */
    private static void lightWell(Rig rig, World w, Location c, int level) {
        Map<Location, Integer> want = new HashMap<>();
        if (level > 0) {
            double d = 1.4;
            double[][] offsets = {{0, 0, 0}, {d, 0, 0}, {-d, 0, 0}, {0, 0, d}, {0, 0, -d}};
            for (double[] o : offsets) {
                Location l = blockLoc(c.clone().add(o[0], o[1], o[2]));
                if (lightable(w, l)) want.put(l, level);
            }
        }
        TechniqueLighting.setLights(rig.lights, w, want);
    }

    /** Puts one real block back for everyone who had it hidden. */
    private static void restoreBlock(Rig rig, Location loc) {
        for (UUID id : rig.viewers) {
            Player pl = Bukkit.getPlayer(id);
            if (pl != null && pl.isOnline()) ClientBlockUtils.revertBlocks(pl, List.of(loc));
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // CORE / HALO / GLINT
    // ─────────────────────────────────────────────────────────────────────────

    /** Poses the black core and its blue glass layers. charge scales the whole core. */
    private static void poseCore(Rig rig, Location c, double tick, double charge, double spinMul, double pulse) {
        float r = (float) (rig.radius * charge);
        for (Piece pc : rig.core) {
            Quaternionf q = new Quaternionf(pc.rot())
                    .rotateY((float) (tick * pc.spin() * spinMul))
                    .rotateX((float) (tick * pc.spin() * 0.6 * spinMul));
            float s = (float) Math.max(TINY, r * pc.rel() * pulse);
            place(pc.display(), c, q, s, s, s, 1, 1);
        }
    }

    /** Poses the four billboard discs (two sizes, each as an octagon of two squares). */
    // ─────────────────────────────────────────────────────────────────────────
    // CHARGE — a point condenses at the hand
    // ─────────────────────────────────────────────────────────────────────────

    /** Builds the core, halo and glint (tiny; {@link #tickCharge} grows them). */
    public static void spawnOrb(BlueProjectile b) {
        Location at = b.caster().getEyeLocation();
        ThreadLocalRandom r = ThreadLocalRandom.current();

        Rig rig = new Rig();
        rig.radius = (float) (0.35 + 0.05 * b.power());
        rig.center = at.clone();

        rig.core.add(new Piece(spawn(rig, at, Material.BLACK_CONCRETE, 6), randomRot(r), 0.6f, 0.0f));
        rig.core.add(new Piece(spawn(rig, at, Material.BLUE_STAINED_GLASS, BRIGHT), randomRot(r), 1.0f, 0.30f));
        rig.core.add(new Piece(spawn(rig, at, Material.LIGHT_BLUE_STAINED_GLASS, BRIGHT), randomRot(r), 1.5f, -0.22f));
        rig.core.add(new Piece(spawn(rig, at, Material.BLUE_STAINED_GLASS, BRIGHT), randomRot(r), 2.1f, 0.14f));
        rig.glint = spawn(rig, at, Material.WHITE_CONCRETE, BRIGHT);

        // The halo: flat discs that always face the viewer.
        Material[] halo = {Material.LIGHT_BLUE_STAINED_GLASS, Material.LIGHT_BLUE_STAINED_GLASS,
                Material.WHITE_STAINED_GLASS, Material.WHITE_STAINED_GLASS};
        for (Material m : halo) {
            BlockDisplay d = spawn(rig, at, m, BRIGHT);
            d.setBillboard(Display.Billboard.CENTER);
            rig.halos.add(d);
        }
        b.setRig(rig);
    }

    /** Called every charge tick. progress runs 0..1. */
    public static void tickCharge(BlueProjectile b, double progress) {
        Rig rig = b.rig();
        if (rig == null) return;

        Player c = b.caster();
        World w = c.getWorld();
        ThreadLocalRandom r = ThreadLocalRandom.current();
        int tick = b.stateTicks();
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
        double rad = rig.radius * charge;
        poseCore(rig, grip, tick, charge, 1.0 + 2.0 * e, 1.0 + 0.08 * Math.sin(tick * 0.9));
        double pulse = 1.0 + 0.08 * Math.sin(tick * 0.5);
        poseHalos(rig.halos, grip, rad * 5.0 * pulse * e, rad * 2.8 * pulse * e);
        poseGlint(rig.glint, grip, tick, rad, 0.5 + 1.2 * e);
        lightPair(rig, w, grip, (int) (6 + 8 * e));

        // Everything nearby leans toward the point: glyphs and sparks stream in.
        for (int i = 0; i < 3; i++) {
            Vector v = randomUnit(r).multiply(1.6 + r.nextDouble() * 1.4);
            w.spawnParticle(Particle.ENCHANT, grip, 0, v.getX(), v.getY(), v.getZ(), 1.0);
        }
        for (int i = 0; i < 3; i++) {
            Vector v = randomUnit(r);
            Location from = grip.clone().add(v.clone().multiply(1.5));
            w.spawnParticle(Particle.END_ROD, from, 0, -v.getX(), -v.getY(), -v.getZ(), 0.15);
        }
        w.spawnParticle(Particle.ELECTRIC_SPARK, grip, 2, rad * 1.5, rad * 1.5, rad * 1.5, 0.1);

        // A ring of light tightening around the hand.
        double ringR = 0.9 * (1.0 - 0.5 * e);
        for (int i = 0; i < 10; i++) {
            double a = Math.PI * 2.0 * i / 10.0 + progress * 7.0;
            Location p = grip.clone().add(right.clone().multiply(Math.cos(a) * ringR))
                    .add(up.clone().multiply(Math.sin(a) * ringR));
            w.spawnParticle(Particle.DUST_COLOR_TRANSITION, p, 1, 0, 0, 0, 0, BLUE_EDGE);
        }

        if (tick == 1) {
            SoundUtils.play(grip, Sound.ENTITY_WARDEN_SONIC_CHARGE, 1.0f, 1.2f);
        } else if (tick % 3 == 0) {
            SoundUtils.play(grip, Sound.BLOCK_AMETHYST_BLOCK_RESONATE, 1.0f, (float) (0.6 + 0.9 * progress));
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // RELEASE + FLIGHT
    // ─────────────────────────────────────────────────────────────────────────

    /** The point is sent out. */
    public static void release(BlueProjectile b) {
        Rig rig = b.rig();
        if (rig == null) return;
        Location at = b.currentLocation();
        World w = at.getWorld();
        if (w == null) return;

        w.spawnParticle(Particle.FLASH, at, 1, 0, 0, 0, 0, Color.WHITE);
        SoundUtils.play(at, Sound.ENTITY_ENDER_EYE_LAUNCH, 1.2f, 0.8f);
        SoundUtils.play(at, Sound.BLOCK_BEACON_POWER_SELECT, 1.0f, 1.6f);
    }

    /** A fading blue ribbon segment left behind the point. */
    private static void ribbon(Rig rig, Location a, Location bb, Material m, float thickness, int now) {
        final BlockDisplay d = spawn(rig, a, m, BRIGHT);
        line(d, a, bb, thickness);
        rig.timed.add(new Timed(d, now, 9, age -> {
            if (age == 1) shrink(d, 7);
        }));
    }

    /** Moves the point: pose, trailing ribbon, contracting rings, sparks, travelling light. */
    public static void tickFlight(BlueProjectile b, Location from, Location to) {
        Rig rig = b.rig();
        World w = to.getWorld();
        if (rig == null || w == null) return;

        ThreadLocalRandom r = ThreadLocalRandom.current();
        int tick = b.flightTicks();
        double t = b.ticksLived();
        double rad = rig.radius;

        rig.center = to.clone();
        poseCore(rig, to, t, 1.0, 1.6, 1.0 + 0.08 * Math.sin(t * 0.9));
        double pulse = 1.0 + 0.08 * Math.sin(t * 0.5);
        poseHalos(rig.halos, to, rad * 5.0 * pulse, rad * 2.8 * pulse);
        poseGlint(rig.glint, to, t, rad, 1.0);
        lightPair(rig, w, to, 13);

        // Rings that CONTRACT toward the point (the mirror of Red's expanding ripples).
        if (tick % 3 == 0) {
            Vector[] bs = basisOf(b.direction());
            for (int i = 0; i < 14; i++) {
                double a = Math.PI * 2.0 * i / 14.0;
                Vector out = bs[0].clone().multiply(Math.cos(a)).add(bs[1].clone().multiply(Math.sin(a)));
                Location p = to.clone().add(out.clone().multiply(1.6));
                w.spawnParticle(Particle.END_ROD, p, 0, -out.getX(), -out.getY(), -out.getZ(), 0.2);
            }
        }
        w.spawnParticle(Particle.ELECTRIC_SPARK, to, 3, 0.5, 0.5, 0.5, 0.15);

        Vector step = to.toVector().subtract(from.toVector());
        double len = step.length();
        int n = Math.max(1, (int) Math.ceil(len / 0.6));
        for (int i = 1; i <= n; i++) {
            Location pt = from.clone().add(step.clone().multiply(i / (double) n));
            w.spawnParticle(Particle.DUST_COLOR_TRANSITION, pt, 1, 0.1, 0.1, 0.1, 0,
                    r.nextBoolean() ? BLUE_EDGE : WHITE_BLUE);
        }

        if (len > 0.05) {
            int now = b.ticksLived();
            float k = (float) (0.5 + 0.12 * b.power());
            ribbon(rig, from, to, Material.BLUE_STAINED_GLASS, 0.5f * k, now);
            if (b.quality() >= 0.7) ribbon(rig, from, to, Material.WHITE_CONCRETE, 0.1f * k, now);
        }

        if (tick % 4 == 1) SoundUtils.play(to, Sound.BLOCK_PORTAL_AMBIENT, 0.8f, 1.2f);
    }

    private static Vector[] basisOf(Vector dir) {
        Vector n = dir.clone().normalize();
        Vector u = n.clone().crossProduct(new Vector(0, 1, 0));
        if (u.lengthSquared() < 1.0e-4) u = new Vector(1, 0, 0);
        u.normalize();
        Vector v = u.clone().crossProduct(n).normalize();
        return new Vector[]{u, v};
    }

    // ─────────────────────────────────────────────────────────────────────────
    // WELL — the point is anchored and pulls
    // ─────────────────────────────────────────────────────────────────────────

    /** Builds the accretion disc, suction streams, tendrils and seal; the bloom of the well. */
    public static void beginWell(BlueProjectile b) {
        Rig rig = b.rig();
        if (rig == null) {
            rig = new Rig();
            b.setRig(rig);
        }
        Location c = b.center();
        World w = c.getWorld();
        if (w == null) return;
        ThreadLocalRandom r = ThreadLocalRandom.current();
        double R = b.pullRadius();
        double rc = b.coreRadius();

        rig.center = c.clone();
        rig.radius = (float) (rc * 0.55);

        RayTraceResult down = w.rayTraceBlocks(c, new Vector(0, -1, 0), 24.0, FluidCollisionMode.NEVER, true);
        rig.hasGround = down != null;
        rig.groundY = down != null ? down.getHitPosition().getY() : c.getY() - 2.0;

        // Accretion disc: inner cubes orbit faster than outer ones.
        Material[] orbitMats = {Material.LIGHT_BLUE_CONCRETE, Material.LIGHT_BLUE_CONCRETE, Material.LIGHT_BLUE_CONCRETE,
                Material.BLUE_STAINED_GLASS, Material.WHITE_CONCRETE};
        int orbiters = Math.max(14, Math.min(48, scaled(b, 30)));
        for (int i = 0; i < orbiters; i++) {
            double orbitR = rc * (1.3 + 2.4 * r.nextDouble());
            double speed = 0.9 / Math.pow(orbitR, 0.8);
            Material m = orbitMats[r.nextInt(orbitMats.length)];
            rig.orbiters.add(new Orbiter(spawn(rig, c, m, BRIGHT), orbitR, r.nextDouble() * Math.PI * 2.0,
                    speed, (r.nextDouble() - 0.5) * 0.5, 0.12f + r.nextFloat() * 0.18f, randomRot(r)));
        }

        // Suction streams: lapis sprites falling in from the edge of the pull radius.
        int sucks = Math.max(10, Math.min(40, scaled(b, 24)));
        for (int i = 0; i < sucks; i++) {
            rig.sucks.add(new Suck(spawnSprite(rig, c), r.nextDouble(), 0.5 + r.nextDouble() * 0.6,
                    r.nextDouble() * Math.PI * 2.0, R * (0.45 + 0.5 * r.nextDouble()), r.nextDouble() - 0.5));
        }

        // Tendrils: thin streaks that glide into the core.
        int streaks = Math.max(4, Math.min(12, scaled(b, 8)));
        for (int i = 0; i < streaks; i++) {
            Material m = r.nextBoolean() ? Material.LIGHT_BLUE_CONCRETE : Material.WHITE_CONCRETE;
            rig.streaks.add(new Streak(spawn(rig, c, m, BRIGHT), r.nextInt(10)));
        }

        // The 蒼 seal above the core.
        final Location sealAt = c.clone().add(0, rc * 3.5 + 1.0, 0);
        final TextDisplay seal = spawnGlyph(rig, sealAt, "蒼", BLUE_TEXT, TINY);
        rig.timed.add(new Timed(seal, b.ticksLived(), 14, age -> {
            if (age == 1) {
                moveDisplay(seal, sealAt, 12f, 0f);
            } else if (age >= 4) {
                seal.setTextOpacity((byte) Math.max(40, 255 - (age - 3) * 22));
            }
        }));

        // Bloom: a shell of light collapses inward onto the point.
        int shell = 60;
        double golden = Math.PI * (3.0 - Math.sqrt(5.0));
        for (int i = 0; i < shell; i++) {
            double y = 1.0 - 2.0 * (i + 0.5) / shell;
            double h = Math.sqrt(Math.max(0.0, 1.0 - y * y));
            double th = golden * i;
            Vector v = new Vector(Math.cos(th) * h, y, Math.sin(th) * h);
            Location p = c.clone().add(v.clone().multiply(R * 0.9));
            w.spawnParticle(Particle.END_ROD, p, 0, -v.getX(), -v.getY(), -v.getZ(), 0.5);
        }
        w.spawnParticle(Particle.FLASH, c, 1, 0, 0, 0, 0, Color.WHITE);
        SoundUtils.play(c, Sound.BLOCK_BEACON_ACTIVATE, 1.5f, 1.3f);
        SoundUtils.play(c, Sound.ENTITY_ENDERMAN_TELEPORT, 1.2f, 0.5f);
        SoundUtils.play(c, Sound.ENTITY_WARDEN_SONIC_CHARGE, 1.2f, 0.8f);
    }

    /** One tick of the open well. t is 1 on the first tick. */
    public static void tickWell(BlueProjectile b, int t) {
        Rig rig = b.rig();
        if (rig == null) return;
        animateWell(b, rig, t);
    }

    /**
     * Shared animation for the well and the collapse. {@code rig.crush} (0..1) pulls everything
     * inward and shrinks it; at 0 the well is fully open.
     */
    private static void animateWell(BlueProjectile b, Rig rig, int t) {
        Location c = rig.center;
        World w = c == null ? null : c.getWorld();
        if (w == null) return;

        ThreadLocalRandom r = ThreadLocalRandom.current();
        double R = b.pullRadius();
        double rc = b.coreRadius();
        double crush = rig.crush;
        double bloom = MathUtil.smoothstep(Math.min(1.0, t / 8.0));
        boolean collapsing = b.state() == BlueProjectile.State.COLLAPSE;

        // ── Core, halo, glint ──
        double charge = (1.0 - 0.9 * crush) * (0.4 + 0.6 * bloom);
        poseCore(rig, c, t, charge, 3.0 + 6.0 * crush, 1.0 + 0.06 * Math.sin(t * 0.5));
        double haloPulse = 1.0 + 0.08 * Math.sin(t * 0.4);
        double haloSize = rig.radius * 5.0 * haloPulse * (1.0 - 0.85 * crush) * bloom;
        poseHalos(rig.halos, c, haloSize, haloSize * 0.55);
        poseGlint(rig.glint, c, t, rig.radius * (1.0 - 0.9 * crush), 1.4);

        // ── Accretion disc: tilted, slowly precessing, inner orbits faster ──
        if (t % 2 == 0 && !rig.orbiters.isEmpty()) {
            double prec = t * 0.012;
            double tilt = 0.5;
            Vector n = new Vector(Math.sin(tilt) * Math.cos(prec), Math.cos(tilt), Math.sin(tilt) * Math.sin(prec)).normalize();
            Vector u = n.clone().crossProduct(new Vector(0, 0, 1));
            if (u.lengthSquared() < 1.0e-4) u = new Vector(1, 0, 0);
            u.normalize();
            Vector v = n.clone().crossProduct(u).normalize();
            for (Orbiter o : rig.orbiters) {
                double ang = o.a0() + (t + 2) * o.speed();
                double rr = o.r() * (1.0 - crush) * (0.3 + 0.7 * bloom);
                Location pos = c.clone()
                        .add(u.clone().multiply(Math.cos(ang) * rr))
                        .add(v.clone().multiply(Math.sin(ang) * rr))
                        .add(n.clone().multiply(o.yoff() * (1.0 - crush)));
                float size = (float) (o.size() * (1.0 - 0.8 * crush));
                place(o.display(), pos, o.rot(), size, size, size, 2, 2);
            }
        }

        // ── Suction streams ──
        for (Suck s : rig.sucks) {
            double su = (t * s.speed() * 0.02 + s.phase()) % 1.0;
            double rr = (s.r0() * Math.pow(1.0 - su, 1.25) + 0.3) * (1.0 - 0.9 * crush);
            double ang = s.a0() + su * 7.0 + t * 0.04;
            Location pos = c.clone().add(Math.cos(ang) * rr, s.ytilt() * rr * 0.5, Math.sin(ang) * rr);
            float size = (float) (0.4 * (1.0 - 0.8 * su) * bloom * (1.0 - 0.8 * crush));
            moveDisplay(s.display(), pos, size, (float) ang);
        }

        // ── Tendrils: placed far out, then glide in while thinning ──
        for (Streak st : rig.streaks) {
            if (t < st.next) continue;
            if (st.stage == 0) {
                if (crush > 0.6) continue;
                Vector dir = randomUnit(r);
                dir.setY(dir.getY() * 0.4);
                dir.normalize();
                Location start = c.clone().add(dir.clone().multiply(R * (0.55 + 0.45 * r.nextDouble())));
                Vector inward = dir.clone().multiply(-1.0);
                st.rot = facing(inward);
                st.end = c.clone().add(dir.clone().multiply(rc * 0.5));
                place(st.display, start, st.rot, 0.07f, 0.07f, 3.2f, 0, 0);
                st.stage = 1;
                st.next = t + 1;
            } else if (st.stage == 1) {
                place(st.display, st.end, st.rot, 0.01f, 0.01f, 1.0f, 13, 13);
                st.stage = 2;
                st.next = t + 14 + r.nextInt(8);
            } else {
                st.stage = 0;
                st.next = t;
            }
        }

        // ── Lifted terrain ──
        if (b.crater()) tickLifts(b, rig, w, c, R, rc, t, collapsing, r);

        // ── Particles ──
        int glyphs = Math.max(2, scaled(b, 6));
        for (int i = 0; i < glyphs; i++) {
            Vector v = randomUnit(r).multiply(R * (0.4 + 0.6 * r.nextDouble()));
            w.spawnParticle(Particle.ENCHANT, c, 0, v.getX(), v.getY(), v.getZ(), 1.0);
        }
        w.spawnParticle(Particle.ELECTRIC_SPARK, c, 3, rc * 0.6, rc * 0.6, rc * 0.6, 0.15);

        // Spiral arms of light in the disc plane.
        for (int arm = 0; arm < 3; arm++) {
            for (int j = 0; j < 5; j++) {
                double rr = rc * (1.5 + 3.0 * (j / 5.0)) * (1.0 - crush);
                double ang = t * 0.25 + arm * 2.094 + j * 0.7;
                Location p = c.clone().add(Math.cos(ang) * rr, Math.sin(ang * 0.5) * rr * 0.12, Math.sin(ang) * rr);
                w.spawnParticle(Particle.DUST_COLOR_TRANSITION, p, 1, 0, 0, 0, 0, (arm + j) % 2 == 0 ? BLUE_EDGE : WHITE_BLUE);
            }
        }

        // Inward-rushing shells every half second (the pull made visible).
        if (t % 10 == 0 && crush < 0.5) {
            int pts = 24;
            for (int axis = 0; axis < 3; axis++) {
                for (int i = 0; i < pts; i++) {
                    double a = Math.PI * 2.0 * i / pts;
                    double cx = Math.cos(a), sz = Math.sin(a);
                    Vector v = axis == 0 ? new Vector(cx, 0, sz) : axis == 1 ? new Vector(cx, sz, 0) : new Vector(0, cx, sz);
                    Location p = c.clone().add(v.clone().multiply(R * 0.85));
                    w.spawnParticle(Particle.END_ROD, p, 0, -v.getX(), -v.getY(), -v.getZ(), 0.4);
                }
            }
        }

        // Light and sound.
        if (!collapsing) lightWell(rig, w, c, 14);
        if (t % 8 == 0) SoundUtils.play(c, Sound.BLOCK_BEACON_AMBIENT, 1.4f, 0.5f);
        if (t % 20 == 0) SoundUtils.play(c, Sound.BLOCK_PORTAL_AMBIENT, 1.0f, 0.6f);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Lifted terrain
    // ─────────────────────────────────────────────────────────────────────────

    private static void tickLifts(BlueProjectile b, Rig rig, World w, Location c, double R, double rc,
                                  int t, boolean collapsing, ThreadLocalRandom r) {
        int cap = Math.max(6, Math.min(24, scaled(b, 14)));
        if (!collapsing && t % 3 == 0 && t < b.wellTicks() - 30 && rig.lifts.size() < cap) {
            spawnLift(rig, w, c, R, rc, t, r);
        }

        Iterator<Lift> it = rig.lifts.iterator();
        while (it.hasNext()) {
            Lift lf = it.next();
            double u = (t - lf.born) / (double) lf.life;
            if (u >= 1.0 || !lf.display.isValid()) {
                crushLift(rig, w, c, lf);
                it.remove();
                continue;
            }
            double rad = lf.r0 * Math.pow(1.0 - u, 1.1) + 0.5;
            double ang = lf.a0 + u * Math.PI * 4.0 * (1.0 + u);
            double h = lf.h0 + (rc * 0.9 - lf.h0) * MathUtil.smoothstep(u);
            Location pos = new Location(w, c.getX() + Math.cos(ang) * rad, c.getY() + h, c.getZ() + Math.sin(ang) * rad);
            float size = (float) Math.max(0.2, 1.0 - 0.75 * u);
            Quaternionf q = new Quaternionf(lf.rot).rotateXYZ((float) (u * 6.0), (float) (u * 8.0), (float) (u * 4.0));
            place(lf.display, pos, q, size, size, size, 1, 1);
        }
    }

    /** Picks a real top-surface block around the well, copies it into a display, hides the real one. */
    private static void spawnLift(Rig rig, World w, Location c, double R, double rc, int t, ThreadLocalRandom r) {
        for (int tries = 0; tries < 6; tries++) {
            double ang = r.nextDouble() * Math.PI * 2.0;
            double d = rc * 1.6 + r.nextDouble() * (R * 0.9 - rc * 1.6);
            int bx = (int) Math.floor(c.getX() + Math.cos(ang) * d);
            int bz = (int) Math.floor(c.getZ() + Math.sin(ang) * d);
            if (!w.isChunkLoaded(bx >> 4, bz >> 4)) continue;

            Location from = new Location(w, bx + 0.5, Math.floor(c.getY()) + 5.0, bz + 0.5);
            RayTraceResult hit = w.rayTraceBlocks(from, new Vector(0, -1, 0), 16.0, FluidCollisionMode.NEVER, true);
            if (hit == null || hit.getHitBlock() == null) continue;

            Block block = hit.getHitBlock();
            Material m = block.getType();
            if (!m.isSolid() || m.getHardness() < 0 || block.getState() instanceof TileState) continue;
            long key = ((long) block.getX() << 40) ^ ((long) block.getZ() << 16) ^ (block.getY() & 0xFFFFL);
            if (!rig.usedBlocks.add(key)) continue;

            final BlockData data = block.getBlockData();
            Location blockLoc = block.getLocation();
            Location start = blockLoc.clone().add(0.5, 0.5, 0.5);
            double dx = start.getX() - c.getX();
            double dz = start.getZ() - c.getZ();

            BlockDisplay disp = w.spawn(start, BlockDisplay.class, t2 -> {
                t2.setBlock(data);
                t2.setPersistent(false);
                t2.setViewRange(VIEW);
                t2.setInterpolationDuration(0);
                t2.setTeleportDuration(0);
            });
            rig.entities.add(disp);
            place(disp, start, new Quaternionf(), 1f, 1f, 1f, 0, 0);

            // Hide the real block for nearby players while its copy is torn away.
            for (Player pl : w.getPlayers()) {
                if (pl.getLocation().distanceSquared(start) <= HIDE_RANGE_SQ) {
                    ClientBlockUtils.sendFakeBlocks(pl, Map.of(blockLoc, AIR));
                    rig.viewers.add(pl.getUniqueId());
                }
            }
            rig.lifts.add(new Lift(disp, blockLoc, randomRot(r), Math.sqrt(dx * dx + dz * dz),
                    Math.atan2(dz, dx), start.getY() - c.getY(), t, 36 + r.nextInt(20)));
            return;
        }
    }

    private static void crushLift(Rig rig, World w, Location c, Lift lf) {
        Location pos = lf.display.isValid() ? lf.display.getLocation() : c;
        w.spawnParticle(Particle.CLOUD, pos, 4, 0.2, 0.2, 0.2, 0.02);
        w.spawnParticle(Particle.ELECTRIC_SPARK, pos, 4, 0.2, 0.2, 0.2, 0.1);
        discard(rig, lf.display);
        restoreBlock(rig, lf.blockLoc);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // COLLAPSE — everything is crushed in, then the space snaps back
    // ─────────────────────────────────────────────────────────────────────────

    /** The collapse begins: terrain is crushed quickly, scars are cut into the ground. */
    public static void beginCollapse(BlueProjectile b) {
        Rig rig = b.rig();
        if (rig == null || rig.center == null) return;
        World w = rig.center.getWorld();
        if (w == null) return;

        int now = b.wellTicks();
        for (Lift lf : rig.lifts) lf.life = Math.min(lf.life, (now - lf.born) + 8);

        if (rig.hasGround) buildScars(rig, b, w);

        SoundUtils.play(rig.center, Sound.ENTITY_WARDEN_SONIC_CHARGE, 1.6f, 0.6f);
        SoundUtils.play(rig.center, Sound.BLOCK_BEACON_DEACTIVATE, 1.4f, 0.7f);
    }

    /** Concentric grooves (dark, with a thin blue glow) cut into the ground under the well. */
    private static void buildScars(Rig rig, BlueProjectile b, World w) {
        double rc = b.coreRadius();
        double[] radii = b.isMax() ? new double[]{rc * 1.5, rc * 3.0, rc * 4.5} : new double[]{rc * 1.5, rc * 3.0};
        double y = rig.groundY + 0.03;
        int segs = 14;
        for (double radius : radii) {
            for (int k = 0; k < segs; k++) {
                double a0 = Math.PI * 2.0 * k / segs;
                double a1 = Math.PI * 2.0 * (k + 1) / segs;
                Location p0 = new Location(w, rig.center.getX() + Math.cos(a0) * radius, y, rig.center.getZ() + Math.sin(a0) * radius);
                Location p1 = new Location(w, rig.center.getX() + Math.cos(a1) * radius, y, rig.center.getZ() + Math.sin(a1) * radius);
                strip(rig, p0, p1, 0.22f, 0.04f, Material.BLACK_CONCRETE, 4);
                strip(rig, p0.clone().add(0, 0.012, 0), p1.clone().add(0, 0.012, 0), 0.07f, 0.05f,
                        Material.LIGHT_BLUE_CONCRETE, BRIGHT);
            }
        }
    }

    /** One tick of the collapse. ct is 1 on the first tick. */
    public static void tickCollapse(BlueProjectile b, int ct) {
        Rig rig = b.rig();
        if (rig == null || rig.center == null) return;
        World w = rig.center.getWorld();
        if (w == null) return;
        Location c = rig.center;
        double R = b.pullRadius();

        rig.crush = MathUtil.smoothstep(Math.min(1.0, ct / 14.0));
        animateWell(b, rig, b.wellTicks() + ct);

        // The final crush: flash, boom, everything gone.
        if (ct == 14) {
            for (Lift lf : new ArrayList<>(rig.lifts)) crushLift(rig, w, c, lf);
            rig.lifts.clear();
            discardCore(rig);
            w.spawnParticle(Particle.FLASH, c, 1, 0, 0, 0, 0, Color.WHITE);
            w.spawnParticle(Particle.EXPLOSION_EMITTER, c, 1, 0, 0, 0, 0);
            SoundUtils.play(c, Sound.ENTITY_GENERIC_EXPLODE, 2.5f, 0.6f);
            SoundUtils.play(c, Sound.ENTITY_WARDEN_SONIC_BOOM, 2.0f, 0.5f);
        }

        // The release: space snaps back as an outward ring.
        if (ct >= BlueProjectile.RELEASE_TICK) {
            if (ct == BlueProjectile.RELEASE_TICK) {
                w.spawnParticle(Particle.SONIC_BOOM, c, 1, 0, 0, 0, 0);
                SoundUtils.play(c, Sound.BLOCK_BEACON_DEACTIVATE, 1.6f, 1.4f);
            }
            double f = (ct - BlueProjectile.RELEASE_TICK + 1) / 7.0;
            double rr = R * 0.7 * Math.min(1.0, f);
            int pts = 40;
            for (int i = 0; i < pts; i++) {
                double a = Math.PI * 2.0 * i / pts;
                double cx = Math.cos(a), sz = Math.sin(a);
                w.spawnParticle(Particle.CLOUD, c.getX() + cx * rr, c.getY(), c.getZ() + sz * rr, 0, cx, 0.05, sz, 0.15);
                if (i % 2 == 0) {
                    w.spawnParticle(Particle.DUST_COLOR_TRANSITION, c.getX() + cx * rr, c.getY(), c.getZ() + sz * rr,
                            1, 0, 0, 0, 0, BLUE_EDGE);
                }
            }
        }

        // Light fades.
        if (ct >= 10) lightWell(rig, w, c, Math.max(0, 14 - (ct - 10) * 2));
    }

    private static void discardCore(Rig rig) {
        for (Piece pc : rig.core) discard(rig, pc.display());
        for (BlockDisplay d : rig.halos) discard(rig, d);
        for (Orbiter o : rig.orbiters) discard(rig, o.display());
        for (Suck s : rig.sucks) discard(rig, s.display());
        for (Streak st : rig.streaks) discard(rig, st.display);
        discard(rig, rig.glint);
        rig.core.clear();
        rig.halos.clear();
        rig.orbiters.clear();
        rig.sucks.clear();
        rig.streaks.clear();
        rig.glint = null;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Ambient upkeep + cleanup
    // ─────────────────────────────────────────────────────────────────────────

    /** Ages timed entities (ribbon, seal): shrinks and removes them on schedule. */
    public static void tickAmbient(BlueProjectile b) {
        Rig rig = b.rig();
        if (rig == null || rig.timed.isEmpty()) return;
        int now = b.ticksLived();

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

    /** Removes every display, light and hidden block. The ground scars fade out later on their own. */
    public static void cleanup(BlueProjectile b) {
        Rig rig = b.rig();
        if (rig == null) return;

        try {
            for (Lift lf : rig.lifts) restoreBlock(rig, lf.blockLoc);
            World w = rig.center != null && rig.center.getWorld() != null ? rig.center.getWorld() : b.caster().getWorld();
            if (w != null && !rig.lights.isEmpty()) TechniqueLighting.setLights(rig.lights, w, new HashMap<>());
        } catch (Throwable ignored) {
            // Restoration is cosmetic; never block the cleanup.
        }

        for (Entity e : new ArrayList<>(rig.entities)) {
            if (e != null && e.isValid()) e.remove();
        }
        rig.entities.clear();
        rig.core.clear();
        rig.halos.clear();
        rig.orbiters.clear();
        rig.sucks.clear();
        rig.streaks.clear();
        rig.lifts.clear();
        rig.timed.clear();

        List<Entity> scars = new ArrayList<>(rig.scars);
        rig.scars.clear();
        if (!scars.isEmpty()) {
            int delay = b.scarTicks();
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
        b.setRig(null);
    }
}
