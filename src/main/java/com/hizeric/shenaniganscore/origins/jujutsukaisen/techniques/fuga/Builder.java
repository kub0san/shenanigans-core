package com.hizeric.shenaniganscore.origins.jujutsukaisen.techniques.fuga;

import com.hizeric.shenaniganscore.core.display.TechniqueLighting;
import com.hizeric.shenaniganscore.ShenanigansCore;
import com.hizeric.shenaniganscore.core.display.DisplayUtils;
import com.hizeric.shenaniganscore.core.sound.SoundUtils;
import com.hizeric.shenaniganscore.origins.jujutsukaisen.domainexpansions.malevolentshrine.Session;
import com.hizeric.shenaniganscore.origins.jujutsukaisen.domainexpansions.malevolentshrine.Slashes;
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
import static com.hizeric.shenaniganscore.core.display.BlockDisplayOps.line;
import static com.hizeric.shenaniganscore.core.display.TechniqueDisplayOps.basis;
import static com.hizeric.shenaniganscore.core.display.TechniqueDisplayOps.discard;
import static com.hizeric.shenaniganscore.core.display.TechniqueDisplayOps.lerp;
import static com.hizeric.shenaniganscore.core.display.TechniqueDisplayOps.moveDisplay;
import static com.hizeric.shenaniganscore.core.display.TechniqueDisplayOps.scaleOnly;
import static com.hizeric.shenaniganscore.core.display.TechniqueDisplayOps.spawn;
import static com.hizeric.shenaniganscore.core.display.TechniqueDisplayOps.flingTo;
import static com.hizeric.shenaniganscore.core.display.TechniqueDisplayOps.spawnGlyph;
import static com.hizeric.shenaniganscore.core.display.TechniqueLighting.setLightPair;
import static com.hizeric.shenaniganscore.core.display.TechniqueLighting.setLightBurst;
import static com.hizeric.shenaniganscore.core.display.TechniqueDisplayOps.tumble;
import static com.hizeric.shenaniganscore.core.util.MathUtil.scaledCount;

/**
 * Every visual of Fuga, in one place — styled to sit next to the Malevolent Shrine.
 *
 * <p>This version builds the technique out of <b>several kinds of entity</b>, each doing the
 * job it is best at:</p>
 * <ul>
 *   <li><b>BlockDisplay</b> — the bow, the arrow, the wake ribbon, the fireball shells, the flame
 *       vortex, the flash disc, the scorch mark, the fire spires: solid, fullbright geometry.</li>
 *   <li><b>ItemDisplay</b> — <i>fire spirits</i>: fire-charge sprites that orbit the caster while he
 *       draws, spiral into the arrowhead, then ride the head as living flames; at impact they
 *       burst out as spinning ember orbs.</li>
 *   <li><b>TextDisplay</b> — <i>kanji</i>: a ring of 火 炎 焔 灼 燃 滅 circles him during the draw and is
 *       absorbed by the arrow; a giant 開 seal slams into the sky at release; 爆 灼 焔 fly out of the
 *       blast as burning debris.</li>
 *   <li><b>Client-side light blocks</b> — a real light source rides the arrow, lighting up the terrain
 *       it crosses, and the explosion pulses and fades. Nothing real is changed.</li>
 *   <li><b>Lightning effect</b> — the Malevolent Shrine version is announced by cosmetic lightning.</li>
 * </ul>
 *
 * <h3>The beats</h3>
 * <ol>
 *   <li><b>Windup</b> — flame bow unfolds, arrow draws back, fire spirits and kanji orbit and converge,
 *       a fire sigil burns at his feet, light gathers at the arrowhead.</li>
 *   <li><b>Release</b> — bow burns away, kanji are absorbed, 開 seal slams in, shock ring off the head.</li>
 *   <li><b>Flight</b> — layered arrow, three fire spirits circling the head, a solid flame ribbon wake,
 *       particle sparks, a travelling light.</li>
 *   <li><b>Impact</b> — flash disc and light pulse, nested fireball, radial burst, shockwave, a spinning
 *       flame vortex pillar, tumbling debris, ember orbs and kanji, burning auras on every victim,
 *       a scorch mark that fades on its own.</li>
 *   <li><b>Shrine version</b> — redder and bigger; lightning; the shrine's four mouths lash into the
 *       blast; blades cut the prepared ground; rippling fire spires.</li>
 * </ol>
 *
 * <p>Everything scales with {@code jujutsukaisen.techniques.fuga.quality} (0.4–2.0, default 1.0):
 * lower it to spawn fewer entities and particles on a weak server.</p>
 */
public final class Builder {

    private Builder() {}

    private static final int BOW_SEGMENTS = 7;

    private static final Particle.DustOptions EMBER = new Particle.DustOptions(Color.fromRGB(255, 110, 20), 1.1f);
    private static final Particle.DustOptions SPARK = new Particle.DustOptions(Color.fromRGB(255, 245, 225), 0.9f);
    private static final Particle.DustOptions BLOOD = new Particle.DustOptions(Color.fromRGB(150, 0, 12), 1.3f);
    private static final Particle.DustTransition FIRE_EDGE =
            new Particle.DustTransition(Color.fromRGB(255, 150, 30), Color.fromRGB(90, 0, 0), 1.8f);
    private static final Particle.DustTransition BLOOD_EDGE =
            new Particle.DustTransition(Color.fromRGB(190, 0, 10), Color.fromRGB(15, 0, 0), 1.8f);

    private static final Material[] DEBRIS_BLOCKS = {
            Material.BLACKSTONE, Material.MAGMA_BLOCK, Material.NETHERRACK,
            Material.COBBLED_DEEPSLATE, Material.BASALT
    };

    private static final String[] SEAL_KANJI = {"火", "炎", "焔", "灼", "燃", "滅"};
    private static final String[] DEBRIS_KANJI = {"爆", "灼", "焔", "滅", "炎", "燃"};
    private static final TextColor FLAME_TEXT = TextColor.color(255, 150, 50);
    private static final TextColor BLOOD_TEXT = TextColor.color(205, 12, 22);

    /** Pre-built light blocks, one per level 0..15. */

    // ─────────────────────────────────────────────────────────────────────────
    // Data carried by the projectile
    // ─────────────────────────────────────────────────────────────────────────

    /** One piece of the arrow. kind: 0 core, 1 glow, 2 aura, 3 head, 4 fletching vane. */
    private record Part(BlockDisplay display, int kind, float roll) {}

    /** A nested fireball cube. */
    private record Cube(BlockDisplay display, Quaternionf rot, float size) {}

    /** A chunk of ground flung out by the blast. */
    private record Debris(BlockDisplay display, Location apex, Location land, float size) {}

    /** An ember orb (item display) or burning kanji (text display) flung out by the blast. */
    private record Fling(Display display, Location apex, Location land, float size, float spin) {}

    /** A column of fire that erupts from the prepared ground (shrine version). */
    private record Spire(BlockDisplay core, BlockDisplay glow, int born, float height, Location base) {}

    /** One cube of the spinning flame vortex. */
    private record VCube(BlockDisplay display, Quaternionf rot, float size, double frac, double phase) {}

    /** A translucent burning aura that follows a victim. */
    private record Aura(LivingEntity victim, BlockDisplay display) {}

    /** An entity with a lifetime and an optional per-tick callback (fade, shrink...). */
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

    /** Everything Fuga spawns, plus the little state the animation needs. */
    public static final class Rig implements LightingRig {
        final List<Entity> entities = new ArrayList<>();

        @Override
        public List<Entity> entities() {
            return entities;
        }
        final List<Part> arrow = new ArrayList<>();
        final List<BlockDisplay> bowCore = new ArrayList<>();
        final List<BlockDisplay> bowGlow = new ArrayList<>();
        BlockDisplay stringA;
        BlockDisplay stringB;
        final List<ItemDisplay> spirits = new ArrayList<>();
        final List<TextDisplay> seal = new ArrayList<>();
        float scale = 1f;
        float thick = 1f;
        double fullLength;
        double length;
        Location tip;
        int bowFade = -1;

