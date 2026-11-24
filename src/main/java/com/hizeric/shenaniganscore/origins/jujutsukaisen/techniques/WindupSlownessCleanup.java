package com.hizeric.shenaniganscore.origins.jujutsukaisen.techniques;

import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The Bukkit half of windup-root ownership: applies an exact effect, verifies identity before
 * removal, and restores whatever root the player had. Kept in one module so all five techniques
 * cannot drift apart on their grant/consume exit paths.
 */
public final class WindupSlownessCleanup {

    /**
     * Last-grant-wins ownership per caster. Overlapping windups must not let an older cast restore
     * the root it snapshotted over a newer cast's effect (issue #37 / SP-C-002).
     */
    private static final Map<UUID, WindupSlowness> OWNERS = new ConcurrentHashMap<>();

    private WindupSlownessCleanup() { }

    public static void grant(Player caster, WindupSlowness tracker, int duration, int amplifier) {
        if (caster == null || tracker == null) return;
        UUID id = caster.getUniqueId();
        WindupSlowness displacedOwner = OWNERS.get(id);
        PotionEffect previous = caster.getPotionEffect(PotionEffectType.SLOWNESS);
        Integer previousDuration = previous == null ? null : previous.getDuration();
        Integer previousAmplifier = previous == null ? null : previous.getAmplifier();
        if (displacedOwner != null) {
            // The live effect belongs to an overlapping windup that is about to lose ownership.
            // Inherit what that windup replaced instead of snapshotting its transient root; otherwise
            // the newest cast would restore a root whose owner has already exited (issue #37).
            previousDuration = displacedOwner.previousDuration();
            previousAmplifier = displacedOwner.previousAmplifier();
        }
        caster.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS,
                duration, amplifier, false, false, true));
        tracker.grant(duration, amplifier, previousDuration, previousAmplifier);
        OWNERS.put(id, tracker);
    }

    public static void clear(Player caster, WindupSlowness tracker) {
        if (caster == null || tracker == null || !tracker.consume() || !caster.isOnline()) return;
        UUID id = caster.getUniqueId();
        if (OWNERS.get(id) != tracker) return;
        OWNERS.remove(id, tracker);
        PotionEffect current = caster.getPotionEffect(PotionEffectType.SLOWNESS);
        if (current != null && !tracker.matches(current.getDuration(), current.getAmplifier())) return;
        caster.removePotionEffect(PotionEffectType.SLOWNESS);
        Integer duration = tracker.previousDuration();
        Integer amplifier = tracker.previousAmplifier();
        if (duration != null && amplifier != null) {
            caster.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS,
                    duration, amplifier, false, false, true));
        }
    }

    /** Drops ownership when a player leaves, so the map cannot accumulate stale UUIDs. */
    public static void forget(UUID playerId) {
        if (playerId != null) OWNERS.remove(playerId);
    }
}
