package com.hizeric.shenaniganscore.origins.jujutsukaisen.domainexpansions.unlimitedvoid;

import com.hizeric.shenaniganscore.ShenanigansCore;
import com.hizeric.shenaniganscore.core.session.Immobilize;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;

import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;
import java.util.UUID;

/**
 * The sure-hit of Unlimited Void — now a <b>staged</b> overload instead of an instant freeze.
 *
 * <ol>
 *   <li><b>Stage 0 — onset</b> (first {@code onset-seconds}, default 0.5): the
 *       victim feels the world go wrong. Heavy slowness, no hard lock yet.</li>
 *   <li><b>Stage 1 — locked</b>: movement is hard-locked by {@code MovementListener}
 *       (via {@link Immobilize}); nausea + weakness; a light "information burn".</li>
 *   <li><b>Stage 2 — deep</b> (after {@code deep-seconds}, default 4): pulsing darkness
 *       on top. Exposure is tracked per victim, so someone who leaves the domain
 *       starts over.</li>
 * </ol>
 *
 * <p>Exempt: the caster, creative and spectator players, armor stands. Refreshed
 * every {@link Session#OVERLOAD_STEP_TICKS} ticks so late arrivals are caught quickly.</p>
 *
 * <p>Config ({@code jujutsukaisen.unlimitedvoid.*}): {@code effect-seconds},
 * {@code psychic-damage}, {@code deep-seconds}.</p>
 */
public final class Overload {

    private Overload() {}
    private static final double DEFAULT_EFFECT_SECONDS = 3.0;
    private static final double DEFAULT_PSYCHIC_DAMAGE = 2.0;
    private static final double DEFAULT_DEEP_SECONDS = 4.0;

    public static void apply(Session session, Player caster) {
        Location center = session.getCenter();
        World world = center.getWorld();
        if (world == null) return;


        double effectSeconds = DEFAULT_EFFECT_SECONDS;
        if (effectSeconds < 0.2 || effectSeconds > 60.0) {
            effectSeconds = DEFAULT_EFFECT_SECONDS;
        }
        int effectTicks = (int) Math.round(effectSeconds * 20.0);

        double psychicDamage = DEFAULT_PSYCHIC_DAMAGE;
        if (psychicDamage < 0.0 || psychicDamage > 20.0) psychicDamage = DEFAULT_PSYCHIC_DAMAGE;

        double deepSeconds = DEFAULT_DEEP_SECONDS;
        if (deepSeconds < 0.5 || deepSeconds > 60.0) deepSeconds = DEFAULT_DEEP_SECONDS;
        int deepTicks = (int) Math.round(deepSeconds * 20.0);
        int onsetTicks = session.getOnsetTicks();

        double radius = session.getRadius();
        double radiusSquared = radius * radius;

        Set<UUID> seen = new HashSet<>();
        Set<UUID> locked = new HashSet<>();
        session.victimStages.clear();

        for (Entity entity : world.getNearbyEntities(center, radius, radius, radius)) {
            if (!(entity instanceof LivingEntity living)) continue;
            if (!living.isValid() || living.isDead()) continue;
            if (living instanceof ArmorStand) continue;
            if (living.getUniqueId().equals(caster.getUniqueId())) continue;
            if (living.getLocation().distanceSquared(center) > radiusSquared) continue;

            if (living instanceof Player player) {
                GameMode mode = player.getGameMode();
                if (mode == GameMode.SPECTATOR || mode == GameMode.CREATIVE) continue;

                UUID id = player.getUniqueId();
                seen.add(id);
                int exposure = session.exposureTicks.merge(id, Session.OVERLOAD_STEP_TICKS, Integer::sum);

                int stage;
                if (exposure < onsetTicks) stage = 0;
                else if (exposure < deepTicks) stage = 1;
                else stage = 2;
                session.victimStages.put(id, stage);

                if (stage == 0) {
                    player.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS,
                            Session.OVERLOAD_STEP_TICKS * 2 + 2, 2, false, false, true));
                    continue;
                }

                locked.add(id);

                player.addPotionEffect(new PotionEffect(PotionEffectType.NAUSEA,
                        effectTicks, 0, false, false, true));
                player.addPotionEffect(new PotionEffect(PotionEffectType.WEAKNESS,
                        effectTicks, 1, false, false, true));
                if (stage >= 2) {
                    player.addPotionEffect(new PotionEffect(PotionEffectType.DARKNESS,
                            60, 0, false, false, true));
                }

                boolean secondMark = exposure % 20 == 0;
                if (secondMark && psychicDamage > 0.0) {
                    // Very light "information burn" — overwhelm, not impact.
                    player.damage(psychicDamage * 0.5, caster);
                }
                if (secondMark) {
                    player.playSound(player.getLocation(),
                            Sound.AMBIENT_SOUL_SAND_VALLEY_MOOD, 0.25f, stage >= 2 ? 0.7f : 0.9f);
                }

                // Stop horizontal drift; let gravity keep working.
                Vector v = player.getVelocity();
                player.setVelocity(new Vector(0, Math.min(v.getY(), 0), 0));

            } else {
                if (living.hasAI() && !session.aiLockedMobs.contains(living)) {
                    session.aiLockedMobs.add(living);
                    living.setAI(false);
                }
            }
        }

        // Anyone who left the domain starts over.
        session.exposureTicks.keySet().retainAll(seen);
        session.immobilizedPlayers.clear();
        session.immobilizedPlayers.addAll(locked);

        // Release mobs that wandered out.
        Iterator<LivingEntity> it = session.aiLockedMobs.iterator();
        while (it.hasNext()) {
            LivingEntity mob = it.next();
            if (!mob.isValid()) {
                it.remove();
                continue;
            }
            if (mob.getWorld() != world || mob.getLocation().distanceSquared(center) > radiusSquared) {
                mob.setAI(true);
                it.remove();
            }
        }
    }
}