        final List<Cube> cubes = new ArrayList<>();
        final List<Debris> debris = new ArrayList<>();
        final List<Fling> flings = new ArrayList<>();
        final List<Spire> spires = new ArrayList<>();
        final List<VCube> vortex = new ArrayList<>();
        final List<Aura> auras = new ArrayList<>();
        final List<Timed> timed = new ArrayList<>();
        final List<Entity> scorch = new ArrayList<>();
        final Map<Location, Integer> lights = new HashMap<>();

        @Override
        public Map<Location, Integer> lights() {
            return lights;
        }
        double groundY;
        boolean hasGround;
        boolean exploded;
    }

    /** The arrow tip as of the last windup tick (where the arrow is released from). */
    static Location tipOf(Rig rig) {
        return rig.tip == null ? null : rig.tip.clone();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Small helpers
    // ─────────────────────────────────────────────────────────────────────────

    /** Entity count helper: base * quality, never below 1. */
    /** Two unit vectors perpendicular to dir (and to each other). */
    /** Spawns a fullbright block display (tiny) and tracks it for cleanup. */
    /** A fire-charge sprite that always faces the viewer. */
    private static ItemDisplay spawnSpirit(Rig rig, Location at, float scale) {
        ItemDisplay d = DisplayUtils.spawnItem(at, new ItemStack(Material.FIRE_CHARGE), VIEW, BRIGHT);
        d.setBillboard(Display.Billboard.CENTER);
        d.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.FIXED);
        d.setTransformation(scaleOnly(scale, 0f));
        rig.entities.add(d);
        return d;
    }

    /** A glowing kanji that always faces the viewer. */
    /** Moves a billboard display (item or text) smoothly and sets its size and roll. */
    /** Like moveDisplay but with longer glides (debris arcs). */
    /**
     * Puts a box of the given size (rotated by q) so that its CENTER sits on {@code center}.
     * interp = ticks the client takes to animate the size/rotation, tele = ticks for the move.
     */
    /** A thin glowing beam between two points (bow limbs, bowstring, wake ribbon). */
    /** Shrinks a block display toward its own centre over {@code ticks}. */
    // ─────────────────────────────────────────────────────────────────────────
    // Client-side light (a real light source that rides the arrow)
    // ─────────────────────────────────────────────────────────────────────────

    /** Only open air can hold a light block without changing what the player sees. */
    /** Makes the visible fake light blocks match {@code want}, sending only the differences. */
    /** The explosion's light: a centre plus six points around it, so the whole scene is lit. */
    // ─────────────────────────────────────────────────────────────────────────
    // WINDUP — the bow of flame, fire spirits and chant kanji
    // ─────────────────────────────────────────────────────────────────────────

    /** Builds the bow, arrow, fire spirits and kanji ring (tiny; {@link #tickWindup} unfolds them). */
    public static void spawnArrow(Projectile p) {
        Location at = p.caster().getEyeLocation();
        boolean boost = p.domainBoost();

        Rig rig = new Rig();
        rig.scale = boost ? 1.35f : 1.0f;
        rig.thick = (float) (0.8 + 0.2 * p.power());
        rig.fullLength = (2.0 + 0.3 * p.power()) * rig.scale;

        Material glowMat = boost ? Material.RED_STAINED_GLASS : Material.ORANGE_STAINED_GLASS;
        Material auraMat = boost ? Material.BLACK_STAINED_GLASS : Material.RED_STAINED_GLASS;

        rig.arrow.add(new Part(spawn(rig, at, Material.WHITE_CONCRETE, BRIGHT), 0, 0f));
        rig.arrow.add(new Part(spawn(rig, at, glowMat, BRIGHT), 1, 0f));
        rig.arrow.add(new Part(spawn(rig, at, auraMat, BRIGHT), 2, 0f));
        rig.arrow.add(new Part(spawn(rig, at, Material.MAGMA_BLOCK, BRIGHT), 3, 0f));
        rig.arrow.add(new Part(spawn(rig, at, Material.MAGMA_BLOCK, BRIGHT), 3, 45f));
        for (int i = 0; i < 3; i++) {
            rig.arrow.add(new Part(spawn(rig, at, Material.RED_CONCRETE, BRIGHT), 4, i * 60f));
        }

        for (int i = 0; i < BOW_SEGMENTS; i++) {
            rig.bowCore.add(spawn(rig, at, Material.MAGMA_BLOCK, BRIGHT));
            rig.bowGlow.add(spawn(rig, at, Material.RED_STAINED_GLASS, BRIGHT));
        }
        rig.stringA = spawn(rig, at, Material.WHITE_CONCRETE, BRIGHT);
        rig.stringB = spawn(rig, at, Material.WHITE_CONCRETE, BRIGHT);

        // Fire spirits: fire-charge sprites that will orbit, then ride the arrowhead.
        int spirits = Math.max(3, Math.min(10, scaledCount(8, p.quality())));
        for (int i = 0; i < spirits; i++) rig.spirits.add(spawnSpirit(rig, at, TINY));

        // Chant kanji: a ring that is absorbed by the arrow.
        int glyphs = Math.max(3, Math.min(8, scaledCount(6, p.quality())));
        TextColor color = boost ? BLOOD_TEXT : FLAME_TEXT;
        for (int i = 0; i < glyphs; i++) {
            rig.seal.add(spawnGlyph(rig, at, SEAL_KANJI[i % SEAL_KANJI.length], color, TINY));
        }

        p.setRig(rig);
    }

    /** Places every part of the arrow around a tip point. Set retransform when size/direction changed. */
    private static void poseArrow(Rig rig, Location tip, Vector dir, double len, boolean retransform) {
        Quaternionf base = facing(dir);
        float k = rig.scale * rig.thick;

        for (Part part : rig.arrow) {
            BlockDisplay d = part.display();
            if (d == null || !d.isValid()) continue;

            double back;
            float sx;
            float sy;
            float sz;
            switch (part.kind()) {
                case 0 -> { back = len / 2.0; sx = 0.10f * k; sy = sx; sz = (float) len; }
                case 1 -> { back = len / 2.0; sx = 0.30f * k; sy = sx; sz = (float) (len * 0.96); }
                case 2 -> { back = len * 0.30; sx = 0.62f * k; sy = sx; sz = (float) (len * 0.6); }
                case 3 -> { float hl = 0.75f * k; back = hl / 2.0; sx = 0.40f * k; sy = sx; sz = hl; }
                default -> { back = len - 0.35 * k; sx = 0.035f; sy = 0.62f * k; sz = 0.55f * k; }
            }

            Location c = tip.clone().subtract(dir.clone().multiply(back));
            if (retransform) {
                Quaternionf q = new Quaternionf(base);
                if (part.roll() != 0f) q.rotateZ((float) Math.toRadians(part.roll()));
                place(d, c, q, sx, sy, sz, 1, 1);
            } else {
                d.setTeleportDuration(1);
                d.teleport(c);
            }
        }
    }

