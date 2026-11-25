package com.hizeric.shenaniganscore.origins.minecraft.weapons.mace;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class MomentumMaceFallProtection implements Listener {
    private static final Map<java.util.UUID, Long> UNTIL = new ConcurrentHashMap<>();
    public MomentumMaceFallProtection() { }

    public static void grant(Player p, long millis) {
        UNTIL.put(p.getUniqueId(), System.currentTimeMillis() + millis);
    }

    @EventHandler(ignoreCancelled = true)
    public void onFall(EntityDamageEvent e) {
        if (e.getCause() != EntityDamageEvent.DamageCause.FALL) return;
        if (!(e.getEntity() instanceof Player p)) return;
        Long until = UNTIL.get(p.getUniqueId());
        if (until != null && System.currentTimeMillis() < until) e.setCancelled(true);
    }
}