package com.hizeric.shenaniganscore.origins.jujutsukaisen.domainexpansions.wombprofusion;

import com.hizeric.shenaniganscore.ShenanigansCore;
import com.hizeric.shenaniganscore.core.display.DisplayUtils;
import com.hizeric.shenaniganscore.core.display.TransformUtils;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.damage.DamageSource;
import org.bukkit.damage.DamageType;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.UUID;

/**
 * The sure-hit: an explosive blast wave in the manner of Maximum: Uzumaki.
 *
 * <h3>Visuals</h3>
 * <ul>
 *   <li><b>Shockwave sphere</b>: ~100 glowing glass plates, tangent to a sphere that races out of the
 *       core. One interpolated teleport each: the client draws the whole expansion.</li>
 *   <li><b>Spiral arms</b>: three arms of small bright cubes lying across the ground. They are sent to
 *       their final spiral positions all at once, so the spiral <i>scales up</i> from the core
 *       as the wave moves out.</li>
 *   <li><b>Ground ring</b>: debris and dust riding the front across the carpet.</li>
 * </ul>
 *
 * <h3>Sure-hit</h3>
 * The wave grows linearly, matching the interpolation the client is drawing. Each tick, every living
 * thing inside the domain's radius that the front has reached is hit once: armour-ignoring magic
 * damage (a percentage of its max health, optionally never lethal), a hard knock away from the tower,
 * and a few seconds of slowness, weakness and nausea. Anyone outside the radius is untouched: the edge
 * of the carpet is the edge of the blast.
 */
public final class Blast {

    /** Ticks for the wave to cross from the core to its full extent. */
    public static final int TRAVEL_TICKS = 18;

    private static final float SEED = 0.01f;
    private static final Random RANDOM = new Random(0xB1A57L);
    private static final Particle.DustOptions VIOLET = new Particle.DustOptions(Color.fromRGB(190, 90, 255), 1.6f);
    private static final Particle.DustOptions BLOOD = new Particle.DustOptions(Color.fromRGB(150, 10, 40), 1.3f);

    private final World world;
    private final Location origin;
    private final Location center;
    private final double radius;
    private final double maxFront;
    private final Player caster;
    private final double damagePercent;
    private final boolean lethal;
    private final double knockback;
    private final List<BlockDisplay> plates = new ArrayList<>();
    private final List<double[]> dirs = new ArrayList<>();
    private final List<Quaternionf> rotations = new ArrayList<>();
    private final List<BlockDisplay> arms = new ArrayList<>();
    private final Set<UUID> hit = new HashSet<>();

    private int tick = -1;
    private boolean closed;

    public Blast(Location origin, Location center, double radius, Player caster,
                 double damagePercent, boolean lethal, double knockback, double quality, List<Entity> sink) {
        this.origin = origin.clone();
        this.center = center.clone();
        this.world = origin.getWorld();
        this.radius = radius;
        this.maxFront = radius * 1.45;
        this.caster = caster;
        this.damagePercent = Math.max(1.0, Math.min(100.0, damagePercent));
        this.lethal = lethal;
        this.knockback = Math.max(0.0, Math.min(5.0, knockback));
        if (world == null) return;

        int count = (int) Math.max(60, Math.min(180, Math.round(100 * quality)));
        double golden = Math.PI * (3.0 - Math.sqrt(5.0));
        for (int i = 0; i < count; i++) {
            double y = 1.0 - (2.0 * i + 1.0) / count;
            double r = Math.sqrt(Math.max(0.0, 1.0 - y * y));
            double theta = golden * i;
            double[] d = {Math.cos(theta) * r, y, Math.sin(theta) * r};
            dirs.add(d);
            rotations.add(new Quaternionf()
                    .rotateY((float) Math.atan2(d[0], d[2]))
                    .rotateX((float) -Math.asin(Math.max(-1.0, Math.min(1.0, d[1])))));

            BlockDisplay p = DisplayUtils.spawnBlock(origin,
                    i % 3 == 0 ? Material.MAGENTA_STAINED_GLASS : Material.PURPLE_STAINED_GLASS, 8.0f, 15);
            TransformUtils.applyTransform(p, SEED, SEED, SEED, 0f, 0f, 0f, 0);
            p.setTeleportDuration(0);
            plates.add(p);
            if (sink != null) sink.add(p);
        }

        Material[] armMaterials = {Material.WHITE_CONCRETE, Material.MAGENTA_CONCRETE, Material.PURPLE_CONCRETE};
        for (int arm = 0; arm < 3; arm++) {
            for (int i = 0; i < 24; i++) {
                BlockDisplay c = DisplayUtils.spawnBlock(origin, armMaterials[(arm + i) % 3], 8.0f, 15);
                TransformUtils.applyTransform(c, SEED, SEED, SEED, 0f, 0f, 0f, 0);
                c.setTeleportDuration(0);
                arms.add(c);
                if (sink != null) sink.add(c);
            }
        }
    }

