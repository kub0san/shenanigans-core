package com.hizeric.shenaniganscore.origins.jujutsukaisen.techniques.piercingblood;

import com.hizeric.shenaniganscore.core.display.TechniqueLighting;
import com.hizeric.shenaniganscore.ShenanigansCore;
import com.hizeric.shenaniganscore.core.display.DisplayUtils;
import com.hizeric.shenaniganscore.core.sound.SoundUtils;
import com.hizeric.shenaniganscore.origins.jujutsukaisen.techniques.piercingblood.PiercingBloodShot.EntityHit;
import com.hizeric.shenaniganscore.origins.jujutsukaisen.techniques.piercingblood.PiercingBloodShot.Punch;
import com.hizeric.shenaniganscore.origins.jujutsukaisen.techniques.piercingblood.PiercingBloodShot.Trace;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.FluidCollisionMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.type.Light;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.LivingEntity;
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
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.IntConsumer;
import com.hizeric.shenaniganscore.core.util.MathUtil;
import com.hizeric.shenaniganscore.core.display.DisplayRig;
import com.hizeric.shenaniganscore.core.display.LightingRig;
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
import static com.hizeric.shenaniganscore.core.display.TechniqueDisplayOps.flingTo;
import static com.hizeric.shenaniganscore.core.display.TechniqueDisplayOps.beamBox;
import static com.hizeric.shenaniganscore.core.display.TechniqueDisplayOps.spawnGlyph;
import static com.hizeric.shenaniganscore.core.display.TechniqueLighting.setLightPath;

/**
 * Every visual of Piercing Blood (穿血), built almost entirely out of display entities.
 *
 * <p><b>The idea:</b> this is a technique about <i>blood under pressure</i>, so everything is liquid,
 * dark and dense — and the displays are used for what each does best:</p>
 *
 * <ul>
 *   <li><b>Blood gauntlets (BlockDisplay)</b> — two hands sheathed in condensed blood hang in front of
 *       the caster, fingers forward. They slam together on the clap and dissolve on the shot.</li>
 *   <li><b>Droplet streams (ItemDisplay)</b> — redstone-dust sprites are drawn out of the caster's body
 *       in swirling streams that spiral into the orb. When the shot fires, they are caught in its
 *       slipstream and streak down the beam.</li>
 *   <li><b>The compression orb (BlockDisplay)</b> — a black core in layered blood-glass that <i>shrinks</i>
 *       as pressure builds (it gets denser, not bigger), with a white glint circling it, a heartbeat
 *       pulse that speeds up, and three translucent pressure shells that keep imploding into it.</li>
 *   <li><b>The beam (BlockDisplay, stretched)</b> — three layers (blood glass, dark red, white glint),
 *       each built from displays that are <i>scaled forward by client interpolation</i>: one spawn and
 *       one transformation per 20-block chunk, with a per-chunk delay, makes the beam visibly race out
 *       at the speed of sound with zero per-tick cost. Afterwards it thins to a thread and beads into
 *       falling droplets.</li>
 *   <li><b>The needle head (BlockDisplay)</b> — a diamond of blood that leads the beam, shedding sonic
 *       rings and sonic booms along the way.</li>
 *   <li><b>Pierce reactions</b> — each target is <i>pinned</i> by a needle that stays through the body for
 *       a moment, sprays droplets forward and bleeds. Wall impacts spit chips of the real block back at
 *       the shooter, leave a bullet-hole decal (oriented flush to the surface), spattered streaks and
 *       cracks, and blood that <i>runs down</i> vertical walls.</li>
 *   <li><b>TextDisplay</b> — a giant 穿 seal flashes ahead of the muzzle at the shot.</li>
 *   <li><b>Client-side light</b> — the beam lights its whole path for a moment, then fades.</li>
 * </ul>
 *
 * <p>Nothing real is ever changed. Everything scales with
 * {@code jujutsukaisen.techniques.piercingblood.quality}.</p>
 */
public final class PiercingBloodBuilder {

    private PiercingBloodBuilder() {}

    /** Beam chunk length: long enough to be cheap, short enough to stay within render culling. */
    private static final double CHUNK = 20.0;

    private static final Particle.DustOptions BLOOD = new Particle.DustOptions(Color.fromRGB(150, 0, 12), 1.3f);
    private static final Particle.DustTransition BLOOD_EDGE =
            new Particle.DustTransition(Color.fromRGB(200, 10, 20), Color.fromRGB(40, 0, 5), 1.5f);
    private static final BlockData BLOOD_BLOCK = Material.REDSTONE_BLOCK.createBlockData();
    private static final TextColor BLOOD_TEXT = TextColor.color(190, 10, 20);


    // ─────────────────────────────────────────────────────────────────────────
    // Data carried by the shot
    // ─────────────────────────────────────────────────────────────────────────

    /** One layer of the compression orb. */
    private record Piece(BlockDisplay display, Quaternionf rot, float rel, float spin) {}

    /** One blood droplet drawn out of the body (offsets are in the caster's view frame). */
    private record Drop(ItemDisplay display, double f, double r, double u, double phase, double speed) {}

    /** One chunk of one beam layer. */
    private record BeamPart(BlockDisplay display, Quaternionf rot, float th, float len, double offset) {}

    /** A droplet (item display) thrown on an arc. */
    private record Spray(Display display, Location apex, Location land, float size, int born) {}

    /** A chip of the real block (block display) thrown on an arc. */
    private record Chip(BlockDisplay display, Location apex, Location land, float size, int born) {}

    /** A streak of blood that will run down a wall once armed. */
    private record Drip(BlockDisplay display, Quaternionf rot, float len, int armTick) {}

    /** A pierced target that visibly bleeds for a while. */
    private record Bleed(LivingEntity victim, double yOff, int until) {}

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

    /** Everything the shot spawns, plus the little state the animation needs. */
    public static final class Rig implements LightingRig {
        final List<Entity> entities = new ArrayList<>();

