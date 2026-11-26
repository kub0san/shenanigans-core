package com.hizeric.shenaniganscore.origins.jujutsukaisen.domainexpansions.malevolentshrine;

import com.hizeric.shenaniganscore.ShenanigansCore;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;

import java.util.concurrent.ThreadLocalRandom;

/**
 * The living half of the Sure-Hit effect — <b>RAVAGE</b> redesign.
 *
 * <p><b>Old Cleave:</b> one 40% hit, a small cross, a sweep sound. Read as a
 * single slash. Players could walk through it, shrug it off, and it felt slow
 * because a whole second passed between identical hits.</p>
 *
 * <p><b>New RAVAGE:</b> each 1s pulse is a 0.55s choreography that converges
 * on the victim — a long <i>lash</i> from the shrine mouth, a <i>helix</i> of
 * orbiting blades, an <i>implosion</i> that closes the world around them, and
 * a <i>mega-cross</i>. Between pulses, <i>micro-cuts</i> every 0.25s keep the
 * air tearing so there is never a quiet moment. Victims are briefly blinded,
 * nauseated and slowed, and each successive ravage escalates (more blades,
 * larger cuts) until they escape or die.</p>
 *
 * <p>Lore-accurate core unchanged: Cleave adapts to the target and always lands,
 * so we bypass invulnerability frames and deal a fixed percentage of max
 * health. Dismantle (inanimate things: gear, projectiles, terrain) still lives
 * in {@link Dismantle}.</p>
 *
 * <p>All timing uses {@code System.currentTimeMillis()} inside
 * {@link org.bukkit.scheduler.BukkitRunnable}.</p>
 */
public final class Cleave {

    /** Hardcoded to the shipped value; no fallback needed. */
    private static final double DEFAULT_DAMAGE_FRACTION = 0.50;

    private static final Particle.DustOptions BLOOD =
            new Particle.DustOptions(org.bukkit.Color.fromRGB(140, 0, 10), 1.5f);
    private static final Particle.DustOptions IMPACT_FLASH =
            new Particle.DustOptions(org.bukkit.Color.fromRGB(255, 255, 255), 2.0f);

    private Cleave() {}

    /**
     * Cleave percent (hardcoded, clamped to a sane 1–100%).
     *
     * <p>Max-health lookup note: this class deliberately uses
     * {@link LivingEntity#getMaxHealth()} rather than reading the attribute
     * field directly. The field was renamed during Bukkit's registry
     * flattening ({@code GENERIC_MAX_HEALTH} -> {@code MAX_HEALTH}), so a
     * direct field reference compiled against the 1.21.1 dev API crashes
     * with {@code NoSuchFieldError} on newer servers (Leaf 26.3). The
     * accessor method is attribute-backed on every version.</p>
     */
    private static double damageFraction() {
        double percent = DEFAULT_DAMAGE_FRACTION * 100.0;
        if (percent < 1.0 || percent > 100.0) return DEFAULT_DAMAGE_FRACTION;
        return percent / 100.0;
    }

    public static void cleave(Session session, Player caster) {
        Location center = session.getCenter();
        double radius = session.getRadius();
        World world = center.getWorld();
        if (world == null) return;

        // The shrine's jaws slam shut on every pulse
        session.bite();
        Location maw = Builder.mouthLocation(session, 0);
        maw.getWorld().playSound(maw, Sound.BLOCK_BONE_BLOCK_BREAK, 2.2f, 0.45f);
        maw.getWorld().playSound(maw, Sound.ENTITY_WARDEN_EMERGE, 1.6f, 0.55f);
        world.playSound(shrineAnchor(session), Sound.ENTITY_WARDEN_HEARTBEAT, 2.2f, 0.6f);

        // Gather first, then stagger — never two victims on the same beat
        java.util.List<LivingEntity> victims = new java.util.ArrayList<>();
        world.getNearbyEntities(center, radius, radius, radius, e ->
                e instanceof LivingEntity living && living.isValid() && !(living instanceof ArmorStand)
        ).forEach(rawEntity -> {
            LivingEntity entity = (LivingEntity) rawEntity;
            if (entity.equals(caster)) return;
            if (entity instanceof Player p && (p.isDead()
                    || p.getGameMode() == GameMode.CREATIVE
                    || p.getGameMode() == GameMode.SPECTATOR)) return;
            victims.add(entity);
        });

        ThreadLocalRandom stagger = ThreadLocalRandom.current();
        for (int i = 0; i < victims.size(); i++) {
            // Each victim gets its own delay: sorted by index + jitter, so with
            // 4 people the four flurries ripple across the arena in 0–360ms.
            long delayMs = i * 90L + stagger.nextInt(0, 130);
            // Fall back to hash-based offset if there are many victims
            if (victims.size() > 3) {
                delayMs = (long) (((victims.get(i).getUniqueId().hashCode() & 0xFF) * 1.6) % 401);
            }
            ravage(session, caster, victims.get(i), delayMs);
        }
    }

