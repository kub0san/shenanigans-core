package com.hizeric.shenaniganscore.origins.jujutsukaisen.techniques.worldslash;

import com.hizeric.shenaniganscore.core.display.TechniqueLighting;
import com.hizeric.shenaniganscore.ShenanigansCore;
import com.hizeric.shenaniganscore.core.display.DisplayUtils;
import com.hizeric.shenaniganscore.core.sound.SoundUtils;
import com.hizeric.shenaniganscore.origins.jujutsukaisen.domainexpansions.malevolentshrine.Builder;
import com.hizeric.shenaniganscore.origins.jujutsukaisen.domainexpansions.malevolentshrine.OpenBarrier;
import com.hizeric.shenaniganscore.origins.jujutsukaisen.domainexpansions.malevolentshrine.Session;
import com.hizeric.shenaniganscore.origins.jujutsukaisen.domainexpansions.malevolentshrine.Slashes;
import com.hizeric.shenaniganscore.origins.jujutsukaisen.techniques.worldslash.WorldSlashCast.CutPlane;
import com.hizeric.shenaniganscore.origins.jujutsukaisen.techniques.worldslash.WorldSlashCast.Hit;
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
import org.bukkit.block.data.type.Light;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
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
import com.hizeric.shenaniganscore.core.display.LightingRig;
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
import static com.hizeric.shenaniganscore.core.display.TechniqueDisplayOps.discard;
import static com.hizeric.shenaniganscore.core.display.TechniqueDisplayOps.lerp;
import static com.hizeric.shenaniganscore.core.display.TechniqueDisplayOps.moveDisplay;
import static com.hizeric.shenaniganscore.core.display.TechniqueDisplayOps.spawn;
import static com.hizeric.shenaniganscore.core.display.TechniqueDisplayOps.beamBox;
import static com.hizeric.shenaniganscore.core.util.MathUtil.smoothstep;
import static com.hizeric.shenaniganscore.core.display.TechniqueDisplayOps.spawnGlyph;
import static com.hizeric.shenaniganscore.core.util.BlockKey.pack;
import static com.hizeric.shenaniganscore.core.display.TechniqueLighting.setLightPath;

/**
 * Every visual of the World Cutting Slash, in the Malevolent Shrine's palette: bone, blood red, black, and
 * white-hot cut lines.
 *
 * <ul>
 *   <li><b>Chant (BlockDisplay + TextDisplay)</b> — two bone hands form the roof-shaped hand sign (the Binding
 *       Vow's visible half). Four kanji (世 界 断 斬) orbit him and are drawn into the hands. The sky tints
 *       crimson for everyone nearby, so the whole area knows what is coming.</li>
 *   <li><b>The cut (BlockDisplay, stretched)</b> — each plane is a faint black sheet with a hairline seam and
 *       red edge lines. The seam <i>unzips</i> along the plane by client interpolation (one spawn and one
 *       transformation per 40-block chunk, with a per-chunk delay), and a thin slit is hidden in the terrain
 *       behind it. Planes unzip one after another.</li>
 *   <li><b>The dodge window</b> — the seam hangs in the air, flickering; players touching a plane get a warning.</li>
 *   <li><b>The sever (BlockDisplay)</b> — the seam opens into a black gap with white edges that slide apart; every
 *       victim gets a flash of the cut sheet through their body, blade slashes and a spray of blood.</li>
 *   <li><b>Sky</b> — a flash at the cut and at the sever (skipped when the Malevolent Shrine owns the sky).</li>
 * </ul>
 *
 * <p>Nothing real is ever changed: displays, particles and short-lived client-side blocks only.</p>
 */
public final class WorldSlashBuilder {

    private WorldSlashBuilder() {}

    /** Chunk length: the largest stretch of a plane that one display covers. */
    private static final double CHUNK = 40.0;
    private static final double SKY_RANGE_SQ = 60.0 * 60.0;
    private static final float GAP_WIDTH = 0.7f;
    private static final double EDGE_OFFSET = 0.35;

    private static final Particle.DustOptions BLOOD = new Particle.DustOptions(Color.fromRGB(150, 0, 12), 1.3f);
    private static final Particle.DustOptions BONE = new Particle.DustOptions(Color.fromRGB(235, 225, 205), 1.0f);
    private static final Particle.DustTransition SEAM_EDGE =
            new Particle.DustTransition(Color.fromRGB(255, 255, 255), Color.fromRGB(200, 20, 30), 1.2f);
    private static final BlockData BLOOD_BLOCK = Material.REDSTONE_BLOCK.createBlockData();
    private static final TextColor BLOOD_TEXT = TextColor.color(190, 15, 25);

