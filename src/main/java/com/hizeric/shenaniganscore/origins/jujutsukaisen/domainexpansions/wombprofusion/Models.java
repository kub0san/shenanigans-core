package com.hizeric.shenaniganscore.origins.jujutsukaisen.domainexpansions.wombprofusion;

import com.hizeric.shenaniganscore.core.model.Parts;
import com.hizeric.shenaniganscore.core.model.Parts.EyeType;
import com.hizeric.shenaniganscore.core.model.Parts.HeadType;
import com.hizeric.shenaniganscore.core.model.Rig;
import org.bukkit.Location;
import org.bukkit.Material;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.Random;

/**
 * The grotesque catalog of Womb Profusion, built from vanilla parts only.
 *
 * <p>Every builder adds parts to a {@link Rig} in the rig's <b>local</b> coordinates (blocks, Y up),
 * so the same model can be placed, scaled and animated by its rig. Randomness comes from the caller's
 * {@link Random}, so a seeded tower is reproducible.</p>
 *
 * <ul>
 *   <li>{@link #lump}: a rotated flesh cube; overlapping lumps merge into one irregular mass.</li>
 *   <li>{@link #face}: a mob skull pressed into the mass, tilted and rolled.</li>
 *   <li>{@link #eye}: a loose eyeball that blinks.</li>
 *   <li>{@link #stitch}: a thin black seam with cross-stitches.</li>
 *   <li>{@link #corpse}: a headless body, embedded or hanging.</li>
 *   <li>{@link #crouching}: a crouched humanoid with a head.</li>
 *   <li>{@link #hand}, {@link #branch}: reaching limbs of flesh.</li>
 * </ul>
 */
final class Models {

    static final float VIEW = 6.0f;

    private static final Vector3f Z_AXIS = new Vector3f(0, 0, 1);
    private static final Vector3f BLOCK_PIVOT = new Vector3f(0.5f, 0.5f, 0.5f);
    private static final Vector3f ITEM_PIVOT = new Vector3f(0, 0, 0);

    /** Pale to raw: the colours of a body that has been rearranged. */
    private static final Material[] FLESH = {
            Material.PINK_TERRACOTTA, Material.PINK_TERRACOTTA, Material.PINK_TERRACOTTA,
            Material.WHITE_TERRACOTTA, Material.WHITE_TERRACOTTA,
            Material.MUSHROOM_STEM, Material.MUSHROOM_STEM,
            Material.BONE_BLOCK,
            Material.RED_TERRACOTTA, Material.RED_TERRACOTTA,
            Material.NETHER_WART_BLOCK,
            Material.TERRACOTTA
    };
    private static final Material[] SKIN = {
            Material.PINK_TERRACOTTA, Material.TERRACOTTA, Material.LIGHT_GRAY_TERRACOTTA
    };

    private static final HeadType[] HEADS = {
            HeadType.PIGLIN, HeadType.PIGLIN, HeadType.PIGLIN,
            HeadType.ZOMBIE, HeadType.ZOMBIE, HeadType.ZOMBIE,
            HeadType.SKELETON, HeadType.SKELETON,
            HeadType.WITHER, HeadType.CREEPER
    };
    private static final EyeType[] EYES = {EyeType.SPIDER, EyeType.SPIDER, EyeType.FERMENTED, EyeType.ENDER};