    private static Location shrineAnchor(Session session) {
        try {
            return session.getShrineCenter();
        } catch (Throwable ignore) {
            return session.getCenter();
        }
    }

    private static void ravage(Session session, Player caster, LivingEntity entity, long delayMs) {
        World world = entity.getWorld();
        if (world == null) return;

        Location hitChest = entity.getLocation().add(0, 1.0, 0);
        Location hitCenter = entity.getLocation().add(0, entity.getHeight() * 0.55, 0);

        // Escalation: every ravage on this victim gets bigger, until they leave.
        int level = session.ravageCounts.merge(entity.getUniqueId(), 1, Integer::sum);
        int lvl = Math.max(1, Math.min(10, level));

        // Closest mouth for the lash so it always looks like the shrine reached.
        Location mouth = closestMouth(session, entity.getLocation());

        // ── The blowout sounds (layered, staggered by the ravage schedule) ──
        world.playSound(hitCenter, Sound.ENTITY_WARDEN_SONIC_BOOM, 1.6f, 0.55f);
        world.playSound(hitCenter, Sound.ITEM_TRIDENT_THUNDER, 1.2f, 1.35f);
        if (lvl >= 3) {
            world.playSound(hitCenter, Sound.ENTITY_ENDER_DRAGON_GROWL, 1.2f, 0.7f);
        }

        // Use the accessor, not the attribute field directly (cross-version safety)
        double maxHealth = entity.getMaxHealth();

        // Sure-Hit: bypass invulnerability frames, no knockback (velocity is saved and restored)
        Vector originalVelocity = entity.getVelocity();
        int originalNoDamageTicks = entity.getNoDamageTicks();

        // Freeze-frame for 2 ticks so the first impact reads, then the flurry.
        entity.setVelocity(new Vector(0, 0, 0));
        entity.setNoDamageTicks(0);
        
        // Creative addition: "Spatial Strobe" effect right before the final hit sequence
        // The impact reads through the cuts, the blood and the screen shake —
        // no blackout. The shrine's blindness is gone; the arena stays legible.
        entity.damage(Math.max(1.0, maxHealth * damageFraction()), caster);
        entity.setVelocity(originalVelocity);
        entity.setNoDamageTicks(originalNoDamageTicks);

        // Scar the floor directly beneath the victim to tie the sure-hit damage to the environment
        session.floor.scar(entity.getLocation().getX(), entity.getLocation().getZ(), true);

        // ── The barrage — randomized, overshooting, never centred on them ──
        ThreadLocalRandom r = ThreadLocalRandom.current();
        
        // Creative addition: Directional Impact Vector (Pushing blood away from the mouth)
        Vector impactDir = mouth.toVector().subtract(entity.getLocation().toVector()).normalize().multiply(-1);
        
        Slashes.lash(session.slashDisplays, mouth, hitChest, 0.075f);
        // Opening: one long, mostly horizontal cut (sparse), then the spaced ravage.
        float opening = 9.0f + lvl * 0.8f + r.nextFloat(0f, 3.0f);
        Slashes.cross(session.slashDisplays, hitCenter, opening);
        if (r.nextInt(4) == 0) {
            Slashes.rake(session.slashDisplays, hitCenter, r.nextFloat(0f, 360f),
                    (r.nextFloat() - 0.5f) * 18f, 7.0f + r.nextFloat(0f, 2.5f));
        }
        Slashes.ravage(session, session.slashDisplays, mouth, hitChest, lvl, delayMs);

        // Immediate blood burst with physics directionality
        world.spawnParticle(Particle.SWEEP_ATTACK, hitCenter, 1, 0.1, 0.1, 0.1, 0);
        world.spawnParticle(Particle.DAMAGE_INDICATOR, hitCenter, 5, 0.2, 0.2, 0.2, 0.4);
        
        // Directional Blood Splatter
        world.spawnParticle(Particle.DUST, hitCenter, 20 + lvl * 5, 
            impactDir.getX() * 0.5, impactDir.getY() * 0.5 + 0.5, impactDir.getZ() * 0.5, 
            0.1, BLOOD);
            
        // Environmental Impact (CRACKing the ground they stand on)
        world.spawnParticle(Particle.BLOCK, entity.getLocation(), 15, 0.3, 0.1, 0.3, 0.1,
            entity.getLocation().clone().subtract(0, 0.1, 0).getBlock().getBlockData());

        world.spawnParticle(Particle.BLOCK, hitCenter, 5, 0.25, 0.35, 0.25, 0.08,
                org.bukkit.Material.BLACKSTONE.createBlockData());
        world.spawnParticle(Particle.SQUID_INK, hitCenter, 3, 0.2, 0.2, 0.2, 0.02);


        // Screen shake: session-owned rotation jitter + small velocity, like being torn open
        if (entity instanceof Player p) {
            session.jitter(p, 7.0, 200L);
            double dmg = Math.max(1.0, maxHealth * damageFraction());
            p.setVelocity(p.getVelocity().add(new Vector(
                    (r.nextDouble() - 0.5) * 0.55,
                    (r.nextDouble() - 0.5) * 0.35,
                    (r.nextDouble() - 0.5) * 0.55)));

            // Overwhelm debuffs — short, stacked, then released next pulse.
            // Nausea and the slowdown say "you are being torn apart"; nothing
            // takes the screen away from the victim.
            p.addPotionEffect(new PotionEffect(PotionEffectType.NAUSEA, 26, 0, false, false, true));
            p.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 18, 2, false, false, true));
            p.addPotionEffect(new PotionEffect(PotionEffectType.HUNGER, 20, 1, false, false, true));

