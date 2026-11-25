package com.hizeric.shenaniganscore.origins.minecraft.weapons.mace;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.inventory.ItemStack;

public final class MomentumMaceHitListener implements Listener {
    private static double empoweredCap() {
        return com.hizeric.shenaniganscore.core.config.AbilityTuningConfig.current()
                .boundedDouble(com.hizeric.shenaniganscore.core.config.AbilityTuningConfig.Section.ITEMS,
                        "momentum-mace", "empowered-hit-damage-cap", 12.0, 0.0, 40.0);
    }
    private static double depletedCap() {
        return com.hizeric.shenaniganscore.core.config.AbilityTuningConfig.current()
                .boundedDouble(com.hizeric.shenaniganscore.core.config.AbilityTuningConfig.Section.ITEMS,
                        "momentum-mace", "depleted-hit-damage-cap", 4.0, 0.0, 40.0);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onDamage(EntityDamageByEntityEvent e) {
        if (!(e.getDamager() instanceof Player attacker)) return;
        if (!(e.getEntity() instanceof Player victim)) return;
        ItemStack hand = attacker.getInventory().getItemInMainHand();
        if (!MomentumMaceItem.isMomentumMace(hand)) return;
        MomentumMaceChargeManager.refresh(attacker);
        boolean empowered = MomentumMaceChargeManager.hasCharge(attacker);
        double cap = empowered ? empoweredCap() : depletedCap();
        if (e.getDamage() > cap) e.setDamage(cap);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onHit(EntityDamageByEntityEvent e) {
        if (!(e.getDamager() instanceof Player attacker)) return;
        if (!(e.getEntity() instanceof Player victim)) return;
        ItemStack hand = attacker.getInventory().getItemInMainHand();
        if (!MomentumMaceItem.isMomentumMace(hand)) return;
        if (e.getFinalDamage() <= 0) return;
        MomentumMaceChargeManager.consumeCharge(attacker);
        // Impact Rebound
        org.bukkit.util.Vector vel = attacker.getVelocity();
        vel.setY(Math.max(vel.getY(), 0.45));
        attacker.setVelocity(vel);
        attacker.setFallDistance(0f);
    }
}