    /** Called every windup tick. progress runs 0..1 over the draw. */
    public static void tickWindup(Projectile p, double progress) {
        Rig rig = p.rig();
        if (rig == null) return;

        Player c = p.caster();
        World w = c.getWorld();
        ThreadLocalRandom r = ThreadLocalRandom.current();
        boolean boost = p.domainBoost();
        float s = rig.scale;
        int tick = p.stateTicks();
        double e = MathUtil.smoothstep(progress);
        Location feet = c.getLocation();

        // Frame: where the caster is looking, and the bow held out to the right.
        Location eye = c.getEyeLocation();
        Vector fwd = eye.getDirection().normalize();
        Vector right = fwd.clone().crossProduct(new Vector(0, 1, 0));
        if (right.lengthSquared() < 1.0e-4) right = new Vector(1, 0, 0);
        right.normalize();
        Vector up = right.clone().crossProduct(fwd).normalize();

        Location riser = eye.clone()
                .add(fwd.clone().multiply(1.6))
                .add(right.clone().multiply(0.6))
                .subtract(up.clone().multiply(0.32));

        double grow = Math.max(0.05, e);
        double halfHeight = (1.0 + 0.16 * p.power()) * s * grow;
        double bulge = 0.55 * s * grow;

        // The bow: a forward-bulging arc of glowing limbs.
        Location[] pts = new Location[BOW_SEGMENTS + 1];
        for (int i = 0; i <= BOW_SEGMENTS; i++) {
            double t = -1.0 + 2.0 * i / BOW_SEGMENTS;
            pts[i] = riser.clone()
                    .add(up.clone().multiply(halfHeight * t))
                    .add(fwd.clone().multiply(bulge * (1.0 - t * t)));
        }
        for (int i = 0; i < BOW_SEGMENTS; i++) {
            line(rig.bowCore.get(i), pts[i], pts[i + 1], 0.15f * s);
            line(rig.bowGlow.get(i), pts[i], pts[i + 1], 0.38f * s);
        }

        // The arrow grows on the string and is drawn back.
        Location grip = riser.clone().add(fwd.clone().multiply(bulge));
        Location tip = grip.clone().add(fwd.clone().multiply(0.45 * s));
        double len = rig.fullLength * (0.12 + 0.88 * e);
        rig.length = len;
        rig.tip = tip.clone();
        poseArrow(rig, tip, fwd, len, true);

        Location nock = tip.clone().subtract(fwd.clone().multiply(len));
        line(rig.stringA, pts[0], nock, 0.05f);
        line(rig.stringB, pts[BOW_SEGMENTS], nock, 0.05f);

        // ── Fire spirits: orbit the caster, then spiral into the arrowhead ──
        double merge = MathUtil.smoothstep((progress - 0.55) / 0.45);
        int ns = rig.spirits.size();
        for (int i = 0; i < ns; i++) {
            double ang = progress * 7.0 + i * Math.PI * 2.0 / ns;
            double orbitR = (1.7 - 0.5 * e) * s;
            double h = 1.2 + 0.35 * Math.sin(progress * 9.0 + i);
            Location orbit = new Location(w,
                    feet.getX() + Math.cos(ang) * orbitR, feet.getY() + h, feet.getZ() + Math.sin(ang) * orbitR);
            float size = (float) ((0.35 + 0.55 * e) * (1.0 - 0.6 * merge) * s);
            moveDisplay(rig.spirits.get(i), lerp(orbit, tip, merge), size, (float) ang);
        }

        // ── Chant kanji: a wider ring that is drawn into the arrow ──
        double merge2 = MathUtil.smoothstep((progress - 0.75) / 0.25);
        int ng = rig.seal.size();
        for (int i = 0; i < ng; i++) {
            double ang = -progress * 5.0 + i * Math.PI * 2.0 / ng;
            double ringR = (2.4 - 0.4 * e) * s;
            double h = 1.0 + 0.25 * Math.sin(progress * 6.0 + i);
            Location ring = new Location(w,
                    feet.getX() + Math.cos(ang) * ringR, feet.getY() + h, feet.getZ() + Math.sin(ang) * ringR);
            float size = (float) (2.2 * Math.min(1.0, progress * 4.0) * (1.0 - 0.85 * merge2));
            moveDisplay(rig.seal.get(i), lerp(ring, tip, merge2), size, 0f);
        }

        // ── Light gathers at the arrowhead ──
        setLightPair(rig, w, tip, (int) (7 + 7 * e), null, 0);

        // ── Embers spiral into the arrowhead ──
        for (int arm = 0; arm < 2; arm++) {
            for (int j = 0; j < 3; j++) {
                double t = 0.3 + 0.35 * j;
                double ang = progress * 16.0 + arm * Math.PI + t * 3.0;
                double rad = (0.35 + 2.4 * (1.0 - progress)) * t * s;
                Location from = grip.clone()
                        .add(right.clone().multiply(Math.cos(ang) * rad))
                        .add(up.clone().multiply(Math.sin(ang) * rad))
                        .subtract(fwd.clone().multiply(0.6 * (1.0 - t)));
                Vector toward = tip.toVector().subtract(from.toVector());
                double dist = toward.length();
                if (dist < 0.05) continue;
                toward.multiply(1.0 / dist);
                w.spawnParticle(Particle.FLAME, from, 0, toward.getX(), toward.getY(), toward.getZ(), 0.22);
                w.spawnParticle(Particle.DUST, from, 1, 0.05, 0.05, 0.05, 0, boost ? BLOOD : EMBER);
                if (boost && r.nextInt(3) == 0) w.spawnParticle(Particle.ASH, from, 1, 0.1, 0.1, 0.1, 0);
            }
        }
        if (progress > 0.35) {
            w.spawnParticle(Particle.DUST, tip, 2, 0.12, 0.12, 0.12, 0, SPARK);
        }

        // ── The fire sigil under his feet ──
        double fy = feet.getY() + 0.08;
        double sigil = (1.0 + 0.2 * p.power()) * (0.6 + 1.4 * e) * s;
        int ringPts = 28;
        for (int i = 0; i < ringPts; i++) {
            double a = Math.PI * 2.0 * i / ringPts + progress * 3.0;
            double x = feet.getX() + Math.cos(a) * sigil;
            double z = feet.getZ() + Math.sin(a) * sigil;
            if (i % 4 == 0) {
                w.spawnParticle(Particle.FLAME, x, fy, z, 1, 0, 0.02, 0, 0.0);
            } else {
                w.spawnParticle(Particle.DUST, x, fy, z, 1, 0, 0, 0, 0, boost ? BLOOD : EMBER);
            }
        }
        for (int k = 0; k < 6; k++) {
            double a = -progress * 4.0 + k * Math.PI / 3.0;
            for (double f : new double[]{0.4, 0.7, 1.0}) {
                w.spawnParticle(Particle.DUST,
                        feet.getX() + Math.cos(a) * sigil * f, fy, feet.getZ() + Math.sin(a) * sigil * f,
                        1, 0, 0, 0, 0, SPARK);
            }
        }
        for (int i = 0; i < 14; i++) {
            double a = -Math.PI * 2.0 * i / 14.0 - progress * 5.0;
            w.spawnParticle(Particle.SMALL_FLAME,
                    feet.getX() + Math.cos(a) * sigil * 0.5, fy, feet.getZ() + Math.sin(a) * sigil * 0.5,
                    1, 0, 0.02, 0, 0.0);
        }

        // ── Sound: a furnace being opened ──
        if (tick == 1) {
            SoundUtils.play(feet, Sound.BLOCK_FURNACE_FIRE_CRACKLE, 1.6f, 0.6f);
            SoundUtils.play(feet, Sound.ITEM_FIRECHARGE_USE, 1.2f, 0.5f);
            if (boost) {
                SoundUtils.play(feet, Sound.BLOCK_BELL_RESONATE, 1.0f, 0.5f);
                SoundUtils.play(feet, Sound.ENTITY_WARDEN_HEARTBEAT, 1.5f, 0.6f);
            }
        } else if (tick % 4 == 0) {
            SoundUtils.play(tip, Sound.BLOCK_FIRE_AMBIENT, 1.2f, (float) (0.5 + 0.8 * progress));
        }
        if (tick == p.windupTicks() - 3) {
            SoundUtils.play(tip, Sound.ITEM_FIRECHARGE_USE, 1.5f, 1.4f);
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // RELEASE + FLIGHT
    // ─────────────────────────────────────────────────────────────────────────

    /** The moment of "Open": bow burns away, kanji are absorbed, the 開 seal slams in. */
    public static void release(Projectile p) {
        Rig rig = p.rig();
        if (rig == null) return;

        Location tip = p.currentLocation();
        Vector dir = p.direction();
        World w = tip.getWorld();
        if (w == null) return;
        boolean boost = p.domainBoost();

        poseArrow(rig, tip, dir, rig.length, true);

        rig.bowFade = 0;
        for (BlockDisplay d : rig.bowCore) shrink(d, 6);
        for (BlockDisplay d : rig.bowGlow) shrink(d, 6);
        shrink(rig.stringA, 4);
        shrink(rig.stringB, 4);

        // The chant kanji are absorbed; three fire spirits stay as the arrowhead's flames.
        for (TextDisplay td : rig.seal) discard(rig, td);
        rig.seal.clear();
        while (rig.spirits.size() > 3) {
            ItemDisplay spent = rig.spirits.remove(rig.spirits.size() - 1);
            w.spawnParticle(Particle.FLAME, spent.getLocation(), 6, 0.1, 0.1, 0.1, 0.05);
            discard(rig, spent);
        }

        // The giant 開 seal slams into the sky ahead of the arrow, then fades.
        final Location sealAt = tip.clone().add(dir.clone().multiply(7.0));
        final TextDisplay seal = spawnGlyph(rig, sealAt, "開", boost ? BLOOD_TEXT : FLAME_TEXT, 6f);
        final float sealScale = 18f * rig.scale;
        rig.timed.add(new Timed(seal, p.ticksLived(), 14, age -> {
            if (age == 1) {
                moveDisplay(seal, sealAt, sealScale, 0f);
            } else if (age >= 5) {
                seal.setTextOpacity((byte) Math.max(40, 255 - (age - 4) * 24));
            }
        }));

        // Shock ring blown off the arrowhead, plus a flash.
        w.spawnParticle(Particle.FLASH, tip, 1, 0, 0, 0, 0, Color.WHITE);
        Vector[] basis = basis(dir);
        int points = 36;

        for (int ring = 0; ring < 2; ring++) {
            double speed = ring == 0 ? 0.55 : 0.32;
            double phase = ring * Math.PI / points;

            for (int i = 0; i < points; i++) {
                double angle = Math.PI * 2.0 * i / points + phase;

                Vector out = basis[0].clone().multiply(Math.cos(angle))
                        .add(basis[1].clone().multiply(Math.sin(angle)));

                w.spawnParticle(
                        Particle.FLAME,
                        tip,
                        0,
                        out.getX(),
                        out.getY(),
                        out.getZ(),
                        speed
                );

                // Color accents are scattered around the ring.
                if (i % 2 == 0) {
                    w.spawnParticle(
                            Particle.DUST_COLOR_TRANSITION,
                            tip,
                            1,
                            0.1, 0.1, 0.1,
                            0,
                            boost ? BLOOD_EDGE : FIRE_EDGE
                    );
                }

                // A few faster sparks give the ring a sharper edge.
                if (i % 6 == 0) {
                    w.spawnParticle(
                            Particle.CRIT,
                            tip,
                            0,
                            out.getX(),
                            out.getY(),
                            out.getZ(),
                            speed * 1.3
                    );
                }
            }
        }

        w.spawnParticle(Particle.EXPLOSION, tip, 2, 0.2, 0.2, 0.2, 0);

        SoundUtils.play(tip, Sound.ENTITY_BLAZE_SHOOT, 2.0f, 0.5f);
        SoundUtils.play(tip, Sound.ENTITY_GENERIC_EXPLODE, 1.2f, 1.5f);
        SoundUtils.play(tip, Sound.ITEM_TRIDENT_THUNDER, 1.0f, 1.6f);
    }

    private static void removeBow(Rig rig) {
        for (BlockDisplay d : rig.bowCore) discard(rig, d);
        for (BlockDisplay d : rig.bowGlow) discard(rig, d);
        discard(rig, rig.stringA);
        discard(rig, rig.stringB);
        rig.bowCore.clear();
        rig.bowGlow.clear();
        rig.stringA = null;
        rig.stringB = null;
    }

    private static void discardArrow(Rig rig) {
        for (Part part : rig.arrow) discard(rig, part.display());
        rig.arrow.clear();
        for (ItemDisplay d : rig.spirits) discard(rig, d);
        rig.spirits.clear();
        removeBow(rig);
    }

    /** A solid flame ribbon segment that fades behind the arrow. */
    private static void ribbon(Rig rig, Location a, Location b, Material m, float thickness, int now) {
        final BlockDisplay d = spawn(rig, a, m, BRIGHT);
        line(d, a, b, thickness);
        rig.timed.add(new Timed(d, now, 10, age -> {
            if (age == 1) shrink(d, 8);
        }));
    }

    /** Moves the arrow along its path: ribbon wake, circling fire spirits, travelling light, sparks. */
    public static void tickFlight(Projectile p, Location from, Location to) {
        Rig rig = p.rig();
        World w = to.getWorld();
        if (rig == null || w == null) return;

        Vector dir = p.direction();
        boolean boost = p.domainBoost();
        ThreadLocalRandom r = ThreadLocalRandom.current();
        int tick = p.flightTicks();
        Vector[] bs = basis(dir);

        poseArrow(rig, to, dir, rig.length, false);

        if (rig.bowFade >= 0 && ++rig.bowFade >= 9) removeBow(rig);

        // Three fire spirits circle the arrowhead like a burning halo.
        for (int k = 0; k < rig.spirits.size(); k++) {
            double ang = tick * 0.9 + k * Math.PI * 2.0 / 3.0;
            Location pos = to.clone().subtract(dir.clone().multiply(0.25))
                    .add(bs[0].clone().multiply(Math.cos(ang) * 0.38))
                    .add(bs[1].clone().multiply(Math.sin(ang) * 0.38));
            float size = (float) ((0.9 + 0.25 * Math.sin(tick * 1.3 + k)) * rig.scale);
            moveDisplay(rig.spirits.get(k), pos, size, (float) ang);
        }

        // A real light source rides the arrow and lights the terrain it crosses.
        setLightPair(rig, w, to, 15, to.clone().subtract(dir.clone().multiply(4.0)), 11);

        // Trail from the previous tail to the current tail (never from the tip).
        Vector back = dir.clone().multiply(rig.length);
        Location a = from.clone().subtract(back);
        Location b = to.clone().subtract(back);
        Vector step = b.toVector().subtract(a.toVector());
        double stepLen = step.length();

        // The wake ribbon: a solid, fading ribbon of flame (orange glass) with a white-hot core.
        if (stepLen > 0.1) {
            float k = rig.scale * rig.thick;
            int now = p.ticksLived();
            ribbon(rig, a, b, boost ? Material.RED_STAINED_GLASS : Material.ORANGE_STAINED_GLASS, 0.55f * k, now);
            if (p.quality() >= 0.7) ribbon(rig, a, b, Material.WHITE_CONCRETE, 0.16f * k, now);
        }

        int n = Math.max(1, (int) Math.ceil(stepLen / 0.55));
        for (int i = 1; i <= n; i++) {
            Location pt = a.clone().add(step.clone().multiply(i / (double) n));
            w.spawnParticle(Particle.FLAME, pt, 1, 0.06, 0.06, 0.06, 0.01);
            if (i % 2 == 0) w.spawnParticle(Particle.SMALL_FLAME, pt, 1, 0.1, 0.1, 0.1, 0.02);
            if (i % 3 == 0) w.spawnParticle(Particle.LARGE_SMOKE, pt, 1, 0.1, 0.1, 0.1, 0.01);
            if (r.nextInt(4) == 0) w.spawnParticle(Particle.DUST, pt, 1, 0.15, 0.15, 0.15, 0, SPARK);
            if (boost && i % 2 == 0) w.spawnParticle(Particle.DUST, pt, 1, 0.15, 0.15, 0.15, 0, BLOOD);
            if (r.nextInt(8) == 0) w.spawnParticle(Particle.LAVA, pt, 1, 0.1, 0.1, 0.1, 0);

            // Occasional embers peel off sideways and drift behind the arrow.
            if (i % 3 == 0 && r.nextInt(3) == 0) {
                double sideAngle = r.nextDouble() * Math.PI * 2.0;

                Vector lateral = bs[0].clone().multiply(Math.cos(sideAngle))
                        .add(bs[1].clone().multiply(Math.sin(sideAngle)));

                Vector emberVelocity = dir.clone().multiply(-0.25)
                        .add(lateral.multiply(0.3))
                        .add(new Vector(0, 0.12 + r.nextDouble() * 0.2, 0));

                w.spawnParticle(
                        Particle.FLAME,
                        pt,
                        0,
                        emberVelocity.getX(),
                        emberVelocity.getY(),
                        emberVelocity.getZ(),
                        0.2
                );
            }
        }

        // Drifting ripples left in the air behind the arrow.
        if (tick % 3 == 0) {
            Location centre = to.clone().subtract(dir.clone().multiply(rig.length * 0.5));
            double rippleRadius = 0.9 + 0.12 * Math.sin(tick * 0.4);

            for (int i = 0; i < 14; i++) {
                double ang = Math.PI * 2.0 * i / 14.0 + tick * 0.18;

                Vector off = bs[0].clone().multiply(Math.cos(ang) * rippleRadius)
                        .add(bs[1].clone().multiply(Math.sin(ang) * rippleRadius));

                w.spawnParticle(
                        Particle.DUST_COLOR_TRANSITION,
                        centre.clone().add(off),
                        1,
                        0, 0, 0, 0,
                        boost ? BLOOD_EDGE : FIRE_EDGE
                );
            }
        }

        if (tick % 3 == 1) SoundUtils.play(to, Sound.BLOCK_FIRE_AMBIENT, 1.6f, 0.55f + r.nextFloat() * 0.2f);
        if (tick % 5 == 2) SoundUtils.play(to, Sound.ITEM_FIRECHARGE_USE, 0.8f, 0.6f);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // IMPACT
    // ─────────────────────────────────────────────────────────────────────────

    /** Frame zero of the detonation: flash, sound, fireball, debris, orbs, kanji, scorch, shrine lashes. */
    public static void createExplosion(Projectile p) {
        Location loc = p.impactLocation();
        World w = loc.getWorld();
        if (w == null) return;

        Rig rig = p.rig();
        if (rig == null) {
            rig = new Rig();
            p.setRig(rig);
        }
        double R = p.impactRadius();
        boolean boost = p.domainBoost();
        ThreadLocalRandom r = ThreadLocalRandom.current();

        discardArrow(rig);
        rig.exploded = true;

        // Find the ground under the blast (for the shockwave, debris and scorch).
        RayTraceResult down = w.rayTraceBlocks(loc, new Vector(0, -1, 0), 10.0, FluidCollisionMode.NEVER, true);
        rig.hasGround = down != null;
        rig.groundY = down != null ? down.getHitPosition().getY() : loc.getY() - 1.0;

        // ── Sound: a furnace door blown off its hinges ──
        SoundUtils.play(loc, Sound.ENTITY_GENERIC_EXPLODE, 4.0f, 0.55f);
        SoundUtils.play(loc, Sound.ENTITY_GENERIC_EXPLODE, 3.0f, 0.9f);
        SoundUtils.play(loc, Sound.ENTITY_WARDEN_SONIC_BOOM, 2.0f, 0.6f);
        SoundUtils.play(loc, Sound.ITEM_TRIDENT_THUNDER, 2.0f, 0.7f);
        SoundUtils.play(loc, Sound.ENTITY_DRAGON_FIREBALL_EXPLODE, 2.0f, 0.7f);
        if (boost) {
            SoundUtils.play(loc, Sound.ENTITY_ENDER_DRAGON_GROWL, 2.0f, 0.6f);
            SoundUtils.play(loc, Sound.BLOCK_BONE_BLOCK_BREAK, 2.0f, 0.45f);
        }

        // ── Flash + radial fire burst ──
        w.spawnParticle(Particle.FLASH, loc, 1, 0, 0, 0, 0, Color.WHITE);
        w.spawnParticle(Particle.EXPLOSION_EMITTER, loc, boost ? 3 : 1, R * 0.12, R * 0.08, R * 0.12, 0);
        w.spawnParticle(Particle.EXPLOSION, loc, 8, R * 0.3, R * 0.2, R * 0.3, 0);

        int bursts = (int) Math.max(40, Math.min(220, (30 + R * 10) * Math.max(0.6, p.quality())));
        double speedScale = Math.max(0.8, Math.min(1.6, R / 6.0));
        for (int i = 0; i < bursts; i++) {
            Vector v = randomUnit(r);
            double sp = (0.35 + r.nextDouble() * 0.9) * speedScale;
            w.spawnParticle(Particle.FLAME, loc, 0, v.getX(), v.getY(), v.getZ(), sp);
            if (i % 4 == 0) w.spawnParticle(Particle.LARGE_SMOKE, loc, 0, v.getX(), v.getY(), v.getZ(), sp * 0.6);
            if (i % 7 == 0) w.spawnParticle(Particle.LAVA, loc, 1, R * 0.2, R * 0.2, R * 0.2, 0);
            if (boost && i % 3 == 0) w.spawnParticle(Particle.DUST, loc, 0, v.getX(), v.getY(), v.getZ(), sp * 0.5, BLOOD);
        }

        spawnFireCrown(w, loc, R, boost, p.quality(), r);

        // ── Nested fireball: white core, orange/red glass glow (two rotated cubes per layer) ──
        double fireR = Math.min(R * 0.62, 9.0);
        float[] sizes = {(float) (fireR * 1.15), (float) (fireR * 1.7), (float) (fireR * 2.3)};
        Material[] mats = {
                Material.WHITE_CONCRETE,
                boost ? Material.RED_STAINED_GLASS : Material.ORANGE_STAINED_GLASS,
                boost ? Material.BLACK_STAINED_GLASS : Material.RED_STAINED_GLASS
        };
        for (int layer = 0; layer < 3; layer++) {
            for (int k = 0; k < 2; k++) {
                Quaternionf q = new Quaternionf().rotateXYZ(
                        (float) (r.nextDouble() * Math.PI),
                        (float) (r.nextDouble() * Math.PI),
                        (float) (r.nextDouble() * Math.PI));
                BlockDisplay d = spawn(rig, loc, mats[layer], BRIGHT);
                rig.cubes.add(new Cube(d, q, sizes[layer]));
            }
        }

        // ── Flash disc: a blinding white octagon that spreads across the ground and dies ──
        if (rig.hasGround && loc.getY() - rig.groundY < 6.0) {
            float S = (float) Math.min(R * 2.3, 22.0);
            Location c = new Location(w, loc.getX(), rig.groundY + 0.06, loc.getZ());
            for (int k = 0; k < 2; k++) {
                final BlockDisplay d = spawn(rig, c, Material.WHITE_CONCRETE, BRIGHT);
                place(d, c, new Quaternionf().rotateY((float) Math.toRadians(k * 45f)), S, 0.05f, S, 3, 0);
                rig.timed.add(new Timed(d, p.ticksLived(), 11, age -> {
                    if (age == 4) shrink(d, 5);
                }));
            }
        }

        // ── Debris: chunks of ground flung out on arcs ──
        int chunks = boost ? 30 : 16;
        chunks = Math.max(6, (int) Math.round(chunks * p.quality()));
        for (int i = 0; i < chunks; i++) {
            double ang = r.nextDouble() * Math.PI * 2.0;
            double dist = R * (0.45 + 0.65 * r.nextDouble());
            float size = 0.35f + r.nextFloat() * 0.5f;
            Location apex = loc.clone().add(Math.cos(ang) * dist * 0.55,
                    R * (0.35 + 0.55 * r.nextDouble()), Math.sin(ang) * dist * 0.55);
            Location land = new Location(w, loc.getX() + Math.cos(ang) * dist,
                    rig.hasGround ? rig.groundY + size / 2.0 : loc.getY() - 0.5,
                    loc.getZ() + Math.sin(ang) * dist);
            Material m = DEBRIS_BLOCKS[r.nextInt(DEBRIS_BLOCKS.length)];
            BlockDisplay d = spawn(rig, loc, m, m == Material.MAGMA_BLOCK ? BRIGHT : 7);
            rig.debris.add(new Debris(d, apex, land, size));
        }

        // ── Ember orbs (fire-charge sprites) and burning kanji, flung out like debris ──
        int orbs = Math.max(4, scaledCount(boost ? 14 : 10, p.quality()));
        for (int i = 0; i < orbs; i++) {
            rig.flings.add(makeFling(rig, loc, w, R, r, null, true));
        }
        int kanji = Math.max(3, Math.min(10, scaledCount(boost ? 8 : 6, p.quality())));
        for (int i = 0; i < kanji; i++) {
            rig.flings.add(makeFling(rig, loc, w, R, r, DEBRIS_KANJI[i % DEBRIS_KANJI.length], boost));
        }

        // ── Scorch mark: a charred octagon with a glowing ember heart (fades on its own) ──
        if (rig.hasGround && loc.getY() - rig.groundY < 5.0) {
            float S = (float) Math.min(R * 1.4, 14.0);
            Location base = new Location(w, loc.getX(), rig.groundY, loc.getZ());
            scorchSlab(rig, base, Material.BLACKSTONE, S, 0.02, 0f, 6);
            scorchSlab(rig, base, Material.BLACKSTONE, S, 0.026, 45f, 6);
            scorchSlab(rig, base, boost ? Material.CRIMSON_NYLIUM : Material.MAGMA_BLOCK,
                    S * 0.45f, 0.034, 22.5f, BRIGHT);
        }

        // ── The Malevolent Shrine joins in: lightning, the four mouths, cut ground ──
        if (boost) {
            try {
                Location strike = new Location(w, loc.getX(), rig.groundY, loc.getZ());
                w.strikeLightningEffect(strike);
            } catch (Throwable ignored) {
                // Purely cosmetic.
            }
            Session shrine = p.shrine();
            if (shrine != null) {
                try {
                    shrine.bite();
                    for (int m = 0; m < 4; m++) {
                        Location mouth = com.hizeric.shenaniganscore.origins.jujutsukaisen.domainexpansions.malevolentshrine.Builder.mouthLocation(shrine, m);
                        if (mouth != null) Slashes.lash(mouth, loc, 0.075f);
                    }
                    Slashes.debris(loc, 30);
                } catch (Throwable ignored) {
                    // The shrine's visuals are a bonus; Fuga still erupts without them.
                }
            }
        }
    }

    /** One ember orb (item display) or burning kanji (text display), launched toward a landing spot. */
    private static Fling makeFling(Rig rig, Location loc, World w, double R, ThreadLocalRandom r,
                                   String glyph, boolean redder) {
        double ang = r.nextDouble() * Math.PI * 2.0;
        double dist = R * (0.5 + 0.7 * r.nextDouble());
        float size = glyph == null ? 0.7f + r.nextFloat() * 0.5f : 1.6f + r.nextFloat() * 1.2f;
        Location apex = loc.clone().add(Math.cos(ang) * dist * 0.55,
                R * (0.45 + 0.6 * r.nextDouble()), Math.sin(ang) * dist * 0.55);
        Location land = new Location(w, loc.getX() + Math.cos(ang) * dist,
                (rig.hasGround ? rig.groundY : loc.getY() - 0.5) + 0.4, loc.getZ() + Math.sin(ang) * dist);
        Display d = glyph == null
                ? spawnSpirit(rig, loc, TINY)
                : spawnGlyph(rig, loc, glyph, redder ? BLOOD_TEXT : FLAME_TEXT, TINY);
        return new Fling(d, apex, land, size, (float) (r.nextDouble() * Math.PI * 2.0));
    }

    private static void scorchSlab(Rig rig, Location base, Material m, float size,
                                   double lift, float yawDeg, int brightness) {
        Location c = base.clone().add(0, lift, 0);
        BlockDisplay d = DisplayUtils.spawnBlock(c, m, VIEW, brightness);
        Quaternionf q = new Quaternionf().rotateY((float) Math.toRadians(yawDeg));
        place(d, c, q, size, 0.04f, size, 0, 0);
        rig.scorch.add(d);
    }

    /** Wraps a victim in a flickering translucent aura of flame (up to a handful per blast). */
    public static void burnAura(Projectile p, LivingEntity victim) {
        Rig rig = p.rig();
        if (rig == null || victim == null || rig.auras.size() >= 5) return;
        BlockDisplay d = spawn(rig, victim.getLocation(),
                p.domainBoost() ? Material.RED_STAINED_GLASS : Material.ORANGE_STAINED_GLASS, BRIGHT);
        rig.auras.add(new Aura(victim, d));
    }

    /** Animates the blast. t is 1 on the first impact tick. */
    public static void tickImpact(Projectile p, int t) {
        Rig rig = p.rig();
        if (rig == null) return;
        Location loc = p.impactLocation();
        World w = loc.getWorld();
        if (w == null) return;

        double R = p.impactRadius();
        boolean boost = p.domainBoost();
        ThreadLocalRandom r = ThreadLocalRandom.current();
        double gy = rig.hasGround ? rig.groundY : loc.getY();

        // ── Light: the whole scene is lit by the blast, then it dims ──
        if (t <= 22) {
            setLightBurst(rig, w, loc, R, t <= 6 ? 15 : Math.max(0, 15 - (t - 6)));
        } else if (t == 23) {
            TechniqueLighting.setLights(rig.lights, w, new HashMap<>());
        }

        // ── Fireball: swell, hold, then shrink away ──
        if (t == 1) {
            for (Cube c : rig.cubes) place(c.display(), loc, c.rot(), c.size(), c.size(), c.size(), 4, 0);
        } else if (t == 8) {
            for (Cube c : rig.cubes) place(c.display(), loc, c.rot(), TINY, TINY, TINY, 14, 0);
        } else if (t == 24) {
            for (Cube c : rig.cubes) discard(rig, c.display());
            rig.cubes.clear();
        }

        // ── Ground shockwave ──
        if (t <= 10) {
            double rr = R * 1.7 * (1.0 - Math.pow(1.0 - t / 10.0, 2.2));
            int pts = (int) Math.max(24, Math.min(90, rr * 6.0));
            for (int i = 0; i < pts; i++) {
                double a = Math.PI * 2.0 * i / pts;
                double cx = Math.cos(a), sz = Math.sin(a);
                double x = loc.getX() + cx * rr;
                double z = loc.getZ() + sz * rr;
                double y = gy + 0.2;
                w.spawnParticle(Particle.DUST_COLOR_TRANSITION, x, y, z, 1, 0, 0, 0, 0,
                        boost ? BLOOD_EDGE : FIRE_EDGE);
                if (i % 2 == 0) w.spawnParticle(Particle.FLAME, x, y, z, 0, cx, 0.25, sz, 0.18);
                if (i % 5 == 0) w.spawnParticle(Particle.LARGE_SMOKE, x, y + 0.3, z, 0, cx, 0.1, sz, 0.1);
            }
        }

        tickTrailingShockwave(w, loc, gy, R, boost, t);

        // ── The flame vortex: a spinning helix of fire that climbs and fades ──
        tickVortex(p, rig, loc, w, R, boost, gy, t, r);

        // ── Smoke cap above the vortex ──
        if (t >= 6 && t <= 22) {
            double height = Math.min(R * 2.2, R * 0.17 * t);
            double capR = R * 0.55 * Math.min(1.0, t / 10.0);
            for (int i = 0; i < 14; i++) {
                double a = Math.PI * 2.0 * i / 14.0 + t * 0.3;
                w.spawnParticle(Particle.LARGE_SMOKE,
                        loc.getX() + Math.cos(a) * capR, gy + height, loc.getZ() + Math.sin(a) * capR,
                        0, Math.cos(a), 0.15, Math.sin(a), 0.05);
            }
        }

        // ── Debris, ember orbs and kanji: up, over, down, gone ──
        if (t == 1) {
            for (Debris d : rig.debris) {
                place(d.display(), d.apex(), tumble(r), d.size(), d.size(), d.size(), 12, 6);
            }
            for (Fling f : rig.flings) flingTo(f.display(), f.apex(), f.size() * 1.3f, f.spin(), 6);
        } else if (t == 7) {
            for (Debris d : rig.debris) {
                place(d.display(), d.land(), tumble(r), d.size(), d.size(), d.size(), 12, 8);
            }
            for (Fling f : rig.flings) flingTo(f.display(), f.land(), f.size(), f.spin() + 2.5f, 8);
        } else if (t == 16) {
            for (Debris d : rig.debris) {
                place(d.display(), d.land(), new Quaternionf(), TINY, TINY, TINY, 4, 0);
            }
            for (Fling f : rig.flings) flingTo(f.display(), f.land(), TINY, f.spin(), 4);
        } else if (t == 21) {
            for (Debris d : rig.debris) discard(rig, d.display());
            for (Fling f : rig.flings) discard(rig, f.display());
            rig.debris.clear();
            rig.flings.clear();
        }

        // ── Burning auras follow every victim ──
        Iterator<Aura> it = rig.auras.iterator();
        while (it.hasNext()) {
            Aura a = it.next();
            LivingEntity v = a.victim();
            if (t >= 30 || !v.isValid() || v.isDead()) {
                discard(rig, a.display());
                it.remove();
                continue;
            }
            double h = v.getHeight();
            float width = (float) (v.getWidth() * 1.7 * (1.0 + 0.1 * Math.sin(t * 1.7)));
            Location centre = v.getLocation().add(0, h * 0.5, 0);
            place(a.display(), centre, new Quaternionf(), width, (float) (h * 1.2), width, 1, 1);
            if (t % 2 == 0) {
                w.spawnParticle(Particle.FLAME, centre, 4, width * 0.3, h * 0.4, width * 0.3, 0.02);
            }
        }

        // ── Lightning: the shrine's thunder rolls through the eruption ──
        if (boost && (t == 4 || t == 7)) {
            try {
                double a = r.nextDouble() * Math.PI * 2.0;
                double d = R * 0.8 * r.nextDouble();
                w.strikeLightningEffect(new Location(w, loc.getX() + Math.cos(a) * d, gy, loc.getZ() + Math.sin(a) * d));
            } catch (Throwable ignored) {
                // Purely cosmetic.
            }
        }

        // ── Shrine version: the prepared ground is cut and erupts ──
        if (boost) tickDomainEruption(rig, loc, w, R, t, gy, r);

        // ── Lingering embers, ash and tall smoke ──
        if (t > 6 && t % 2 == 0) {
            for (int k = 0; k < 4; k++) {
                double a = r.nextDouble() * Math.PI * 2.0;
                double d = r.nextDouble() * R * 0.7;
                Location spot = new Location(w, loc.getX() + Math.cos(a) * d, gy + 0.3, loc.getZ() + Math.sin(a) * d);
                w.spawnParticle(Particle.ASH, spot, 6, 0.6, 0.5, 0.6, 0.01);
                w.spawnParticle(Particle.CAMPFIRE_COSY_SMOKE, spot, 0, 0, 1, 0, 0.05 + r.nextDouble() * 0.05);
                w.spawnParticle(Particle.LAVA, spot, 1, 0.3, 0.3, 0.3, 0);
                if (boost) w.spawnParticle(Particle.FLAME, spot, 8, 0.8, 0.1, 0.8, 0.02);
            }
        }
    }

    /** Sends a fan of fire upward and outward from the impact point. */
    private static void spawnFireCrown(
            World w,
            Location loc,
            double radius,
            boolean boost,
            double quality,
            ThreadLocalRandom random
    ) {
        int rays = (int) Math.max(
                16,
                Math.min(64, Math.round((boost ? 5.0 : 3.5) * radius * quality))
        );

        double speedScale = Math.max(0.8, Math.min(1.6, radius / 6.0));

        for (int i = 0; i < rays; i++) {
            double angle = Math.PI * 2.0 * i / rays;
            double dx = Math.cos(angle);
            double dz = Math.sin(angle);

            // Emit two slightly different jets per direction for a fuller crown.
            for (int jet = 0; jet < 2; jet++) {
                double spread = jet == 0 ? 1.0 : 0.72;
                double upward = (0.5 + random.nextDouble() * 0.65) * spread;
                double outward = (0.35 + random.nextDouble() * 0.55) * spread;
                double speed = (0.65 + random.nextDouble() * 0.45) * speedScale;

                w.spawnParticle(
                        Particle.FLAME,
                        loc,
                        0,
                        dx * outward,
                        upward,
                        dz * outward,
                        speed
                );

                if ((i + jet) % 3 == 0) {
                    w.spawnParticle(
                            Particle.LARGE_SMOKE,
                            loc,
                            0,
                            dx * outward * 0.7,
                            upward * 0.6,
                            dz * outward * 0.7,
                            speed * 0.65
                    );
                }

                if (boost && (i + jet) % 4 == 0) {
                    w.spawnParticle(
                            Particle.DUST,
                            loc,
                            0,
                            dx * outward,
                            upward,
                            dz * outward,
                            speed * 0.5,
                            BLOOD
                    );
                }
            }
        }
    }

    /** A thinner second wave that follows the main ground shockwave. */
    private static void tickTrailingShockwave(
            World w,
            Location loc,
            double gy,
            double R,
            boolean boost,
            int t
    ) {
        int delay = 2;
        int age = t - delay;

        if (age < 1 || age > 10) return;

        double progress = age / 10.0;
        double radius = R * 1.45 * (1.0 - Math.pow(1.0 - progress, 2.0));
        int points = (int) Math.max(28, Math.min(72, radius * 5.0));

        Particle.DustTransition color = boost ? BLOOD_EDGE : FIRE_EDGE;

        for (int i = 0; i < points; i++) {
            double angle = Math.PI * 2.0 * i / points;
            double dx = Math.cos(angle);
            double dz = Math.sin(angle);
            double x = loc.getX() + dx * radius;
            double z = loc.getZ() + dz * radius;
            double y = gy + 0.12;

            w.spawnParticle(
                    Particle.DUST_COLOR_TRANSITION,
                    x, y, z,
                    1, 0, 0, 0, 0,
                    color
            );

            // Sparse outward-moving flames make the wave feel energetic.
            if (i % 3 == 0) {
                w.spawnParticle(
                        Particle.FLAME,
                        x, y, z,
                        0, dx * 0.06, 0.025, dz * 0.06, 0.01
                );
            }
        }
    }

    /** A helix of burning cubes that rises out of the impact, spinning, then burns out. */
    private static void tickVortex(Projectile p, Rig rig, Location loc, World w, double R,
                                   boolean boost, double gy, int t, ThreadLocalRandom r) {
        if (t == 2 && rig.vortex.isEmpty()) {
            int n = Math.max(5, scaledCount(boost ? 14 : 9, p.quality()));
            for (int i = 0; i < n; i++) {
                double frac = i / (double) Math.max(1, n - 1);
                Material m = (i % 2 == 0)
                        ? Material.MAGMA_BLOCK
                        : (boost ? Material.RED_STAINED_GLASS : Material.ORANGE_STAINED_GLASS);
                float size = (float) (R * 0.20 * (1.0 - 0.55 * frac) + 0.35);
                Quaternionf q = new Quaternionf().rotateXYZ(
                        (float) (r.nextDouble() * Math.PI),
                        (float) (r.nextDouble() * Math.PI),
                        (float) (r.nextDouble() * Math.PI));
                BlockDisplay d = spawn(rig, new Location(w, loc.getX(), gy, loc.getZ()), m, BRIGHT);
                rig.vortex.add(new VCube(d, q, size, frac, r.nextDouble() * Math.PI * 2.0));
            }
        }
        if (t < 3 || t > 26 || rig.vortex.isEmpty()) return;

        double grow = MathUtil.smoothstep((t - 2) / 8.0);
        double fade = t >= 18 ? Math.max(0.0, 1.0 - (t - 18) / 8.0) : 1.0;
        double height = R * 2.0 * grow;
        int index = 0;
        for (VCube v : rig.vortex) {
            double rad = R * 0.28 * (1.0 - 0.55 * v.frac()) * grow + 0.2;
            double ang = t * 0.55 + v.frac() * 5.0 + v.phase();

            Location pos = new Location(
                    w,
                    loc.getX() + Math.cos(ang) * rad,
                    gy + v.frac() * height + v.size() / 2.0,
                    loc.getZ() + Math.sin(ang) * rad
            );

            float sz = (float) (v.size() * fade * grow) + 0.02f;

            // Copy the stored rotation before adding this tick's spin.
            Quaternionf spin = new Quaternionf(v.rot())
                    .rotateY((float) (t * 0.09 + v.frac() * 0.7))
                    .rotateX((float) (t * 0.04));

            place(v.display(), pos, spin, sz, sz, sz, 1, 1);

            // Stagger particle emissions so every cube does not emit on the same tick.
            if ((t + index) % 2 == 0) {
                w.spawnParticle(
                        Particle.FLAME, pos,
                        1, 0.05, 0.05, 0.05, 0.01
                );

                if (index % 2 == 0) {
                    w.spawnParticle(
                            Particle.LARGE_SMOKE, pos,
                            0, 0, 0.025, 0, 0.015
                    );
                }
            }

            index++;
        }
        if (t % 2 == 0) {
            w.spawnParticle(Particle.FLAME, loc.getX(), gy + height * 0.5, loc.getZ(), 6, R * 0.15, height * 0.4, R * 0.15, 0.03);
        }
    }

    /** The Malevolent Shrine finisher: blades through the prepared ground, then rippling fire spires. */
    private static void tickDomainEruption(Rig rig, Location loc, World w, double R, int t,
                                           double gy, ThreadLocalRandom r) {
        try {
            if (t <= 12) {
                Slashes.flurry(loc, 2, R * 0.5);
            }
            if (t == 2 || t == 5 || t == 8) {
                Location spot = loc.clone().add((r.nextDouble() - 0.5) * R, 0.4, (r.nextDouble() - 0.5) * R);
                Slashes.cross(spot, 14.0f);
            }
        } catch (Throwable ignored) {
            // Blades are decoration.
        }

        // Spires ripple outward from the centre.
        if (t >= 3 && t <= 16) {
            for (int i = 0; i < 2; i++) {
                double a = r.nextDouble() * Math.PI * 2.0;
                double rad = R * (0.25 + 0.75 * (t - 3) / 13.0) * (0.85 + 0.3 * r.nextDouble());
                Location base = new Location(w, loc.getX() + Math.cos(a) * rad, gy, loc.getZ() + Math.sin(a) * rad);
                float h = (float) (2.5 + r.nextDouble() * R * 0.5);
                BlockDisplay core = spawn(rig, base, Material.MAGMA_BLOCK, BRIGHT);
                BlockDisplay glow = spawn(rig, base, Material.ORANGE_STAINED_GLASS, BRIGHT);
                rig.spires.add(new Spire(core, glow, t, h, base));
            }
        }

        List<Spire> done = new ArrayList<>();

        for (Spire s : rig.spires) {
            int age = t - s.born();

            if (age >= 10) {
                discard(rig, s.core());
                discard(rig, s.glow());
                done.add(s);
                continue;
            }

            if (age < 1) continue;

            // Smoothly grow during the first four ticks.
            double rise = Math.min(1.0, age / 4.0);
            rise = MathUtil.smoothstep(rise); // smoothstep

            // Begin collapsing after the peak.
            double collapse = age <= 6
                    ? 1.0
                    : Math.max(0.08, 1.0 - (age - 6) / 4.0);

            double heightNow = Math.max(0.12, s.height() * rise * collapse);
            double widthScale = Math.max(0.08, rise * collapse);

            Location centre = s.base().clone().add(0, heightNow / 2.0, 0);

            float coreWidth = (float) (0.38 * widthScale);
            float glowWidth = (float) (0.95 * widthScale);

            // Update every tick with a one-tick interpolation to avoid lagging behind.
            place(
                    s.core(), centre, new Quaternionf(),
                    coreWidth, (float) heightNow, coreWidth,
                    1, 0
            );

            place(
                    s.glow(), centre, new Quaternionf(),
                    glowWidth, (float) (heightNow * 0.92), glowWidth,
                    1, 0
            );

            if (age == 1) {
                w.spawnParticle(
                        Particle.FLAME,
                        s.base().clone().add(0, Math.min(heightNow, 1.0), 0),
                        10, 0.3, 0.3, 0.3, 0.04
                );
            }

            if (age == 5) {
                w.spawnParticle(
                        Particle.LARGE_SMOKE,
                        s.base().clone().add(0, heightNow, 0),
                        5, 0.25, 0.2, 0.25, 0.02
                );
            }
        }

        rig.spires.removeAll(done);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Ambient upkeep (called every tick by the projectile)
    // ─────────────────────────────────────────────────────────────────────────

    /** Ages timed entities (ribbon, seal, disc): shrinks, fades and removes them on schedule. */
    public static void tickAmbient(Projectile p) {
        Rig rig = p.rig();
        if (rig == null || rig.timed.isEmpty()) return;
        int now = p.ticksLived();

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

    // ─────────────────────────────────────────────────────────────────────────
    // Cleanup
    // ─────────────────────────────────────────────────────────────────────────

    /** Removes every display and light. The scorch mark is handed to its own timer and fades out later. */
    public static void cleanup(Projectile p) {
        Rig rig = p.rig();
        if (rig == null) return;

        // Put the real light back (the fake light blocks vanish for everyone).
        try {
            World w = p.currentLocation() != null && p.currentLocation().getWorld() != null
                    ? p.currentLocation().getWorld() : p.caster().getWorld();
            if (w != null && !rig.lights.isEmpty()) TechniqueLighting.setLights(rig.lights, w, new HashMap<>());
        } catch (Throwable ignored) {
            // Lighting is cosmetic; never block the cleanup.
        }

        for (Entity e : new ArrayList<>(rig.entities)) {
            if (e != null && e.isValid()) e.remove();
        }
        rig.entities.clear();
        rig.arrow.clear();
        rig.spirits.clear();
        rig.seal.clear();
        rig.cubes.clear();
        rig.debris.clear();
        rig.flings.clear();
        rig.spires.clear();
        rig.vortex.clear();
        rig.auras.clear();
        rig.timed.clear();

        List<Entity> scorch = new ArrayList<>(rig.scorch);
        rig.scorch.clear();
        if (!scorch.isEmpty()) {
            int delay = p.scorchTicks();
            try {
                ShenanigansCore plugin = ShenanigansCore.getInstance();
                Bukkit.getScheduler().runTaskLater(plugin, () -> {
                    for (Entity e : scorch) {
                        if (e instanceof BlockDisplay d && d.isValid()) shrink(d, 20);
                    }
                }, delay);
                Bukkit.getScheduler().runTaskLater(plugin, () -> {
                    for (Entity e : scorch) {
                        if (e != null && e.isValid()) e.remove();
                    }
                }, delay + 22L);
            } catch (Throwable t) {
                // Plugin is shutting down: the scorch goes with it.
                for (Entity e : scorch) {
                    if (e != null && e.isValid()) e.remove();
                }
            }
        }
        p.setRig(null);
    }
}