        @Override
        public List<Entity> entities() {
            return entities;
        }
        final List<Piece> orb = new ArrayList<>();
        final List<BlockDisplay> shells = new ArrayList<>();
        BlockDisplay glint;
        final List<BlockDisplay> handL = new ArrayList<>();
        final List<BlockDisplay> handR = new ArrayList<>();
        final List<Drop> drops = new ArrayList<>();
        float orbRadius = 0.5f;
        float k = 1f;
        Location muzzle;

        final List<BeamPart> beam = new ArrayList<>();
        final List<BlockDisplay> head = new ArrayList<>();
        final List<Spray> sprays = new ArrayList<>();
        final List<Chip> chips = new ArrayList<>();
        final List<Drip> drips = new ArrayList<>();
        final List<Bleed> bleeds = new ArrayList<>();
        final List<Timed> timed = new ArrayList<>();
        final List<Entity> scars = new ArrayList<>();
        final List<Location> lightPts = new ArrayList<>();
        final Map<Location, Integer> lights = new HashMap<>();

        @Override
        public Map<Location, Integer> lights() {
            return lights;
        }
        int booms;
    }

    /** Where the shot leaves the fingertips. */
    static Location muzzleOf(Rig rig) {
        return rig.muzzle == null ? null : rig.muzzle.clone();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Small helpers
    // ─────────────────────────────────────────────────────────────────────────

    /** facing(n) rolled so that the local X axis lies along {@code td} (td must lie in the plane of n). */
    private static Quaternionf inPlane(Vector n, Vector td) {
        Quaternionf q = facing(n);
        Vector3f qx = q.transform(new Vector3f(1, 0, 0));
        Vector ex = new Vector(qx.x, qx.y, qx.z);
        double sin = n.dot(ex.clone().crossProduct(td));
        double cos = ex.dot(td);
        q.rotateZ((float) Math.atan2(sin, cos));
        return q;
    }

    private static int scaled(PiercingBloodShot shot, int base) {
        return (int) Math.max(1, Math.round(base * shot.quality()));
    }

    /** Spawns a fullbright block display (tiny) and tracks it for cleanup. */
    /** A blood-droplet sprite (redstone dust) that always faces the viewer. */
    private static ItemDisplay spawnDrop(Rig rig, Location at) {
        ItemDisplay d = DisplayUtils.spawnItem(at, new ItemStack(Material.REDSTONE), VIEW, BRIGHT);
        d.setBillboard(Display.Billboard.CENTER);
        d.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.FIXED);
        d.setTransformation(scaleOnly(TINY, 0f));
        rig.entities.add(d);
        return d;
    }

    /** A glowing kanji that always faces the viewer. */
    /** Box of the given size whose CENTER sits on {@code center}. */
    /**
     * A box that starts at the display's location and grows FORWARD along its local +Z.
     * Changing {@code sz} with an interpolation duration makes it visibly stretch; the delay
     * decides when the stretch starts. No teleport is involved.
     */
    /** Like {@link #beamBox} but grows along local +X (blood running down a wall). */
    private static void dripBox(BlockDisplay d, Quaternionf q, float len, float w, float h, int interp, int delay) {
        if (d == null || !d.isValid()) return;
        Vector3f off = new Vector3f(0f, -w / 2f, -h / 2f);
        q.transform(off);
        d.setInterpolationDelay(delay);
        d.setInterpolationDuration(interp);
        d.setTransformation(new Transformation(off, new Quaternionf(q), new Vector3f(len, w, h), new Quaternionf()));
    }

