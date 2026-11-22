package com.hizeric.shenaniganscore.origins.jujutsukaisen.domainexpansions.timecellmoonpalace;

import com.hizeric.shenaniganscore.ShenanigansCore;
import com.hizeric.shenaniganscore.core.display.DisplayUtils;
import com.hizeric.shenaniganscore.core.display.TransformUtils;
import com.hizeric.shenaniganscore.core.particle.ParticleUtils;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.LivingEntity;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Visual vocabulary of the 24 FPS rule. Everything here is cosmetic; the rules live in
 * {@link Rule24}.
 *
 * <ul>
 *   <li><b>inject</b>: two film strips slide into the target's neck.</li>
 *   <li><b>afterimage</b>: a posed white/black silhouette left on every moved frame.</li>
 *   <li><b>cell</b>: when the cells freeze, the victim is boxed in a film frame (four black
 *       bars, frame-hole sprockets, a pale glass panel). It shatters on thaw.</li>
 *   <li><b>slashBurst</b>: countless small cuts open all over the body at the moment of freezing.</li>
 *   <li><b>severLimb</b>: a white-hot tear, and the limb tumbles away.</li>
 * </ul>
 *
 * <p>Display churn is bounded: each effect spawns a fixed handful, always removes them, and
 * registers them in {@code Session.spawnedDisplays} so a hard close still cleans up.</p>
 */
final class Film {

    private static final float SEED = 0.01f;

    private static final BlockData CRUMB = Material.REDSTONE_BLOCK.createBlockData();
    private static final BlockData SHARD = Material.GLASS.createBlockData();

    private record Seed(BlockDisplay d, float sx, float sy, float sz, float yaw, float lean) {}

    /** A film frame around a frozen victim. */
    static final class Cell {
        final List<BlockDisplay> bars = new ArrayList<>();
        BlockDisplay panel;
        Location center;
        double w, h;
    }

    private Film() {}

    // ─────────────────────────────────────────────────────────────────────────
    // Spawn helpers
    // ─────────────────────────────────────────────────────────────────────────

    private static BlockDisplay spawn(Session s, Material m, Location at, float yaw, float lean, int bright) {
        BlockDisplay d = DisplayUtils.spawnBlock(at, m, 1.5f, bright);
        TransformUtils.applyTransform(d, SEED, SEED, SEED, yaw, lean, 0f, 0);
        s.spawnedDisplays.add(d);
        return d;
    }

    /** Displays spawned as seeds only animate if their first transform change arrives a tick later. */
    private static void growAll(List<Seed> seeds, int ticks) {
        ShenanigansCore.runTaskLater( () -> {
            for (Seed sd : seeds) {
                if (sd.d().isValid()) {
                    TransformUtils.applyTransform(sd.d(), sd.sx(), sd.sy(), sd.sz(), sd.yaw(), sd.lean(), 0f, ticks);
                }
            }
        }, 1L);
    }