    private static final String[] CHANT_KANJI = {"世", "界", "断", "斬"};


    // ─────────────────────────────────────────────────────────────────────────
    // Data carried by the cast
    // ─────────────────────────────────────────────────────────────────────────

    /** One chunk of one layer of a plane. */
    private record Part(BlockDisplay display, float sx, float sy, float len, double offset) {}

    /** The gap that opens along one chunk at the sever. */
    private record GapPart(BlockDisplay gap, BlockDisplay edgeA, BlockDisplay edgeB,
                           Location anchor, float sy, float len) {}

    /** The visuals of one cut plane. */
    private static final class PlaneVis {
        final CutPlane plane;
        final Quaternionf q;
        final int armTick;
        final double speed;
        final List<Part> parts = new ArrayList<>();
        final List<GapPart> gaps = new ArrayList<>();
        double slitA;

        PlaneVis(CutPlane plane, Quaternionf q, int armTick, double speed) {
            this.plane = plane;
            this.q = q;
            this.armTick = armTick;
            this.speed = speed;
            this.slitA = plane.a0;
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

    /** Everything the cast spawns, plus the little state the animation needs. */
    public static final class Rig implements LightingRig {
        final List<Entity> entities = new ArrayList<>();

        @Override
        public List<Entity> entities() {
            return entities;
        }
        final List<BlockDisplay> handL = new ArrayList<>();
        final List<BlockDisplay> handR = new ArrayList<>();
        final List<TextDisplay> glyphs = new ArrayList<>();
        final List<PlaneVis> planes = new ArrayList<>();
        final List<Timed> timed = new ArrayList<>();

        final Set<Long> erased = new HashSet<>();
        final List<Location> erasedLocs = new ArrayList<>();
        final Set<UUID> viewers = new HashSet<>();

        final List<Location> lightPts = new ArrayList<>();
        final Map<Location, Integer> lights = new HashMap<>();

        @Override
        public Map<Location, Integer> lights() {
            return lights;
        }

        final Map<UUID, Long> skyFrom = new HashMap<>();
        final Set<UUID> skyTouched = new HashSet<>();
        boolean skyEnabled;
        boolean gapsArmed;
        Location muzzle;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Small helpers
    // ─────────────────────────────────────────────────────────────────────────

    /** facing(a) rolled so that the local Y axis points along b (b must be perpendicular to a). */
    private static Quaternionf orient(Vector a, Vector b) {
        Quaternionf q = facing(a);
        Vector3f qy = q.transform(new Vector3f(0, 1, 0));
        Vector ey = new Vector(qy.x, qy.y, qy.z);
        double sin = a.dot(ey.clone().crossProduct(b));
        double cos = ey.dot(b);
        q.rotateZ((float) Math.atan2(sin, cos));
        return q;
    }

    /** Spawns a fullbright block display (tiny) and tracks it for cleanup. */
    /** A glowing kanji that always faces the viewer. */
    /** Box of the given size (rotated by q) whose CENTER sits on {@code center}. */
    /**
     * A box that starts at the display's location, is centred on its other two axes, and grows FORWARD along its
     * local +Z. Changing {@code sz} with an interpolation duration makes it visibly stretch; the delay decides when
     * the stretch starts. No teleport is involved.
     */
    // ─────────────────────────────────────────────────────────────────────────
    // Client-side light, sky and slit
    // ─────────────────────────────────────────────────────────────────────────
    /** Briefly forces the sky to a time of day for everyone nearby (skipped if the shrine owns the sky). */
    private static void flashSky(Rig rig, World w, Location centre, long time) {
        if (!rig.skyEnabled) return;
        for (Player pl : w.getPlayers()) {
            if (pl.getLocation().distanceSquared(centre) <= SKY_RANGE_SQ) {
                rig.skyTouched.add(pl.getUniqueId());
                pl.setPlayerTime(time, false);
            }
        }
    }

    private static void resetSky(Rig rig) {
        for (UUID id : rig.skyTouched) {
            Player pl = Bukkit.getPlayer(id);
            if (pl == null || !pl.isOnline()) continue;
            try {
                OpenBarrier.resetSky(pl);
            } catch (Throwable ignored) {
                pl.resetPlayerTime();
            }
        }
        rig.skyTouched.clear();
    }
    /** Hides a one-block-thick slit along the part of a plane between fromA and toA. */
    private static void eraseSlice(Rig rig, World w, CutPlane p, double fromA, double toA, List<Location> out) {
        int minY = w.getMinHeight();
        int maxY = w.getMaxHeight();
        for (double ta = fromA; ta <= toA; ta += 0.5) {
            for (double tb = -p.bHalf; tb <= p.bHalf; tb += 0.5) {
                Vector v = p.at(ta, tb);
                int x = (int) Math.floor(v.getX());
                int y = (int) Math.floor(v.getY());
                int z = (int) Math.floor(v.getZ());
                if (y < minY || y >= maxY) continue;
                long key = pack(x, y, z);
                if (rig.erased.contains(key)) continue;
                if (!w.isChunkLoaded(x >> 4, z >> 4)) continue;

                rig.erased.add(key);
                Block block = w.getBlockAt(x, y, z);
                if (block.getType().isAir()) continue;
                if (rig.erasedLocs.size() >= WorldSlashCast.MAX_SLIT_BLOCKS) return;

                Location loc = new Location(w, x, y, z);
                rig.erasedLocs.add(loc);
                out.add(loc);
            }
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // CHANT — the hand sign and the Binding Vow
    // ─────────────────────────────────────────────────────────────────────────

    /** Builds the bone hands and the kanji (tiny; {@link #tickChant} unfolds them). */
    public static void spawnChant(WorldSlashCast c) {
        Location at = c.caster().getEyeLocation();
        World w = at.getWorld();

        Rig rig = new Rig();
        rig.skyEnabled = !c.isEnhanced();

        for (int side = 0; side < 2; side++) {
            List<BlockDisplay> hand = side == 0 ? rig.handL : rig.handR;
            hand.add(spawn(rig, at, Material.BONE_BLOCK, 9));
            for (int f = 0; f < 4; f++) hand.add(spawn(rig, at, Material.BONE_BLOCK, 9));
        }
        for (String glyph : CHANT_KANJI) {
            rig.glyphs.add(spawnGlyph(rig, at, glyph, BLOOD_TEXT, TINY));
        }

        // Remember everyone's time of day so the crimson tint can fade in from it (and be undone).
        if (rig.skyEnabled && w != null) {
            for (Player pl : w.getPlayers()) {
                if (pl.getLocation().distanceSquared(at) <= SKY_RANGE_SQ) {
                    rig.skyFrom.put(pl.getUniqueId(), pl.getPlayerTime());
                }
            }
        }
        c.setRig(rig);
    }

    /** Poses both hands in the roof-shaped sign: palms facing, fingertips leaning together. */
    private static void poseHands(Rig rig, Location m, Vector fwd, double lean, double gap) {
        Quaternionf base = facing(fwd);
        for (int side = -1; side <= 1; side += 2) {
            List<BlockDisplay> hand = side < 0 ? rig.handL : rig.handR;
            if (hand.size() < 5) continue;

            Quaternionf q = new Quaternionf(base).rotateZ((float) (side * lean));
            Vector3f off = base.transform(new Vector3f((float) (side * gap), 0f, 0f));
            Location hc = m.clone().add(off.x, off.y, off.z);

            handPart(hand.get(0), hc, q, 0f, 0.22f, 0f, 0.06f, 0.44f, 0.5f);
            for (int f = 0; f < 4; f++) {
                handPart(hand.get(1 + f), hc, q, 0f, 0.44f + 0.15f, -0.15f + 0.10f * f, 0.05f, 0.3f, 0.06f);
            }
        }
    }

    private static void handPart(BlockDisplay d, Location hc, Quaternionf q,
                                 float lx, float ly, float lz, float sx, float sy, float sz) {
        Vector3f o = q.transform(new Vector3f(lx, ly, lz));
        place(d, hc.clone().add(o.x, o.y, o.z), q, sx, sy, sz, 1, 1);
    }

    /** Called every chant tick. progress runs 0..1. */
    public static void tickChant(WorldSlashCast c, double progress) {
        Rig rig = c.rig();
        if (rig == null) return;

        Player p = c.caster();
        World w = p.getWorld();
        ThreadLocalRandom r = ThreadLocalRandom.current();
        int tick = c.stateTicks();
        double e = smoothstep(progress);

        Location eye = p.getEyeLocation();
        Vector fwd = eye.getDirection().normalize();
        Vector right = fwd.clone().crossProduct(new Vector(0, 1, 0));
        if (right.lengthSquared() < 1.0e-4) right = new Vector(1, 0, 0);
        right.normalize();
        Vector up = right.clone().crossProduct(fwd).normalize();

        Location m = eye.clone().add(fwd.clone().multiply(1.4)).subtract(up.clone().multiply(0.45));
        rig.muzzle = m.clone();
        poseHands(rig, m, fwd, 0.17 + 0.32 * e, 0.32 - 0.14 * e);

        // The four kanji orbit him, then are drawn into the hands.
        double merge = smoothstep((progress - 0.7) / 0.3);
        Location ringC = eye.clone().subtract(up.clone().multiply(0.3));
        int n = rig.glyphs.size();
        for (int i = 0; i < n; i++) {
            double ang = progress * 6.0 + i * Math.PI * 2.0 / n;
            double ringR = 1.9 * (1.0 - 0.15 * merge);
            Location ring = ringC.clone()
                    .add(right.clone().multiply(Math.cos(ang) * ringR))
                    .add(fwd.clone().multiply(Math.sin(ang) * ringR))
                    .add(up.clone().multiply(Math.sin(progress * 5.0 + i) * 0.25));
            float size = (float) (1.8 * Math.min(1.0, progress * 4.0) * (1.0 - 0.8 * merge));
            moveDisplay(rig.glyphs.get(i), lerp(ring, m, merge), size, 0f);
        }

        // The sky bleeds crimson for everyone nearby: a warning anyone can see.
        if (rig.skyEnabled) {
            for (Map.Entry<UUID, Long> entry : rig.skyFrom.entrySet()) {
                Player pl = Bukkit.getPlayer(entry.getKey());
                if (pl == null || !pl.isOnline()) continue;
                rig.skyTouched.add(entry.getKey());
                try {
                    OpenBarrier.fadeIn(pl, entry.getValue(), progress);
                } catch (Throwable ignored) {
                    // Sky tint is cosmetic.
                }
            }
        }

        // Blood and ash drawn into the hands.
        for (int i = 0; i < 3; i++) {
            Vector v = randomUnit(r).multiply((1.0 - e) * 1.2 + 0.2);
            w.spawnParticle(Particle.DUST, m.clone().add(v), 1, 0, 0, 0, 0, i % 2 == 0 ? BLOOD : BONE);
        }
        w.spawnParticle(Particle.SQUID_INK, m, 1, 0.15, 0.15, 0.15, 0.01);

        // HUD: the Binding Vow.
        if (tick % 2 == 0) {
            int filled = (int) Math.round(progress * 10.0);
            p.sendActionBar(MiniMessage.miniMessage().deserialize(
                    "<dark_red>世界ヲ断ツ <red>" + "▮".repeat(filled) + "<dark_gray>" + "▯".repeat(10 - filled)));
        }

        if (tick == 1) {
            SoundUtils.play(m, Sound.BLOCK_BELL_RESONATE, 1.5f, 0.5f);
            SoundUtils.play(m, Sound.ENTITY_WARDEN_HEARTBEAT, 1.6f, 0.5f);
        } else if (tick % 4 == 0) {
            SoundUtils.play(m, Sound.BLOCK_BONE_BLOCK_BREAK, 1.2f, (float) (0.5 + 0.6 * progress));
        }
        if (tick == WorldSlashCast.CHANT_TICKS) {
            SoundUtils.play(m, Sound.BLOCK_BELL_USE, 2.0f, 0.6f);
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // CUT — seams unzip along the planes
    // ─────────────────────────────────────────────────────────────────────────

    private static void addPart(Rig rig, PlaneVis vis, Location anchor, Material m, int brightness,
                                float sx, float sy, float len, double offset) {
        BlockDisplay d = DisplayUtils.spawnBlock(anchor, m, VIEW, brightness);
        rig.entities.add(d);
        beamBox(d, vis.q, sx, sy, TINY, 0, 0);
        vis.parts.add(new Part(d, sx, sy, len, offset));
    }

    /** Locks in the visuals of every plane, dissolves the hands, and begins the first unzip. */
    public static void beginCut(WorldSlashCast c) {
        Rig rig = c.rig();
        if (rig == null) return;
        World w = c.caster().getWorld();
        Location centre = c.centre();

        // The hands and kanji have done their work.
        for (BlockDisplay d : rig.handL) shrink(d, 3);
        for (BlockDisplay d : rig.handR) shrink(d, 3);
        for (TextDisplay td : rig.glyphs) discard(rig, td);
        rig.glyphs.clear();
        final int now = c.ticksLived();
        for (BlockDisplay d : rig.handL) rig.timed.add(new Timed(d, now, 4, null));
        for (BlockDisplay d : rig.handR) rig.timed.add(new Timed(d, now, 4, null));

        // The planes: a faint black sheet, a hairline seam (core + glow) and two red edge lines per chunk.
        List<CutPlane> planes = c.planes();
        for (int i = 0; i < planes.size(); i++) {
            CutPlane p = planes.get(i);
            PlaneVis vis = new PlaneVis(p, orient(p.a, p.b), 2 + i * WorldSlashCast.STAGGER_TICKS,
                    (p.a1 - p.a0) / WorldSlashCast.UNZIP_TICKS);
            double total = p.a1 - p.a0;
            int chunks = (int) Math.ceil(total / CHUNK);
            for (int k = 0; k < chunks; k++) {
                double off = k * CHUNK;
                float len = (float) Math.min(CHUNK, total - off);
                Location anchor = p.locAt(p.a0 + off, 0.0);
                addPart(rig, vis, anchor, Material.BLACK_STAINED_GLASS, 4, 0.02f, (float) (p.bHalf * 2.0), len, off);
                addPart(rig, vis, anchor, Material.RED_STAINED_GLASS, BRIGHT, 0.18f, 0.18f, len, off);
                addPart(rig, vis, anchor, Material.WHITE_CONCRETE, BRIGHT, 0.035f, 0.035f, len, off);
                addPart(rig, vis, p.locAt(p.a0 + off, p.bHalf), Material.RED_CONCRETE, BRIGHT, 0.07f, 0.07f, len, off);
                addPart(rig, vis, p.locAt(p.a0 + off, -p.bHalf), Material.RED_CONCRETE, BRIGHT, 0.07f, 0.07f, len, off);
            }
            rig.planes.add(vis);

            // Light points along the seam (capped).
            for (double ta = p.a0 + 4.0; ta < p.a1 && rig.lightPts.size() < 24; ta += 8.0) {
                rig.lightPts.add(p.locAt(ta, 0.0));
            }
        }

        // A giant 断 flashes ahead of the caster.
        Player caster = c.caster();
        Vector look = caster.getEyeLocation().getDirection().normalize();
        final Location sealAt = caster.getEyeLocation().add(look.multiply(6.0)).add(0, 2.0, 0);
        final TextDisplay seal = spawnGlyph(rig, sealAt, "断", BLOOD_TEXT, TINY);
        rig.timed.add(new Timed(seal, now, 14, age -> {
            if (age == 1) {
                moveDisplay(seal, sealAt, 14f, 0f);
            } else if (age >= 4) {
                seal.setTextOpacity((byte) Math.max(40, 255 - (age - 3) * 22));
            }
        }));

        // The sky flashes white as space is cut.
        resetSky(rig);
        flashSky(rig, w, centre, 6000L);

        // In the shrine, the four mouths join the cut.
        Session shrine = c.shrine();
        if (shrine != null) {
            try {
                shrine.bite();
                for (int m = 0; m < 4; m++) {
                    Location mouth = Builder.mouthLocation(shrine, m);
                    if (mouth != null) Slashes.lash(mouth, centre, 0.075f);
                }
                Slashes.debris(centre, 30);
            } catch (Throwable ignored) {
                // The shrine's visuals are a bonus.
            }
        }

        SoundUtils.play(centre, Sound.ITEM_TRIDENT_THUNDER, 2.0f, 1.8f);
        SoundUtils.play(centre, Sound.BLOCK_GLASS_BREAK, 1.6f, 0.5f);
        SoundUtils.play(centre, Sound.ENTITY_WARDEN_SONIC_BOOM, 1.4f, 1.6f);
    }

    /** Starts the stretch of one plane: each chunk begins growing when the seam reaches it. */
    private static void arm(PlaneVis vis) {
        for (Part part : vis.parts) {
            int delay = (int) Math.floor(part.offset() / vis.speed);
            int dur = Math.max(1, (int) Math.ceil(part.len() / vis.speed));
            beamBox(part.display(), vis.q, part.sx(), part.sy(), part.len(), dur, delay);
        }
    }

    /** One tick of the unzip. t is 1 on the first CUT tick. */
    public static void tickCut(WorldSlashCast c, int t) {
        Rig rig = c.rig();
        if (rig == null) return;
        World w = c.caster().getWorld();
        ThreadLocalRandom r = ThreadLocalRandom.current();
        List<Location> newlySlit = new ArrayList<>();

        for (PlaneVis vis : rig.planes) {
            CutPlane p = vis.plane;
            if (t == vis.armTick) {
                arm(vis);
                SoundUtils.play(p.locAt(p.a0, 0.0), Sound.ENTITY_PLAYER_ATTACK_SWEEP, 1.4f, 0.6f);
            }
            if (t >= vis.armTick && t <= vis.armTick + WorldSlashCast.UNZIP_TICKS) {
                double head = Math.min(p.a1, p.a0 + vis.speed * (t - vis.armTick + 1));
                Location hp = p.locAt(head, 0.0);

                // The slash itself, riding the head of the seam, and sparks behind it.
                w.spawnParticle(Particle.SWEEP_ATTACK, hp, 1, 0.2, 0.2, 0.2, 0);
                w.spawnParticle(Particle.ELECTRIC_SPARK, hp, 6, 0.3, 0.6, 0.3, 0.3);
                w.spawnParticle(Particle.DUST_COLOR_TRANSITION, hp, 6, 0.2, 0.8, 0.2, 0, SEAM_EDGE);
                for (int i = 0; i < 3; i++) {
                    double back = p.a0 + (head - p.a0) * r.nextDouble();
                    w.spawnParticle(Particle.SWEEP_ATTACK, p.locAt(back, (r.nextDouble() - 0.5) * 3.0), 1, 0, 0, 0, 0);
                }

                // The terrain behind the head is split by a thin slit.
                eraseSlice(rig, w, p, vis.slitA, head, newlySlit);
                vis.slitA = head;
            }
        }
        TechniqueEraseOps.sendErase(w, c.centre(), newlySlit, rig.viewers);

        setLightPath(rig, w, rig.lightPts, 12);
        if (t == 3) resetSky(rig);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // WAIT — the dodge window
    // ─────────────────────────────────────────────────────────────────────────

    public static void tickWait(WorldSlashCast c, int t) {
        Rig rig = c.rig();
        if (rig == null) return;
        World w = c.caster().getWorld();
        ThreadLocalRandom r = ThreadLocalRandom.current();

        // The seam hangs in the air, shimmering.
        for (PlaneVis vis : rig.planes) {
            CutPlane p = vis.plane;
            for (int i = 0; i < 2; i++) {
                double ta = p.a0 + (p.a1 - p.a0) * r.nextDouble();
                double tb = (r.nextDouble() - 0.5) * p.bHalf;
                w.spawnParticle(Particle.ELECTRIC_SPARK, p.locAt(ta, tb), 1, 0.1, 0.1, 0.1, 0.1);
            }
            w.spawnParticle(Particle.DUST, p.locAt(p.a0 + (p.a1 - p.a0) * r.nextDouble(), 0.0), 1, 0.1, 0.2, 0.1, 0, BLOOD);
        }

        // Anyone standing on a plane is told to move.
        if (t % 4 == 1) {
            for (Player pl : w.getPlayers()) {
                if (pl.equals(c.caster()) || pl.getLocation().distanceSquared(c.centre()) > 90.0 * 90.0) continue;
                Vector chest = pl.getLocation().add(0, pl.getHeight() * 0.5, 0).toVector();
                for (PlaneVis vis : rig.planes) {
                    if (vis.plane.touches(chest, pl.getWidth() / 2.0)) {
                        pl.sendActionBar(MiniMessage.miniMessage().deserialize(
                                "<dark_red><bold>⚠ <red>The space around you has been cut — move!"));
                        break;
                    }
                }
            }
        }

        // A ticking that rises toward the sever.
        if (t % 3 == 0) {
            SoundUtils.play(c.centre(), Sound.BLOCK_COMPARATOR_CLICK, 1.4f,
                    (float) (0.5 + 1.0 * t / (double) WorldSlashCast.WAIT_TICKS));
        }
        setLightPath(rig, w, rig.lightPts, 12);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // SEVER — the gap opens and everything still on a plane is cut
    // ─────────────────────────────────────────────────────────────────────────

    /** The seam opens into a black gap; every victim gets the severance visuals. */
    public static void beginSever(WorldSlashCast c, List<Hit> hits) {
        Rig rig = c.rig();
        if (rig == null) return;
        World w = c.caster().getWorld();
        Location centre = c.centre();

        // Gap parts for every chunk of every plane: tiny now, armed next tick (so the client animates them).
        for (PlaneVis vis : rig.planes) {
            CutPlane p = vis.plane;
            double total = p.a1 - p.a0;
            int chunks = (int) Math.ceil(total / CHUNK);
            for (int k = 0; k < chunks; k++) {
                double off = k * CHUNK;
                float len = (float) Math.min(CHUNK, total - off);
                Location anchor = p.locAt(p.a0 + off, 0.0);
                float sy = (float) (p.bHalf * 2.0);

                BlockDisplay gap = DisplayUtils.spawnBlock(anchor, Material.BLACK_CONCRETE, VIEW, 0);
                BlockDisplay edgeA = DisplayUtils.spawnBlock(anchor, Material.WHITE_CONCRETE, VIEW, BRIGHT);
                BlockDisplay edgeB = DisplayUtils.spawnBlock(anchor, Material.WHITE_CONCRETE, VIEW, BRIGHT);
                rig.entities.add(gap);
                rig.entities.add(edgeA);
                rig.entities.add(edgeB);
                beamBox(gap, vis.q, TINY, sy, len, 0, 0);
                beamBox(edgeA, vis.q, TINY, sy, len, 0, 0);
                beamBox(edgeB, vis.q, TINY, sy, len, 0, 0);
                vis.gaps.add(new GapPart(gap, edgeA, edgeB, anchor, sy, len));
            }
        }
        rig.gapsArmed = false;

        // The sky goes black for a moment as the two halves part.
        flashSky(rig, w, centre, 18000L);

        // Each victim: a flash of the cut sheet through their body, blade slashes, blood.
        for (Hit h : hits) onHit(c, rig, h);

        // The shrine's jaws close on the cut.
        Session shrine = c.shrine();
        if (shrine != null) {
            try {
                shrine.bite();
            } catch (Throwable ignored) {
                // Decoration.
            }
        }

        SoundUtils.play(centre, Sound.ENTITY_GENERIC_EXPLODE, 2.0f, 0.5f);
        SoundUtils.play(centre, Sound.BLOCK_BONE_BLOCK_BREAK, 2.0f, 0.45f);
        SoundUtils.play(centre, Sound.ENTITY_ENDER_DRAGON_GROWL, 1.5f, 0.5f);
        SoundUtils.play(centre, Sound.ENTITY_WARDEN_SONIC_BOOM, 2.0f, 0.7f);
    }

    /** What a victim sees and feels when the cut reaches them. */
    private static void onHit(WorldSlashCast c, Rig rig, Hit h) {
        LivingEntity victim = h.entity();
        World w = victim.getWorld();
        Location chest = victim.getLocation().add(0, victim.getHeight() * 0.55, 0);
        List<CutPlane> planes = c.planes();
        CutPlane p = planes.get(Math.max(0, Math.min(planes.size() - 1, h.plane())));
        final int now = c.ticksLived();

        // The cut sheet flashes through their body.
        Quaternionf q = orient(p.a, p.b);
        final BlockDisplay sheet = spawn(rig, chest, Material.WHITE_CONCRETE, BRIGHT);
        place(sheet, chest, q, 0.03f, 2.6f, 2.6f, 0, 0);
        final BlockDisplay glow = spawn(rig, chest, Material.RED_STAINED_GLASS, BRIGHT);
        place(glow, chest, q, 0.18f, 2.8f, 2.8f, 0, 0);
        rig.timed.add(new Timed(sheet, now, 10, age -> {
            if (age == 4) shrink(sheet, 5);
        }));
        rig.timed.add(new Timed(glow, now, 10, age -> {
            if (age == 4) shrink(glow, 5);
        }));

        // The shrine's own blades.
        try {
            Slashes.cross(chest, 9.0f);
            Slashes.flurry(chest, 2, 1.5);
        } catch (Throwable ignored) {
            // Decoration.
        }

        w.spawnParticle(Particle.DAMAGE_INDICATOR, chest, 8, 0.3, 0.4, 0.3, 0.3);
        w.spawnParticle(Particle.SWEEP_ATTACK, chest, 2, 0.3, 0.3, 0.3, 0);
        w.spawnParticle(Particle.DUST, chest, 16, 0.3, 0.5, 0.3, 0, BLOOD);
        w.spawnParticle(Particle.BLOCK, chest, 10, 0.3, 0.4, 0.3, 0.1, BLOOD_BLOCK);
        w.spawnParticle(Particle.FLASH, chest, 1, 0, 0, 0, 0, Color.WHITE);
        SoundUtils.play(chest, Sound.ENTITY_PLAYER_ATTACK_CRIT, 1.6f, 0.6f);
        SoundUtils.play(chest, Sound.ENTITY_WARDEN_ATTACK_IMPACT, 1.4f, 0.8f);
    }

    /** One tick of the open gap. t is 1 on the first SEVER tick. */
    public static void tickSever(WorldSlashCast c, int t) {
        Rig rig = c.rig();
        if (rig == null) return;
        World w = c.caster().getWorld();
        ThreadLocalRandom r = ThreadLocalRandom.current();

        // The gap grows and its white edges slide apart (armed one tick after spawning).
        if (t == 2 && !rig.gapsArmed) {
            rig.gapsArmed = true;
            for (PlaneVis vis : rig.planes) {
                Vector n = vis.plane.n;
                for (GapPart gp : vis.gaps) {
                    beamBox(gp.gap(), vis.q, GAP_WIDTH, gp.sy(), gp.len(), 5, 0);

                    beamBox(gp.edgeA(), vis.q, 0.04f, gp.sy(), gp.len(), 3, 0);
                    gp.edgeA().setTeleportDuration(5);
                    gp.edgeA().teleport(gp.anchor().clone().add(n.clone().multiply(EDGE_OFFSET)));

                    beamBox(gp.edgeB(), vis.q, 0.04f, gp.sy(), gp.len(), 3, 0);
                    gp.edgeB().setTeleportDuration(5);
                    gp.edgeB().teleport(gp.anchor().clone().subtract(n.clone().multiply(EDGE_OFFSET)));
                }
            }
        }
        if (t == 3) resetSky(rig);

        // Light leaks out of the gap; void shards drift in it.
        for (PlaneVis vis : rig.planes) {
            CutPlane p = vis.plane;
            for (int i = 0; i < 3; i++) {
                double ta = p.a0 + (p.a1 - p.a0) * r.nextDouble();
                double tb = (r.nextDouble() - 0.5) * p.bHalf * 1.4;
                Location at = p.locAt(ta, tb);
                w.spawnParticle(Particle.REVERSE_PORTAL, at, 2, 0.15, 0.15, 0.15, 0.1);
                if (i == 0) w.spawnParticle(Particle.END_ROD, at, 1, 0.1, 0.1, 0.1, 0.02);
            }
        }

        setLightPath(rig, w, rig.lightPts, Math.max(0, 12 - t / 2));
    }

    // ─────────────────────────────────────────────────────────────────────────
    // CLOSE — the gap closes
    // ─────────────────────────────────────────────────────────────────────────

    /** The gap and seams close and fade; the slit in the terrain returns later. */
    public static void tickClose(WorldSlashCast c, int t) {
        Rig rig = c.rig();
        if (rig == null) return;
        World w = c.caster().getWorld();

        if (t == 1) {
            for (PlaneVis vis : rig.planes) {
                for (GapPart gp : vis.gaps) {
                    beamBox(gp.gap(), vis.q, 0.02f, gp.sy(), gp.len(), 8, 0);

                    beamBox(gp.edgeA(), vis.q, 0.02f, gp.sy(), gp.len(), 8, 0);
                    gp.edgeA().setTeleportDuration(8);
                    gp.edgeA().teleport(gp.anchor());
                    beamBox(gp.edgeB(), vis.q, 0.02f, gp.sy(), gp.len(), 8, 0);
                    gp.edgeB().setTeleportDuration(8);
                    gp.edgeB().teleport(gp.anchor());
                }
                for (Part part : vis.parts) {
                    beamBox(part.display(), vis.q, 0.02f, 0.02f, part.len(), 8, 0);
                }
            }
            SoundUtils.play(c.centre(), Sound.BLOCK_BEACON_DEACTIVATE, 1.4f, 0.7f);
        }
        if (t == WorldSlashCast.CLOSE_TICKS - 1) {
            for (PlaneVis vis : rig.planes) {
                for (GapPart gp : vis.gaps) {
                    discard(rig, gp.gap());
                    discard(rig, gp.edgeA());
                    discard(rig, gp.edgeB());
                }
                vis.gaps.clear();
                for (Part part : vis.parts) discard(rig, part.display());
                vis.parts.clear();
            }
        }
        setLightPath(rig, w, rig.lightPts, Math.max(0, 8 - t));
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Ambient upkeep + cleanup
    // ─────────────────────────────────────────────────────────────────────────

    /** Ages timed entities (seal, hands, severance flashes): shrinks and removes them on schedule. */
    public static void tickAmbient(WorldSlashCast c) {
        Rig rig = c.rig();
        if (rig == null || rig.timed.isEmpty()) return;
        int now = c.ticksLived();

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

    /** Removes every display and light, resets the sky, and schedules the slit in the terrain to close. */
    public static void cleanup(WorldSlashCast c) {
        Rig rig = c.rig();
        if (rig == null) return;

        try {
            resetSky(rig);
            if (!rig.lights.isEmpty()) TechniqueLighting.setLights(rig.lights, c.caster().getWorld(), new HashMap<>());
        } catch (Throwable ignored) {
            // Sky and light are cosmetic; never block the cleanup.
        }

        for (Entity e : new ArrayList<>(rig.entities)) {
            if (e != null && e.isValid()) e.remove();
        }
        rig.entities.clear();
        rig.handL.clear();
        rig.handR.clear();
        rig.glyphs.clear();
        rig.planes.clear();
        rig.timed.clear();

        // The terrain comes back after a while (the real world was never touched).
        if (!rig.erasedLocs.isEmpty()) {
            final List<Location> locs = new ArrayList<>(rig.erasedLocs);
            final Set<UUID> viewers = new HashSet<>(rig.viewers);
            rig.erasedLocs.clear();
            try {
                ShenanigansCore.runTaskLater(
                        () -> TechniqueEraseOps.restore(locs, viewers), WorldSlashCast.SLIT_RESTORE_TICKS);
            } catch (Throwable t) {
                TechniqueEraseOps.restore(locs, viewers);
            }
        }
        c.setRig(null);
    }

}