    private static void ring(World w, Location c, Vector[] bs, double radius, int n, double speed, boolean boom) {
        for (int i = 0; i < n; i++) {
            double a = Math.PI * 2.0 * i / n;
            Vector out = bs[0].clone().multiply(Math.cos(a)).add(bs[1].clone().multiply(Math.sin(a)));
            w.spawnParticle(Particle.CLOUD, c, 0, out.getX(), out.getY(), out.getZ(), speed);
            if (i % 2 == 0) {
                w.spawnParticle(Particle.DUST_COLOR_TRANSITION, c.clone().add(out.clone().multiply(radius)),
                        1, 0, 0, 0, 0, BLOOD_EDGE);
            }
        }
        if (boom) w.spawnParticle(Particle.SONIC_BOOM, c, 1, 0, 0, 0, 0);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Client-side light along the beam
    // ─────────────────────────────────────────────────────────────────────────
    // ─────────────────────────────────────────────────────────────────────────
    // COMPRESS — blood is drawn out of the body and squeezed
    // ─────────────────────────────────────────────────────────────────────────

    /** Builds the gauntlets, orb, shells and droplet streams (tiny; the tick methods unfold them). */
    public static void spawnCompress(PiercingBloodShot shot) {
        Location at = shot.caster().getEyeLocation();
        ThreadLocalRandom r = ThreadLocalRandom.current();

        Rig rig = new Rig();
        rig.orbRadius = (float) (0.40 + 0.05 * shot.power());
        rig.k = (float) (0.8 + 0.1 * shot.power());

        // The orb: a black core in layered blood-glass.
        rig.orb.add(new Piece(spawn(rig, at, Material.BLACK_CONCRETE, 5), randomRot(r), 0.55f, 0.0f));
        rig.orb.add(new Piece(spawn(rig, at, Material.RED_STAINED_GLASS, BRIGHT), randomRot(r), 1.0f, 0.35f));
        rig.orb.add(new Piece(spawn(rig, at, Material.RED_STAINED_GLASS, BRIGHT), randomRot(r), 1.0f, -0.28f));
        rig.orb.add(new Piece(spawn(rig, at, Material.RED_STAINED_GLASS, BRIGHT), randomRot(r), 1.45f, 0.18f));
        rig.glint = spawn(rig, at, Material.WHITE_CONCRETE, BRIGHT);
        for (int i = 0; i < 3; i++) rig.shells.add(spawn(rig, at, Material.RED_STAINED_GLASS, BRIGHT));

        // Two blood gauntlets: a palm and four fingers each.
        for (int side = 0; side < 2; side++) {
            List<BlockDisplay> hand = side == 0 ? rig.handL : rig.handR;
            hand.add(spawn(rig, at, Material.REDSTONE_BLOCK, 12));
            for (int f = 0; f < 4; f++) hand.add(spawn(rig, at, Material.RED_CONCRETE, 10));
        }

        // Droplets drawn out of the body.
        int n = Math.max(8, Math.min(40, scaled(shot, 18)));
        for (int i = 0; i < n; i++) {
            double side = r.nextBoolean() ? 1.0 : -1.0;
            rig.drops.add(new Drop(spawnDrop(rig, at),
                    r.nextDouble() * 0.4,
                    side * (0.2 + r.nextDouble() * 0.7),
                    -1.0 + r.nextDouble() * 1.1,
                    r.nextDouble(),
                    0.35 + r.nextDouble() * 0.3));
        }
        shot.setRig(rig);
    }

    /** Places both gauntlets: palms facing each other, fingers forward, {@code gap} apart. */
    private static void poseHands(Rig rig, Location m, Vector fwd, Vector right, Vector up,
                                  double gap, double shake) {
        Quaternionf q = facing(fwd);
        for (int side = -1; side <= 1; side += 2) {
            List<BlockDisplay> hand = side < 0 ? rig.handL : rig.handR;
            if (hand.isEmpty()) continue;
            Location palm = m.clone().add(right.clone().multiply(side * (gap / 2.0 + 0.06)))
                    .add(up.clone().multiply(shake * side));
            place(hand.get(0), palm, q, 0.10f, 0.44f, 0.50f, 1, 1);
            for (int f = 0; f < 4; f++) {
                Location fc = palm.clone().add(fwd.clone().multiply(0.44)).add(up.clone().multiply(-0.15 + 0.10 * f));
                place(hand.get(1 + f), fc, q, 0.07f, 0.07f, 0.38f, 1, 1);
            }
        }
    }

    /** Poses the orb's layers around a centre at a given radius. */
    private static void poseOrb(Rig rig, Location c, double tick, double radius, double pulse) {
        for (Piece pc : rig.orb) {
            Quaternionf q = new Quaternionf(pc.rot())
                    .rotateY((float) (tick * pc.spin()))
                    .rotateX((float) (tick * pc.spin() * 0.6));
            float s = (float) Math.max(TINY, radius * pc.rel() * pulse);
            place(pc.display(), c, q, s, s, s, 1, 1);
        }
    }

    /** Called every compress tick. progress runs 0..1. */
    public static void tickCompress(PiercingBloodShot shot, double progress) {
        Rig rig = shot.rig();
        if (rig == null) return;

        Player c = shot.caster();
        World w = c.getWorld();
        ThreadLocalRandom r = ThreadLocalRandom.current();
        int tick = shot.stateTicks();
        double e = MathUtil.smoothstep(progress);

        Location eye = c.getEyeLocation();
        Vector fwd = eye.getDirection().normalize();
        Vector right = fwd.clone().crossProduct(new Vector(0, 1, 0));
        if (right.lengthSquared() < 1.0e-4) right = new Vector(1, 0, 0);
        right.normalize();
        Vector up = right.clone().crossProduct(fwd).normalize();

        Location m = eye.clone().add(fwd.clone().multiply(1.5)).subtract(up.clone().multiply(0.38));
        rig.muzzle = m.clone().add(fwd.clone().multiply(0.85));

        // Hands inch together while the pressure builds; they tremble more and more.
        double gap = 0.95 - 0.40 * e;
        poseHands(rig, m, fwd, right, up, gap, Math.sin(tick * 2.3) * (0.004 + 0.012 * e));

        // The orb shrinks as it is compressed: denser, not bigger. It beats like a heart.
        Location o = m.clone().add(fwd.clone().multiply(0.35));
        double rad = rig.orbRadius * (1.0 - 0.72 * e);
        double beat = Math.max(0.0, Math.sin(tick * (0.6 + 0.9 * e)));
        double pulse = 1.0 + 0.10 * beat;
        double tremor = 0.005 + 0.02 * e;
        Location oj = o.clone().add(Math.sin(tick * 3.1) * tremor, Math.cos(tick * 2.7) * tremor, Math.sin(tick * 2.1) * tremor);
        poseOrb(rig, oj, tick, rad, pulse);

        // A white glint circles the core, faster and faster.
        double ga = tick * (0.5 + 1.1 * e);
        Location gl = oj.clone().add(right.clone().multiply(Math.cos(ga) * rad * 0.7))
                .add(up.clone().multiply(Math.sin(ga) * rad * 0.7));
        place(rig.glint, gl, new Quaternionf(), (float) (rad * 0.28), (float) (rad * 0.28), (float) (rad * 0.28), 1, 1);

        // Pressure shells: translucent cubes that keep imploding into the orb.
        for (int i = 0; i < rig.shells.size(); i++) {
            double frac = ((tick * 0.09 * (1.0 + e)) + i / (double) rig.shells.size()) % 1.0;
            float s = (float) (rad * (1.4 + 1.8 * (1.0 - frac)));
            Quaternionf q = new Quaternionf().rotateY((float) (tick * 0.2 + i)).rotateX((float) (i * 1.1));
            place(rig.shells.get(i), oj, q, s, s, s, 1, 1);
        }

        // Droplets stream out of the body, swirling into the orb.
        for (Drop d : rig.drops) {
            double s = ((tick * d.speed() * 0.08) + d.phase()) % 1.0;
            double ease = MathUtil.smoothstep(s);
            Location origin = eye.clone().add(fwd.clone().multiply(d.f()))
                    .add(right.clone().multiply(d.r())).add(up.clone().multiply(d.u()));
            Location pos = lerp(origin, oj, ease);
            double ang = d.phase() * Math.PI * 2.0 + s * 6.0;
            double sw = (1.0 - s) * 0.45;
            pos.add(right.clone().multiply(Math.cos(ang) * sw)).add(up.clone().multiply(Math.sin(ang) * sw));
            float size = (float) (0.45 * (1.0 - 0.75 * s) * rig.k);
            moveDisplay(d.display(), pos, size, (float) ang);
        }

        // A little ambient blood.
        w.spawnParticle(Particle.DUST, oj, 2, rad, rad, rad, 0, BLOOD);
        if (tick % 4 == 0) w.spawnParticle(Particle.DAMAGE_INDICATOR, oj, 1, rad, rad, rad, 0.05);

        // HUD: a compression gauge.
        if (tick % 2 == 0) {
            int filled = (int) Math.round(progress * 10.0);
            c.sendActionBar(MiniMessage.miniMessage().deserialize(
                    "<dark_red>Compression <red>" + "▮".repeat(filled) + "<dark_gray>" + "▯".repeat(10 - filled)
                            + " <white>" + (int) Math.round(progress * 100.0) + "%"));
        }

        // Sound: a heartbeat that speeds up as the blood is squeezed.
        int interval = (int) Math.max(3, Math.round(10 - 7 * progress));
        if (tick == 1 || tick % interval == 0) {
            SoundUtils.play(m, Sound.ENTITY_WARDEN_HEARTBEAT, 1.2f, (float) (0.7 + 0.5 * progress));
        }
        if (tick == 1) SoundUtils.play(m, Sound.BLOCK_HONEY_BLOCK_BREAK, 1.0f, 0.6f);
    }

    /** The clap: the palms slam together and the orb collapses to a point. */
    public static void tickClap(PiercingBloodShot shot, int t) {
        Rig rig = shot.rig();
        if (rig == null) return;

        Player c = shot.caster();
        World w = c.getWorld();
        Location eye = c.getEyeLocation();
        Vector fwd = eye.getDirection().normalize();
        Vector right = fwd.clone().crossProduct(new Vector(0, 1, 0));
        if (right.lengthSquared() < 1.0e-4) right = new Vector(1, 0, 0);
        right.normalize();
        Vector up = right.clone().crossProduct(fwd).normalize();

        Location m = eye.clone().add(fwd.clone().multiply(1.5)).subtract(up.clone().multiply(0.38));
        rig.muzzle = m.clone().add(fwd.clone().multiply(0.85));

        double f = 1.0 - t / (double) PiercingBloodShot.CLAP_TICKS;
        poseHands(rig, m, fwd, right, up, 0.55 * f, 0.0);

        Location o = m.clone().add(fwd.clone().multiply(0.35));
        double rad = rig.orbRadius * 0.28 * f;
        poseOrb(rig, o, shot.ticksLived(), rad, 1.0);
        for (BlockDisplay s : rig.shells) place(s, o, new Quaternionf(), TINY, TINY, TINY, 1, 1);

        if (t == PiercingBloodShot.CLAP_TICKS) {
            Vector[] bs = basis(fwd);
            ring(w, o, bs, 0.6, 14, 0.3, false);
            w.spawnParticle(Particle.FLASH, o, 1, 0, 0, 0, 0, Color.WHITE);
            w.spawnParticle(Particle.DAMAGE_INDICATOR, o, 6, 0.2, 0.2, 0.2, 0.2);
            SoundUtils.play(o, Sound.ENTITY_PLAYER_ATTACK_CRIT, 2.0f, 1.7f);
            SoundUtils.play(o, Sound.ENTITY_GENERIC_EXPLODE, 0.8f, 1.9f);
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // FIRE — the beam
    // ─────────────────────────────────────────────────────────────────────────

    /** Builds the beam, the head, the 穿 seal and the lights; dissolves the hands and orb. */
    public static void fire(PiercingBloodShot shot) {
        Rig rig = shot.rig();
        if (rig == null) return;
        Trace tr = shot.trace();
        if (tr == null) return;
        World w = tr.start.getWorld();
        if (w == null) return;

        Location s0 = tr.start;
        Vector d0 = tr.dir;
        double len0 = tr.length;
        float k = rig.k;
        int now = shot.ticksLived();
        ThreadLocalRandom r = ThreadLocalRandom.current();

        // The gauntlets and orb dissolve.
        for (BlockDisplay d : rig.handL) shrink(d, 5);
        for (BlockDisplay d : rig.handR) shrink(d, 5);
        for (Piece pc : rig.orb) shrink(pc.display(), 3);
        shrink(rig.glint, 3);
        for (BlockDisplay d : rig.shells) shrink(d, 3);

        // The droplets are caught in the slipstream and streak down the beam.
        for (Drop dr : rig.drops) {
            double s = 2.0 + r.nextDouble() * Math.min(len0, 32.0);
            Location to = s0.clone().add(d0.clone().multiply(s)).add(randomUnit(r).multiply(0.45));
            flingTo(dr.display(), to, 0.18f, (float) (r.nextDouble() * 6.0), 4);
            rig.timed.add(new Timed(dr.display(), now, 8, null));
        }
        rig.drops.clear();

        // The beam: three layers, chunked. Spawned tiny now, stretched forward next tick.
        Quaternionf q = facing(d0);
        Material[] mats = {Material.RED_STAINED_GLASS, Material.REDSTONE_BLOCK, Material.WHITE_CONCRETE};
        float[] th = {0.46f * k, 0.20f * k, 0.06f * k};
        int chunks = (int) Math.ceil(len0 / CHUNK);
        for (int i = 0; i < chunks; i++) {
            Location anchor = s0.clone().add(d0.clone().multiply(i * CHUNK));
            float len = (float) Math.min(CHUNK, len0 - i * CHUNK);
            for (int l = 0; l < mats.length; l++) {
                BlockDisplay d = DisplayUtils.spawnBlock(anchor, mats[l], VIEW, BRIGHT);
                rig.entities.add(d);
                beamBox(d, q, th[l], th[l], TINY, 0, 0);
                rig.beam.add(new BeamPart(d, q, th[l], len, i * CHUNK));
            }
        }

        // The needle head that leads the beam.
        rig.head.add(spawn(rig, s0, Material.REDSTONE_BLOCK, BRIGHT));
        rig.head.add(spawn(rig, s0, Material.REDSTONE_BLOCK, BRIGHT));
        rig.head.add(spawn(rig, s0, Material.WHITE_CONCRETE, BRIGHT));

        // The 穿 seal flashes ahead of the muzzle.
        final Location sealAt = s0.clone().add(d0.clone().multiply(8.0));
        final TextDisplay seal = spawnGlyph(rig, sealAt, "穿", BLOOD_TEXT, TINY);
        rig.timed.add(new Timed(seal, now, 12, age -> {
            if (age == 1) {
                moveDisplay(seal, sealAt, 16f, 0f);
            } else if (age >= 4) {
                seal.setTextOpacity((byte) Math.max(40, 255 - (age - 3) * 26));
            }
        }));

        // Lights along the path.
        for (int i = 1; i <= 12; i++) {
            double dist = i * 8.0;
            if (dist >= len0) break;
            rig.lightPts.add(s0.clone().add(d0.clone().multiply(dist)));
        }

        // Muzzle blast at the fingertips: a cone of vapour and blood, and a flash.
        Vector[] bs = basis(d0);
        w.spawnParticle(Particle.FLASH, s0, 1, 0, 0, 0, 0, Color.WHITE);
        for (int i = 0; i < 20; i++) {
            Vector v = d0.clone().add(randomUnit(r).multiply(0.3)).normalize();
            w.spawnParticle(Particle.CLOUD, s0, 0, v.getX(), v.getY(), v.getZ(), 0.8);
            if (i % 2 == 0) w.spawnParticle(Particle.DUST, s0.clone().add(v.multiply(0.6)), 1, 0.05, 0.05, 0.05, 0, BLOOD);
        }
        ring(w, s0, bs, 0.8, 16, 0.35, false);

        SoundUtils.play(s0, Sound.ENTITY_WARDEN_SONIC_BOOM, 2.5f, 1.5f);
        SoundUtils.play(s0, Sound.ENTITY_WARDEN_SONIC_BOOM, 2.0f, 0.9f);
        SoundUtils.play(s0, Sound.ENTITY_FIREWORK_ROCKET_BLAST, 1.2f, 1.8f);
        SoundUtils.play(s0, Sound.ENTITY_PLAYER_ATTACK_CRIT, 1.0f, 0.6f);
    }

    /** The tick on which the beam's head reaches a given distance. */
    private static int reactTick(double distance, double speed) {
        return (int) Math.floor(distance / speed) + 1;
    }

    /** Animates the shot. t is 1 on the first tick after firing. */
    public static void tickBeam(PiercingBloodShot shot, int t) {
        Rig rig = shot.rig();
        Trace tr = shot.trace();
        if (rig == null || tr == null) return;
        World w = tr.start.getWorld();
        if (w == null) return;

        Location s0 = tr.start;
        Vector d0 = tr.dir;
        double len0 = tr.length;
        double speed = shot.headSpeed();
        int th = (int) Math.ceil(len0 / speed);
        float k = rig.k;
        ThreadLocalRandom r = ThreadLocalRandom.current();

        if (t == 1) armBeam(rig, speed);

        // ── The needle head races out, shedding rings and sonic booms ──
        double dist = Math.min(len0, t * speed);
        double prev = Math.min(len0, (t - 1) * speed);
        if (t <= th) {
            Location hp = s0.clone().add(d0.clone().multiply(Math.max(0.0, dist - 0.4)));
            Quaternionf base = facing(d0);
            for (int i = 0; i < rig.head.size(); i++) {
                Quaternionf q = new Quaternionf(base);
                if (i == 1) q.rotateZ((float) Math.toRadians(45.0));
                float sx = i < 2 ? 0.5f * k : 0.2f * k;
                float sz = i < 2 ? 1.1f * k : 1.6f * k;
                place(rig.head.get(i), hp, q, sx, sx, sz, 0, 1);
            }
            Vector[] bs = basis(d0);
            for (double s = Math.ceil((prev + 0.01) / 7.0) * 7.0; s <= dist; s += 7.0) {
                boolean boom = rig.booms < 6 && ((int) (s / 7.0)) % 2 == 0;
                if (boom) rig.booms++;
                ring(w, s0.clone().add(d0.clone().multiply(s)), bs, 0.9, 14, 0.28, boom);
            }
        } else if (t == th + 1) {
            for (BlockDisplay d : rig.head) shrink(d, 3);
        }

        // ── Reactions: each thing the shot touches reacts when the head arrives ──
        for (EntityHit hit : tr.hits) {
            if (reactTick(hit.distance(), speed) == t) reactEntity(shot, rig, w, hit, t, r);
        }
        for (Punch punch : tr.punches) {
            double d = punch.point().distance(s0);
            if (reactTick(d, speed) == t) reactPunch(w, punch, r);
        }
        if (tr.impact != null && reactTick(len0, speed) == t) {
            reactImpact(shot, rig, w, tr, t, r);
        }

        // ── The beam thins to a thread, beads into droplets, vanishes ──
        if (t == th + 3) beadBeam(shot, rig, w, tr, t, r);
        if (t == th + 4) {
            for (BeamPart p : rig.beam) beamBox(p.display(), p.rot(), 0.02f, 0.02f, p.len(), 8, 0);
        }
        if (t == th + 14) {
            for (BeamPart p : rig.beam) discard(rig, p.display());
            rig.beam.clear();
            for (BlockDisplay d : rig.head) discard(rig, d);
            rig.head.clear();
        }

        // ── Upkeep of everything the reactions spawned ──
        tickSprays(rig, t);
        tickChips(rig, t, r);
        tickDrips(rig, t);
        tickBleeds(rig, w, t, r);

        // ── Light: the whole path glows, then fades ──
        if (t <= th + 3) {
            setLightPath(rig, w, rig.lightPts, 12);
        } else if (t <= th + 14) {
            setLightPath(rig, w, rig.lightPts, Math.max(0, 12 - (t - (th + 3))));
        } else if (t == th + 15) {
            setLightPath(rig, w, rig.lightPts, 0);
        }
    }

    /** Starts the stretch: each chunk begins growing when the head reaches it, at head speed. */
    private static void armBeam(Rig rig, double speed) {
        for (BeamPart p : rig.beam) {
            int delay = (int) Math.floor(p.offset() / speed);
            int dur = Math.max(1, (int) Math.ceil(p.len() / speed));
            beamBox(p.display(), p.rot(), p.th(), p.th(), p.len(), dur, delay);
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Reactions
    // ─────────────────────────────────────────────────────────────────────────

    /** A target is pinned: ring burst, a needle through the body, forward spray, bleeding. */
    private static void reactEntity(PiercingBloodShot shot, Rig rig, World w, EntityHit h, int t,
                                    ThreadLocalRandom r) {
        Location p = h.point();
        Vector d = shot.trace().dir;
        Vector[] bs = basis(d);
        float k = rig.k;
        int now = shot.ticksLived();

        ring(w, p, bs, 0.9, 14, 0.25, false);
        w.spawnParticle(Particle.BLOCK, p, 20, 0.25, 0.3, 0.25, 0.1, BLOOD_BLOCK);
        w.spawnParticle(Particle.DAMAGE_INDICATOR, p, 4, 0.2, 0.3, 0.2, 0.1);

        // Droplets sprayed forward out of the exit wound.
        int n = scaled(shot, 8);
        double feetY = h.entity().getLocation().getY();
        for (int i = 0; i < n; i++) {
            Vector side = randomUnit(r);
            side.subtract(d.clone().multiply(side.dot(d)));
            if (side.lengthSquared() > 1.0e-4) side.normalize().multiply(0.4 + r.nextDouble() * 1.1);
            Location apex = p.clone().add(d.clone().multiply(0.8 + r.nextDouble() * 1.6)).add(side)
                    .add(0, 0.3 + r.nextDouble() * 0.8, 0);
            Location land = apex.clone().add(d.clone().multiply(0.6 + r.nextDouble()));
            land.setY(feetY + 0.08);
            rig.sprays.add(new Spray(spawnDrop(rig, p), apex, land, 0.28f + r.nextFloat() * 0.2f, t));
        }

        // A needle stays through the body for a moment: the shot's puncture, pinned in place.
        Quaternionf q = facing(d);
        BlockDisplay n1 = spawn(rig, p, Material.REDSTONE_BLOCK, BRIGHT);
        place(n1, p, q, 0.16f * k, 0.16f * k, 3.2f, 0, 0);
        BlockDisplay n2 = spawn(rig, p, Material.WHITE_CONCRETE, BRIGHT);
        place(n2, p, q, 0.05f * k, 0.05f * k, 3.4f, 0, 0);
        rig.timed.add(new Timed(n1, now, 16, age -> {
            if (age == 10) shrink(n1, 6);
        }));
        rig.timed.add(new Timed(n2, now, 16, age -> {
            if (age == 10) shrink(n2, 6);
        }));

        if (shot.bleedTicks() > 0) {
            rig.bleeds.add(new Bleed(h.entity(), p.getY() - feetY, t + shot.bleedTicks()));
        }
        SoundUtils.play(p, Sound.ENTITY_PLAYER_ATTACK_CRIT, 1.3f, 0.7f);
        SoundUtils.play(p, Sound.BLOCK_HONEY_BLOCK_BREAK, 1.2f, 0.6f);
    }

    /** A soft block is punched through: a spray of chips and a ring. */
    private static void reactPunch(World w, Punch punch, ThreadLocalRandom r) {
        Location p = punch.point();
        w.spawnParticle(Particle.BLOCK, p, 18, 0.3, 0.3, 0.3, 0.08, punch.data());
        w.spawnParticle(Particle.CLOUD, p, 6, 0.3, 0.3, 0.3, 0.05);
        SoundUtils.play(p, Sound.BLOCK_STONE_BREAK, 1.0f, 1.6f);
    }

    /** The shot stops on solid ground: chips fly back at the shooter, a bullet hole is left, blood runs. */
    private static void reactImpact(PiercingBloodShot shot, Rig rig, World w, Trace tr, int t,
                                    ThreadLocalRandom r) {
        PiercingBloodShot.Impact imp = tr.impact;
        Location hp = imp.point();
        BlockFace face = imp.face();
        Vector n = face.getDirection().clone();
        if (n.lengthSquared() < 1.0e-4) n = new Vector(0, 1, 0);
        n.normalize();

        // Block spray thrown back out of the hole.
        for (int i = 0; i < 24; i++) {
            Vector v = n.clone().add(randomUnit(r).multiply(0.8)).normalize();
            w.spawnParticle(Particle.BLOCK, hp, 0, v.getX(), v.getY(), v.getZ(), 0.5 + r.nextDouble() * 0.6, imp.data());
        }
        w.spawnParticle(Particle.FLASH, hp, 1, 0, 0, 0, 0, Color.WHITE);
        w.spawnParticle(Particle.DAMAGE_INDICATOR, hp, 6, 0.2, 0.2, 0.2, 0.2);
        w.spawnParticle(Particle.SONIC_BOOM, hp, 1, 0, 0, 0, 0);
        SoundUtils.play(hp, Sound.ENTITY_GENERIC_EXPLODE, 1.0f, 1.7f);
        SoundUtils.play(hp, Sound.BLOCK_STONE_BREAK, 1.6f, 0.7f);

        buildChips(shot, rig, w, hp, n, imp.data(), t, r);
        buildDecal(shot, rig, w, hp, n, t, r);
    }

    /** Chips of the real block (block displays) fly out on arcs. */
    private static void buildChips(PiercingBloodShot shot, Rig rig, World w, Location hp, Vector n,
                                   BlockData data, int t, ThreadLocalRandom r) {
        int count = Math.max(4, Math.min(10, scaled(shot, 6)));
        for (int i = 0; i < count; i++) {
            float size = 0.22f + r.nextFloat() * 0.2f;
            Vector out = n.clone().add(randomUnit(r).multiply(0.7)).normalize();
            if (out.dot(n) < 0.2) out = n.clone();
            Location start = hp.clone().add(n.clone().multiply(0.1));
            Location apex = hp.clone().add(out.clone().multiply(0.8 + r.nextDouble() * 1.2)).add(0, 0.3 + r.nextDouble() * 0.6, 0);
            RayTraceResult ground = w.rayTraceBlocks(apex, new Vector(0, -1, 0), 25.0, FluidCollisionMode.NEVER, true);
            Location land = ground != null
                    ? ground.getHitPosition().toLocation(w).add(0, size / 2.0, 0)
                    : apex.clone().add(0, -3.0, 0);

            BlockDisplay d = w.spawn(start, BlockDisplay.class, disp -> {
                disp.setBlock(data);
                disp.setPersistent(false);
                disp.setViewRange(VIEW);
                disp.setInterpolationDuration(0);
                disp.setTeleportDuration(0);
            });
            rig.entities.add(d);
            place(d, start, randomRot(r), size, size, size, 0, 0);
            rig.chips.add(new Chip(d, apex, land, size, t));
        }
    }

    /** The wall's wound: a flush bullet hole, spattered streaks, cracks, and drips on vertical walls. */
    private static void buildDecal(PiercingBloodShot shot, Rig rig, World w, Location hp, Vector n,
                                   int t, ThreadLocalRandom r) {
        Location base = hp.clone().add(n.clone().multiply(0.02));
        Quaternionf qn = facing(n);

        // Blood ring and the black hole (each an octagon of two rotated squares).
        decal(rig, base, qn, 0f, 0.9f, Material.RED_CONCRETE, 9);
        decal(rig, base.clone().add(n.clone().multiply(0.004)), qn, 45f, 0.9f, Material.RED_CONCRETE, 9);
        decal(rig, base.clone().add(n.clone().multiply(0.010)), qn, 0f, 0.52f, Material.BLACK_CONCRETE, 2);
        decal(rig, base.clone().add(n.clone().multiply(0.014)), qn, 45f, 0.52f, Material.BLACK_CONCRETE, 2);

        Vector t1 = n.clone().crossProduct(Math.abs(n.getY()) > 0.9 ? new Vector(1, 0, 0) : new Vector(0, 1, 0)).normalize();
        Vector t2 = n.clone().crossProduct(t1).normalize();

        // Spattered streaks.
        int streaks = Math.max(5, scaled(shot, 8));
        for (int i = 0; i < streaks; i++) {
            double a = r.nextDouble() * Math.PI * 2.0;
            Vector td = t1.clone().multiply(Math.cos(a)).add(t2.clone().multiply(Math.sin(a))).normalize();
            double len = 0.5 + r.nextDouble() * 1.2;
            Location c = base.clone().add(n.clone().multiply(0.012)).add(td.clone().multiply(0.3 + len / 2.0));
            strip(rig, c, inPlane(n, td), (float) len, 0.07f + r.nextFloat() * 0.05f,
                    i % 2 == 0 ? Material.REDSTONE_BLOCK : Material.RED_CONCRETE, 9);
        }

        // Cracks.
        for (int i = 0; i < 5; i++) {
            double a = r.nextDouble() * Math.PI * 2.0;
            Vector td = t1.clone().multiply(Math.cos(a)).add(t2.clone().multiply(Math.sin(a))).normalize();
            double len = 0.4 + r.nextDouble() * 0.5;
            Location c = base.clone().add(n.clone().multiply(0.016)).add(td.clone().multiply(0.25 + len / 2.0));
            strip(rig, c, inPlane(n, td), (float) len, 0.035f, Material.BLACK_CONCRETE, 2);
        }

        // On a vertical wall the blood runs down.
        if (Math.abs(n.getY()) < 0.5) {
            Vector down = new Vector(0, -1, 0);
            Vector pd = down.clone().subtract(n.clone().multiply(down.dot(n))).normalize();
            Vector side = n.clone().crossProduct(new Vector(0, 1, 0)).normalize();
            for (int i = 0; i < 3; i++) {
                Location anchor = base.clone().add(n.clone().multiply(0.014))
                        .add(side.clone().multiply((i - 1) * 0.22)).add(0, -0.05, 0);
                Quaternionf q = inPlane(n, pd);
                BlockDisplay d = DisplayUtils.spawnBlock(anchor, Material.REDSTONE_BLOCK, VIEW, 9);
                dripBox(d, q, TINY, 0.05f, 0.02f, 0, 0);
                rig.scars.add(d);
                rig.drips.add(new Drip(d, q, 0.8f + r.nextFloat() * 1.2f, t + 2));
            }
        }
    }

    private static void decal(Rig rig, Location centre, Quaternionf q, float rollDeg, float size,
                              Material m, int brightness) {
        Quaternionf rq = new Quaternionf(q).rotateZ((float) Math.toRadians(rollDeg));
        BlockDisplay d = DisplayUtils.spawnBlock(centre, m, VIEW, brightness);
        place(d, centre, rq, size, size, 0.02f, 0, 0);
        rig.scars.add(d);
    }

    private static void strip(Rig rig, Location centre, Quaternionf q, float len, float width,
                              Material m, int brightness) {
        BlockDisplay d = DisplayUtils.spawnBlock(centre, m, VIEW, brightness);
        place(d, centre, q, len, width, 0.02f, 0, 0);
        rig.scars.add(d);
    }

    /** After the head passes, the beam beads into droplets that fall. */
    private static void beadBeam(PiercingBloodShot shot, Rig rig, World w, Trace tr, int t, ThreadLocalRandom r) {
        int n = scaled(shot, 14);
        for (int i = 0; i < n; i++) {
            double s = r.nextDouble() * tr.length;
            Location pos = tr.start.clone().add(tr.dir.clone().multiply(s));
            Location apex = pos.clone().add((r.nextDouble() - 0.5) * 0.4, 0.1, (r.nextDouble() - 0.5) * 0.4);
            RayTraceResult ground = w.rayTraceBlocks(pos, new Vector(0, -1, 0), 30.0, FluidCollisionMode.NEVER, true);
            Location land = ground != null
                    ? ground.getHitPosition().toLocation(w).add(0, 0.05, 0)
                    : pos.clone().add(0, -3.0, 0);
            rig.sprays.add(new Spray(spawnDrop(rig, pos), apex, land, 0.3f, t));
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Per-tick upkeep for what the reactions spawned
    // ─────────────────────────────────────────────────────────────────────────

    /** Droplets: up/out, down, shrink, gone. */
    private static void tickSprays(Rig rig, int t) {
        Iterator<Spray> it = rig.sprays.iterator();
        while (it.hasNext()) {
            Spray s = it.next();
            int age = t - s.born();
            if (age == 1) {
                flingTo(s.display(), s.apex(), s.size(), 0f, 3);
            } else if (age == 4) {
                flingTo(s.display(), s.land(), s.size(), 1.5f, 7);
            } else if (age == 12) {
                flingTo(s.display(), s.land(), TINY, 0f, 4);
            } else if (age >= 16) {
                discard(rig, s.display());
                it.remove();
            }
        }
    }

    /** Chips of block: up/out, down, shrink, gone. */
    private static void tickChips(Rig rig, int t, ThreadLocalRandom r) {
        Iterator<Chip> it = rig.chips.iterator();
        while (it.hasNext()) {
            Chip c = it.next();
            int age = t - c.born();
            float s = c.size();
            if (age == 1) {
                place(c.display(), c.apex(), randomRot(r), s, s, s, 3, 3);
            } else if (age == 4) {
                place(c.display(), c.land(), randomRot(r), s, s, s, 7, 7);
            } else if (age == 12) {
                place(c.display(), c.land(), new Quaternionf(), TINY, TINY, TINY, 4, 0);
            } else if (age >= 16) {
                discard(rig, c.display());
                it.remove();
            }
        }
    }

    /** Streaks that were armed this tick start running down the wall. */
    private static void tickDrips(Rig rig, int t) {
        Iterator<Drip> it = rig.drips.iterator();
        while (it.hasNext()) {
            Drip d = it.next();
            if (t >= d.armTick()) {
                dripBox(d.display(), d.rot(), d.len(), 0.05f, 0.02f, 30, 0);
                it.remove();
            }
        }
    }

    /** Pierced targets drip blood for a while. */
    private static void tickBleeds(Rig rig, World w, int t, ThreadLocalRandom r) {
        Iterator<Bleed> it = rig.bleeds.iterator();
        while (it.hasNext()) {
            Bleed b = it.next();
            LivingEntity v = b.victim();
            if (t > b.until() || !v.isValid() || v.isDead()) {
                it.remove();
                continue;
            }
            if (t % 3 == 0) {
                Location at = v.getLocation().add(0, b.yOff(), 0);
                w.spawnParticle(Particle.BLOCK, at, 4, 0.15, 0.15, 0.15, 0, BLOOD_BLOCK);
                w.spawnParticle(Particle.DUST, at, 3, 0.15, 0.2, 0.15, 0, BLOOD);
                if (t % 6 == 0) w.spawnParticle(Particle.DAMAGE_INDICATOR, at, 1, 0.1, 0.1, 0.1, 0.05);
            }
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Ambient upkeep + cleanup
    // ─────────────────────────────────────────────────────────────────────────

    /** Ages timed entities (seal, needles, slipstream droplets): shrinks and removes them on schedule. */
    public static void tickAmbient(PiercingBloodShot shot) {
        Rig rig = shot.rig();
        if (rig == null || rig.timed.isEmpty()) return;
        int now = shot.ticksLived();

        Iterator<Timed> it = rig.timed.iterator();
        while (it.hasNext()) {
            Timed timed = it.next();
            if (timed.entity == null || !timed.entity.isValid()) {
                it.remove();
                continue;
            }
            int age = now - timed.born;
            if (age >= timed.life) {
                discard(rig, timed.entity);
                it.remove();
                continue;
            }
            if (timed.onAge != null) timed.onAge.accept(age);
        }
    }

    /** Removes every display and light. The wall decals fade out later on their own. */
    public static void cleanup(PiercingBloodShot shot) {
        Rig rig = shot.rig();
        if (rig == null) return;

        try {
            World w = shot.caster().getWorld();
            if (!rig.lights.isEmpty()) TechniqueLighting.setLights(rig.lights, w, new HashMap<>());
        } catch (Throwable ignored) {
            // Lighting is cosmetic; never block the cleanup.
        }

        for (Entity e : new ArrayList<>(rig.entities)) {
            if (e != null && e.isValid()) e.remove();
        }
        rig.entities.clear();
        rig.orb.clear();
        rig.shells.clear();
        rig.handL.clear();
        rig.handR.clear();
        rig.drops.clear();
        rig.beam.clear();
        rig.head.clear();
        rig.sprays.clear();
        rig.chips.clear();
        rig.timed.clear();

        // Streaks still waiting to run down a wall are armed immediately so they don't freeze at a dot.
        for (Drip d : rig.drips) dripBox(d.display(), d.rot(), d.len(), 0.05f, 0.02f, 20, 0);
        rig.drips.clear();

        List<Entity> scars = new ArrayList<>(rig.scars);
        rig.scars.clear();
        if (!scars.isEmpty()) {
            int delay = shot.scarTicks();
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
        shot.setRig(null);
    }
}