    /** Fires the blast. */
    public void start() {
        if (tick >= 0 || closed || world == null) return;
        tick = 0;

        // The sphere of plates.
        double side = Math.sqrt(4.0 * Math.PI * maxFront * maxFront / plates.size()) * 1.14;
        for (int i = 0; i < plates.size(); i++) {
            BlockDisplay p = plates.get(i);
            if (!p.isValid()) continue;
            double[] d = dirs.get(i);
            TransformUtils.applyTransform(p, new Vector3f((float) side, (float) side, 0.5f), rotations.get(i),
                    new Vector3f(0.5f, 0.5f, 0.5f), TRAVEL_TICKS);
            p.setTeleportDuration(TRAVEL_TICKS);
            p.teleport(origin.clone().add(d[0] * maxFront, d[1] * maxFront, d[2] * maxFront));
        }

        // The spiral arms, lying across the ground, scaling up with the wave.
        double groundY = center.getY() + 1.2;
        for (int a = 0; a < 3; a++) {
            for (int i = 0; i < 24; i++) {
                BlockDisplay c = arms.get(a * 24 + i);
                if (!c.isValid()) continue;
                double s = (i + 1) / 24.0;
                double angle = a * Math.PI * 2.0 / 3.0 + s * Math.PI * 2.4;
                double dist = maxFront * 0.96 * s;
                float size = (float) (0.5 + RANDOM.nextDouble() * 0.9);
                TransformUtils.applyTransform(c, size, size, size, RANDOM.nextFloat() * 90f, 0f, 0f, TRAVEL_TICKS);
                c.setTeleportDuration(TRAVEL_TICKS);
                c.teleport(new Location(world, center.getX() + Math.cos(angle) * dist,
                        groundY + Math.sin(s * 9.0) * 0.6, center.getZ() + Math.sin(angle) * dist));
            }
        }

        world.playSound(origin, Sound.ENTITY_GENERIC_EXPLODE, 3.0f, 0.5f);
        world.playSound(origin, Sound.ENTITY_WARDEN_SONIC_BOOM, 3.0f, 0.55f);
        world.playSound(origin, Sound.ENTITY_ENDER_DRAGON_GROWL, 2.0f, 0.5f);
        world.spawnParticle(Particle.EXPLOSION_EMITTER, origin, 2, 0.5, 0.5, 0.5, 0);
        flash(origin);
    }

    /** FLASH needs colour data on newer servers and none on older ones. */
    private void flash(Location at) {
        try {
            world.spawnParticle(Particle.FLASH, at, 1, 0, 0, 0, 0, Color.WHITE);
        } catch (Throwable t) {
            try {
                world.spawnParticle(Particle.FLASH, at, 1, 0, 0, 0, 0);
            } catch (Throwable ignored) {
                world.spawnParticle(Particle.END_ROD, at, 24, 0.5, 0.5, 0.5, 0.2);
            }
        }
    }

    public void tick() {
        if (closed || tick < 0 || world == null) return;
        tick++;
        double front = maxFront * Math.min(1.0, tick / (double) TRAVEL_TICKS);

        if (tick <= TRAVEL_TICKS) {
            ground(front);
            strike(front);
        }
        if (tick == TRAVEL_TICKS + 2) dissolve(10);
    }

    /** Debris and dust riding the front across the carpet. */
    private void ground(double front) {
        double ring = front * 0.9;
        if (ring < 1.0 || ring > radius + 1.0) return;
        int points = (int) Math.min(90, Math.max(24, ring * 2.2));
        for (int i = 0; i < points; i++) {
            double a = Math.PI * 2.0 * i / points + tick * 0.15;
            double x = center.getX() + Math.cos(a) * ring;
            double z = center.getZ() + Math.sin(a) * ring;
            double y = world.getHighestBlockYAt((int) Math.floor(x), (int) Math.floor(z)) + 1.2;
            world.spawnParticle(Particle.DUST, x, y, z, 1, 0.2, 0.3, 0.2, 0, (i & 1) == 0 ? VIOLET : BLOOD);
            if (i % 4 == 0) {
                world.spawnParticle(Particle.BLOCK, x, y, z, 3, 0.3, 0.2, 0.3, 0.15,
                        Material.PINK_TERRACOTTA.createBlockData());
            }
        }
    }