    /** Thin out at {@code thinAt}, remove at {@code removeAt} (ticks from now). */
    private static void lifecycle(List<Seed> seeds, long thinAt, long removeAt) {
        ShenanigansCore plugin = ShenanigansCore.getInstance();
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            for (Seed sd : seeds) {
                if (sd.d().isValid()) {
                    TransformUtils.applyTransform(sd.d(), 0.01f, 0.01f, sd.sz() * 0.9f, sd.yaw(), sd.lean(), 0f, 2);
                }
            }
        }, thinAt);
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            for (Seed sd : seeds) {
                if (sd.d().isValid()) sd.d().remove();
            }
        }, removeAt);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Injection
    // ─────────────────────────────────────────────────────────────────────────

    /** Two lines of film slide into the neck. Lore: "nothing appeared to happen". */
    static void inject(Session s, LivingEntity target) {
        World w = target.getWorld();
        Location neck = target.getLocation().add(0, target.getHeight() * 0.82, 0);
        ShenanigansCore plugin = ShenanigansCore.getInstance();

        for (int i = 0; i < 2; i++) {
            double ox = i == 0 ? -0.14 : 0.14;
            Location start = neck.clone().add(ox, 2.4 + i * 0.5, 0);
            Location end = neck.clone().add(ox, 0.15, 0);

            BlockDisplay d = DisplayUtils.spawnBlock(start, Material.BLACK_CONCRETE, 1.5f, 15);
            d.setTeleportDuration(6);
            TransformUtils.applyTransform(d, 0.10f, 1.3f, 0.03f, s.getCasterYaw(), 0f, 0f, 0);
            s.spawnedDisplays.add(d);

            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (s.isActive() && d.isValid()) d.teleport(end);
            }, 1L);
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (d.isValid()) d.remove();
            }, 12L);
        }

        for (int k = 0; k < 7; k++) {
            Particle.DustOptions c = (k & 1) == 0 ? ParticleUtils.WHITE_DUST : ParticleUtils.BLACK_DUST;
            w.spawnParticle(Particle.DUST, neck.getX() - 0.14, neck.getY() + 0.3 + k * 0.32, neck.getZ(), 1, 0, 0, 0, 0, c);
            w.spawnParticle(Particle.DUST, neck.getX() + 0.14, neck.getY() + 0.3 + k * 0.32, neck.getZ(), 1, 0, 0, 0, 0, c);
        }
        w.playSound(neck, Sound.BLOCK_CHAIN_PLACE, 1.0f, 1.6f);
        w.playSound(neck, Sound.BLOCK_COMPARATOR_CLICK, 1.0f, 0.7f);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Frames
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * One ghost pose per frame: three poses rotate (arms down / raised / forward, legs together
     * / apart), and the colour flips pale/black, so a runner leaves a flipbook, not a smear.
     */
    static void afterimage(World w, Location at, long frame) {
        Particle.DustOptions c = (frame & 1L) == 0 ? ParticleUtils.WHITE_DUST : ParticleUtils.BLACK_DUST;
        int pose = (int) (frame % 3);
        double yaw = Math.toRadians(at.getYaw());
        double sx = Math.cos(yaw);
        double sz = Math.sin(yaw);
        double x = at.getX(), y = at.getY(), z = at.getZ();

        for (int k = 0; k < 6; k++) {
            w.spawnParticle(Particle.DUST, x, y + 0.75 + k * 0.17, z, 1, 0.03, 0.02, 0.03, 0, c);
        }
        w.spawnParticle(Particle.DUST, x, y + 1.78, z, 1, 0, 0, 0, 0, c);

        double armY = pose == 1 ? 1.55 : 1.05;
        double armReach = pose == 2 ? 0.0 : 0.42;
        for (int side = -1; side <= 1; side += 2) {
            w.spawnParticle(Particle.DUST, x + sx * armReach * side, armY, z + sz * armReach * side, 1, 0, 0, 0, 0, c);
            if (pose == 2) {
                w.spawnParticle(Particle.DUST, x - sz * 0.42, y + 1.1, z + sx * 0.42, 1, 0, 0, 0, 0, c);
            }
            double spread = pose == 0 ? 0.1 : 0.28;
            for (int k = 0; k < 3; k++) {
                w.spawnParticle(Particle.DUST, x + sx * spread * side, y + 0.1 + k * 0.22, z + sz * spread * side,
                        1, 0.02, 0.02, 0.02, 0, c);
            }
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // The film cell: the frame a frozen victim is trapped in
    // ─────────────────────────────────────────────────────────────────────────

    static Cell cell(Session s, LivingEntity t) {
        Cell c = new Cell();
        double h = Math.max(1.0, t.getHeight()) + 0.5;
        double wd = Math.max(0.8, t.getWidth()) + 0.9;
        Location mid = t.getLocation().add(0, h / 2.0 - 0.1, 0);
        c.center = mid;
        c.w = wd;
        c.h = h;

        float yaw = s.getCasterYaw();
        double rad = Math.toRadians(yaw);
        double lx = Math.cos(rad);
        double lz = Math.sin(rad);
        List<Seed> seeds = new ArrayList<>();

        // the frame: two uprights, top and bottom bars
        BlockDisplay left = spawn(s, Material.BLACK_CONCRETE, mid.clone().add(-lx * wd / 2, 0, -lz * wd / 2), yaw, 0f, 15);
        BlockDisplay right = spawn(s, Material.BLACK_CONCRETE, mid.clone().add(lx * wd / 2, 0, lz * wd / 2), yaw, 0f, 15);
        BlockDisplay top = spawn(s, Material.BLACK_CONCRETE, mid.clone().add(0, h / 2, 0), yaw, 0f, 15);
        BlockDisplay bottom = spawn(s, Material.BLACK_CONCRETE, mid.clone().add(0, -h / 2, 0), yaw, 0f, 15);
        seeds.add(new Seed(left, 0.09f, (float) h, 0.09f, yaw, 0f));
        seeds.add(new Seed(right, 0.09f, (float) h, 0.09f, yaw, 0f));
        seeds.add(new Seed(top, (float) wd + 0.09f, 0.09f, 0.09f, yaw, 0f));
        seeds.add(new Seed(bottom, (float) wd + 0.09f, 0.09f, 0.09f, yaw, 0f));
        c.bars.add(left);
        c.bars.add(right);
        c.bars.add(top);
        c.bars.add(bottom);

        // frame holes down both edges
        for (int side = -1; side <= 1; side += 2) {
            for (int k = 0; k < 4; k++) {
                double yo = -h * 0.375 + k * (h * 0.25);
                Location at = mid.clone().add(lx * (wd / 2 + 0.16) * side, yo, lz * (wd / 2 + 0.16) * side);
                BlockDisplay hole = spawn(s, Material.WHITE_CONCRETE, at, yaw, 0f, 15);
                seeds.add(new Seed(hole, 0.08f, 0.16f, 0.08f, yaw, 0f));
                c.bars.add(hole);
            }
        }

        // the frozen image itself: a pale pane
        BlockDisplay panel = spawn(s, Material.LIGHT_BLUE_STAINED_GLASS, mid, yaw, 0f, 9);
        seeds.add(new Seed(panel, (float) wd, (float) h, 0.02f, yaw, 0f));
        c.panel = panel;

        growAll(seeds, 4);
        World w = mid.getWorld();
        w.playSound(mid, Sound.BLOCK_GLASS_PLACE, 1.0f, 0.6f);
        w.playSound(mid, Sound.BLOCK_CHAIN_PLACE, 1.0f, 1.4f);
        return c;
    }

    /** The frame breaks apart and flies outward. */
    static void shatter(Session s, Cell c) {
        if (c == null || c.center == null) return;
        World w = c.center.getWorld();
        if (w == null) return;

        w.spawnParticle(Particle.BLOCK, c.center, 36, c.w / 2, c.h / 2, 0.25, 0.1, SHARD);
        w.playSound(c.center, Sound.BLOCK_GLASS_BREAK, 1.2f, 1.2f);
        if (c.panel != null && c.panel.isValid()) c.panel.remove();

        ThreadLocalRandom r = ThreadLocalRandom.current();
        float yaw = s.getCasterYaw();
        for (BlockDisplay b : c.bars) {
            if (!b.isValid()) continue;
            Vector out = b.getLocation().toVector().subtract(c.center.toVector());
            if (out.lengthSquared() < 1.0e-4) out = new Vector(r.nextDouble() - 0.5, 0.2, r.nextDouble() - 0.5);
            out.normalize().multiply(1.0 + r.nextDouble() * 0.8);
            b.setTeleportDuration(6);
            b.teleport(b.getLocation().add(out));
            TransformUtils.applyTransform(b, SEED, SEED, SEED, yaw, r.nextFloat(-60f, 60f), 0f, 6);
        }
        List<BlockDisplay> bars = new ArrayList<>(c.bars);
        ShenanigansCore.runTaskLater( () -> {
            for (BlockDisplay b : bars) {
                if (b.isValid()) b.remove();
            }
        }, 8L);
        c.bars.clear();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Injuries
    // ─────────────────────────────────────────────────────────────────────────

    /** Countless small gashes: short fine blood lines at random spots on the body. */
    static void cuts(LivingEntity e, int gashes) {
        World w = e.getWorld();
        Location base = e.getLocation();
        double h = Math.max(0.6, e.getHeight());
        double hw = Math.max(0.3, e.getWidth() * 0.5);
        ThreadLocalRandom r = ThreadLocalRandom.current();

        for (int i = 0; i < gashes; i++) {
            double cx = base.getX() + (r.nextDouble() - 0.5) * hw * 1.6;
            double cy = base.getY() + 0.15 + r.nextDouble() * (h - 0.3);
            double cz = base.getZ() + (r.nextDouble() - 0.5) * hw * 1.6;
            double ang = r.nextDouble() * Math.PI * 2.0;
            double dx = Math.cos(ang) * 0.09;
            double dy = (r.nextDouble() - 0.5) * 0.12;
            double dz = Math.sin(ang) * 0.09;
            for (int step = -2; step <= 2; step++) {
                w.spawnParticle(Particle.DUST, cx + dx * step, cy + dy * step, cz + dz * step, 1, 0, 0, 0, 0, ParticleUtils.BLOOD_FINE_DUST);
            }
        }
        Location mid = base.clone().add(0, h * 0.55, 0);
        w.spawnParticle(Particle.DAMAGE_INDICATOR, mid, Math.min(6, gashes), hw * 0.5, h * 0.3, hw * 0.5, 0.1);
        w.spawnParticle(Particle.BLOCK, mid, 2 + gashes, hw * 0.5, h * 0.3, hw * 0.5, 0.05, CRUMB);
        w.playSound(mid, Sound.ENTITY_PLAYER_ATTACK_CRIT, 0.5f, 1.7f);
    }

    /**
     * The moment the cells freeze: a dozen hairline cuts snap open across the whole body as
     * thin bars, hold for a few ticks, then fade.
     */
    static void slashBurst(Session s, LivingEntity e, int count) {
        ThreadLocalRandom r = ThreadLocalRandom.current();
        double h = Math.max(0.8, e.getHeight());
        double hw = Math.max(0.3, e.getWidth() * 0.5);
        List<Seed> seeds = new ArrayList<>();

        for (int i = 0; i < count; i++) {
            Location p = e.getLocation().add((r.nextDouble() - 0.5) * hw * 1.6,
                    0.15 + r.nextDouble() * (h - 0.3), (r.nextDouble() - 0.5) * hw * 1.6);
            float len = 0.5f + r.nextFloat() * 0.6f;
            float yaw = r.nextFloat(0f, 360f);
            float lean = r.nextFloat(-70f, 70f);
            Material m = (i % 3 == 0) ? Material.WHITE_CONCRETE : Material.RED_CONCRETE;
            BlockDisplay d = spawn(s, m, p, yaw, lean, 15);
            seeds.add(new Seed(d, 0.035f, 0.035f, len, yaw, lean));
        }
        growAll(seeds, 2);
        lifecycle(seeds, 6L, 9L);
    }

    /** A white-hot tear with a red glow, in the plane of the film. */
    static void tear(Session s, Location at, float len, float lean) {
        float yaw = s.getCasterYaw() - 90f;
        BlockDisplay core = spawn(s, Material.WHITE_CONCRETE, at, yaw, lean, 15);
        BlockDisplay glow = spawn(s, Material.RED_CONCRETE, at, yaw, lean, 15);
        List<Seed> seeds = new ArrayList<>();
        seeds.add(new Seed(core, 0.035f, 0.035f, len, yaw, lean));
        seeds.add(new Seed(glow, 0.14f, 0.14f, len * 0.88f, yaw, lean));
        growAll(seeds, 2);
        lifecycle(seeds, 5L, 8L);
    }

    static void freezeBurst(Session s, LivingEntity e) {
        World w = e.getWorld();
        Location mid = e.getLocation().add(0, e.getHeight() * 0.55, 0);
        w.spawnParticle(Particle.SNOWFLAKE, mid, 40, 0.4, 0.7, 0.4, 0.04);
        w.spawnParticle(Particle.END_ROD, mid, 14, 0.4, 0.7, 0.4, 0.03);
        w.spawnParticle(Particle.DUST, mid, 24, 0.4, 0.7, 0.4, 0, ParticleUtils.WHITE_DUST);
        w.playSound(mid, Sound.ENTITY_PLAYER_HURT_FREEZE, 1.2f, 0.8f);
        w.playSound(mid, Sound.BLOCK_GLASS_BREAK, 0.7f, 1.5f);
        cuts(e, 8);
        slashBurst(s, e, 10);
    }

    /** Frozen state: pale frost and the two film lines standing out of the neck. */
    static void aura(LivingEntity e) {
        World w = e.getWorld();
        Location l = e.getLocation();
        double h = e.getHeight();
        w.spawnParticle(Particle.SNOWFLAKE, l.getX(), l.getY() + h * 0.5, l.getZ(), 2, 0.35, h * 0.4, 0.35, 0.01);
        for (int k = 0; k < 4; k++) {
            Particle.DustOptions c = (k & 1) == 0 ? ParticleUtils.WHITE_DUST : ParticleUtils.BLACK_DUST;
            w.spawnParticle(Particle.DUST, l.getX() - 0.1, l.getY() + h * 0.82 + k * 0.2, l.getZ(), 1, 0, 0, 0, 0, c);
            w.spawnParticle(Particle.DUST, l.getX() + 0.1, l.getY() + h * 0.82 + k * 0.2, l.getZ(), 1, 0, 0, 0, 0, c);
        }
    }

    static void thaw(LivingEntity e) {
        World w = e.getWorld();
        Location mid = e.getLocation().add(0, e.getHeight() * 0.55, 0);
        w.spawnParticle(Particle.SNOWFLAKE, mid, 24, 0.4, 0.7, 0.4, 0.06);
        w.playSound(mid, Sound.BLOCK_AMETHYST_BLOCK_BREAK, 1.0f, 0.7f);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Severing
    // ─────────────────────────────────────────────────────────────────────────

    /** Particles + sound for the cut itself; {@link #severLimb} adds the displays. */
    static void severBurst(LivingEntity e, boolean arm) {
        World w = e.getWorld();
        Location limb = e.getLocation().add(0, e.getHeight() * (arm ? 0.68 : 0.25), 0);
        w.spawnParticle(Particle.DUST, limb, 46, 0.25, 0.25, 0.25, 0, ParticleUtils.BLOOD_DUST);
        w.spawnParticle(Particle.BLOCK, limb, 26, 0.2, 0.2, 0.2, 0.12, CRUMB);
        w.spawnParticle(Particle.DAMAGE_INDICATOR, limb, 10, 0.25, 0.25, 0.25, 0.3);
        w.spawnParticle(Particle.SWEEP_ATTACK, limb, 2, 0.15, 0.15, 0.15, 0);
        w.playSound(limb, Sound.ENTITY_PLAYER_ATTACK_CRIT, 1.4f, 0.6f);
        w.playSound(limb, Sound.BLOCK_BONE_BLOCK_BREAK, 1.6f, 0.5f);
        w.playSound(limb, Sound.ENTITY_GENERIC_EXPLODE, 0.5f, 1.8f);
    }

    /** Two crossing tears, then the limb tumbles away on an arc and lies there for a moment. */
    static void severLimb(Session s, LivingEntity e, boolean arm) {
        World w = e.getWorld();
        ThreadLocalRandom r = ThreadLocalRandom.current();
        Location feet = e.getLocation();
        Location limb = feet.clone().add(0, e.getHeight() * (arm ? 0.68 : 0.25), 0);

        tear(s, limb, 2.6f, 38f);
        tear(s, limb, 2.1f, -44f);

        float lw = 0.22f;
        float lh = arm ? 0.7f : 0.78f;
        float yaw = s.getCasterYaw();
        BlockDisplay d = spawn(s, Material.PINK_TERRACOTTA, limb, yaw, 0f, 12);

        double ang = r.nextDouble() * Math.PI * 2.0;
        double dist = 1.4 + r.nextDouble() * 0.8;
        Location land = new Location(w, limb.getX() + Math.cos(ang) * dist, feet.getY() + 0.12,
                limb.getZ() + Math.sin(ang) * dist);
        float tumble = r.nextFloat(60f, 110f);
        float roll = r.nextFloat(-40f, 40f);

        ShenanigansCore plugin = ShenanigansCore.getInstance();
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!d.isValid()) return;
            d.setTeleportDuration(12);
            d.teleport(land);
            TransformUtils.applyTransform(d, lw, lh, lw, yaw, tumble, roll, 12);
        }, 1L);

        // blood on the arc (the display's own position jumps at once, so compute the path)
        new BukkitRunnable() {
            int k = 0;

            @Override
            public void run() {
                k++;
                double t = k / 12.0;
                double px = limb.getX() + (land.getX() - limb.getX()) * t;
                double pz = limb.getZ() + (land.getZ() - limb.getZ()) * t;
                double py = limb.getY() + (land.getY() - limb.getY()) * t + Math.sin(Math.PI * t) * 0.7;
                w.spawnParticle(Particle.DUST, px, py, pz, 3, 0.08, 0.08, 0.08, 0, ParticleUtils.BLOOD_DUST);
                w.spawnParticle(Particle.FALLING_DUST, px, py, pz, 1, 0.05, 0.05, 0.05, 0, CRUMB);
                if (k >= 12 || !d.isValid()) cancel();
            }
        }.runTaskTimer(plugin, 1L, 1L);

        // it lies there, then goes
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (d.isValid()) TransformUtils.applyTransform(d, SEED, SEED, SEED, yaw, tumble, roll, 4);
        }, 46L);
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (d.isValid()) d.remove();
        }, 51L);
    }
}
