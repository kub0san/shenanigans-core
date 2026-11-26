package com.hizeric.shenaniganscore.origins.jujutsukaisen.domainexpansions.malevolentshrine;

import com.hizeric.shenaniganscore.ShenanigansCore;
import com.hizeric.shenaniganscore.core.display.TransformUtils;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Display;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.util.concurrent.ThreadLocalRandom;

/**
 * Visual vocabulary of the Shrine's cuts.
 *
 * <p>Every blade is two display entities: a razor-thin white-hot core and a wider
 * blood-red glass glow. A blade does not just appear: it spawns short, then
 * <b>sweeps</b> open (yaw rotates while the length interpolates out), holds for a
 * beat, and thins away. That single trick is what makes the cut read as a
 * <i>motion</i> instead of a static white stick.</p>
 *
 * <h3>Cleave RAVAGE vocabulary (new)</h3>
 * <p>Cleave is no longer one cross. It is a short choreography that converges on
 * the victim: a <b>lash</b> stretched from the shrine mouth, a <b>helix</b> of
 * orbiting blades, an <b>implosion</b> of inward blades, and a final <b>mega-cross</b>.
 * Timing is driven by {@link System#currentTimeMillis()} inside a
 * {@link BukkitRunnable} so the flurry reads as one overwhelming second, not a
 * single 40% hit.</p>
 *
 * All state is cached: BlockData and DustOptions are created once.
 */
public final class Slashes {

    private static final BlockData CORE = Material.WHITE_CONCRETE.createBlockData();
    private static final BlockData GLOW = Material.RED_STAINED_GLASS.createBlockData();
    private static final BlockData SHARD = Material.OAK_PLANKS.createBlockData();
    private static final BlockData DEBRIS = Material.BLACKSTONE.createBlockData();

    private static final Particle.DustOptions BLOOD = new Particle.DustOptions(Color.fromRGB(150, 0, 12), 1.4f);
    private static final Particle.DustOptions SPARK = new Particle.DustOptions(Color.fromRGB(255, 245, 245), 0.9f);
    private static final Particle.DustOptions EMBER = new Particle.DustOptions(Color.fromRGB(255, 90, 20), 1.0f);
    private static final Particle.DustOptions WHITE_HOT = new Particle.DustOptions(Color.fromRGB(255, 255, 255), 1.6f);

    private Slashes() {}

    // ─────────────────────────────────────────────────────────────────────────
    // Primitive: one sweeping blade
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * @param loc       centre of the blade
     * @param length    final length in blocks
     * @param yaw       heading of the cut (degrees)
     * @param pitch     tilt of the cut: 0 = flat/horizontal, +/-90 = vertical
     * @param thickness core thickness (glow is 5x)
     * @param life      total lifetime in ticks (min ~5)
     */
    public static void blade(Location loc, float length, float yaw, float pitch, float thickness, int life) {
        blade(null, loc, length, yaw, pitch, thickness, life);
    }

