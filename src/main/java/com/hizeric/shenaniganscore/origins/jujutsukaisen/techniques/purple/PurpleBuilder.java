package com.hizeric.shenaniganscore.origins.jujutsukaisen.techniques.purple;

import com.hizeric.shenaniganscore.core.display.TechniqueLighting;
import com.hizeric.shenaniganscore.ShenanigansCore;
import com.hizeric.shenaniganscore.core.display.DisplayUtils;
import com.hizeric.shenaniganscore.core.sound.SoundUtils;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.inventory.ItemStack;
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
import com.hizeric.shenaniganscore.core.display.DisplayRig;
import com.hizeric.shenaniganscore.core.display.TechniqueEraseOps;
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
import static com.hizeric.shenaniganscore.core.display.TechniqueDisplayOps.basis;
import static com.hizeric.shenaniganscore.core.display.TechniqueDisplayOps.discard;
import static com.hizeric.shenaniganscore.core.display.TechniqueDisplayOps.lerp;
import static com.hizeric.shenaniganscore.core.display.TechniqueDisplayOps.moveDisplay;
import static com.hizeric.shenaniganscore.core.display.TechniqueDisplayOps.randomRot;
import static com.hizeric.shenaniganscore.core.display.TechniqueDisplayOps.scaleOnly;
import static com.hizeric.shenaniganscore.core.display.TechniqueDisplayOps.spawn;
import static com.hizeric.shenaniganscore.core.display.BlockDisplayOps.line;
import static com.hizeric.shenaniganscore.core.util.MathUtil.smoothstep;
import static com.hizeric.shenaniganscore.core.display.TechniqueDisplayOps.spawnGlyph;
import static com.hizeric.shenaniganscore.core.util.BlockKey.pack;
import static com.hizeric.shenaniganscore.core.display.TechniqueDisplayOps.poseGlint;
import static com.hizeric.shenaniganscore.core.display.TechniqueDisplayOps.poseHalos;

/**
 * Every visual of Hollow Technique: Purple — built from the very same displays as Red and Blue.
 *
 * <p><b>The idea: nothing is rebuilt, everything is morphed.</b> Purple is made of Blue and Red, so this
 * builder spawns the Blue orb (black core, layered blue glass, a billboard halo, a glint) at the left hand
 * and the Red orb (white core, layered crimson glass, a crown of rays) at the right, exactly as
 * {@code /blue} and {@code /red} do. At the moment they collide, the <i>same entities</i> are changed in place:</p>
 *
 * <ul>
 *   <li>Blue's glass layers become purple and magenta glass (a display's block can be swapped live);</li>
 *   <li>Red's crimson layers become more shells around the core;</li>
 *   <li>Blue's billboard halo turns magenta and pink — a bloom around the imaginary mass;</li>
 *   <li>Red's crown of rays <i>stays red</i>, now radiating from the purple core;</li>
 *   <li>two new groups of cubes — red and blue — orbit the core in opposite directions in a tilted plane,
 *       a visible double helix of attraction and repulsion locked together;</li>
 *   <li>amethyst sprites (Blue's suction streams, recoloured) spiral into it.</li>
 * </ul>
 *
 * <p><b>Erasure.</b> The imaginary mass erases terrain: blocks inside its path (or inside the remote sphere)
 * are hidden on clients with fake air, a few of them are copied into displays that are pulled into the core
 * and shrink to nothing, and everything is restored a few seconds later. Nothing real is ever changed.</p>
 *
 * <p>Everything scales with {@code jujutsukaisen.techniques.purple.quality}.</p>
 */
public final class PurpleBuilder {

    private PurpleBuilder() {}

    private static final Particle.DustTransition PURPLE_EDGE =
            new Particle.DustTransition(Color.fromRGB(200, 110, 255), Color.fromRGB(60, 10, 110), 1.6f);
    private static final Particle.DustTransition HOT_EDGE =
            new Particle.DustTransition(Color.fromRGB(255, 235, 255), Color.fromRGB(190, 70, 255), 1.4f);
    private static final Particle.DustTransition RED_EDGE =
            new Particle.DustTransition(Color.fromRGB(255, 70, 50), Color.fromRGB(90, 0, 10), 1.5f);
    private static final Particle.DustTransition BLUE_EDGE =
            new Particle.DustTransition(Color.fromRGB(120, 190, 255), Color.fromRGB(20, 40, 160), 1.5f);
    private static final TextColor PURPLE_TEXT = TextColor.color(190, 90, 255);

    // ─────────────────────────────────────────────────────────────────────────
    // Data carried by the technique
    // ─────────────────────────────────────────────────────────────────────────

    /** One layer of an orb: rel = size relative to the orb radius, spin = angular speed. */
    private record Piece(BlockDisplay display, Quaternionf rot, float rel, float spin) {}

    /** One ray of Red's crown. */
    private record Ray(BlockDisplay display, Vector dir, double phase) {}

    /** One cube of the red/blue double helix. A negative speed orbits the other way. */
    private record Orbiter(BlockDisplay display, double r, double a0, double speed, double yoff,
                           float size, Quaternionf rot) {}

    /** One amethyst sprite spiralling into the core. */
    private record Suck(ItemDisplay display, double phase, double speed, double a0, double r0, double ytilt) {}

    /** One cube of the remote blast sphere: rel = side length relative to the sphere's radius. */
    private record Cube(BlockDisplay display, Quaternionf rot, float rel) {}

    /** A block that was just erased, copied so it can be pulled into the core. */
    private record Sample(Location loc, BlockData data) {}

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

        // Blue half (reused as the purple core and halo).
        final List<Piece> blueCore = new ArrayList<>();
        final List<BlockDisplay> halos = new ArrayList<>();
        BlockDisplay glint;
        // Red half (its shells become extra layers; its crown of rays stays red).
        final List<Piece> redCore = new ArrayList<>();
        final List<Ray> rays = new ArrayList<>();
        // Added at the fusion.
        final List<Orbiter> orbiters = new ArrayList<>();
        final List<Suck> sucks = new ArrayList<>();
        final List<Cube> cubes = new ArrayList<>();
        final List<Timed> timed = new ArrayList<>();

        // Erasure.
        final Set<Long> erased = new HashSet<>();
        final List<Location> erasedLocs = new ArrayList<>();
        final Set<UUID> viewers = new HashSet<>();
        final Map<Location, Integer> lights = new HashMap<>();

        float blueR = 0.4f;
        float redR = 0.5f;
        float purpleR = 0.7f;
        Location blueC;
        Location redC;
        Location purpleC;
        Location blueFrom;
        Location redFrom;
        Vector right = new Vector(1, 0, 0);
        Vector up = new Vector(0, 1, 0);
        boolean fused;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Small helpers
    // ─────────────────────────────────────────────────────────────────────────

