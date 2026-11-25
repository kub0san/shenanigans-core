package com.hizeric.shenaniganscore.origins.jujutsukaisen.domainexpansions.malevolentshrine;

import com.hizeric.shenaniganscore.ShenanigansCore;
import com.hizeric.shenaniganscore.core.display.TransformUtils;
import com.hizeric.shenaniganscore.core.util.MathUtil;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.AbstractArrow;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.TNTPrimed;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Dismantle: the shrine's cut for everything that is NOT alive.
 *
 * <p>Lore: Cleave hits living things, Dismantle slices inanimate objects. In PvP the
 * most important inanimate objects a player carries are their gear, so Dismantle now
 * actually does something:</p>
 * <ul>
 *   <li><b>Shreds armor</b>: every pulse eats a % of each worn piece's durability
 *       (shredded gear can break — see {@link #canDestroyArmor()}).</li>
 *   <li><b>Breaks guards</b>: a raised shield is cut apart and put on cooldown.</li>
 *   <li><b>Cuts projectiles and primed TNT</b> out of the air; the caster's own pass.</li>
 *   <li><b>Crescent waves</b> roar out of the shrine's mouths, scar the floor, slice
 *       projectiles and shred anyone they pass through (skill-based bonus shred).</li>
 * </ul>
 */
public final class Dismantle {

    // ── Crescent-wave geometry ──
    private static final int SEGMENTS = 9;
    private static final double ARC_RADIUS = 5.2;
    private static final double ARC_HALF_ANGLE = 58.0;      // degrees — wider arc
    private static final float CORE_THICK = 0.16f;
    private static final float BLADE_HEIGHT = 3.4f;
    private static final float GLOW_THICK = 0.75f;
    private static final float GLOW_HEIGHT = 4.2f;

    private static final BlockData CORE = Material.WHITE_CONCRETE.createBlockData();
    private static final BlockData GLOW = Material.RED_STAINED_GLASS.createBlockData();
    private static final BlockData DEBRIS = Material.BLACKSTONE.createBlockData();
    private static final Particle.DustOptions BLOOD_DUST =
            new Particle.DustOptions(org.bukkit.Color.fromRGB(150, 0, 12), 1.3f);

    private Dismantle() {}

    /** Armor share of max durability removed per pulse (config percent / 100). */
    private static double shredFraction() {
        double percent = 8.0;
        return percent < 0.1 || percent > 100.0 ? 0.08 : percent / 100.0;
    }

    /** Extra armor share removed when a crescent wave passes through a player. */
    private static double waveShredFraction() {
        double percent = 14.0;
        return percent < 0.1 || percent > 100.0 ? 0.14 : percent / 100.0;
    }

    /** True = shredded armor can break outright (the shipped default). */
    private static boolean canDestroyArmor() {
        return true;
    }

    private static int shieldCooldownTicks() {
        return Math.max(0, 120);
    }

    private static int maxLiveWaves() {
        int v = 10;
        return Math.max(1, Math.min(32, v));
    }

    private static double waveSpeed() {
        // Hardcoded to the shipped value (0.95 blocks/tick = 19 blocks/s).
        double v = 0.95;
        return v <= 0.0 || v > 7.0 ? 0.95 : v;
    }

    private static int waveLifetimeTicks() {
        // Hardcoded to the shipped value (44 ticks = 2.2s).
        int v = 44;
        return Math.max(10, Math.min(200, v));
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Periodic sure-hit pulse
    // ─────────────────────────────────────────────────────────────────────────

    /** Called once per second: shreds the gear of everyone inside the domain. */
    public static void pulse(Session session, Player caster) {
        for (Player p : playersInside(session, caster)) {
            shredArmor(p, shredFraction());
            breakShield(p, session.slashDisplays);

            Location chest = p.getLocation().add(0, 1.0, 0);
            Slashes.flurry(session.slashDisplays, chest, 1, 3.2);

            if (session.warned.add(p.getUniqueId())) {
                p.sendMessage(MiniMessage.miniMessage().deserialize(
                        "<dark_red>✂ <red>Dismantle <gray>— the shrine is shredding your gear. Get out of the barrier."));
            }
        }
    }

    /** How long the shrine "stalks" a projectile with mini slashes before the final cut. */
    private static int projectileCutDelayTicks() {
        int v = 45;
        return Math.max(10, Math.min(200, v));
    }

    public static final class TrackedCut {
        final Entity entity;
        final long startMs;
        final long cutAtMs;

        TrackedCut(Entity entity, long startMs, long delayMs) {
            this.entity = entity;
            this.startMs = startMs;
            this.cutAtMs = startMs + delayMs;
        }
    }

    /** Scans for enemy projectiles/TNT in the domain and marks them for a delayed, telegraphed cut. */
    public static void trackProjectiles(Session session, Player caster) {
        Location center = session.getCenter();
        double radius = session.getRadius();
        World world = center.getWorld();
        if (world == null) return;

        Map<UUID, TrackedCut> tracked = session.trackedProjectileCuts;
        long now = System.currentTimeMillis();
        long delayMs = projectileCutDelayTicks() * 50L;

        for (Entity e : world.getNearbyEntities(center, radius, radius, radius,
                e -> (e instanceof Projectile || e instanceof TNTPrimed) && e.isValid())) {
            if (e instanceof Projectile pr && pr.getShooter() == caster) continue;
            if (hasHitBlock(e)) continue;
            tracked.putIfAbsent(e.getUniqueId(), new TrackedCut(e, now, delayMs));
        }
    }

    /** Advances delayed projectile cuts: spams mini slashes around targets, then cuts them before impact. */
    public static void tickTrackedProjectiles(Session session, Player caster) {
        Map<UUID, TrackedCut> tracked = session.trackedProjectileCuts;
        if (tracked.isEmpty()) return;

        long now = System.currentTimeMillis();
        Iterator<Map.Entry<UUID, TrackedCut>> it = tracked.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<UUID, TrackedCut> entry = it.next();
            TrackedCut tc = entry.getValue();
            Entity e = tc.entity;

            if (e == null || !e.isValid() || e.isDead()) {
                // Despawned / dead before the delayed cut resolved.
                it.remove();
                continue;
            }
            if (e instanceof Projectile pr && pr.getShooter() == caster) {
                it.remove();
                continue;
            }

            Location loc = e.getLocation();
            spawnMiniSlashes(session.slashDisplays, loc, tc, now);

            // Cut after the telegraphed delay, but always before the projectile can hit a block
            // (so things like ender pearls never get to teleport/trigger their impact).
            if (now >= tc.cutAtMs || isAboutToHitBlock(e)) {
                cut(session.slashDisplays, e);
                it.remove();
            }
        }
    }

    /** External hook (e.g. crescent waves) to schedule a delayed cut instead of instant removal. */
    public static void scheduleCut(Session session, Entity e) {
        if (e == null || !e.isValid() || e.isDead() || hasHitBlock(e)) return;
        long now = System.currentTimeMillis();
        long delayMs = projectileCutDelayTicks() * 50L;
        session.trackedProjectileCuts.putIfAbsent(e.getUniqueId(), new TrackedCut(e, now, delayMs));
    }

    private static boolean hasHitBlock(Entity e) {
        if (e instanceof AbstractArrow arrow) {
            try {
                if (arrow.isInBlock()) return true;
            } catch (Throwable ignored) {
                // intentional: isInBlock() can throw for an invalid entity; treat as not-hit.
            }
        }
        if (!e.isValid() || e.isDead()) return true;
        if (e.isOnGround()) return true;
        Location loc = e.getLocation();
        Block b = loc.getBlock();
        return b != null && b.getType().isSolid() && !b.isPassable();
    }

    /**
     * True when the projectile is about to collide with a block very soon.
     * Used to force the delayed cut to resolve before impact (e.g. ender pearl teleport).
     */
    private static boolean isAboutToHitBlock(Entity e) {
        if (!(e instanceof Projectile)) return false;
        if (hasHitBlock(e)) return true;

        Vector vel = e.getVelocity();
        double speed = vel.length();
        if (speed < 1.0E-4) return hasHitBlock(e);

        Location start = e.getLocation();
        World world = start.getWorld();
        if (world == null) return false;

        Vector dir = vel.clone().normalize();
        // Look ahead ~4 ticks of travel (or at least a short distance) to catch imminent impacts.
        double lookAhead = Math.max(0.75, Math.min(6.0, speed * 4.0));
        try {
            var hit = world.rayTraceBlocks(start, dir, lookAhead);
            if (hit != null && hit.getHitBlock() != null) return true;
        } catch (Throwable ignored) {
            // Fallback: manual sampling along the path.
        }

        double step = 0.35;
        for (double d = step; d <= lookAhead; d += step) {
            Block b = start.clone().add(dir.clone().multiply(d)).getBlock();
            if (b.getType().isSolid() && !b.isPassable()) return true;
        }
        return false;
    }

    private static void spawnMiniSlashes(SlashDisplayRegistry owner, Location loc, TrackedCut tc, long now) {
        World w = loc.getWorld();
        if (w == null) return;
        ThreadLocalRandom r = ThreadLocalRandom.current();
        long life = Math.max(1L, tc.cutAtMs - tc.startMs);
        double progress = MathUtil.clamp((now - tc.startMs) / (double) life, 0.0, 1.0);

        // Bunch of mini slashes swarming around the projectile, ramping up as the cut nears.
        int count = 1 + (progress > 0.35 ? 1 : 0) + (progress > 0.7 ? 1 : 0);
        for (int i = 0; i < count; i++) {
            Location around = loc.clone().add(
                    (r.nextDouble() - 0.5) * 1.6,
                    (r.nextDouble() - 0.5) * 1.2,
                    (r.nextDouble() - 0.5) * 1.6);
            float yaw = r.nextFloat() * 360f;
            float pitch = (r.nextFloat() - 0.5f) * 50f;
            float len = 1.8f + r.nextFloat() * 1.6f;
            Slashes.blade(owner, around, len, yaw, pitch, 0.028f + r.nextFloat() * 0.015f, 5);
        }
        if (r.nextInt(3) == 0) {
            w.playSound(loc, Sound.ENTITY_PLAYER_ATTACK_SWEEP, 0.35f, 1.3f + r.nextFloat() * 0.5f);
        }
    }

    /** Final delayed cut: a real shrine slash entity tears through the projectile. */
    private static void cut(SlashDisplayRegistry owner, Entity e) {
        Location loc = e.getLocation();
        Vector travel = e.getVelocity();
        e.remove();
        World w = loc.getWorld();
        if (w == null) return;

        Vector dir = travel.lengthSquared() < 1.0E-6 ? new Vector(0, 0, 1) : travel.clone().normalize();
        double len = 5.0 + ThreadLocalRandom.current().nextDouble() * 4.0;
        Vector tipVec = loc.clone().toVector().add(dir).multiply(len);
        Location tip = new Location(loc.getWorld(),
                tipVec.getX(), tipVec.getY(), tipVec.getZ());
        Slashes.lash(owner, loc, tip, 0.075f);
        w.playSound(loc, Sound.ENTITY_PLAYER_ATTACK_SWEEP, 0.9f, 1.15f);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Gear damage
    // ─────────────────────────────────────────────────────────────────────────

    static void shredArmor(Player p, double fraction) {
        PlayerInventory inv = p.getInventory();
        ItemStack[] armor = inv.getArmorContents();
        boolean changed = false;

        for (ItemStack piece : armor) {
            if (piece == null || piece.getType().isAir()) continue;
            ItemMeta meta = piece.getItemMeta();
            if (!(meta instanceof Damageable dmg) || meta.isUnbreakable()) continue;

            int max = dmg.hasMaxDamage() ? dmg.getMaxDamage() : piece.getType().getMaxDurability();
            if (max <= 0) continue;

            int next = dmg.getDamage() + Math.max(1, (int) Math.ceil(max * fraction));
            if (!canDestroyArmor()) next = Math.min(next, max - 1);
            if (next <= dmg.getDamage()) continue;

            dmg.setDamage(next);
            piece.setItemMeta(dmg);
            changed = true;
        }

        if (changed) {
            inv.setArmorContents(armor);
            p.getWorld().playSound(p.getLocation(), Sound.ENTITY_PLAYER_ATTACK_CRIT, 0.6f, 0.7f);
        }
    }

    static void breakShield(Player p, SlashDisplayRegistry owner) {
        if (!p.isBlocking()) return;
        p.clearActiveItem();
        p.setCooldown(Material.SHIELD, shieldCooldownTicks());
        p.getWorld().playSound(p.getLocation(), Sound.ITEM_SHIELD_BREAK, 1.0f, 0.8f);
        Slashes.cross(owner, p.getLocation().add(0, 1.2, 0), 2.6f);
    }

    private static List<Player> playersInside(Session session, Player caster) {
        Location c = session.getCenter();
        World w = c.getWorld();
        List<Player> out = new ArrayList<>();
        if (w == null) return out;
        double r2 = session.getRadius() * session.getRadius();
        for (Player p : w.getPlayers()) {
            if (p.equals(caster) || p.isDead()) continue;
            if (p.getGameMode() == GameMode.CREATIVE || p.getGameMode() == GameMode.SPECTATOR) continue;
            if (p.getLocation().distanceSquared(c) <= r2) out.add(p);
        }
        return out;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Crescent waves
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Fires a volley: up to three waves, each leaving the shrine mouth closest to its target
     * and flying at that target. Waves are staggered 110–220ms apart so they
     * do not all roar out at the same instant. With nobody to hit (solo testing)
     * one wave rolls outward anyway.
     */
    public static void volley(Session session, Player caster) {
        List<Player> targets = playersInside(session, caster);
        Collections.shuffle(targets);
        int count = Math.min(3, targets.size());

        for (int i = 0; i < count; i++) {
            Player t = targets.get(i);
            int best = 0;
            double bestD = Double.MAX_VALUE;
            for (int m = 0; m < 4; m++) {
                double d = Builder.mouthLocation(session, m).distanceSquared(t.getLocation());
                if (d < bestD) {
                    bestD = d;
                    best = m;
                }
            }
            Location origin = Builder.mouthLocation(session, best);
            Vector dir = t.getLocation().toVector().subtract(origin.toVector()).setY(0);
            // Stagger each wave: i * 130ms + jitter, so a triple volley rips across in sequence
            long staggerMs = i * 130L + ThreadLocalRandom.current().nextInt(0, 90);
            launch(session, caster, origin, dir, staggerMs);
        }

        if (count == 0) {
            int m = ThreadLocalRandom.current().nextInt(4);
            Location origin = Builder.mouthLocation(session, m);
            Vector dir = origin.toVector().subtract(session.getShrineCenter().toVector()).setY(0);
            launch(session, caster, origin, dir, 0L);
        }
    }

    private static void launch(Session session, Player caster, Location origin, Vector dir, long staggerMs) {
        if (session.liveWaves >= maxLiveWaves()) return;
        Vector normalized = dir.lengthSquared() < 1.0E-4
                ? new Vector(0, 0, 1) : dir.clone().normalize();
        Location originCopy = origin.clone();
        Vector dirCopy = normalized;

        World w = origin.getWorld();
        long start = System.currentTimeMillis() + Math.max(0L, staggerMs);
        new BukkitRunnable() {
            boolean launched = false;

            @Override
            public void run() {
                // Gated on session liveness: a domain that closed mid-stagger
                // must not fire its wave into a torn-down world.
                switch (DismantleWaveGate.decide(launched, System.currentTimeMillis(), start, session.isActive())) {
                    case CANCEL -> cancel();
                    case LAUNCH -> {
                        launched = true;
                        w.spawnParticle(Particle.LARGE_SMOKE, originCopy, 16, 0.9, 0.9, 0.9, 0.035);
                        w.spawnParticle(Particle.DUST, originCopy, 18, 0.6, 0.7, 0.6, 0,
                                new Particle.DustOptions(org.bukkit.Color.fromRGB(255, 80, 20), 1.2f));
                        w.playSound(originCopy, Sound.ITEM_TRIDENT_THUNDER, 1.6f, 1.55f);
                        w.playSound(originCopy, Sound.ENTITY_PLAYER_ATTACK_SWEEP, 2.2f, 0.45f);
                        w.playSound(originCopy, Sound.ENTITY_GHAST_SHOOT, 1.4f, 0.85f);
                        new Wave(session, caster, originCopy.clone(), dirCopy.clone())
                                .runTaskTimer(ShenanigansCore.getInstance(), 1L, 1L);
                    }
                    default -> {
                        // WAIT: keep checking next tick.
                    }
                }
            }
        }.runTaskTimer(ShenanigansCore.getInstance(), 1L, 1L);
    }

    private static void launch(Session session, Player caster, Location origin, Vector dir) {
        launch(session, caster, origin, dir, 0L);
    }

    private static BlockDisplay spawn(Location loc, BlockData data, float sx, float sy, float sz, float yaw) {
        return loc.getWorld().spawn(loc, BlockDisplay.class, d -> {
            d.setBlock(data);
            d.setPersistent(false);
            d.setViewRange(4.0f);
            d.setBrightness(new Display.Brightness(15, 15));
            d.setTeleportDuration(2);
            TransformUtils.applyTransform(d, sx, sy, sz, yaw, 0f, 0f, 0);
        });
    }

    /** One tall, fast crescent of nine upright blade segments raking the ground. */
    private static final class Wave extends BukkitRunnable {
        private final Session session;
        private final Player caster;
        private final Vector dir;
        private final Vector perp;
        private final Location pos;
        private final BlockDisplay[] core = new BlockDisplay[SEGMENTS];
        private final BlockDisplay[] glow = new BlockDisplay[SEGMENTS];
        private final double[] fwd = new double[SEGMENTS];
        private final double[] lat = new double[SEGMENTS];
        private final float[] len = new float[SEGMENTS];
        private final float[] yaw = new float[SEGMENTS];
        private final Set<UUID> hit = new HashSet<>();
        private final double speed;
        private final int lifetime;
        private final double wobblePhase = ThreadLocalRandom.current().nextDouble() * Math.PI * 2.0;
        private int age = 0;
        private boolean done = false;

        Wave(Session session, Player caster, Location origin, Vector dir) {
            this.session = session;
            this.caster = caster;
            this.dir = dir;
            this.perp = new Vector(-dir.getZ(), 0, dir.getX());
            this.pos = origin;
            this.speed = waveSpeed() * (0.92 + ThreadLocalRandom.current().nextDouble() * 0.22);
            this.lifetime = waveLifetimeTicks();
            session.liveWaves++;

            double span = Math.toRadians(2 * ARC_HALF_ANGLE) / (SEGMENTS - 1);
            for (int i = 0; i < SEGMENTS; i++) {
                double phi = Math.toRadians(-ARC_HALF_ANGLE) + span * i;
                fwd[i] = ARC_RADIUS * (Math.cos(phi) - 1.0);
                lat[i] = ARC_RADIUS * Math.sin(phi);

                // Tangent of the arc at this segment -> blade heading
                double tx = dir.getX() * -Math.sin(phi) + perp.getX() * Math.cos(phi);
                double tz = dir.getZ() * -Math.sin(phi) + perp.getZ() * Math.cos(phi);
                yaw[i] = (float) Math.toDegrees(Math.atan2(-tx, tz));
                len[i] = (float) (ARC_RADIUS * span * 1.35);

                Location l = segLoc(i);
                core[i] = spawn(l, CORE, CORE_THICK, BLADE_HEIGHT, len[i], yaw[i]);
                glow[i] = spawn(l, GLOW, GLOW_THICK, GLOW_HEIGHT, len[i] * 0.92f, yaw[i]);
            }
        }

        private Location segLoc(int i) {
            double x = pos.getX() + dir.getX() * fwd[i] + perp.getX() * lat[i];
            double z = pos.getZ() + dir.getZ() * fwd[i] + perp.getZ() * lat[i];
            double gy = session.floor.groundY(x, z, pos.getY() - 1.5);
            // Slight breathing: the crescent's height undulates as it races
            double bob = Math.sin(age * 0.18 + wobblePhase + i * 0.35) * 0.22;
            return new Location(pos.getWorld(), x, gy + BLADE_HEIGHT / 2.0 + 0.12 + bob, z);
        }

        @Override
        public void run() {
            if (done) return;

            double edge = session.getRadius() + 4.0;
            if (!session.isActive() || age >= lifetime
                    || pos.distanceSquared(session.getCenter()) > edge * edge) {
                finish();
                return;
            }

            age++;
            pos.add(dir.clone().multiply(speed));
            World w = pos.getWorld();

            for (int i = 0; i < SEGMENTS; i++) {
                Location l = segLoc(i);
                if (core[i].isValid()) core[i].teleport(l);
                if (glow[i].isValid()) glow[i].teleport(l);

                // The crescent drags a scar through the floor and throws chunks
                session.floor.scar(l.getX(), l.getZ(), true);
                if ((i & 1) == 0) {
                    w.spawnParticle(Particle.BLOCK, l.getX(), l.getY() - BLADE_HEIGHT / 2.0, l.getZ(),
                            4, 0.35, 0.12, 0.35, 0.06, DEBRIS);
                    w.spawnParticle(Particle.DUST, l.getX(), l.getY() - BLADE_HEIGHT / 2.0, l.getZ(),
                            2, 0.25, 0.15, 0.25, 0, BLOOD_DUST);
                    w.spawnParticle(Particle.CRIT, l, 1, 0.15, 0.25, 0.15, 0.12);
                }
            }

            if (age % 4 == 0) {
                w.playSound(pos, Sound.ENTITY_PLAYER_ATTACK_SWEEP, 0.9f, 0.55f + ThreadLocalRandom.current().nextFloat() * 0.2f);
            }
            if (age % 8 == 0) {
                w.playSound(pos, Sound.BLOCK_DEEPSLATE_BREAK, 0.7f, 0.65f);
            }
            checkHits(w);
        }

        private void checkHits(World w) {
            for (Player p : w.getPlayers()) {
                if (p.equals(caster) || p.isDead() || hit.contains(p.getUniqueId())) continue;
                if (p.getGameMode() == GameMode.CREATIVE || p.getGameMode() == GameMode.SPECTATOR) continue;
                if (!inside(p.getLocation())) continue;

                hit.add(p.getUniqueId());
                shredArmor(p, waveShredFraction());
                breakShield(p, session.slashDisplays);
                Slashes.cross(session.slashDisplays, p.getLocation().add(0, 1.0, 0), 5.5f);
                w.playSound(p.getLocation(), Sound.ENTITY_PLAYER_ATTACK_SWEEP, 1.4f, 0.75f);
                w.playSound(p.getLocation(), Sound.BLOCK_ANVIL_BREAK, 0.8f, 0.9f);
                w.spawnParticle(Particle.CRIT, p.getLocation().add(0, 1.2, 0), 14, 0.4, 0.6, 0.4, 0.18);
            }

            w.getNearbyEntities(pos, 7, 4, 7, e -> (e instanceof Projectile || e instanceof TNTPrimed) && e.isValid())
                    .forEach(e -> {
                        if (e instanceof Projectile pr && pr.getShooter() == caster) return;
                        if (inside(e.getLocation())) scheduleCut(session, e);
                    });
        }

        /** Is this point inside the crescent's swept band? */
        private boolean inside(Location l) {
            double rx = l.getX() - pos.getX();
            double rz = l.getZ() - pos.getZ();
            double f = rx * dir.getX() + rz * dir.getZ();
            double lt = rx * perp.getX() + rz * perp.getZ();

            if (Math.abs(lt) > ARC_RADIUS * Math.sin(Math.toRadians(ARC_HALF_ANGLE)) + 0.8) return false;

            double ratio = Math.min(1.0, Math.abs(lt) / ARC_RADIUS);
            double arcF = ARC_RADIUS * (Math.sqrt(1.0 - ratio * ratio) - 1.0);
            double ground = session.floor.groundY(l.getX(), l.getZ(), pos.getY() - 1.5);
            double dy = l.getY() - ground;
            return Math.abs(f - arcF) < 2.0 && dy > -1.5 && dy < 4.4;
        }

        private void finish() {
            if (done) return;
            done = true;
            cancel();
            session.liveWaves = Math.max(0, session.liveWaves - 1);

            for (int i = 0; i < SEGMENTS; i++) {
                if (core[i].isValid()) {
                    TransformUtils.applyTransform(core[i], 0.01f, BLADE_HEIGHT, len[i], yaw[i], 0f, 0f, 3);
                }
                if (glow[i].isValid()) {
                    TransformUtils.applyTransform(glow[i], 0.01f, GLOW_HEIGHT, len[i], yaw[i], 0f, 0f, 3);
                }
            }
            ShenanigansCore.runTaskLater( () -> {
                for (int i = 0; i < SEGMENTS; i++) {
                    core[i].remove();
                    glow[i].remove();
                }
            }, 4L);
        }
    }
}
