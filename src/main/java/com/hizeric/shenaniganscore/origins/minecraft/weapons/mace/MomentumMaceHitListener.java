package com.hizeric.shenaniganscore.origins.minecraft.weapons.mace;

import com.hizeric.shenaniganscore.core.config.AbilityTuningConfig;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.inventory.ItemStack;

/** PvP-only damage profiles, confirmed-hit charge consumption, and Impact Rebound. */
public final class MomentumMaceHitListener implements Listener {
    private static double cap(String field, double fallback) {
        return AbilityTuningConfig.current().boundedDouble(AbilityTuningConfig.Section.ITEMS,
                "momentum-mace", field, fallback, 0.0, 40.0);
    }

    private boolean valid(ItemStack hand, EntityDamageByEntityEvent e) {
        return e.getDamager() instanceof Player
                && e.getEntity() instanceof Player
                && MomentumMaceItem.isMomentumMace(hand);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onDamage(EntityDamageByEntityEvent e) {
        if (!(e.getDamager() instanceof Player attacker)) return;
        if (!(e.getEntity() instanceof Player)) return;
        ItemStack hand = attacker.getInventory().getItemInMainHand();
        if (!valid(hand, e)) return;
        boolean empowered = MomentumMaceChargeManager.hasCharge(attacker);
        double limit = empowered ? cap("empowered-hit-damage-cap", 12.0) : cap("depleted-hit-damage-cap", 4.0);
        if (e.getDamage() > limit) e.setDamage(limit);
        if (e.getDamage() <= 0) return;
        // Impact Rebound — keeps horizontal momentum, adds a small upward impulse.
        var velocity = attacker.getVelocity();
        velocity.setY(Math.max(velocity.getY(), cap("impact-rebound-y", 0.45)));
        attacker.setVelocity(velocity);
        attacker.setFallDistance(0f);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onConfirmedHit(EntityDamageByEntityEvent e) {
        if (!(e.getDamager() instanceof Player attacker)) return;
        if (!(e.getEntity() instanceof Player)) return;
        ItemStack hand = attacker.getInventory().getItemInMainHand();
        if (!valid(hand, e)) return;
        if (e.getFinalDamage() <= 0) return;
        MomentumMaceChargeManager.consumeCharge(attacker);
    }
}