    private static int scaled(PurpleProjectile p, int base) {
        return (int) Math.max(1, Math.round(base * p.quality()));
    }

    /** Spawns a fullbright block display (tiny) and tracks it for cleanup. */
    /** An amethyst-shard sprite that always faces the viewer. */
    private static ItemDisplay spawnSprite(Rig rig, Location at) {
        ItemDisplay d = DisplayUtils.spawnItem(at, new ItemStack(Material.AMETHYST_SHARD), VIEW, BRIGHT);
        d.setBillboard(Display.Billboard.CENTER);
        d.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.FIXED);
        d.setTransformation(scaleOnly(TINY, 0f));
        rig.entities.add(d);
        return d;
    }

    /** A glowing kanji that always faces the viewer. */
    /** Box of the given size (rotated by q) whose CENTER sits on {@code center}. */
    /** A thin glowing beam between two points. */
    private static void morph(BlockDisplay d, Material m) {
        if (d != null && d.isValid()) d.setBlock(m.createBlockData());
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Client-side light
    // ─────────────────────────────────────────────────────────────────────────

    private static void lightPoint(Rig rig, World w, Location a, int levelA, Location b, int levelB) {
        Map<Location, Integer> want = new HashMap<>();
        if (a != null && levelA > 0) {
            Location la = blockLoc(a);
            if (lightable(w, la)) want.put(la, levelA);
        }
        if (b != null && levelB > 0) {
            Location lb = blockLoc(b);
            if (lightable(w, lb)) want.merge(lb, levelB, Math::max);
        }
        TechniqueLighting.setLights(rig.lights, w, want);
    }

    private static void lightBurst(Rig rig, World w, Location centre, double R, int level) {
        Map<Location, Integer> want = new HashMap<>();
        if (level > 0) {
            double d = Math.min(R * 0.45, 8.0);
            double[][] offsets = {{0, 0, 0}, {d, 0, 0}, {-d, 0, 0}, {0, d, 0}, {0, -d, 0}, {0, 0, d}, {0, 0, -d}};
            for (double[] o : offsets) {
                Location l = blockLoc(centre.clone().add(o[0], o[1], o[2]));
                if (lightable(w, l)) want.put(l, level);
            }
        }
        TechniqueLighting.setLights(rig.lights, w, want);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Pose helpers shared by both halves and the fused orb
    // ─────────────────────────────────────────────────────────────────────────

    private static void poseCore(List<Piece> pieces, Location c, double tick, double radius, double spinMul, double pulse) {
        for (Piece pc : pieces) {
            Quaternionf q = new Quaternionf(pc.rot())
                    .rotateY((float) (tick * pc.spin() * spinMul))
                    .rotateX((float) (tick * pc.spin() * 0.6 * spinMul));
            float s = (float) Math.max(TINY, radius * pc.rel() * pulse);
            place(pc.display(), c, q, s, s, s, 1, 1);
        }
    }

    /** Four billboard discs (two sizes, each an octagon of two squares). */
    /** Red's crown of rays. fixedLen > 0 forces every ray to that length (the blast). */
    private static void poseRays(Rig rig, Location c, double tick, double radius, double scale, double fixedLen, float thickness) {
        for (Ray ray : rig.rays) {
            double len = fixedLen > 0.0
                    ? fixedLen
                    : radius * (1.3 + 1.1 * (0.5 + 0.5 * Math.sin(tick * 0.55 + ray.phase()))) * scale;
            if (len < 0.05) {
                place(ray.display(), c, new Quaternionf(), TINY, TINY, TINY, 1, 1);
                continue;
            }
            Location a = c.clone().add(ray.dir().clone().multiply(radius * 0.9));
            Location b = a.clone().add(ray.dir().clone().multiply(len));
            line(ray.display(), a, b, thickness);
        }
    }

    /** The red/blue double helix: two groups of cubes orbiting in opposite directions in a tilted plane. */
    private static void poseOrbiters(Rig rig, Location c, int t, double charge) {
        if (t % 2 != 0 || rig.orbiters.isEmpty()) return;
        double prec = t * 0.012;
        double tilt = 0.5;
        Vector n = new Vector(Math.sin(tilt) * Math.cos(prec), Math.cos(tilt), Math.sin(tilt) * Math.sin(prec)).normalize();
        Vector u = n.clone().crossProduct(new Vector(0, 0, 1));
        if (u.lengthSquared() < 1.0e-4) u = new Vector(1, 0, 0);
        u.normalize();
        Vector v = n.clone().crossProduct(u).normalize();
        for (Orbiter o : rig.orbiters) {
            double ang = o.a0() + (t + 2) * o.speed();
            double rr = o.r() * charge;
            Location pos = c.clone()
                    .add(u.clone().multiply(Math.cos(ang) * rr))
                    .add(v.clone().multiply(Math.sin(ang) * rr))
                    .add(n.clone().multiply(o.yoff() * charge));
            float size = (float) (o.size() * charge);
            place(o.display(), pos, o.rot(), size, size, size, 2, 2);
        }
    }

    /** Amethyst sprites spiralling into the core. */
    private static void poseSucks(Rig rig, Location c, int t, double charge) {
        for (Suck s : rig.sucks) {
            double su = (t * s.speed() * 0.02 + s.phase()) % 1.0;
            double rr = (s.r0() * Math.pow(1.0 - su, 1.25) + 0.3) * charge;
            double ang = s.a0() + su * 7.0 + t * 0.04;
            Location pos = c.clone().add(Math.cos(ang) * rr, s.ytilt() * rr * 0.5, Math.sin(ang) * rr);
            float size = (float) (0.4 * (1.0 - 0.8 * su) * charge);
            moveDisplay(s.display(), pos, size, (float) ang);
        }
    }

    /** Poses the whole fused orb around a centre. charge scales it; spinMul is how fast it churns. */
    private static void posePurple(Rig rig, Location c, int t, double charge, double spinMul, double rayScale) {
        double r = rig.purpleR * charge;
        double pulse = 1.0 + 0.06 * Math.sin(t * 0.5);
        poseCore(rig.blueCore, c, t, r, spinMul, pulse);
        poseCore(rig.redCore, c, t, r * 0.7, -spinMul * 0.8, pulse);
        double hp = 1.0 + 0.08 * Math.sin(t * 0.4);
        poseHalos(rig.halos, c, r * 5.5 * hp, r * 3.0 * hp);
        poseGlint(rig.glint, c, t, r, 1.6);
        poseRays(rig, c, t, r, rayScale, 0.0, (float) (0.05 + 0.03 * rig.purpleR));
        poseOrbiters(rig, c, t, charge);
        poseSucks(rig, c, t, charge);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // CONJURE — Blue at the left hand, Red at the right
    // ─────────────────────────────────────────────────────────────────────────

    /** Builds the Blue orb and the Red orb: the same displays the Blue and Red techniques produce. */
    public static void spawnHalves(PurpleProjectile p) {
        Location at = p.caster().getEyeLocation();
        ThreadLocalRandom r = ThreadLocalRandom.current();

        Rig rig = new Rig();
        rig.blueR = (float) (0.35 + 0.05 * p.power());
        rig.redR = (float) (0.45 + 0.10 * p.power());
        rig.purpleR = (float) (0.55 + 0.10 * p.power());
        rig.blueC = at.clone();
        rig.redC = at.clone();
        rig.purpleC = at.clone();

        // Blue: black core, layered blue glass, billboard halo, glint.
        rig.blueCore.add(new Piece(spawn(rig, at, Material.BLACK_CONCRETE, 6), randomRot(r), 0.6f, 0.0f));
        rig.blueCore.add(new Piece(spawn(rig, at, Material.BLUE_STAINED_GLASS, BRIGHT), randomRot(r), 1.0f, 0.30f));
        rig.blueCore.add(new Piece(spawn(rig, at, Material.LIGHT_BLUE_STAINED_GLASS, BRIGHT), randomRot(r), 1.5f, -0.22f));
        rig.blueCore.add(new Piece(spawn(rig, at, Material.BLUE_STAINED_GLASS, BRIGHT), randomRot(r), 2.1f, 0.14f));
        rig.glint = spawn(rig, at, Material.WHITE_CONCRETE, BRIGHT);
        Material[] halo = {Material.LIGHT_BLUE_STAINED_GLASS, Material.LIGHT_BLUE_STAINED_GLASS,
                Material.WHITE_STAINED_GLASS, Material.WHITE_STAINED_GLASS};
        for (Material m : halo) {
            BlockDisplay d = spawn(rig, at, m, BRIGHT);
            d.setBillboard(Display.Billboard.CENTER);
            rig.halos.add(d);
        }

        // Red: white core, layered crimson glass, crown of rays.
        rig.redCore.add(new Piece(spawn(rig, at, Material.WHITE_CONCRETE, BRIGHT), randomRot(r), 0.9f, 0.0f));
        rig.redCore.add(new Piece(spawn(rig, at, Material.RED_STAINED_GLASS, BRIGHT), randomRot(r), 1.55f, 0.22f));
        rig.redCore.add(new Piece(spawn(rig, at, Material.RED_STAINED_GLASS, BRIGHT), randomRot(r), 1.55f, -0.17f));
        rig.redCore.add(new Piece(spawn(rig, at, Material.RED_STAINED_GLASS, BRIGHT), randomRot(r), 2.2f, 0.12f));
        rig.redCore.add(new Piece(spawn(rig, at, Material.RED_STAINED_GLASS, BRIGHT), randomRot(r), 2.2f, -0.09f));
        int rays = Math.max(6, Math.min(16, scaled(p, 10)));
        double golden = Math.PI * (3.0 - Math.sqrt(5.0));
        for (int i = 0; i < rays; i++) {
            double y = 1.0 - 2.0 * (i + 0.5) / rays;
            double h = Math.sqrt(Math.max(0.0, 1.0 - y * y));
            double th = golden * i;
            Vector dir = new Vector(Math.cos(th) * h, y, Math.sin(th) * h);
            rig.rays.add(new Ray(spawn(rig, at, Material.RED_CONCRETE, BRIGHT), dir, r.nextDouble() * Math.PI * 2.0));
        }
        p.setRig(rig);
    }

    /** Called every conjure tick. progress runs 0..1. */
    public static void tickConjure(PurpleProjectile p, double progress) {
        Rig rig = p.rig();
        if (rig == null) return;

        Player c = p.caster();
        World w = c.getWorld();
        ThreadLocalRandom r = ThreadLocalRandom.current();
        int tick = p.stateTicks();
        double e = smoothstep(progress);

        Location eye = c.getEyeLocation();
        Vector fwd = eye.getDirection().normalize();
        Vector right = fwd.clone().crossProduct(new Vector(0, 1, 0));
        if (right.lengthSquared() < 1.0e-4) right = new Vector(1, 0, 0);
        right.normalize();
        Vector up = right.clone().crossProduct(fwd).normalize();
        rig.right = right.clone();
        rig.up = up.clone();

        Location bh = eye.clone().add(fwd.clone().multiply(1.7)).subtract(right.clone().multiply(0.8)).subtract(up.clone().multiply(0.1));
        Location rh = eye.clone().add(fwd.clone().multiply(1.7)).add(right.clone().multiply(0.8)).subtract(up.clone().multiply(0.1));
        rig.blueC = bh.clone();
        rig.redC = rh.clone();

        poseBlue(rig, bh, tick, 0.15 + 0.85 * e, e);
        poseRed(rig, rh, tick, 0.15 + 0.85 * e, e);

        double blueRad = rig.blueR * (0.15 + 0.85 * e);
        double redRad = rig.redR * (0.15 + 0.85 * e);

        // Blue: glyphs and sparks lean in.
        for (int i = 0; i < 2; i++) {
            Vector v = randomUnit(r).multiply(1.6 + r.nextDouble() * 1.2);
            w.spawnParticle(Particle.ENCHANT, bh, 0, v.getX(), v.getY(), v.getZ(), 1.0);
        }
        w.spawnParticle(Particle.ELECTRIC_SPARK, bh, 2, blueRad * 1.5, blueRad * 1.5, blueRad * 1.5, 0.1);
        // Red: sparks are thrown off.
        for (int i = 0; i < 3; i++) {
            Vector v = randomUnit(r);
            w.spawnParticle(Particle.END_ROD, rh, 0, v.getX(), v.getY(), v.getZ(), 0.1 + 0.1 * e);
        }
        w.spawnParticle(Particle.ELECTRIC_SPARK, rh, 2, redRad * 1.5, redRad * 1.5, redRad * 1.5, 0.1);

        // A ring of each colour around its hand.
        for (int i = 0; i < 10; i++) {
            double a = Math.PI * 2.0 * i / 10.0 + progress * 7.0;
            double cr = 0.9 * (1.0 - 0.5 * e);
            Vector ring = right.clone().multiply(Math.cos(a) * cr).add(up.clone().multiply(Math.sin(a) * cr));
            w.spawnParticle(Particle.DUST_COLOR_TRANSITION, bh.clone().add(ring), 1, 0, 0, 0, 0, BLUE_EDGE);
            w.spawnParticle(Particle.DUST_COLOR_TRANSITION, rh.clone().add(ring.multiply(-1.0)), 1, 0, 0, 0, 0, RED_EDGE);
        }

        lightPoint(rig, w, lerp(bh, rh, 0.5), (int) (6 + 8 * e), null, 0);

        if (tick == 1) {
            SoundUtils.play(bh, Sound.ENTITY_WARDEN_SONIC_CHARGE, 1.0f, 1.3f);
            SoundUtils.play(rh, Sound.BLOCK_RESPAWN_ANCHOR_CHARGE, 1.3f, 0.6f);
        } else if (tick % 3 == 0) {
            SoundUtils.play(bh, Sound.BLOCK_AMETHYST_BLOCK_RESONATE, 1.0f, (float) (0.6 + 0.9 * progress));
        }
        if (tick % 2 == 0) {
            int filled = (int) Math.round(progress * 10.0);
            c.sendActionBar(MiniMessage.miniMessage().deserialize(
                    "<blue>■ Lapse <dark_gray>+ <red>■ Reversal <dark_gray>| <white>"
                            + "▮".repeat(filled) + "<dark_gray>" + "▯".repeat(10 - filled)));
        }
    }

    /** Poses the Blue half: black core, glass layers, halo, glint. */
    private static void poseBlue(Rig rig, Location c, double tick, double charge, double e) {
        double rad = rig.blueR * charge;
        poseCore(rig.blueCore, c, tick, rad, 1.0 + 2.0 * e + 3.0 * Math.max(0.0, charge - 1.0), 1.0 + 0.08 * Math.sin(tick * 0.9));
        double pulse = 1.0 + 0.08 * Math.sin(tick * 0.5);
        poseHalos(rig.halos, c, rad * 5.0 * pulse * Math.min(1.0, e + 0.2), rad * 2.8 * pulse * Math.min(1.0, e + 0.2));
        poseGlint(rig.glint, c, tick, rad, 0.5 + 1.2 * e);
    }

    /** Poses the Red half: white core, crimson glass, crown of rays. */
    private static void poseRed(Rig rig, Location c, double tick, double charge, double e) {
        double rad = rig.redR * charge;
        poseCore(rig.redCore, c, tick, rad, 1.0, 1.0);
        poseRays(rig, c, tick, rad, 0.4 + 0.6 * e, 0.0, (float) (0.05 + 0.03 * rig.redR));
    }

    // ─────────────────────────────────────────────────────────────────────────
    // SEND (remote) — Blue and Red are thrown apart, curving toward the target
    // ─────────────────────────────────────────────────────────────────────────

    public static void beginSend(PurpleProjectile p) {
        Rig rig = p.rig();
        if (rig == null) return;
        rig.blueFrom = rig.blueC.clone();
        rig.redFrom = rig.redC.clone();
        SoundUtils.play(rig.blueC, Sound.ENTITY_ENDER_EYE_LAUNCH, 1.2f, 0.8f);
        SoundUtils.play(rig.redC, Sound.ENTITY_ENDER_EYE_LAUNCH, 1.2f, 1.1f);
        SoundUtils.play(rig.blueC, Sound.BLOCK_BEACON_POWER_SELECT, 1.0f, 1.6f);
    }

    public static void tickSend(PurpleProjectile p, double progress) {
        Rig rig = p.rig();
        Location target = p.target();
        if (rig == null || target == null || rig.blueFrom == null) return;
        World w = target.getWorld();
        if (w == null) return;

        double e = smoothstep(progress);
        double dist = rig.blueFrom.distance(target);
        double arc = Math.min(7.0, 0.2 * dist);
        double bump = Math.sin(Math.PI * e);

        // Blue swings out to the left, Red to the right; both curve in and arrive together.
        Location bp = lerp(rig.blueFrom, target, e)
                .add(rig.right.clone().multiply(-arc * bump)).add(rig.up.clone().multiply(arc * 0.5 * bump));
        Location rp = lerp(rig.redFrom, target, e)
                .add(rig.right.clone().multiply(arc * bump)).add(rig.up.clone().multiply(arc * 0.5 * bump));
        rig.blueC = bp;
        rig.redC = rp;

        int tick = p.ticksLived();
        poseBlue(rig, bp, tick, 1.0, 1.0);
        poseRed(rig, rp, tick, 1.0, 1.0);

        w.spawnParticle(Particle.DUST_COLOR_TRANSITION, bp, 3, 0.15, 0.15, 0.15, 0, BLUE_EDGE);
        w.spawnParticle(Particle.DUST_COLOR_TRANSITION, rp, 3, 0.15, 0.15, 0.15, 0, RED_EDGE);
        w.spawnParticle(Particle.ELECTRIC_SPARK, bp, 2, 0.3, 0.3, 0.3, 0.1);
        w.spawnParticle(Particle.ELECTRIC_SPARK, rp, 2, 0.3, 0.3, 0.3, 0.1);
        lightPoint(rig, w, bp, 12, rp, 12);
        if (p.stateTicks() % 4 == 1) SoundUtils.play(bp, Sound.BLOCK_PORTAL_AMBIENT, 0.8f, 1.2f);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // FUSE — the two spiral into each other and collide
    // ─────────────────────────────────────────────────────────────────────────

    public static void beginFuse(PurpleProjectile p) {
        Rig rig = p.rig();
        if (rig == null) return;
        Location f = p.fusePoint();
        if (p.isRemote()) {
            // They arrive together at the target: start the spiral a little apart.
            rig.blueFrom = f.clone().subtract(rig.right.clone().multiply(0.9));
            rig.redFrom = f.clone().add(rig.right.clone().multiply(0.9));
        } else {
            rig.blueFrom = rig.blueC.clone();
            rig.redFrom = rig.redC.clone();
        }
        rig.purpleC = f.clone();
    }

    public static void tickFuse(PurpleProjectile p, double progress) {
        Rig rig = p.rig();
        Location f = p.fusePoint();
        if (rig == null || f == null || rig.blueFrom == null) return;
        World w = f.getWorld();
        if (w == null) return;
        ThreadLocalRandom r = ThreadLocalRandom.current();
        int tick = p.stateTicks();
        double e = smoothstep(progress);

        // The two orbit each other tighter and tighter while closing in.
        double swirl = (1.0 - e) * 0.55;
        double th = e * Math.PI * 5.0;
        Vector u = rig.right;
        Vector v = rig.up;
        Location bp = lerp(rig.blueFrom, f, e).add(u.clone().multiply(Math.cos(th) * swirl)).add(v.clone().multiply(Math.sin(th) * swirl));
        Location rp = lerp(rig.redFrom, f, e).subtract(u.clone().multiply(Math.cos(th) * swirl)).subtract(v.clone().multiply(Math.sin(th) * swirl));
        rig.blueC = bp;
        rig.redC = rp;

        poseBlue(rig, bp, p.ticksLived(), 1.0 + 0.2 * e, 1.0);
        poseRed(rig, rp, p.ticksLived(), 1.0 + 0.2 * e, 1.0);

        // Trails of both colours, and purple where they begin to overlap.
        w.spawnParticle(Particle.DUST_COLOR_TRANSITION, bp, 4, 0.12, 0.12, 0.12, 0, BLUE_EDGE);
        w.spawnParticle(Particle.DUST_COLOR_TRANSITION, rp, 4, 0.12, 0.12, 0.12, 0, RED_EDGE);
        Location mid = lerp(bp, rp, 0.5);
        w.spawnParticle(Particle.ELECTRIC_SPARK, mid, 3, 0.25, 0.25, 0.25, 0.2);
        if (e > 0.6) {
            w.spawnParticle(Particle.DUST_COLOR_TRANSITION, mid, 5, 0.3, 0.3, 0.3, 0, PURPLE_EDGE);
            w.spawnParticle(Particle.REVERSE_PORTAL, mid, 3, 0.3, 0.3, 0.3, 0.1);
        }
        if (e > 0.92 && tick % 2 == 0) w.spawnParticle(Particle.FLASH, mid, 1, 0, 0, 0, 0, Color.WHITE);

        lightPoint(rig, w, f, (int) (8 + 6 * e), null, 0);

        // A heartbeat that quickens, and a rising resonance.
        int interval = (int) Math.max(2, Math.round(8 - 6 * progress));
        if (tick == 1 || tick % interval == 0) {
            SoundUtils.play(mid, Sound.ENTITY_WARDEN_HEARTBEAT, 1.4f, (float) (0.7 + 0.7 * progress));
        }
        if (tick % 2 == 0) SoundUtils.play(mid, Sound.BLOCK_AMETHYST_BLOCK_RESONATE, 1.2f, (float) (0.5 + 1.4 * progress));
    }

    /** The collision: the same displays are morphed to purple and the imaginary mass is born. */
    public static void fusion(PurpleProjectile p) {
        Rig rig = p.rig();
        Location f = p.fusePoint();
        if (rig == null || f == null) return;
        World w = f.getWorld();
        if (w == null) return;
        ThreadLocalRandom r = ThreadLocalRandom.current();
        double R = rig.purpleR;

        rig.purpleC = f.clone();
        rig.fused = true;

        // Blue's glass layers become purple and magenta; its halo turns magenta and pink.
        rig.blueCore.get(1).display().setBlock(Material.PURPLE_STAINED_GLASS.createBlockData());
        rig.blueCore.get(2).display().setBlock(Material.MAGENTA_STAINED_GLASS.createBlockData());
        rig.blueCore.get(3).display().setBlock(Material.PURPLE_STAINED_GLASS.createBlockData());
        morph(rig.halos.get(0), Material.MAGENTA_STAINED_GLASS);
        morph(rig.halos.get(1), Material.MAGENTA_STAINED_GLASS);
        morph(rig.halos.get(2), Material.PINK_STAINED_GLASS);
        morph(rig.halos.get(3), Material.PINK_STAINED_GLASS);

        // Red's white heart is consumed; its crimson layers become more shells. Its rays stay red.
        Piece heart = rig.redCore.remove(0);
        shrink(heart.display(), 3);
        rig.timed.add(new Timed(heart.display(), p.ticksLived(), 4, null));
        Material[] shell = {Material.MAGENTA_STAINED_GLASS, Material.PURPLE_STAINED_GLASS,
                Material.PURPLE_STAINED_GLASS, Material.MAGENTA_STAINED_GLASS};
        for (int i = 0; i < rig.redCore.size() && i < shell.length; i++) {
            morph(rig.redCore.get(i).display(), shell[i]);
        }

        // The double helix: red cubes one way, blue cubes the other.
        int orbiters = Math.max(12, Math.min(40, scaled(p, 24)));
        for (int i = 0; i < orbiters; i++) {
            boolean red = i % 2 == 0;
            double orbitR = R * (1.6 + 1.8 * r.nextDouble());
            double speed = (red ? 1.0 : -1.0) * 0.9 / Math.pow(Math.max(0.6, orbitR), 0.8);
            Material m = red ? Material.RED_CONCRETE : Material.LIGHT_BLUE_CONCRETE;
            rig.orbiters.add(new Orbiter(spawn(rig, f, m, BRIGHT), orbitR, r.nextDouble() * Math.PI * 2.0,
                    speed, (r.nextDouble() - 0.5) * 0.5, 0.12f + r.nextFloat() * 0.18f, randomRot(r)));
        }
        // Amethyst sprites fall into the core.
        int sucks = Math.max(6, Math.min(24, scaled(p, 14)));
        for (int i = 0; i < sucks; i++) {
            rig.sucks.add(new Suck(spawnSprite(rig, f), r.nextDouble(), 0.5 + r.nextDouble() * 0.6,
                    r.nextDouble() * Math.PI * 2.0, R * (2.5 + 2.0 * r.nextDouble()), r.nextDouble() - 0.5));
        }

        // The 茈 seal flashes above the new core.
        final Location sealAt = f.clone().add(0, R * 3.0 + 1.5, 0);
        final TextDisplay seal = spawnGlyph(rig, sealAt, "茈", PURPLE_TEXT, TINY);
        rig.timed.add(new Timed(seal, p.ticksLived(), 14, age -> {
            if (age == 1) {
                moveDisplay(seal, sealAt, 14f, 0f);
            } else if (age >= 4) {
                seal.setTextOpacity((byte) Math.max(40, 255 - (age - 3) * 22));
            }
        }));

        // The collision itself: flash, sonic booms, a shockwave in the plane they met in, and a tear in space.
        Vector[] bs = basis(p.isRemote() ? new Vector(0, 1, 0) : p.caster().getEyeLocation().getDirection());
        w.spawnParticle(Particle.FLASH, f, 1, 0, 0, 0, 0, Color.WHITE);
        w.spawnParticle(Particle.SONIC_BOOM, f, 1, 0, 0, 0, 0);
        w.spawnParticle(Particle.EXPLOSION_EMITTER, f, 1, 0, 0, 0, 0);
        for (int i = 0; i < 40; i++) {
            double a = Math.PI * 2.0 * i / 40.0;
            Vector out = bs[0].clone().multiply(Math.cos(a)).add(bs[1].clone().multiply(Math.sin(a)));
            w.spawnParticle(Particle.CLOUD, f, 0, out.getX(), out.getY(), out.getZ(), 0.5);
            if (i % 2 == 0) w.spawnParticle(Particle.DUST_COLOR_TRANSITION, f.clone().add(out.clone().multiply(0.8)), 1, 0, 0, 0, 0, PURPLE_EDGE);
        }
        for (int i = 0; i < 30; i++) {
            Vector v = randomUnit(r);
            w.spawnParticle(Particle.REVERSE_PORTAL, f, 0, v.getX(), v.getY(), v.getZ(), 0.6);
        }
        SoundUtils.play(f, Sound.ENTITY_WARDEN_SONIC_BOOM, 2.0f, 0.7f);
        SoundUtils.play(f, Sound.BLOCK_END_PORTAL_SPAWN, 0.8f, 1.5f);
        SoundUtils.play(f, Sound.ENTITY_LIGHTNING_BOLT_THUNDER, 1.2f, 1.8f);

        posePurple(rig, f, p.ticksLived(), 0.3, 3.0, 0.4);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // FIRE (standard) — the imaginary mass flies and erases
    // ─────────────────────────────────────────────────────────────────────────

    public static void beginFire(PurpleProjectile p) {
        Rig rig = p.rig();
        Location f = p.fusePoint();
        if (rig == null || f == null) return;
        World w = f.getWorld();
        if (w == null) return;

        Vector[] bs = basis(p.direction());
        for (int i = 0; i < 24; i++) {
            double a = Math.PI * 2.0 * i / 24.0;
            Vector out = bs[0].clone().multiply(Math.cos(a)).add(bs[1].clone().multiply(Math.sin(a)));
            w.spawnParticle(Particle.END_ROD, f, 0, out.getX(), out.getY(), out.getZ(), 0.4);
        }
        SoundUtils.play(f, Sound.ENTITY_WARDEN_SONIC_BOOM, 2.5f, 0.6f);
        SoundUtils.play(f, Sound.BLOCK_BEACON_POWER_SELECT, 1.2f, 0.5f);
    }

    /** A fading ribbon segment left behind the mass: the tunnel it has torn. */
    private static void ribbon(Rig rig, Location a, Location b, Material m, float thickness, int now) {
        final BlockDisplay d = spawn(rig, a, m, BRIGHT);
        line(d, a, b, thickness);
        rig.timed.add(new Timed(d, now, 12, age -> {
            if (age == 2) shrink(d, 9);
        }));
    }

    public static void tickFire(PurpleProjectile p, Location prev, Location cur, double speed) {
        Rig rig = p.rig();
        World w = cur.getWorld();
        if (rig == null || w == null) return;
        ThreadLocalRandom r = ThreadLocalRandom.current();
        int t = p.stateTicks();
        Vector dir = p.direction();
        double tube = p.tubeRadius();

        rig.purpleC = cur.clone();
        double charge = 0.6 + 0.4 * smoothstep(Math.min(1.0, t / 6.0));
        posePurple(rig, cur, p.ticksLived(), charge, 2.5, 0.6);

        // The tunnel: a thick fading purple ribbon with a black heart.
        if (prev.distanceSquared(cur) > 0.01) {
            int now = p.ticksLived();
            ribbon(rig, prev, cur, Material.PURPLE_STAINED_GLASS, (float) (tube * 1.7), now);
            if (p.quality() >= 0.7) ribbon(rig, prev, cur, Material.BLACK_CONCRETE, (float) (tube * 0.5), now);
        }

        // Rings along the tube: one set pulled in (Blue), one pushed out (Red), alternating.
        if (t % 2 == 0) {
            Vector[] bs = basis(dir);
            boolean inward = (t / 2) % 2 == 0;
            Location ringAt = cur.clone().subtract(dir.clone().multiply(1.0));
            for (int i = 0; i < 20; i++) {
                double a = Math.PI * 2.0 * i / 20.0;
                Vector out = bs[0].clone().multiply(Math.cos(a)).add(bs[1].clone().multiply(Math.sin(a)));
                Location p0 = ringAt.clone().add(out.clone().multiply(tube));
                if (inward) {
                    w.spawnParticle(Particle.END_ROD, p0, 0, -out.getX(), -out.getY(), -out.getZ(), 0.3);
                } else {
                    w.spawnParticle(Particle.CLOUD, ringAt, 0, out.getX(), out.getY(), out.getZ(), 0.3);
                }
                if (i % 2 == 0) w.spawnParticle(Particle.DUST_COLOR_TRANSITION, p0, 1, 0, 0, 0, 0, inward ? BLUE_EDGE : RED_EDGE);
            }
        }
        w.spawnParticle(Particle.ELECTRIC_SPARK, cur, 4, 0.8, 0.8, 0.8, 0.2);
        w.spawnParticle(Particle.REVERSE_PORTAL, cur, 6, tube * 0.5, tube * 0.5, tube * 0.5, 0.2);
        w.spawnParticle(Particle.DUST_COLOR_TRANSITION, cur, 6, tube * 0.5, tube * 0.5, tube * 0.5, 0, PURPLE_EDGE);

        // Erase terrain along the path.
        if (p.eraseVisual()) eraseAlong(p, rig, w, prev, cur, tube);

        lightPoint(rig, w, cur, 15, cur.clone().subtract(dir.clone().multiply(5.0)), 12);

        if (t % 4 == 1) SoundUtils.play(cur, Sound.BLOCK_PORTAL_TRAVEL, 0.5f, 0.4f);
        if (t % 5 == 2) SoundUtils.play(cur, Sound.BLOCK_BEACON_AMBIENT, 1.5f, 0.5f);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // BLAST (remote) — a sphere of erasure
    // ─────────────────────────────────────────────────────────────────────────

    /** The point detonates: layered purple cubes (the blast sphere), and the biggest sound in the plugin. */
    public static void beginBlast(PurpleProjectile p) {
        Rig rig = p.rig();
        Location f = p.fusePoint();
        if (rig == null || f == null) return;
        World w = f.getWorld();
        if (w == null) return;
        ThreadLocalRandom r = ThreadLocalRandom.current();

        Material[] mats = {Material.BLACK_CONCRETE, Material.PURPLE_STAINED_GLASS, Material.MAGENTA_STAINED_GLASS};
        float[] rel = {1.0f, 1.35f, 1.7f};
        for (int layer = 0; layer < 3; layer++) {
            for (int k = 0; k < 2; k++) {
                rig.cubes.add(new Cube(spawn(rig, f, mats[layer], layer == 0 ? 6 : BRIGHT), randomRot(r), rel[layer]));
            }
        }

        w.spawnParticle(Particle.FLASH, f, 1, 0, 0, 0, 0, Color.WHITE);
        w.spawnParticle(Particle.SONIC_BOOM, f, 1, 0, 0, 0, 0);
        w.spawnParticle(Particle.EXPLOSION_EMITTER, f, 2, 0, 0, 0, 0);
        SoundUtils.play(f, Sound.ENTITY_GENERIC_EXPLODE, 4.0f, 0.5f);
        SoundUtils.play(f, Sound.ENTITY_WARDEN_SONIC_BOOM, 3.0f, 0.5f);
        SoundUtils.play(f, Sound.ENTITY_LIGHTNING_BOLT_THUNDER, 2.5f, 0.6f);
        SoundUtils.play(f, Sound.ENTITY_ENDER_DRAGON_GROWL, 2.0f, 0.5f);
    }

    /** One tick of the expanding sphere. radius is the current radius of erasure. */
    public static void tickBlast(PurpleProjectile p, int t, double radius) {
        Rig rig = p.rig();
        Location f = p.fusePoint();
        if (rig == null || f == null) return;
        World w = f.getWorld();
        if (w == null) return;
        ThreadLocalRandom r = ThreadLocalRandom.current();
        double R = p.nukeRadius();

        rig.purpleC = f.clone();

        // The orb churns hard at the centre; Red's rays lance out to the edge of the sphere.
        posePurple(rig, f, p.ticksLived(), 1.0, 5.0, 0.0);
        poseRays(rig, f, p.ticksLived(), rig.purpleR, 1.0, Math.max(0.1, radius * 1.15), (float) (0.12 + 0.02 * p.power()));

        // The blast sphere: layered cubes that grow with the radius.
        for (Cube c : rig.cubes) {
            float s = (float) Math.min(40.0, Math.max(TINY, radius * c.rel() * 1.15));
            place(c.display(), f, c.rot(), s, s, s, 1, 0);
        }

        // Erase the sphere (client-side).
        if (p.eraseVisual()) eraseBlastSphere(p, rig, w, f, radius);

        // Shock shell and ground ring.
        if (t <= 18) {
            int n = (int) Math.max(30, Math.min(140, radius * 8.0 * p.quality()));
            double golden = Math.PI * (3.0 - Math.sqrt(5.0));
            double spin = r.nextDouble() * Math.PI * 2.0;
            for (int i = 0; i < n; i++) {
                double y = 1.0 - 2.0 * (i + 0.5) / n;
                double h = Math.sqrt(Math.max(0.0, 1.0 - y * y));
                double th = golden * i + spin;
                Vector v = new Vector(Math.cos(th) * h, y, Math.sin(th) * h);
                Location sp = f.clone().add(v.clone().multiply(radius));
                if (i % 3 == 0) {
                    w.spawnParticle(Particle.END_ROD, sp, 0, v.getX(), v.getY(), v.getZ(), 0.05);
                } else {
                    w.spawnParticle(Particle.DUST_COLOR_TRANSITION, sp, 1, 0, 0, 0, 0, i % 2 == 0 ? PURPLE_EDGE : HOT_EDGE);
                }
            }
            if (t % 2 == 0) {
                int pts = (int) Math.max(24, Math.min(90, radius * 6.0));
                for (int i = 0; i < pts; i++) {
                    double a = Math.PI * 2.0 * i / pts;
                    double cx = Math.cos(a), sz = Math.sin(a);
                    w.spawnParticle(Particle.CLOUD, f.getX() + cx * radius, f.getY(), f.getZ() + sz * radius,
                            0, cx, 0.05, sz, 0.2);
                }
            }
        }

        lightBurst(rig, w, f, R, 15);
        if (t % 4 == 1) SoundUtils.play(f, Sound.BLOCK_PORTAL_TRAVEL, 1.0f, 0.4f);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // COLLAPSE
    // ─────────────────────────────────────────────────────────────────────────

    public static void beginCollapse(PurpleProjectile p) {
        Rig rig = p.rig();
        if (rig == null || rig.purpleC == null) return;
        SoundUtils.play(rig.purpleC, Sound.ENTITY_WARDEN_SONIC_CHARGE, 1.6f, 0.6f);
        SoundUtils.play(rig.purpleC, Sound.BLOCK_BEACON_DEACTIVATE, 1.4f, 0.6f);
    }

    /** The imaginary mass folds in on itself. ct is 1 on the first collapse tick. */
    public static void tickCollapse(PurpleProjectile p, int ct) {
        Rig rig = p.rig();
        if (rig == null || rig.purpleC == null) return;
        World w = rig.purpleC.getWorld();
        if (w == null) return;
        Location c = rig.purpleC;

        double e = smoothstep(Math.min(1.0, ct / 8.0));
        if (ct < 9 && !rig.blueCore.isEmpty()) {
            posePurple(rig, c, p.ticksLived(), Math.max(0.02, 1.0 - e), 6.0 + 6.0 * e, 0.6 * (1.0 - e));
            for (Cube cube : rig.cubes) {
                float s = (float) Math.max(TINY, p.isRemote() ? p.nukeRadius() * cube.rel() * 1.15 * (1.0 - e) : TINY);
                place(cube.display(), c, cube.rot(), Math.min(40f, s), Math.min(40f, s), Math.min(40f, s), 1, 0);
            }
        }

        if (ct == 8) {
            discardAll(rig);
            w.spawnParticle(Particle.FLASH, c, 1, 0, 0, 0, 0, Color.WHITE);
            w.spawnParticle(Particle.EXPLOSION_EMITTER, c, 1, 0, 0, 0, 0);
            w.spawnParticle(Particle.SONIC_BOOM, c, 1, 0, 0, 0, 0);
            SoundUtils.play(c, Sound.ENTITY_GENERIC_EXPLODE, 2.5f, 0.6f);
            SoundUtils.play(c, Sound.ENTITY_WARDEN_SONIC_BOOM, 2.0f, 0.5f);
        }

        if (ct >= 8) lightBurst(rig, w, c, 6.0, Math.max(0, 15 - (ct - 8) * 3));
    }

    private static void discardAll(Rig rig) {
        for (Piece pc : rig.blueCore) discard(rig, pc.display());
        for (Piece pc : rig.redCore) discard(rig, pc.display());
        for (BlockDisplay d : rig.halos) discard(rig, d);
        for (Ray ray : rig.rays) discard(rig, ray.display());
        for (Orbiter o : rig.orbiters) discard(rig, o.display());
        for (Suck s : rig.sucks) discard(rig, s.display());
        for (Cube c : rig.cubes) discard(rig, c.display());
        discard(rig, rig.glint);
        rig.blueCore.clear();
        rig.redCore.clear();
        rig.halos.clear();
        rig.rays.clear();
        rig.orbiters.clear();
        rig.sucks.clear();
        rig.cubes.clear();
        rig.glint = null;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Erasure (client-side only)
    // ─────────────────────────────────────────────────────────────────────────
    /** Erases a sphere around the segment prev→cur (one sample about every block). */
    private static void eraseAlong(PurpleProjectile p, Rig rig, World w, Location prev, Location cur, double radius) {
        List<Location> newly = new ArrayList<>();
        List<Sample> samples = new ArrayList<>();
        Vector seg = cur.toVector().subtract(prev.toVector());
        int steps = Math.max(1, (int) Math.ceil(seg.length()));
        for (int i = 1; i <= steps; i++) {
            Location at = prev.clone().add(seg.clone().multiply(i / (double) steps));
            eraseSphere(p, rig, w, at, radius, newly, samples);
        }
        TechniqueEraseOps.sendErase(w, cur, newly, rig.viewers);
        eat(p, rig, w, cur, samples);
    }

    /** Erases the whole sphere of the remote blast (only cells not already erased are processed). */
    private static void eraseBlastSphere(PurpleProjectile p, Rig rig, World w, Location centre, double radius) {
        List<Location> newly = new ArrayList<>();
        List<Sample> samples = new ArrayList<>();
        eraseSphere(p, rig, w, centre, radius, newly, samples);
        TechniqueEraseOps.sendErase(w, centre, newly, rig.viewers);
        eat(p, rig, w, centre, samples);
    }

    private static void eraseSphere(PurpleProjectile p, Rig rig, World w, Location c, double r,
                                    List<Location> out, List<Sample> samples) {
        int cap = p.maxErase();
        int ir = (int) Math.ceil(r);
        int cx = c.getBlockX();
        int cy = c.getBlockY();
        int cz = c.getBlockZ();
        double r2 = r * r;
        int minY = w.getMinHeight();
        int maxY = w.getMaxHeight();
        ThreadLocalRandom rng = ThreadLocalRandom.current();

        for (int dx = -ir; dx <= ir; dx++) {
            for (int dz = -ir; dz <= ir; dz++) {
                for (int dy = -ir; dy <= ir; dy++) {
                    if (dx * dx + dy * dy + dz * dz > r2) continue;
                    int x = cx + dx;
                    int y = cy + dy;
                    int z = cz + dz;
                    if (y < minY || y >= maxY) continue;
                    long key = pack(x, y, z);
                    if (rig.erased.contains(key)) continue;
                    if (!w.isChunkLoaded(x >> 4, z >> 4)) continue;

                    rig.erased.add(key);
                    Block block = w.getBlockAt(x, y, z);
                    if (block.getType().isAir()) continue;
                    if (rig.erasedLocs.size() >= cap) return;

                    Location loc = new Location(w, x, y, z);
                    rig.erasedLocs.add(loc);
                    out.add(loc);
                    if (samples.size() < 4 && block.getType().isSolid() && rng.nextInt(6) == 0) {
                        samples.add(new Sample(loc, block.getBlockData()));
                    }
                }
            }
        }
    }

    /** Sends fake air for the newly erased blocks to everyone nearby. */
    /** A few erased blocks are copied into displays that are pulled into the core and shrink to nothing. */
    private static void eat(PurpleProjectile p, Rig rig, World w, Location core, List<Sample> samples) {
        if (samples.isEmpty() || rig.timed.size() > 70) return;
        int now = p.ticksLived();
        for (Sample s : samples) {
            final Location start = s.loc().clone().add(0.5, 0.5, 0.5);
            final BlockData data = s.data();
            final Location to = core.clone();
            final BlockDisplay d = w.spawn(start, BlockDisplay.class, t2 -> {
                t2.setBlock(data);
                t2.setPersistent(false);
                t2.setViewRange(VIEW);
                t2.setInterpolationDuration(0);
                t2.setTeleportDuration(0);
            });
            rig.entities.add(d);
            place(d, start, new Quaternionf(), 1f, 1f, 1f, 0, 0);
            rig.timed.add(new Timed(d, now, 9, age -> {
                if (age == 1) place(d, to, new Quaternionf().rotateXYZ(2f, 3f, 1f), TINY, TINY, TINY, 6, 6);
            }));
            w.spawnParticle(Particle.BLOCK, start, 6, 0.3, 0.3, 0.3, 0.05, data);
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Hits
    // ─────────────────────────────────────────────────────────────────────────

    /** A victim is erased: a flash, a tear in space, a billboard bloom that shrinks away. */
    public static void onHit(PurpleProjectile p, LivingEntity victim) {
        Rig rig = p.rig();
        if (rig == null) return;
        World w = victim.getWorld();
        Location at = victim.getLocation().add(0, victim.getHeight() * 0.5, 0);

        w.spawnParticle(Particle.FLASH, at, 1, 0, 0, 0, 0, Color.WHITE);
        w.spawnParticle(Particle.REVERSE_PORTAL, at, 20, 0.4, 0.5, 0.4, 0.3);
        w.spawnParticle(Particle.ELECTRIC_SPARK, at, 12, 0.4, 0.5, 0.4, 0.3);
        w.spawnParticle(Particle.DUST_COLOR_TRANSITION, at, 14, 0.4, 0.5, 0.4, 0, PURPLE_EDGE);
        SoundUtils.play(at, Sound.ENTITY_ENDERMAN_TELEPORT, 1.4f, 0.5f);

        final BlockDisplay bloom = spawn(rig, at, Material.MAGENTA_STAINED_GLASS, BRIGHT);
        bloom.setBillboard(Display.Billboard.CENTER);
        place(bloom, at, new Quaternionf(), 2.6f, 2.6f, 0.03f, 0, 0);
        rig.timed.add(new Timed(bloom, p.ticksLived(), 8, age -> {
            if (age == 1) shrink(bloom, 6);
        }));
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Ambient upkeep + cleanup
    // ─────────────────────────────────────────────────────────────────────────

    /** Ages timed entities (seal, bloom, tunnel ribbon, eaten blocks): shrinks and removes them on schedule. */
    public static void tickAmbient(PurpleProjectile p) {
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

    /** Removes every display and light, and schedules the erased terrain to come back. */
    public static void cleanup(PurpleProjectile p) {
        Rig rig = p.rig();
        if (rig == null) return;

        try {
            World w = rig.purpleC != null && rig.purpleC.getWorld() != null ? rig.purpleC.getWorld() : p.caster().getWorld();
            if (w != null && !rig.lights.isEmpty()) TechniqueLighting.setLights(rig.lights, w, new HashMap<>());
        } catch (Throwable ignored) {
            // Lighting is cosmetic; never block the cleanup.
        }

        for (Entity e : new ArrayList<>(rig.entities)) {
            if (e != null && e.isValid()) e.remove();
        }
        rig.entities.clear();
        rig.blueCore.clear();
        rig.redCore.clear();
        rig.halos.clear();
        rig.rays.clear();
        rig.orbiters.clear();
        rig.sucks.clear();
        rig.cubes.clear();
        rig.timed.clear();

        // The erased terrain returns after a while (the real world was never touched).
        if (!rig.erasedLocs.isEmpty()) {
            final List<Location> locs = new ArrayList<>(rig.erasedLocs);
            final Set<UUID> viewers = new HashSet<>(rig.viewers);
            rig.erasedLocs.clear();
            try {
                var task = ShenanigansCore.runTaskLater(
                        () -> TechniqueEraseOps.restore(locs, viewers), p.eraseTicks());
                // During disable the scheduler helper returns null instead of throwing (issue #57).
                if (task == null) TechniqueEraseOps.restore(locs, viewers);
            } catch (Throwable t) {
                // Plugin is shutting down: restore immediately.
                TechniqueEraseOps.restore(locs, viewers);
            }
        }
        p.setRig(null);
    }

}