            double distanceToEdge = Math.max(0.0,
                    session.getRadius() - p.getLocation().distance(session.getCenter()));
            String tag = lvl >= 5 ? "<dark_red><bold>RAVAGE x" + lvl + "</bold>"
                    : "<dark_red>Cleave";
            p.sendActionBar(MiniMessage.miniMessage().deserialize(
                    tag + " <dark_gray>| <red>-" + (int) Math.ceil(dmg) + "❤"
                            + " <gray>Escape route: <white>" + (int) Math.ceil(distanceToEdge) + "m"));
            if (lvl == 4) {
                p.sendActionBar(MiniMessage.miniMessage().deserialize(
                        "<dark_red><bold>✂ 被斬! <red>The shrine has your scent. <gray>RUN.</gray></bold>"));
            }
        } else {
            // Mobs get the same freeze + stagger so they visibly lock up
            entity.setVelocity(new Vector(0, 0.15, 0));
            world.spawnParticle(Particle.DUST, hitCenter, 8, 0.3, 0.4, 0.3, 0, BLOOD);
        }
    }

    /** Visual-only micro-cuts between the 1s pulses. No damage. */
    public static void microCut(Session session, Player caster) {
        Location center = session.getCenter();
        World world = center.getWorld();
        if (world == null) return;
        double r2 = session.getRadius() * session.getRadius();

        world.getNearbyEntities(center, session.getRadius(), session.getRadius(),
                session.getRadius(), e -> e instanceof LivingEntity living
                        && living.isValid() && !(living instanceof ArmorStand)
        ).forEach(raw -> {
            LivingEntity entity = (LivingEntity) raw;
            if (entity.equals(caster)) return;
            if (entity.getLocation().distanceSquared(center) > r2) return;
            if (entity instanceof Player p && (p.isDead()
                    || p.getGameMode() == GameMode.CREATIVE
                    || p.getGameMode() == GameMode.SPECTATOR)) return;

            Location chest = entity.getLocation().add(0, 0.85 + ThreadLocalRandom.current().nextDouble() * 0.6, 0);
            Slashes.microCut(session.slashDisplays, chest);
            world.playSound(chest, Sound.ENTITY_PLAYER_ATTACK_SWEEP, 0.55f, 0.95f + ThreadLocalRandom.current().nextFloat() * 0.4f);
        });
    }

    private static Location closestMouth(Session session, Location near) {
        double best = Double.MAX_VALUE;
        Location bestLoc = Builder.mouthLocation(session, 0);
        for (int m = 0; m < 4; m++) {
            Location mouth = Builder.mouthLocation(session, m);
            if (mouth == null) continue;
            double d = mouth.distanceSquared(near);
            if (d < best) {
                best = d;
                bestLoc = mouth;
            }
        }
        return bestLoc;
    }
}