    private void strike(double front) {
        double r2 = radius * radius;
        for (Entity raw : world.getNearbyEntities(origin, maxFront, maxFront, maxFront,
                e -> e instanceof LivingEntity && !(e instanceof ArmorStand))) {
            LivingEntity le = (LivingEntity) raw;
            if (le.equals(caster) || hit.contains(le.getUniqueId())) continue;
            if (le instanceof Player p && (p.getGameMode() == GameMode.CREATIVE
                    || p.getGameMode() == GameMode.SPECTATOR || p.isDead())) continue;
            if (le.getLocation().distanceSquared(center) > r2) continue;
            if (le.getLocation().distance(origin) > front) continue;
            hit.add(le.getUniqueId());
            hit(le);
        }
    }

    private void hit(LivingEntity le) {
        double amount = le.getMaxHealth() * damagePercent / 100.0;
        if (!lethal) amount = Math.min(amount, Math.max(0.0, le.getHealth() - 1.0));
        if (amount > 0.0) hurt(le, amount);

        Vector away = le.getLocation().toVector().subtract(center.toVector()).setY(0);
        if (away.lengthSquared() < 1.0e-4) away = new Vector(RANDOM.nextDouble() - 0.5, 0, RANDOM.nextDouble() - 0.5);
        Vector velocity = away.normalize().multiply(knockback).setY(0.65);
        ShenanigansCore.runTask( () -> {
            if (le.isValid()) le.setVelocity(velocity);
        });

        if (le instanceof Player p) {
            p.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 80, 1, false, false, true));
            p.addPotionEffect(new PotionEffect(PotionEffectType.WEAKNESS, 160, 0, false, false, true));
            p.addPotionEffect(new PotionEffect(PotionEffectType.NAUSEA, 80, 0, false, false, true));
        }
        Location at = le.getLocation().add(0, le.getHeight() * 0.5, 0);
        world.spawnParticle(Particle.EXPLOSION, at, 1, 0, 0, 0, 0);
        world.spawnParticle(Particle.DUST, at, 30, 0.4, 0.6, 0.4, 0, VIOLET);
        world.spawnParticle(Particle.DUST, at, 18, 0.4, 0.6, 0.4, 0, BLOOD);
        world.playSound(at, Sound.ENTITY_GENERIC_EXPLODE, 1.6f, 0.7f);
        world.playSound(at, Sound.ENTITY_PLAYER_ATTACK_CRIT, 1.4f, 0.5f);
    }

    /** Armour-ignoring magic damage credited to the caster, with no i-frame throttling. */
    private void hurt(LivingEntity le, double amount) {
        int noDamage = le.getNoDamageTicks();
        le.setNoDamageTicks(0);
        try {
            le.damage(amount, DamageSource.builder(DamageType.MAGIC).withCausingEntity(caster).build());
        } catch (Throwable t) {
            le.damage(amount, caster);
        }
        le.setNoDamageTicks(noDamage);
    }

    private void dissolve(int ticks) {
        for (int i = 0; i < plates.size(); i++) {
            BlockDisplay p = plates.get(i);
            if (p.isValid()) {
                TransformUtils.applyTransform(p, new Vector3f(SEED), rotations.get(i),
                        new Vector3f(0.5f, 0.5f, 0.5f), ticks);
            }
        }
        for (BlockDisplay c : arms) {
            if (c.isValid()) TransformUtils.applyTransform(c, SEED, SEED, SEED, 0f, 0f, 0f, ticks);
        }
    }

    public boolean finished() {
        return tick > TRAVEL_TICKS + 14;
    }

    public void close() {
        if (closed) return;
        closed = true;
        for (BlockDisplay p : plates) if (p.isValid()) p.remove();
        for (BlockDisplay c : arms) if (c.isValid()) c.remove();
        plates.clear();
        arms.clear();
        dirs.clear();
        rotations.clear();
    }
}
