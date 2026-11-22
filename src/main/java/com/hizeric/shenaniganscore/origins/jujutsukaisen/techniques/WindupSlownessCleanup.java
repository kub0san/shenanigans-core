package com.hizeric.shenaniganscore.origins.jujutsukaisen.techniques;

import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

/**
 * The Bukkit half of windup-root ownership: applies an exact effect, verifies identity before
 * removal, and restores whatever root the player had. Kept in one module so all five techniques
 * cannot drift apart on their grant/consume exit paths.
 */
public final class WindupSlownessCleanup {

    private WindupSlownessCleanup() { }

    public static void grant(Player caster, WindupSlowness tracker, int duration, int amplifier) {
        if (caster == null || tracker == null) return;
        PotionEffect previous = caster.getPotionEffect(PotionEffectType.SLOWNESS);
        caster.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS,
                duration, amplifier, false, false, true));
        tracker.grant(duration, amplifier,
                previous == null ? null : previous.getDuration(),
                previous == null ? null : previous.getAmplifier());
    }

    public static void clear(Player caster, WindupSlowness tracker) {
        if (caster == null || tracker == null || !tracker.consume() || !caster.isOnline()) return;
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
}