    private Models() {
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    static Material flesh(Random r) {
        return FLESH[r.nextInt(FLESH.length)];
    }

    static HeadType headType(Random r) {
        return HEADS[r.nextInt(HEADS.length)];
    }

    private static Location at(Rig rig) {
        return rig.origin();
    }

    /** A rotation that turns local +Z onto {@code dir}. */
    static Quaternionf pointZ(Vector3f dir) {
        Vector3f d = new Vector3f(dir);
        if (d.lengthSquared() < 1.0e-8f) return new Quaternionf();
        return new Quaternionf().rotationTo(Z_AXIS, d.normalize());
    }

    private static Quaternionf euler(float yawDeg, float pitchDeg, float rollDeg) {
        return new Quaternionf()
                .rotateY((float) Math.toRadians(yawDeg))
                .rotateX((float) Math.toRadians(pitchDeg))
                .rotateZ((float) Math.toRadians(rollDeg));
    }

    private static Rig.Part block(Rig rig, Material m, Vector3f pos, Quaternionf rot, Vector3f size, int brightness) {
        return rig.add(Parts.block(at(rig), m, brightness, VIEW), pos, rot, size, BLOCK_PIVOT, false);
    }

    /** A thin bar from {@code a} to {@code b}. */
    private static Rig.Part bar(Rig rig, Material m, Vector3f a, Vector3f b, float thickness, int brightness) {
        Vector3f dir = new Vector3f(b).sub(a);
        float len = dir.length();
        Vector3f mid = new Vector3f(a).add(b).mul(0.5f);
        return block(rig, m, mid, pointZ(dir), new Vector3f(thickness, thickness, Math.max(0.02f, len)), brightness);
    }

    // ------------------------------------------------------------------
    // Mass
    // ------------------------------------------------------------------

    /** A flesh cube turned to a random orientation: lumps overlap into one lumpy mass. */
    static Rig.Part lump(Rig rig, Random r, Vector3f pos, float size, Material m) {
        Quaternionf rot = euler(r.nextFloat() * 360f, r.nextFloat() * 50f - 25f, r.nextFloat() * 50f - 25f);
        float sy = size * (0.75f + r.nextFloat() * 0.5f);
        return block(rig, m, pos, rot, new Vector3f(size, sy, size * (0.8f + r.nextFloat() * 0.4f)), 9);
    }

    /**
     * A face pressed into the mass. {@code outX/outZ} is the horizontal direction it looks. The
     * skull is tilted and rolled so no two sit the same, and sized in world blocks.
     */
    static Rig.Part face(Rig rig, Random r, Vector3f pos, float outX, float outZ, float sizeBlocks, HeadType type) {
        float facing = (float) Math.atan2(outX, outZ);
        Quaternionf rot = new Quaternionf()
                .rotateY(facing + (float) Math.toRadians(Parts.headYawOffset()))
                .rotateX((float) Math.toRadians(r.nextFloat() * 40f - 20f))
                .rotateZ((float) Math.toRadians(r.nextFloat() * 40f - 20f));
        float scale = sizeBlocks / Parts.headSize();
        return rig.add(Parts.head(at(rig), type, 11, VIEW), pos, rot, new Vector3f(scale),
                new Vector3f(0, Parts.headCenterY(), 0), false);
    }

    /** A loose eyeball. It always faces the viewer, so it only needs a position and a size. */
    static Rig.Part eye(Rig rig, Random r, Vector3f pos, float size) {
        EyeType type = EYES[r.nextInt(EYES.length)];
        return rig.add(Parts.eye(at(rig), type, 15, VIEW), pos, new Quaternionf(),
                new Vector3f(size), ITEM_PIVOT, true);
    }

    /** A black seam from {@code a} to {@code b} with cross-stitches along it. */
    static void stitch(Rig rig, Random r, Vector3f a, Vector3f b, Vector3f outward) {
        bar(rig, Material.BLACK_CONCRETE, a, b, 0.05f, 7);

        Vector3f dir = new Vector3f(b).sub(a);
        float len = dir.length();
        if (len < 0.3f) return;
        dir.normalize();
        Vector3f across = new Vector3f(dir).cross(new Vector3f(outward).normalize());
        if (across.lengthSquared() < 1.0e-6f) across.set(0, 1, 0);
        across.normalize().mul(0.17f);

        int crosses = 3;
        for (int i = 0; i < crosses; i++) {
            float t = (i + 1f) / (crosses + 1f) + (r.nextFloat() - 0.5f) * 0.06f;
            Vector3f c = new Vector3f(a).lerp(b, t);
            bar(rig, Material.BLACK_CONCRETE, new Vector3f(c).sub(across), new Vector3f(c).add(across), 0.04f, 7);
        }
    }

    // ------------------------------------------------------------------
    // Bodies
    // ------------------------------------------------------------------

    /** A body part placed relative to a posed frame (position, rotation, scale). */
    private record Frame(Vector3f pos, Quaternionf rot, float scale) {
        Vector3f at(float x, float y, float z) {
            return new Vector3f(x, y, z).mul(scale).rotate(rot).add(pos);
        }

        Quaternionf with(Quaternionf local) {
            return new Quaternionf(rot).mul(local);
        }
    }

    private static void limb(Rig rig, Frame f, Material m, float cx, float cy, float cz,
                             float sx, float sy, float sz, Quaternionf local, int brightness) {
        block(rig, m, f.at(cx, cy, cz), f.with(local),
                new Vector3f(sx, sy, sz).mul(f.scale()), brightness);
    }

    /**
     * A headless corpse: torso, a raw neck stump, two arms and two legs, about 1.8 blocks tall at
     * scale 1. {@code pos} is the point between its feet. The arms and legs flop to random angles.
     */
    static void corpse(Rig rig, Random r, Vector3f pos, Quaternionf pose, float scale) {
        Frame f = new Frame(pos, pose, scale);
        Material skin = SKIN[r.nextInt(SKIN.length)];

        limb(rig, f, Material.GRAY_TERRACOTTA, 0, 1.05f, 0, 0.5f, 0.7f, 0.25f, new Quaternionf(), 9);
        limb(rig, f, Material.RED_CONCRETE, 0, 1.45f, 0, 0.2f, 0.12f, 0.2f, new Quaternionf(), 9);
        for (int side = -1; side <= 1; side += 2) {
            float armAngle = side * (10f + r.nextFloat() * 55f);
            limb(rig, f, skin, side * 0.38f, 1.05f, 0, 0.2f, 0.7f, 0.2f, euler(0, 0, armAngle), 9);
            float legAngle = side * (r.nextFloat() * 14f);
            limb(rig, f, skin, side * 0.14f, 0.35f, 0, 0.22f, 0.7f, 0.22f, euler(0, 0, legAngle), 9);
        }
    }

    /**
     * A crouched humanoid with a head, hugging its knees. {@code pos} is the point between its feet;
     * it faces local +Z.
     */
    static void crouching(Rig rig, Random r, Vector3f pos, Quaternionf pose, float scale) {
        Frame f = new Frame(pos, pose, scale);
        Material skin = SKIN[r.nextInt(SKIN.length)];

        limb(rig, f, Material.GRAY_TERRACOTTA, 0, 0.62f, 0.05f, 0.5f, 0.6f, 0.25f, euler(0, 55, 0), 9);
        for (int side = -1; side <= 1; side += 2) {
            limb(rig, f, skin, side * 0.14f, 0.28f, 0.1f, 0.22f, 0.5f, 0.22f, euler(0, -70, 0), 9);
            limb(rig, f, skin, side * 0.14f, 0.2f, 0.32f, 0.2f, 0.45f, 0.2f, euler(0, 0, 0), 9);
            limb(rig, f, skin, side * 0.34f, 0.45f, 0.3f, 0.18f, 0.5f, 0.18f, euler(0, 60, side * 8f), 9);
        }
        // The head hangs low and forward, facing the ground a little.
        Quaternionf headRot = new Quaternionf(pose)
                .rotateY((float) Math.toRadians(Parts.headYawOffset()))
                .rotateX((float) Math.toRadians(20f));
        float s = 0.5f * scale / Parts.headSize();
        rig.add(Parts.head(at(rig), r.nextBoolean() ? HeadType.PIGLIN : HeadType.ZOMBIE, 11, VIEW),
                f.at(0, 0.98f, 0.42f), headRot, new Vector3f(s),
                new Vector3f(0, Parts.headCenterY(), 0), false);
    }

    // ------------------------------------------------------------------
    // Limbs
    // ------------------------------------------------------------------

    /** A reaching hand at {@code pos}, fingers fanned along {@code dir}. About 0.4 blocks at scale 1. */
    static void hand(Rig rig, Random r, Vector3f pos, Vector3f dir, float scale) {
        Vector3f d = new Vector3f(dir).normalize();
        Vector3f side = new Vector3f(d).cross(new Vector3f(0, 1, 0));
        if (side.lengthSquared() < 1.0e-6f) side.set(1, 0, 0);
        side.normalize();
        Material skin = SKIN[r.nextInt(SKIN.length)];

        block(rig, skin, pos, pointZ(d), new Vector3f(0.3f, 0.1f, 0.3f).mul(scale), 9);
        for (int i = 0; i < 4; i++) {
            float off = (i - 1.5f) * 0.085f * scale;
            Vector3f base = new Vector3f(pos).add(new Vector3f(side).mul(off)).add(new Vector3f(d).mul(0.15f * scale));
            Vector3f curl = new Vector3f(d).mul(0.36f * scale)
                    .add(new Vector3f(0, -0.1f * scale * (0.4f + r.nextFloat() * 0.6f), 0))
                    .add(new Vector3f(side).mul((i - 1.5f) * 0.05f * scale));
            bar(rig, skin, base, new Vector3f(base).add(curl), 0.06f * scale, 9);
        }
        bar(rig, skin, new Vector3f(pos).add(new Vector3f(side).mul(0.15f * scale)),
                new Vector3f(pos).add(new Vector3f(side).mul(0.32f * scale)).add(new Vector3f(d).mul(0.2f * scale)),
                0.07f * scale, 9);
    }

    /**
     * A branch of flesh: tapering segments that arc up and out from {@code start}, ending in a hand
     * or a small face. {@code outward} is the horizontal direction it reaches.
     *
     * @return the tip position (local)
     */
    static Vector3f branch(Rig rig, Random r, Vector3f start, Vector3f outward, float length, float thickness) {
        Vector3f out = new Vector3f(outward.x, 0, outward.z).normalize();
        int segments = 5;
        float rise = length * (0.35f + r.nextFloat() * 0.4f);
        Vector3f wobble = new Vector3f(-out.z, 0, out.x).mul((r.nextFloat() - 0.5f) * length * 0.35f);

        Vector3f prev = new Vector3f(start);
        Vector3f tip = new Vector3f(start);
        for (int i = 1; i <= segments; i++) {
            float t = i / (float) segments;
            Vector3f p = new Vector3f(start)
                    .add(new Vector3f(out).mul(length * t))
                    .add(0, rise * t * t, 0)
                    .add(new Vector3f(wobble).mul((float) Math.sin(t * Math.PI)));
            float th = thickness * (1.0f - 0.7f * t);
            bar(rig, flesh(r), prev, p, th, 9);
            // a knuckle of flesh at every joint
            block(rig, flesh(r), p, euler(r.nextFloat() * 360f, 0, 0),
                    new Vector3f(th * 1.5f), 9);
            prev = p;
            tip = p;
        }

        if (r.nextInt(10) < 7) {
            Vector3f aim = new Vector3f(out).add(0, 0.3f, 0);
            hand(rig, r, new Vector3f(tip).add(new Vector3f(aim).normalize().mul(0.15f * thickness * 3f)),
                    aim, Math.max(0.8f, thickness * 3.2f));
        } else {
            face(rig, r, tip, out.x, out.z, Math.max(0.5f, thickness * 2.2f), headType(r));
        }
        return tip;
    }
}