    /** Blade variant whose two display halves are registered to a session owner. */
    static void blade(SlashDisplayRegistry owner, Location loc, float length, float yaw, float pitch,
                      float thickness, int life) {
        World w = loc.getWorld();
        if (w == null) return;

        final float sweep = 34f;
        final float endYaw = yaw + sweep;
        final BlockDisplay core = spawnBlock(loc, CORE, thickness, thickness, 0.05f, yaw - sweep, pitch);
        final BlockDisplay glow = spawnBlock(loc, GLOW, thickness * 5f, thickness * 5f, 0.05f, yaw - sweep, pitch);
        own(owner, core);
        own(owner, glow);

        new BukkitRunnable() {
            int t = 0;

            @Override
            public void run() {
                t++;
                if (!core.isValid() || !glow.isValid()) {
                    core.remove();
                    glow.remove();
                    cancel();
                    return;
                }
                if (t == 1) {
                    // Wide, elegant swing: long blade arcs through the target heading.
                    // Start with white-hot flare
                    w.spawnParticle(Particle.DUST, loc, 5, 0.2, 0.2, 0.2, 0, WHITE_HOT);
                    
                    TransformUtils.applyTransform(core, thickness, thickness, length, endYaw, pitch, 0f, 3);
                    TransformUtils.applyTransform(glow, thickness * 5f, thickness * 5f, length * 0.92f, endYaw, pitch, 0f, 3);
                } else if (t == life - 3) {
                    TransformUtils.applyTransform(core, 0.01f, 0.01f, length * 0.98f, endYaw, pitch, 0f, 2);
                    TransformUtils.applyTransform(glow, 0.01f, 0.01f, length * 0.9f, endYaw, pitch, 0f, 2);
                } else if (t >= life) {
                    core.remove();
                    glow.remove();
                    
                    // Lingering "scar" effect
                    spawnScar(loc, length, yaw, pitch);
                    
                    cancel();
                }
            }
        }.runTaskTimer(ShenanigansCore.getInstance(), 1L, 1L);

        // A faint trailing spark line sells the sweep direction.
        Vector dir = dirForYaw(yaw);
        Location trail = loc.clone().add(dir.clone().multiply(length * 0.18));
        w.spawnParticle(Particle.DUST, trail, 2, 0.18, 0.05, 0.18, 0, SPARK);
    }

    private static void spawnScar(Location loc, float length, float yaw, float pitch) {
        World w = loc.getWorld();
        if (w == null) return;
        Vector dir = dirForYaw(yaw);
        new BukkitRunnable() {
            int i = 0;
            @Override
            public void run() {
                if (i++ > 15) { cancel(); return; }
                // Spawn thin dark smoke/scar along the path of the slash
                for(int step = 0; step < 3; step++) {
                    double offset = (i * 0.2) - (length * 0.2);
                    w.spawnParticle(Particle.SMOKE, 
                        loc.clone().add(dir.clone().multiply(offset)), 
                        1, 0.05, 0.05, 0.05, 0.01);
                }
            }
        }.runTaskTimer(ShenanigansCore.getInstance(), 0L, 2L);
    }

    private static void own(SlashDisplayRegistry owner, BlockDisplay display) {
        if (owner == null || display == null) return;
        owner.own(display, BlockDisplay::isValid, BlockDisplay::remove);
    }

