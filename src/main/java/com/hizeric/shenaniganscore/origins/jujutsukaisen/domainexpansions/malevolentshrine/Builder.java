package com.hizeric.shenaniganscore.origins.jujutsukaisen.domainexpansions.malevolentshrine;

import com.hizeric.shenaniganscore.ShenanigansCore;
import com.hizeric.shenaniganscore.core.display.TransformUtils;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Tag;
import org.bukkit.block.Block;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Display;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.util.Vector;

import java.util.EnumMap;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Geometry of the Chinjudo shrine, redesigned.
 *
 * <h3>What is new</h3>
 * <ul>
 *   <li><b>Four mouths</b> (lore: four grotesque mouths as entrances), each with lips,
 *       gums and interlocking fangs. The lower jaw is a separate
 *       group that opens and closes (out of phase per mouth) and <b>snaps shut on every Cleave</b>.</li>
 *   <li><b>Real curves.</b> {@link #curve} builds tapering, bending horns from short
 *       segments: onigawara-style upturned roof corners, a lyre of crown horns on the
 *       tower, and the bull skulls' horns.</li>
 *   <li><b>Fanged eaves</b>: a fringe of bone teeth hangs from the lower roof.</li>
 *   <li><b>Hanging human skulls</b> (lore) on chains that sway like pendulums.</li>
 *   <li><b>Cursed heart</b> with orbiting wisps, a ring of 20 relic "fingers" around the
 *       altar, and a spinning crest above the tower.</li>
 *   <li><b>Skull island</b>: bone piles and skulls scattered at the base.</li>
 *   <li><b>Staggered rise</b>: each piece erupts with jitter, overshoots, then slams down with dust.</li>
 * </ul>
 *
 * <p>Coordinates are shrine-local: +z is the front maw, +x is right, y=0 is ground.
 * Lean convention (matches the original builder): positive leanX tips the top of a piece
 * toward its local +z; positive leanZ tips it toward local -x.</p>
 */
public final class Builder {

    private Builder() {}

    /** Set false on weak servers: skips bone piles, fangs and relic fingers and trims horns. */
    public static final boolean HIGH_DETAIL = true;

    private static final double RISE_DEPTH = 13.0;
    private static final Map<Material, BlockData> CACHE = new EnumMap<>(Material.class);
    private static final BlockData DEBRIS = Material.BLACKSTONE.createBlockData();

    private static BlockData data(Material m) {
        return CACHE.computeIfAbsent(m, Material::createBlockData);
    }

    private static void shrineGlow(BlockDisplay display, Color color) {
        if (display == null) return;
        display.setGlowing(true);
        display.setGlowColorOverride(color);
        display.setBrightness(new Display.Brightness(15, 15));
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Data carried by the Session
    // ─────────────────────────────────────────────────────────────────────────

    /** One display plus everything needed to re-pose it later (collapse tilt, jitter). */
    public record Piece(BlockDisplay display, int wave, float sx, float sy, float sz,
                        float yaw, float leanX, float leanZ, Location home) {}

    /** A lower-jaw part that drops when its mouth opens. */
    public record JawPart(BlockDisplay display, Location rest, float drop, int mouth) {}

    /** A chained skull swinging from the roof. */
    public static final class HangingSkull {
        private final BlockDisplay chain;
        private final BlockDisplay[] parts;
        private final Vector[] offsets;
        private final Vector pivot;
        private final double length;
        private final double ax;
        private final double az;
        private final double phase;
        private final double amp;
        private final float chainYaw;

        private HangingSkull(BlockDisplay chain, BlockDisplay[] parts, Vector[] offsets, Vector pivot,
                             double length, double ax, double az, double phase, double amp) {
            this.chain = chain;
            this.parts = parts;
            this.offsets = offsets;
            this.pivot = pivot;
            this.length = length;
            this.ax = ax;
            this.az = az;
            this.phase = phase;
            this.amp = amp;
            this.chainYaw = (float) Math.toDegrees(Math.atan2(-ax, az));
        }

        /** Pendulum motion: call every 2 ticks. */
        void update(double t) {
            if (chain == null || !chain.isValid()) return;
            double ang = amp * Math.sin(t * 1.25 + phase);
            double sin = Math.sin(ang);
            double cos = Math.cos(ang);

            Vector bottom = new Vector(
                    pivot.getX() + ax * sin * length,
                    pivot.getY() - cos * length,
                    pivot.getZ() + az * sin * length);

            World w = chain.getWorld();
            Location mid = new Location(w,
                    (pivot.getX() + bottom.getX()) / 2.0,
                    (pivot.getY() + bottom.getY()) / 2.0,
                    (pivot.getZ() + bottom.getZ()) / 2.0);

            chain.setTeleportDuration(2);
            chain.teleport(mid);
            TransformUtils.applyTransform(chain, 0.12f, (float) length, 0.12f, chainYaw,
                    (float) -Math.toDegrees(ang), 0f, 2);

            for (int i = 0; i < parts.length; i++) {
                if (!parts[i].isValid()) continue;
                parts[i].setTeleportDuration(2);
                parts[i].teleport(new Location(w,
                        bottom.getX() + offsets[i].getX(),
                        bottom.getY() + offsets[i].getY(),
                        bottom.getZ() + offsets[i].getZ()));
            }
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Placement core
    // ─────────────────────────────────────────────────────────────────────────

    /** Shrine-local -> world (honours the caster's yaw). */
    public static Location toWorld(Session s, double x, double y, double z) {
        Location c = s.getShrineCenter();
        double rad = Math.toRadians(s.getCasterYaw());
        return new Location(c.getWorld(),
                c.getX() + (x * Math.cos(rad)) - (z * Math.sin(rad)),
                c.getY() + y,
                c.getZ() + (x * Math.sin(rad)) + (z * Math.cos(rad)));
    }

    /**
     * Finds the top of the actual terrain at a column, rather than trusting the
     * highest-block heightmap (which can select leaves, logs, or other clutter).
     * The returned value is a world-space Y coordinate at the collision surface.
     * We add +1.0 to ensure the shrine sits on top of the generated floor.
     */
    public static double terrainSurfaceY(World world, int blockX, int blockZ) {
        if (world == null) return 0.0;
        if (!world.isChunkLoaded(blockX >> 4, blockZ >> 4)) {
            return world.getHighestBlockYAt(blockX, blockZ) + 2.0;
        }

        int highest = world.getHighestBlockYAt(blockX, blockZ);
        int floor = Math.max(world.getMinHeight(), highest - 64);
        for (int y = highest; y >= floor; y--) {
            Block block = world.getBlockAt(blockX, y, blockZ);
            Material material = block.getType();
            if (material.isAir() || block.isLiquid() || !material.isSolid()) continue;
            if (Tag.LEAVES.isTagged(material) || Tag.LOGS.isTagged(material)) continue;
            return block.getBoundingBox().getMaxY() + 1.0;
        }
        return highest + 2.0;
    }

    /**
     * Height difference between the local ground and the shrine's anchor surface.
     *
     * <p>Inside the shrine's platform this is always zero: the ground is flat,
     * so every pillar is the same length and the roof stays level, whatever
     * the terrain under the shrine used to do. Outside the platform the raw
     * terrain is sampled as before.</p>
     */
    public static double terrainOffsetY(Session session, double localX, double localZ) {
        Location anchor = session.getShrineCenter();
        Location sample = toWorld(session, localX, 0.0, localZ);
        World world = anchor.getWorld();
        if (world == null) return 0.0;

        Floor floor = session.floor;
        if (floor != null && floor.ready()) {
            return floor.terrainOffset(sample.getX(), sample.getZ());
        }
        if (!world.isChunkLoaded(sample.getBlockX() >> 4, sample.getBlockZ() >> 4)) {
            return 0.0;
        }
        return terrainSurfaceY(world, sample.getBlockX(), sample.getBlockZ()) - anchor.getY();
    }

    public static BlockDisplay spawnPiece(Session session, Material material, double x, double y, double z,
                                          float scaleX, float scaleY, float scaleZ,
                                          float rotationY, float leanX, float leanZ) {
        Location finalLoc = toWorld(session, x, y, z);
        return spawnAt(session, data(material), finalLoc, scaleX, scaleY, scaleZ,
                session.getCasterYaw() + rotationY, leanX, leanZ);
    }

    /** Helper to avoid writing 0,0 for lean every time. */
    public static BlockDisplay spawnPiece(Session session, Material material, double x, double y, double z,
                                          float scaleX, float scaleY, float scaleZ, float rotationY) {
        return spawnPiece(session, material, x, y, z, scaleX, scaleY, scaleZ, rotationY, 0, 0);
    }

    /**
     * Spawns underground and animates the eruption: jittered start, a quick rise with a small
     * overshoot, then a settling slam with dust (heavy pieces) and a rumble.
     */
    private static BlockDisplay spawnAt(Session s, BlockData blockData, Location finalLoc,
                                        float sx, float sy, float sz, float yaw, float lx, float lz) {
        World w = finalLoc.getWorld();
        Location start = finalLoc.clone().subtract(0, RISE_DEPTH, 0);

        BlockDisplay d = w.spawn(start, BlockDisplay.class, e -> {
            e.setBlock(blockData);
            e.setPersistent(false);
            e.setViewRange(4.0f);
            e.setBrightness(new Display.Brightness(14, 14));
            e.setTeleportDuration(12);
            TransformUtils.applyTransform(e, sx, sy, sz, yaw, lx, lz, 0);
        });

        s.spawnedDisplays.add(d);
        s.pieces.add(new Piece(d, s.getCurrentWave(), sx, sy, sz, yaw, lx, lz, finalLoc.clone()));

        final boolean heavy = sx * sy * sz > 18f;
        final int delay = 1 + ThreadLocalRandom.current().nextInt(5);
        JavaPlugin plugin = ShenanigansCore.getInstance();

        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!s.isActive() || !d.isValid()) return;
            d.teleport(finalLoc.clone().add(0, 0.2, 0));      // overshoot
        }, delay);

        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!s.isActive() || !d.isValid()) return;
            d.setTeleportDuration(3);
            d.teleport(finalLoc);                              // slam
            impact(finalLoc, heavy);
        }, delay + 12L);

        return d;
    }

    private static void impact(Location loc, boolean heavy) {
        World w = loc.getWorld();
        if (w == null) return;
        if (heavy) {
            w.spawnParticle(Particle.BLOCK, loc, 16, 0.8, 0.3, 0.8, 0.05, DEBRIS);
            w.spawnParticle(Particle.LARGE_SMOKE, loc, 4, 0.6, 0.2, 0.6, 0.01);
            w.playSound(loc, Sound.BLOCK_DEEPSLATE_BREAK, 1.2f, 0.5f);
        } else if (ThreadLocalRandom.current().nextInt(8) == 0) {
            w.spawnParticle(Particle.BLOCK, loc, 3, 0.3, 0.2, 0.3, 0.05, DEBRIS);
        }
    }

    /**
     * Frame placement: (u across, y up, w forward) relative to an anchor facing
     * {@code theta} degrees (0 = +z, 180 = -z, -90 = +x, 90 = -x).
     */
    private static BlockDisplay fp(Session s, double theta, double ax, double az, Material m,
                                   double u, double y, double w,
                                   float sx, float sy, float sz, float lx, float lz) {
        double r = Math.toRadians(theta);
        double x = ax + (u * Math.cos(r)) - (w * Math.sin(r));
        double z = az + (u * Math.sin(r)) + (w * Math.cos(r));
        return spawnPiece(s, m, x, y, z, sx, sy, sz, (float) theta, lx, lz);
    }

    private static BlockDisplay fp(Session s, double theta, double ax, double az, Material m,
                                   double u, double y, double w, float sx, float sy, float sz) {
        return fp(s, theta, ax, az, m, u, y, w, sx, sy, sz, 0f, 0f);
    }

    /**
     * A tapering, bending horn built from short segments in the vertical plane that points
     * along (dx, dz). {@code a0}/{@code a1} are the tangent angles at base/tip, measured from
     * vertical toward (dx, dz): 90 = horizontal outward, 0 = straight up, negative = curling back.
     */
    private static void curve(Session s, Material m, double x, double y, double z, double dx, double dz,
                              double a0, double a1, double length, int segs, float w0, float w1) {
        double hl = Math.hypot(dx, dz);
        dx /= hl;
        dz /= hl;
        if (!HIGH_DETAIL) segs = Math.max(3, segs - 2);

        float yaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
        double step = length / segs;
        double px = x, py = y, pz = z;

        for (int i = 0; i < segs; i++) {
            double t = (i + 0.5) / segs;
            double a = Math.toRadians(a0 + (a1 - a0) * t);
            double hx = Math.sin(a) * step;
            double hy = Math.cos(a) * step;
            float w = w0 + (w1 - w0) * (float) t;
            spawnPiece(s, m, px + dx * hx * 0.5, py + hy * 0.5, pz + dz * hx * 0.5,
                    w, (float) (step * 1.15), w, yaw, (float) Math.toDegrees(a), 0f);
            px += dx * hx;
            py += hy;
            pz += dz * hx;
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // WAVE 1 - foundation, porch, lantern posts
    // ─────────────────────────────────────────────────────────────────────────

    public static void buildWave1(Session s) {
        spawnPiece(s, Material.POLISHED_BLACKSTONE_BRICKS, 0, -1.5, 0, 21, 5, 17, 0);
        spawnPiece(s, Material.POLISHED_BLACKSTONE, 0, 1.5, 0, 18, 1, 14, 0);

        // Blood-red trim along the terrace edge
        spawnPiece(s, Material.RED_NETHER_BRICKS, 0, 2.06, -6.85, 18.2f, 0.12f, 0.3f, 0);
        spawnPiece(s, Material.RED_NETHER_BRICKS, 8.85, 2.06, 0, 0.3f, 0.12f, 14.2f, 0);
        spawnPiece(s, Material.RED_NETHER_BRICKS, -8.85, 2.06, 0, 0.3f, 0.12f, 14.2f, 0);

        // Five-step porch climbing to the front maw
        for (int i = 0; i < 5; i++) {
            double top = 0.4 * (i + 1);
            double zc = 11.0 - 0.9 * i;
            float width = 7.6f - 0.25f * i;
            spawnPiece(s, i % 2 == 0 ? Material.POLISHED_BLACKSTONE_BRICKS : Material.POLISHED_BLACKSTONE,
                    0, (top - 1.0) / 2.0, zc, width, (float) (top + 1.0), 1.0f, 0);
        }

        for (int sd = -1; sd <= 1; sd += 2) {
            // Cheek walls beside the stairs
            spawnPiece(s, Material.POLISHED_BLACKSTONE_BRICKS, 4.3 * sd, 0.9, 9.2, 0.8f, 2.6f, 4.4f, 0);
            // Lantern posts
            spawnPiece(s, Material.POLISHED_BLACKSTONE, 4.8 * sd, 2, 9.6, 0.9f, 3, 0.9f, 0);
            BlockDisplay lantern = spawnPiece(
                    s, Material.SHROOMLIGHT,
                    4.8 * sd, 3.9, 9.6,
                    1.1f, 1.1f, 1.1f,
                    0
            );
            shrineGlow(lantern, Color.fromRGB(255, 115, 45));
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // WAVE 2 - twelve pillars and tie-beams
    // ─────────────────────────────────────────────────────────────────────────

    public static void buildWave2(Session s) {
        double[][] coords = {
                {-7, 5.5}, {-3.2, 5.5}, {3.2, 5.5}, {7, 5.5},
                {-7, -5.5}, {-3.2, -5.5}, {3.2, -5.5}, {7, -5.5},
                {-7, 2.6}, {-7, -2.6}, {7, 2.6}, {7, -2.6}
        };
        for (double[] c : coords) {
            // The lower end of every support is calculated independently. On a
            // hillside the shafts become different lengths, while the capitals
            // remain on the same architectural datum and the roof stays level.
            // The foundation top is y=1, so low ground is supported by the plinth;
            // raised ground moves the foot upward and shortens the shaft.
            double footY = Math.max(1.0, terrainOffsetY(s, c[0], c[1]));
            footY = Math.min(footY, 7.15); // leave a visible shaft even on steep terrain
            double shaftBottom = footY + 0.9;
            double shaftTop = 8.5;
            double shaftHeight = Math.max(0.35, shaftTop - shaftBottom);
            double shaftCenter = shaftBottom + shaftHeight * 0.5;

            spawnPiece(s, Material.POLISHED_BLACKSTONE, c[0], footY + 0.45, c[1],
                    1.55f, 0.9f, 1.55f, 0); // grounded plinth
            spawnPiece(s, Material.RED_NETHER_BRICKS, c[0], shaftCenter, c[1],
                    0.9f, (float) shaftHeight, 0.9f, 0); // terrain-length shaft
            spawnPiece(s, Material.DARK_OAK_PLANKS, c[0], 8.0, c[1],
                    1.9f, 0.3f, 1.9f, 0); // bracket
            spawnPiece(s, Material.POLISHED_BLACKSTONE, c[0], 8.35, c[1],
                    1.3f, 0.5f, 1.3f, 0); // capital
        }
        // Tie beams
        spawnPiece(s, Material.DARK_OAK_PLANKS, 0, 8.05, 5.9, 14.8f, 0.45f, 0.5f, 0);
        spawnPiece(s, Material.DARK_OAK_PLANKS, 0, 8.05, -5.9, 14.8f, 0.45f, 0.5f, 0);
        spawnPiece(s, Material.DARK_OAK_PLANKS, 7.4, 8.05, 0, 0.5f, 0.45f, 11.8f, 0);
        spawnPiece(s, Material.DARK_OAK_PLANKS, -7.4, 8.05, 0, 0.5f, 0.45f, 11.8f, 0);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // WAVE 3 - walls with mouth-openings, altar, cursed heart, relics, wisps
    // ─────────────────────────────────────────────────────────────────────────

    /** A wall with a mouth-shaped opening, built in an anchor frame facing {@code theta}. */
    private static void wallFace(Session s, double theta, double dist, double span, double openWidth) {
        double segW = (span - openWidth) / 2.0;
        double off = openWidth / 2.0 + segW / 2.0;
        for (int sd = -1; sd <= 1; sd += 2) {
            fp(s, theta, 0, 0, Material.DEEPSLATE_TILES, off * sd, 5.25, dist, (float) segW, 6.5f, 0.7f);
        }
        fp(s, theta, 0, 0, Material.DEEPSLATE_TILES, 0, 7.25, dist, (float) openWidth, 2.5f, 0.7f);
    }

    public static void buildWave3(Session s) {
        wallFace(s, 0, 5.5, 14, 6.4);       // front
        wallFace(s, 180, 5.6, 14, 6.4);     // back
        wallFace(s, -90, 7.0, 11, 3.6);     // right
        wallFace(s, 90, 7.0, 11, 3.6);      // left

        // Entrance awning boards (same angled pair as the original design)
        spawnPiece(s, Material.DARK_OAK_PLANKS, -3.74, 4, 7, 3.2f, 4, 0.25f, 109.89f);
        spawnPiece(s, Material.DARK_OAK_PLANKS, 3.74, 4, 7, 3.2f, 4, 0.25f, 70.11f);

        // Altar and the beating heart
        spawnPiece(s, Material.POLISHED_BLACKSTONE_BRICKS, 0, 2.25, -3.2, 4.6f, 0.5f, 4.6f, 0);
        spawnPiece(s, Material.OBSIDIAN, 0, 2.75, -3.2, 3.2f, 1.5f, 3.2f, 0);
        s.setShrineCore(spawnPiece(s, Material.REDSTONE_BLOCK, 0, 5, -3.2, 1.5f, 1.5f, 1.5f, 0));
        shrineGlow(s.getShrineCore(), Color.fromRGB(255, 25, 35));
        spawnPiece(s, Material.RED_STAINED_GLASS, 0, 5, -3.2, 2.4f, 2.4f, 2.4f, 0);

        // Eight cursed wisps that will orbit the heart (fewer on very large radii)
        int wispCount = s.getRadius() > 128.0 ? 4 : 8;
        for (int i = 0; i < wispCount; i++) {
            double a = i * (Math.PI * 2.0 / wispCount);
            BlockDisplay wisp = spawnPiece(s, Material.RED_STAINED_GLASS,
                    Math.cos(a) * 2.1, 5.0, -3.2 + Math.sin(a) * 2.1, 0.28f, 0.28f, 0.28f, 0);
            shrineGlow(wisp, Color.fromRGB(255, 45, 70));
            s.wisps.add(wisp);
        }

        // Twenty relic "fingers" ringed around the altar (Sukuna's twenty fingers)
        if (HIGH_DETAIL) {
            int fingerCount = s.getRadius() > 128.0 ? 8 : 20;
            for (int i = 0; i < fingerCount; i++) {
                double phi = Math.toRadians(i * (360.0 / fingerCount));
                double x = 2.45 * Math.cos(phi);
                double z = -3.2 + 2.45 * Math.sin(phi);
                float ry = (float) Math.toDegrees(Math.atan2(Math.cos(phi), -Math.sin(phi)));
                spawnPiece(s, Material.BONE_BLOCK, x, 2.9, z, 0.17f, 0.8f, 0.17f, ry, 14f, 0f);
            }
        }
    }

    /**
     * Spins and pulses the cursed heart, orbits the wisps and turns the crest.
     * Call every 2 ticks during the domain for smooth interpolation.
     */
    public static void animateCore(Session s, double elapsed) {
        BlockDisplay core = s.getShrineCore();
        if (core != null && core.isValid() && core.getWorld() != null) {
            float spinYaw = (float) ((elapsed * 90.0) % 360.0);
            float pulse = (float) (1.5 + (0.15 * Math.sin(elapsed * Math.PI * 2.0)));
            TransformUtils.applyTransform(core, pulse, pulse, pulse, spinYaw, 0, 0, 2);
        }

        BlockDisplay crest = s.getShrineCrest();
        if (crest != null && crest.isValid()) {
            TransformUtils.applyTransform(crest, 0.8f, 0.8f, 0.8f, (float) ((elapsed * -120.0) % 360.0), 35f, 35f, 2);
        }

        int i = 0;
        for (BlockDisplay wisp : s.wisps) {
            if (wisp.isValid()) {
                double ang = elapsed * 2.4 + i * (Math.PI / 4.0);
                double rr = 2.1 + 0.35 * Math.sin(elapsed * 1.7 + i);
                double yy = 5.0 + 0.9 * Math.sin(elapsed * 1.3 + i * 0.9);
                wisp.setTeleportDuration(2);
                wisp.teleport(toWorld(s, rr * Math.cos(ang), yy, -3.2 + rr * Math.sin(ang)));
            }
            i++;
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // WAVE 4 - the four mouths
    // ─────────────────────────────────────────────────────────────────────────

    public static void buildWave4(Session s) {
        mouth(s, 0, 0, 5.5, 6.4, true);       // front: the great maw
        mouth(s, 1, 180, 5.6, 6.4, true);     // back
        mouth(s, 2, -90, 7.0, 3.6, false);    // right
        mouth(s, 3, 90, 7.0, 3.6, false);     // left
    }

    private static void jaw(Session s, int mouthIdx, float drop) {
        Piece last = s.pieces.get(s.pieces.size() - 1);
        s.jaws.add(new JawPart(last.display(), last.home(), drop, mouthIdx));
    }

    private static void mouth(Session s, int idx, double theta, double dist, double ow, boolean showy) {
        final double hw = ow / 2.0;
        final int n = Math.max(4, (int) Math.round(ow / 0.8));
        final double pitch = ow / n;
        final float drop = 0.95f;

        // Upper lip and gum (fixed)
        fp(s, theta, 0, 0, Material.PINK_TERRACOTTA, 0, 6.12, dist + 0.15, (float) (ow + 1.0), 0.45f, 1.1f);
        fp(s, theta, 0, 0, Material.NETHER_WART_BLOCK, 0, 5.62, dist + 0.05, (float) ow, 0.7f, 0.9f);

        // Upper fangs (two-piece taper; canines are longer)
        for (int i = 0; i < n; i++) {
            double u = -hw + (i + 0.5) * pitch;
            float grow = (showy && (i == 1 || i == n - 2)) ? 1.45f : 1f;
            double baseBottom = 5.3 - 0.9 * grow;
            fp(s, theta, 0, 0, Material.BONE_BLOCK, u, 5.3 - 0.45 * grow, dist + 0.3, 0.6f, 0.9f * grow, 0.55f);
            fp(s, theta, 0, 0, Material.BONE_BLOCK, u, baseBottom - 0.4 * grow, dist + 0.3, 0.34f, 0.8f * grow, 0.32f);
        }

        // Lower jaw (animated group): gum, lip, interlocking fangs
        fp(s, theta, 0, 0, Material.NETHER_WART_BLOCK, 0, 2.95, dist + 0.05, (float) ow, 0.7f, 0.9f);
        jaw(s, idx, drop);
        fp(s, theta, 0, 0, Material.PINK_TERRACOTTA, 0, 2.55, dist + 0.15, (float) (ow + 1.0), 0.45f, 1.1f);
        jaw(s, idx, drop);
        for (int i = 0; i < n - 1; i++) {
            double u = -hw + (i + 1) * pitch;
            fp(s, theta, 0, 0, Material.BONE_BLOCK, u, 3.55, dist + 0.3, 0.6f, 0.8f, 0.55f);
            jaw(s, idx, drop);
            fp(s, theta, 0, 0, Material.BONE_BLOCK, u, 4.2, dist + 0.3, 0.34f, 0.7f, 0.32f);
            jaw(s, idx, drop);
        }

        // Eyes above the mouth: angry, inner end raised
        double eyeU = showy ? 2.0 : 1.0;
        float eyeW = showy ? 1.4f : 0.9f;
        for (int sd = -1; sd <= 1; sd += 2) {
            fp(s, theta, 0, 0, Material.REDSTONE_BLOCK, sd * eyeU, 7.4, dist + 0.4, eyeW, 0.35f, 0.2f, 0f, -15f * sd);
        }
    }

    /** World position just outside a mouth (0 front, 1 back, 2 right, 3 left). */
    public static Location mouthLocation(Session s, int idx) {
        return switch (idx) {
            case 0 -> toWorld(s, 0, 4, 7.2);
            case 1 -> toWorld(s, 0, 4, -7.2);
            case 2 -> toWorld(s, 8.6, 4, 0);
            default -> toWorld(s, -8.6, 4, 0);
        };
    }

    /**
     * Chewing animation: each mouth opens and closes out of phase. Call every 4 ticks.
     * While a Cleave bite is active the jaws slam shut.
     */
    public static void animateJaws(Session s, double elapsed) {
        boolean snap = elapsed < s.getBiteUntil();
        for (JawPart j : s.jaws) {
            BlockDisplay d = j.display();
            if (!d.isValid()) continue;
            double open = snap ? 0.0 : 0.5 + 0.5 * Math.sin(elapsed * 0.9 + j.mouth() * 1.7);
            open = open * open;
            d.setTeleportDuration(snap ? 2 : 4);
            d.teleport(j.rest().clone().subtract(0, j.drop() * open, 0));
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // WAVE 5 - lower roof: stepped hip roof, upturned corners, fanged fringe
    // ─────────────────────────────────────────────────────────────────────────

    public static void buildWave5(Session s) {
        double[][] roofs = {{8.85, 20, 16}, {9.45, 18.4, 14.4}, {10.05, 16.4, 12.4},
                {10.65, 14.2, 10.4}, {11.25, 12, 8.4}, {11.85, 9.8, 6.4}};
        for (double[] r : roofs) {
            spawnPiece(s, Material.DEEPSLATE_TILES, 0, r[0], 0, (float) r[1], 0.6f, (float) r[2], 0);
        }

        // Upturned eave boards (outer edge rises)
        spawnPiece(s, Material.RED_NETHER_BRICKS, 0, 9.35, 8.3, 20, 0.35f, 1.2f, 0, -12, 0);
        spawnPiece(s, Material.RED_NETHER_BRICKS, 0, 9.35, -8.3, 20, 0.35f, 1.2f, 0, 12, 0);
        spawnPiece(s, Material.RED_NETHER_BRICKS, 10.3, 9.35, 0, 1.2f, 0.35f, 16, 0, 0, 12);
        spawnPiece(s, Material.RED_NETHER_BRICKS, -10.3, 9.35, 0, 1.2f, 0.35f, 16, 0, 0, -12);

        // Onigawara-style corner horns: sweep out, then flick skyward
        for (int sx = -1; sx <= 1; sx += 2) {
            for (int sz = -1; sz <= 1; sz += 2) {
                curve(s, Material.RED_NETHER_BRICKS, sx * 9.6, 9.2, sz * 7.6, sx, sz, 78, 8, 4.4, 5, 0.75f, 0.22f);
            }
        }

        // Fanged fringe hanging from the eaves
        if (HIGH_DETAIL) {
            for (double x = -9; x <= 9.01; x += 2) {
                spawnPiece(s, Material.BONE_BLOCK, x, 8.0, 7.8, 0.34f, 0.9f, 0.34f, 0);
                spawnPiece(s, Material.BONE_BLOCK, x, 8.0, -7.8, 0.34f, 0.9f, 0.34f, 0);
            }
            for (double z = -7; z <= 7.01; z += 2) {
                spawnPiece(s, Material.BONE_BLOCK, 9.8, 8.0, z, 0.34f, 0.9f, 0.34f, 0);
                spawnPiece(s, Material.BONE_BLOCK, -9.8, 8.0, z, 0.34f, 0.9f, 0.34f, 0);
            }
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // WAVE 6 - upper roof, glowing eyes, hanging human skulls
    // ─────────────────────────────────────────────────────────────────────────

    public static void buildWave6(Session s) {
        spawnPiece(s, Material.RED_NETHER_BRICKS, 0, 13, 0, 7.2f, 2.4f, 4.8f, 0);
        double[][] roofs = {{14.45, 11.4, 8.2}, {15.05, 9.6, 6.6}, {15.65, 7.8, 5}, {16.25, 6, 3.6}, {16.85, 4.6, 2}};
        for (double[] r : roofs) {
            spawnPiece(s, Material.DEEPSLATE_TILES, 0, r[0], 0, (float) r[1], 0.6f, (float) r[2], 0);
        }

        // Slit eyes on the upper storey (front and back)
        for (int sd = -1; sd <= 1; sd += 2) {
            spawnPiece(s, Material.SHROOMLIGHT, 1.8 * sd, 13.1, 2.45, 1.3f, 0.3f, 0.15f, 0, 0, -14f * sd);
            spawnPiece(s, Material.SHROOMLIGHT, 1.8 * sd, 13.1, -2.45, 1.3f, 0.3f, 0.15f, 0, 0, -14f * sd);
        }

        for (int sx = -1; sx <= 1; sx += 2) {
            for (int sz = -1; sz <= 1; sz += 2) {
                curve(s, Material.RED_NETHER_BRICKS, sx * 5.4, 14.9, sz * 3.9, sx, sz, 80, 5, 3.6, 4, 0.6f, 0.18f);
            }
        }

        // Human skulls on chains from the lower eaves
        for (int sx = -1; sx <= 1; sx += 2) {
            for (int sz = -1; sz <= 1; sz += 2) {
                hangingSkull(s, sx * 9.6, 8.5, sz * 7.6, 2.6, 1.0f);
            }
            hangingSkull(s, sx * 9.6, 8.5, 0, 2.2, 0.85f);
        }
    }

    private static void hangingSkull(Session s, double lx, double ly, double lz, double length, float sc) {
        int base = s.pieces.size();

        double fl = Math.hypot(lx, lz);
        double fx = fl < 0.01 ? 0 : lx / fl;
        double fz = fl < 0.01 ? 1 : lz / fl;
        float ry = (float) Math.toDegrees(Math.atan2(-fx, fz));
        double by = ly - length;

        // Order matters: chain first, then cranium, jaw, eyes
        spawnPiece(s, Material.BLACK_CONCRETE, lx, ly - length / 2.0, lz, 0.12f, (float) length, 0.12f, 0);
        spawnPiece(s, Material.BONE_BLOCK, lx, by - 0.5 * sc, lz, sc, 0.9f * sc, sc, ry);
        spawnPiece(s, Material.BONE_BLOCK, lx + fx * 0.12 * sc, by - 1.1 * sc, lz + fz * 0.12 * sc,
                0.62f * sc, 0.34f * sc, 0.78f * sc, ry);
        for (int sd = -1; sd <= 1; sd += 2) {
            // across-vector of the skull's frame is (fz, -fx)
            spawnPiece(s, Material.REDSTONE_BLOCK,
                    lx + fx * 0.52 * sc + fz * 0.24 * sc * sd,
                    by - 0.45 * sc,
                    lz + fz * 0.52 * sc - fx * 0.24 * sc * sd,
                    0.26f * sc, 0.22f * sc, 0.12f * sc, ry);
        }

        BlockDisplay chain = s.pieces.get(base).display();
        BlockDisplay[] parts = new BlockDisplay[4];
        Vector[] offsets = new Vector[4];
        Vector bottom = toWorld(s, lx, by, lz).toVector();
        for (int i = 0; i < 4; i++) {
            Piece p = s.pieces.get(base + 1 + i);
            parts[i] = p.display();
            offsets[i] = p.home().toVector().subtract(bottom);
        }

        Random rnd = ThreadLocalRandom.current();
        double a = rnd.nextDouble() * Math.PI * 2;
        s.hanging.add(new HangingSkull(chain, parts, offsets, toWorld(s, lx, ly, lz).toVector(),
                length, Math.cos(a), Math.sin(a), rnd.nextDouble() * 6.28, 0.13 + rnd.nextDouble() * 0.05));
    }

    /** Pendulum sway for every hanging skull. Call every 2 ticks. */
    public static void swaySkulls(Session s, double elapsed) {
        for (HangingSkull h : s.hanging) h.update(elapsed);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // WAVE 7 - tower, lyre of crown horns, spinning crest
    // ─────────────────────────────────────────────────────────────────────────

    public static void buildWave7(Session s) {
        spawnPiece(s, Material.RED_NETHER_BRICKS, 0, 17.5, 0, 3.2f, 1, 2.2f, 0);

        // Three sweeping horns per side: they flare out, then curl back inward
        for (int sd = -1; sd <= 1; sd += 2) {
            curve(s, Material.BONE_BLOCK, sd * 1.0, 17.9, 0, sd, 0, 62, -35, 7.0, 8, 0.95f, 0.2f);
            curve(s, Material.BONE_BLOCK, sd * 0.7, 18.2, 0, sd, 0, 40, -55, 8.2, 8, 0.8f, 0.18f);
            curve(s, Material.BONE_BLOCK, sd * 0.4, 18.4, 0, sd, 0, 18, -70, 6.0, 7, 0.62f, 0.15f);
        }

        // Finial spire topped by a spinning crest (animated in animateCore)
        spawnPiece(s, Material.RED_NETHER_BRICKS, 0, 19.0, 0, 1.2f, 2.0f, 1.2f, 0);
        spawnPiece(s, Material.BONE_BLOCK, 0, 21.0, 0, 0.5f, 2.5f, 0.5f, 0);
        s.setShrineCrest(spawnPiece(s, Material.REDSTONE_BLOCK, 0, 23.0, 0, 0.8f, 0.8f, 0.8f, 0, 35f, 35f));
        shrineGlow(s.getShrineCrest(), Color.fromRGB(255, 30, 18));
    }

    // ─────────────────────────────────────────────────────────────────────────
    // WAVES 8 + 9 - the bull-skull ring and the skull island
    // ─────────────────────────────────────────────────────────────────────────

    public static void buildWave8(Session s) {
        for (int k = 0; k < 5; k++) bullSkull(s, k);
        bonePile(s, 8128L, HIGH_DETAIL ? 6 : 3);
    }

    public static void buildWave9(Session s) {
        for (int k = 5; k < 10; k++) bullSkull(s, k);
        bonePile(s, 9301L, HIGH_DETAIL ? 6 : 3);
    }

    /** A bull skull on a pedestal, facing inward toward the shrine. */
    private static void bullSkull(Session s, int k) {
        double a = Math.toRadians(k * 36.0);
        // Slightly irregular ring spacing avoids the "perfectly placed props" look.
        double ringRadius = 21.0 + 0.55 * Math.sin(k * 1.71);
        double ox = ringRadius * Math.sin(a);
        double oz = ringRadius * Math.cos(a);
        double theta = Math.toDegrees(Math.atan2(ox, -oz)); // forward = toward centre
        // The pedestal is a true support, not a floating decoration. Its foot
        // is placed just below the real terrain surface and its top stays on a
        // shared architectural datum. Lower ground therefore produces a longer
        // pillar; higher ground shortens it. On a very high ridge the skull group
        // is lifted just enough to remain above the terrain.
        double terrain = terrainOffsetY(s, ox, oz);
        double footY = terrain - 0.35;
        double headDatumY = Math.max(1.0, terrain + 0.75);
        double pedestalHeight = Math.max(0.8, headDatumY - footY);
        double pedestalCenterY = footY + pedestalHeight * 0.5;
        double headOffset = headDatumY - 1.0;

        fp(s, theta, ox, oz, Material.DEEPSLATE_BRICKS, 0, pedestalCenterY, 0,
                2.2f, (float) pedestalHeight, 2.2f, 0f, 0f);
        fp(s, theta, ox, oz, Material.POLISHED_BLACKSTONE, 0, 1.15 + headOffset, 0,
                2.6f, 0.3f, 2.6f, 0f, 0f);

        // Cranium + muzzle
        fp(s, theta, ox, oz, Material.BONE_BLOCK, 0, 2.3 + headOffset, 0,
                2.3f, 1.8f, 2.3f, 0f, 0f);
        fp(s, theta, ox, oz, Material.BONE_BLOCK, 0, 1.75 + headOffset, 1.35,
                1.3f, 1.1f, 1.0f, 0f, 0f);
        // A real skull block on the front of the cranium breaks up the cuboid
        // silhouette and gives the horned guardians a more skeletal face.
        fp(s, theta, ox, oz, Material.SKELETON_SKULL, 0, 2.35 + headOffset, 1.27,
                1.15f, 1.15f, 0.72f, 0f, 0f);

        // Sockets (carved in) and the red glow inside them
        for (int sd = -1; sd <= 1; sd += 2) {
            fp(s, theta, ox, oz, Material.BLACK_CONCRETE, 0.7 * sd,
                    2.6 + headOffset, 1.1, 0.6f, 0.5f, 0.3f, 0f, 0f);
            if (HIGH_DETAIL) {
                fp(s, theta, ox, oz, Material.REDSTONE_BLOCK, 0.7 * sd,
                        2.6 + headOffset, 1.18, 0.3f, 0.25f, 0.2f, 0f, 0f);
            }
        }

        // Horns: each skull gets a subtle individual curl, avoiding a repeated prop ring.
        double cr = Math.cos(Math.toRadians(theta));
        double sr = Math.sin(Math.toRadians(theta));
        double hornScale = 0.88 + 0.12 * (0.5 + 0.5 * Math.sin(k * 2.37));
        double hornLength = 3.55 + 0.65 * hornScale;
        for (int sd = -1; sd <= 1; sd += 2) {
            double bx = ox + 1.15 * sd * cr;
            double bz = oz + 1.15 * sd * sr;
            double curl = (k % 2 == 0 ? 7.0 : 15.0);
            curve(s, Material.BONE_BLOCK, bx, 2.9 + headOffset, bz,
                    sd * cr, sd * sr, 85, curl, hornLength, 5,
                    (float) (0.6 * hornScale), (float) (0.16 * hornScale));
        }
    }

    /** Skulls and long bones heaped around the foundation (the skull island). */
    private static void bonePile(Session s, long seed, int clusters) {
        Random r = new Random(seed);
        for (int c = 0; c < clusters; c++) {
            double ang = r.nextDouble() * Math.PI * 2;
            double rad = 12.0 + r.nextDouble() * 4.5;
            double x = Math.sin(ang) * rad;
            double z = Math.cos(ang) * rad;
            if (z > 7.5 && Math.abs(x) < 6.5) x += 8.0 * (x >= 0 ? 1 : -1); // keep the porch clear
            float yaw = r.nextFloat() * 360f;
            double yr = Math.toRadians(yaw);
            double fx = -Math.sin(yr);
            double fz = Math.cos(yr);
            double ux = Math.cos(yr);
            double uz = Math.sin(yr);

            double rootY = terrainOffsetY(s, x, z);
            spawnPiece(s, Material.BONE_BLOCK, x, 0.45 + rootY, z, 0.95f, 0.85f, 0.95f, yaw, 0f, 8f);
            double jawX = x + fx * 0.12, jawZ = z + fz * 0.12;
            spawnPiece(s, Material.BONE_BLOCK, jawX, 0.08 + terrainOffsetY(s, jawX, jawZ), jawZ,
                    0.6f, 0.3f, 0.7f, yaw);
            for (int sd = -1; sd <= 1; sd += 2) {
                double eyeX = x + fx * 0.47 + ux * 0.22 * sd;
                double eyeZ = z + fz * 0.47 + uz * 0.22 * sd;
                spawnPiece(s, Material.BLACK_CONCRETE,
                        eyeX, 0.55 + terrainOffsetY(s, eyeX, eyeZ), eyeZ,
                        0.22f, 0.2f, 0.1f, yaw);
            }
            // Two long bones lying across the heap, each settled to its own column.
            for (int b = 0; b < 2; b++) {
                float by = r.nextFloat() * 360f;
                double boneX = x + (r.nextDouble() - 0.5) * 1.8;
                double boneZ = z + (r.nextDouble() - 0.5) * 1.8;
                spawnPiece(s, Material.BONE_BLOCK, boneX,
                        0.2 + terrainOffsetY(s, boneX, boneZ), boneZ,
                        0.3f, 1.5f, 0.3f, by, 78f, 0f);
            }
        }
    }
}