    private static BlockDisplay spawnBlock(Location loc, BlockData data, float sx, float sy, float sz, float yaw, float pitch) {
        return loc.getWorld().spawn(loc, BlockDisplay.class, d -> {
            d.setBlock(data);
            d.setPersistent(false);
            d.setViewRange(4.0f);
            d.setBrightness(new Display.Brightness(15, 15));
            if (data.getMaterial() == Material.WHITE_CONCRETE) {
                d.setGlowing(true);
                d.setGlowColorOverride(Color.fromRGB(255, 245, 245));
            } else if (data.getMaterial() == Material.RED_STAINED_GLASS) {
                d.setGlowing(true);
                d.setGlowColorOverride(Color.fromRGB(200, 20, 20));
            }
            TransformUtils.applyTransform(d, sx, sy, sz, yaw, pitch, 0f, 0);
        });
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Overshoot helpers — cuts that go PAST the victim
    // ─────────────────────────────────────────────────────────────────────────

    /** Horizontal direction vector for a yaw in degrees (matches Display yaw). */
    private static Vector dirForYaw(float yawDeg) {
        double r = Math.toRadians(yawDeg);
        return new Vector(-Math.sin(r), 0, Math.cos(r));
    }

    /**
     * A long, sparse blade that lunges through and far past the victim.
     * Mostly horizontal, with slight randomized tilt for variety.
     */
    public static void overshootBlade(Location victim, float yaw, float pitch,
                                      float baseLength, float thickness) {
        overshootBlade(null, victim, yaw, pitch, baseLength, thickness);
    }

    static void overshootBlade(SlashDisplayRegistry owner, Location victim, float yaw, float pitch,
                               float baseLength, float thickness) {
        World w = victim.getWorld();
        if (w == null) return;
        ThreadLocalRandom r = ThreadLocalRandom.current();
        float len = baseLength * (2.3f + r.nextFloat() * 1.8f);
        float thick = thickness * (0.75f + r.nextFloat() * 0.55f);
        int life = 8 + r.nextInt(4);

        // Bias strongly toward horizontal cuts.
        float flatPitch = Math.abs(pitch) < 22f ? pitch : (pitch > 0 ? 12f : -12f);
        flatPitch += (r.nextFloat() - 0.5f) * 18f;

        Vector dir = dirForYaw(yaw);
        // Push far past the victim so the cut feels expansive.
        double push = len * (0.42 + r.nextDouble() * 0.28);
        double lateral = (r.nextDouble() - 0.5) * 1.4;
        Vector side = new Vector(-dir.getZ(), 0, dir.getX());
        Location centre = victim.clone()
                .add(dir.clone().multiply(push))
                .add(side.clone().multiply(lateral))
                .add(0, (r.nextDouble() - 0.5) * 0.35, 0);
        blade(owner, centre, len, yaw + (r.nextFloat() - 0.5f) * 14f, flatPitch, thick, life);
    }

    /**
     * A sparse, wide rake: 1–2 long horizontal blades spaced far apart.
     */
    public static void rake(Location victim, float yaw, float pitch, float baseLength) {
        rake(null, victim, yaw, pitch, baseLength);
    }

    static void rake(SlashDisplayRegistry owner, Location victim, float yaw, float pitch, float baseLength) {
        World w = victim.getWorld();
        if (w == null) return;
        ThreadLocalRandom r = ThreadLocalRandom.current();
        Vector dir = dirForYaw(yaw);
        Vector side = new Vector(-dir.getZ(), 0, dir.getX());
        int passes = 1 + r.nextInt(2); // 1–2
        float push = baseLength * (0.34f + r.nextFloat() * 0.22f);
        float flatPitch = Math.abs(pitch) < 20f ? pitch : (pitch > 0 ? 10f : -10f);
        for (int i = 0; i < passes; i++) {
            double off = (i - (passes - 1) / 2.0) * 1.15 + (r.nextDouble() - 0.5) * 0.5;
            Location c = victim.clone()
                    .add(dir.clone().multiply(push))
                    .add(side.clone().multiply(off))
                    .add(0, (r.nextDouble() - 0.5) * 0.3, 0);
            float len = baseLength * (2.4f + r.nextFloat() * 1.4f);
            blade(owner, c, len, yaw + (r.nextFloat() - 0.5f) * 10f,
                    flatPitch + (r.nextFloat() - 0.5f) * 16f,
                    0.055f + r.nextFloat() * 0.025f, 8 + r.nextInt(4));
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Compositions
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Sparse cleave mark: 1–2 long, mostly horizontal cuts crossing through the
     * victim, with a restrained blood burst.
     */
    public static void cross(Location loc, float size) {
        cross(null, loc, size);
    }

    static void cross(SlashDisplayRegistry owner, Location loc, float size) {
        World w = loc.getWorld();
        if (w == null) return;
        ThreadLocalRandom r = ThreadLocalRandom.current();
        float yaw = r.nextFloat() * 360f;
        int blades = 1 + (r.nextInt(3) == 0 ? 1 : 0); // usually 1, sometimes 2
        for (int k = 0; k < blades; k++) {
            float bYaw = yaw + k * 90f + r.nextFloat(-18f, 18f);
            float bPitch = (r.nextFloat() - 0.5f) * 24f; // mostly horizontal
            overshootBlade(owner, loc, bYaw, bPitch, size, 0.062f + r.nextFloat(0f, 0.018f));
        }

        w.spawnParticle(Particle.DUST, loc, 12, 0.35, 0.30, 0.35, 0, BLOOD);
        w.spawnParticle(Particle.DUST, loc, 6, 0.22, 0.22, 0.22, 0, SPARK);
        w.spawnParticle(Particle.SWEEP_ATTACK, loc, 1, 0.10, 0.10, 0.10, 0);
        w.spawnParticle(Particle.CRIT, loc, 4, 0.22, 0.28, 0.22, 0.10);
    }

    /** Sparse ambient dismantle: a few long, mostly horizontal cuts spread far apart. */
    public static void flurry(Location center, int count, double spread) {
        flurry(null, center, count, spread);
    }

    static void flurry(SlashDisplayRegistry owner, Location center, int count, double spread) {
        ThreadLocalRandom r = ThreadLocalRandom.current();
        int n = Math.max(1, Math.min(count, 2));
        for (int i = 0; i < n; i++) {
            Location l = center.clone().add(
                    (r.nextDouble() - 0.5) * spread * 2.6,
                    (r.nextDouble() - 0.5) * spread * 0.35,
                    (r.nextDouble() - 0.5) * spread * 2.6);
            float yaw = r.nextFloat() * 360f;
            float pitch = (r.nextFloat() - 0.5f) * 22f; // horizontal bias
            blade(owner, l,
                    7.5f + r.nextFloat() * 9.0f,
                    yaw,
                    pitch,
                    0.045f + r.nextFloat() * 0.03f,
                    8 + r.nextInt(4));
        }
    }

    /**
     * A projectile sliced in two: both halves tumble apart and fall away.
     */
    public static void split(Location loc, Vector travel) {
        World w = loc.getWorld();
        if (w == null) return;

        Vector dir = travel.lengthSquared() < 1.0E-6 ? new Vector(0, 0, 1) : travel.clone().normalize();
        Vector side = new Vector(-dir.getZ(), 0, dir.getX());
        if (side.lengthSquared() < 1.0E-6) side = new Vector(1, 0, 0);
        side.normalize();
        float yaw = (float) Math.toDegrees(Math.atan2(-dir.getX(), dir.getZ()));

        for (int sign = -1; sign <= 1; sign += 2) {
            final BlockDisplay half = spawnBlock(loc, SHARD, 0.07f, 0.07f, 0.35f, yaw, 0f);
            half.setTeleportDuration(6);
            final Location to = loc.clone()
                    .add(side.clone().multiply(0.9 * sign))
                    .add(dir.clone().multiply(0.3))
                    .subtract(0, 0.7, 0);
            ShenanigansCore.runTaskLater( () -> {
                if (half.isValid()) half.teleport(to);
            }, 1L);
            ShenanigansCore.runTaskLater( () -> {
                if (half.isValid()) half.remove();
            }, 9L);
        }

        w.spawnParticle(Particle.SWEEP_ATTACK, loc, 1, 0, 0, 0, 0);
        w.spawnParticle(Particle.CRIT, loc, 8, 0.12, 0.12, 0.12, 0.12);
        w.spawnParticle(Particle.DUST, loc, 5, 0.1, 0.1, 0.1, 0, SPARK);
    }

    /** Chunks of the floor thrown up by a cut. */
    public static void debris(Location loc, int count) {
        World w = loc.getWorld();
        if (w == null) return;
        w.spawnParticle(Particle.BLOCK, loc, count, 0.4, 0.2, 0.4, 0.1, DEBRIS);
    }

    // ═════════════════════════════════════════════════════════════════════════
    // RAVAGE — Cleave choreography
    // ═════════════════════════════════════════════════════════════════════════

    /**
     * A long, sweeping lash from the shrine mouth through the victim and far beyond.
     */
    public static void lash(Location from, Location to, float thickness) {
        lash(null, from, to, thickness);
    }

    static void lash(SlashDisplayRegistry owner, Location from, Location to, float thickness) {
        World w = to.getWorld();
        if (w == null || from.getWorld() != to.getWorld()) return;
        Vector delta = to.toVector().subtract(from.toVector());
        double dist = delta.length();
        if (dist < 1.0) return;
        ThreadLocalRandom r = ThreadLocalRandom.current();
        float yaw = (float) Math.toDegrees(Math.atan2(-delta.getX(), delta.getZ()));
        // Keep the lash mostly level for a clean horizontal cleave line.
        float pitch = (float) Math.toDegrees(Math.atan2(delta.getY(), Math.hypot(delta.getX(), delta.getZ())));
        float flatPitch = (float) (-pitch * 0.25f + (r.nextFloat() - 0.5f) * 14f);
        Vector dir = delta.clone().normalize();
        // Much longer overshoot for a dramatic, far-reaching cut.
        double overshoot = 8.0 + r.nextDouble() * 10.0;
        double total = dist + overshoot;
        Location centre = from.clone().add(dir.clone().multiply(total * 0.5));
        float len = (float) Math.max(3.0, total);
        float thick = thickness * (0.85f + r.nextFloat() * 0.45f);
        blade(owner, centre, len, yaw, flatPitch, thick, 9 + r.nextInt(4));
        w.spawnParticle(Particle.DUST, to, 5, 0.18, 0.22, 0.18, 0, EMBER);
        Location tip = to.clone().add(dir.clone().multiply(overshoot));
        w.spawnParticle(Particle.DUST, tip, 5, 0.18, 0.12, 0.18, 0, SPARK);
        w.spawnParticle(Particle.END_ROD, tip, 1, 0.04, 0.04, 0.04, 0.0);
    }

    /**
     * Sparse orbit of long, mostly horizontal blades circling the victim.
     */
    public static void helix(Location center, int count, double radius, float size) {
        helix(null, center, count, radius, size);
    }

    static void helix(SlashDisplayRegistry owner, Location center, int count, double radius, float size) {
        World w = center.getWorld();
        if (w == null) return;
        ThreadLocalRandom r = ThreadLocalRandom.current();
        float baseYaw = r.nextFloat() * 360f;
        int n = Math.max(2, Math.min(3, count));
        double spreadRadius = radius * 1.35;
        for (int i = 0; i < n; i++) {
            double a = (Math.PI * 2.0 * i / n) + Math.toRadians(baseYaw) + r.nextDouble() * 0.5;
            Location p = center.clone().add(
                    Math.cos(a) * spreadRadius,
                    -0.15 + (i % 2) * 0.3,
                    Math.sin(a) * spreadRadius);
            float yaw = baseYaw + i * (360f / n) + r.nextFloat(20f, 55f);
            float pitch = (r.nextFloat() - 0.5f) * 26f; // mostly horizontal
            float len = size * (2.4f + r.nextFloat() * 1.6f);
            blade(owner, p, len, yaw, pitch, 0.055f + r.nextFloat(0f, 0.02f), 8 + r.nextInt(4));
        }
        w.spawnParticle(Particle.DUST, center, 6, radius * 0.7, 0.4, radius * 0.7, 0, BLOOD);
    }

    /**
     * Sparse inward cuts: a few long, mostly horizontal blades closing from afar.
     */
    public static void implosion(Location center, int count, double startRadius, float size) {
        implosion(null, center, count, startRadius, size);
    }

    static void implosion(SlashDisplayRegistry owner, Location center, int count, double startRadius, float size) {
        World w = center.getWorld();
        if (w == null) return;
        ThreadLocalRandom r = ThreadLocalRandom.current();
        int n = Math.max(2, Math.min(3, count));
        for (int i = 0; i < n; i++) {
            double a = (Math.PI * 2.0 * i / n) + r.nextDouble() * 0.8;
            double sr = startRadius * (1.15 + r.nextDouble() * 0.35);
            Location start = center.clone().add(
                    Math.cos(a) * sr,
                    0.25 + r.nextDouble() * 0.5,
                    Math.sin(a) * sr);
            float inwardYaw = (float) Math.toDegrees(Math.atan2(
                    -(center.getX() - start.getX()),
                    (center.getZ() - start.getZ())));
            float pitch = (r.nextFloat() - 0.5f) * 18f; // mostly horizontal
            final float len = size * (2.2f + r.nextFloat() * 1.0f);
            final BlockDisplay core = spawnBlock(start, CORE, 0.06f, 0.06f, len, inwardYaw, pitch);
            final BlockDisplay glow = spawnBlock(start, GLOW, 0.28f, 0.28f, len * 0.9f, inwardYaw, pitch);
            own(owner, core);
            own(owner, glow);
            final Location dest = center.clone().add(0, 0.1, 0);
            long born = System.currentTimeMillis();
            new BukkitRunnable() {
                @Override
                public void run() {
                    double el = System.currentTimeMillis() - born;
                    if (el >= 320L) {
                        if (core.isValid()) core.remove();
                        if (glow.isValid()) glow.remove();
                        cancel();
                        return;
                    }
                    double p = Math.min(1.0, el / 320.0);
                    Location at = new Location(dest.getWorld(),
                            start.getX() + (dest.getX() - start.getX()) * p,
                            start.getY() + (dest.getY() - start.getY()) * p,
                            start.getZ() + (dest.getZ() - start.getZ()) * p);
                    float shrink = (float) (len * (1.0 - p * 0.88));
                    if (core.isValid()) {
                        core.setTeleportDuration(2);
                        core.teleport(at);
                        TransformUtils.applyTransform(core, 0.06f, 0.06f, Math.max(0.3f, shrink), inwardYaw, pitch, 0f, 2);
                    }
                    if (glow.isValid()) {
                        glow.setTeleportDuration(2);
                        glow.teleport(at);
                        TransformUtils.applyTransform(glow, 0.28f, 0.28f, Math.max(0.3f, shrink * 0.9f), inwardYaw, pitch, 0f, 2);
                    }
                }
            }.runTaskTimer(ShenanigansCore.getInstance(), 1L, 1L);
        }
    }

    /**
     * The full RAVAGE barrage around one victim. Timed with
     * {@link System#currentTimeMillis()} so it reads as one continuous second
     * of cutting, not five unrelated hits. Counts are deliberately lean:
     * each stage is 2–4 blades, fully randomized, staggered per victim.
     *
     * @param mouth   the shrine mouth the lash starts from (may be null)
     * @param victim  centre of the barrage (usually victim chest)
     * @param level   escalation (1 = first hit, grows per ravage)
     * @param delayMs per-victim stagger so cuts never land in lockstep
     */
    public static void ravage(Location mouth, Location victim, int level, long delayMs) {
        ravage(null, null, mouth, victim, level, delayMs);
    }

    /**
     * RAVAGE tied to its owning shrine session. The liveness check stops any
     * remaining stage from spawning displays into a domain that has closed, and
     * every stage registers its blades with the session's slash registry.
     */
    static void ravage(Session session, SlashDisplayRegistry owner, Location mouth, Location victim,
                       int level, long delayMs) {
        World w = victim.getWorld();
        if (w == null) return;
        ThreadLocalRandom r = ThreadLocalRandom.current();
        int lvl = Math.max(1, Math.min(10, level));
        // Wider stagger so cuts land sparsely, not in a dense burst.
        long start = System.currentTimeMillis() + Math.max(0L, delayMs) + r.nextInt(0, 120);

        // Fewer, longer, mostly-horizontal cuts spaced over a longer window.
        new BukkitRunnable() {
            boolean sHelix = false, sLash = false, sImplode = false, sMega = false;

            @Override
            public void run() {
                long elapsed = System.currentTimeMillis() - start;
                if (!ShrineSessionDecision.shouldKeepRavageRunnable(session == null || session.isActive(), elapsed)) {
                    cancel();
                    return;
                }
                long el = elapsed;
                if (el < 0) return; // still waiting for this victim's stagger

                if (!sHelix && el >= 120L) {
                    sHelix = true;
                    int blades = 2 + (lvl >= 7 ? 1 : 0);
                    float helixLen = 8.0f + lvl * 0.7f + r.nextFloat(0f, 2.5f);
                    helix(owner, victim, blades, 1.9 + r.nextDouble(0f, 0.8f), helixLen);
                    w.playSound(victim, org.bukkit.Sound.ENTITY_PLAYER_ATTACK_SWEEP, 1.25f, 0.85f + r.nextFloat() * 0.2f);
                    w.spawnParticle(Particle.CRIT, victim, 6 + lvl / 2, 0.5, 0.45, 0.5, 0.12);
                }
                if (!sLash && el >= 340L) {
                    sLash = true;
                    if (mouth != null && mouth.getWorld() == w) {
                        lash(owner, mouth, victim, 0.078f);
                    } else {
                        overshootBlade(owner, victim, r.nextFloat() * 360f, (r.nextFloat() - 0.5f) * 18f, 9.0f, 0.072f);
                    }
                    w.playSound(victim, org.bukkit.Sound.ITEM_TRIDENT_THUNDER, 1.2f, 1.35f);
                    w.spawnParticle(Particle.SWEEP_ATTACK, victim, 1, 0.12, 0.12, 0.12, 0);
                }
                if (!sImplode && el >= 560L) {
                    sImplode = true;
                    implosion(owner, victim, 2 + (lvl >= 8 ? 1 : 0), 3.0 + r.nextDouble() * 0.8, 5.0f);
                    w.playSound(victim, org.bukkit.Sound.ENTITY_WARDEN_SONIC_BOOM, 0.95f, 0.7f);
                    w.spawnParticle(Particle.DUST, victim, 10, 0.5, 0.45, 0.5, 0, BLOOD);
                }
                if (!sMega && el >= 760L) {
                    sMega = true;
                    float mega = 10.5f + lvl * 0.9f + r.nextFloat(0f, 3.0f);
                    cross(owner, victim, mega);
                    if (r.nextInt(3) == 0) {
                        rake(owner, victim, r.nextFloat() * 360f, (r.nextFloat() - 0.5f) * 20f, 7.0f + r.nextFloat(0f, 2.5f));
                    }
                    w.spawnParticle(Particle.DAMAGE_INDICATOR, victim, 5 + lvl / 2, 0.3, 0.35, 0.3, 0.4);
                    w.spawnParticle(Particle.DUST, victim, 14 + lvl, 0.45, 0.45, 0.45, 0, BLOOD);
                    w.spawnParticle(Particle.SWEEP_ATTACK, victim, 1, 0.22, 0.18, 0.22, 0);
                    w.playSound(victim, org.bukkit.Sound.ENTITY_GENERIC_EXPLODE, 0.45f, 0.92f);
                    w.playSound(victim, org.bukkit.Sound.ITEM_TRIDENT_THUNDER, 0.55f, 1.5f);
                    debris(victim, 2 + lvl / 3);
                }
            }
        }.runTaskTimer(ShenanigansCore.getInstance(), 1L, 1L);
    }

    /** Backwards-compatible: no stagger. */
    public static void ravage(Location mouth, Location victim, int level) {
        ravage(mouth, victim, level, ThreadLocalRandom.current().nextInt(0, 420));
    }

    /** Light, damage-free micro-cuts between the main Cleave pulses. */
    public static void microCut(Location victim) {
        microCut(null, victim);
    }

    static void microCut(SlashDisplayRegistry owner, Location victim) {
        World w = victim.getWorld();
        if (w == null) return;
        ThreadLocalRandom r = ThreadLocalRandom.current();
        float yaw = r.nextFloat() * 360f;
        float pitch = (r.nextFloat() - 0.5f) * 20f; // mostly horizontal
        overshootBlade(owner, victim.clone().add(0, 0.45 + r.nextDouble() * 0.4, 0), yaw, pitch, 6.5f, 0.048f);
        w.spawnParticle(Particle.DUST, victim, 3, 0.20, 0.25, 0.20, 0, BLOOD);
    }
}
