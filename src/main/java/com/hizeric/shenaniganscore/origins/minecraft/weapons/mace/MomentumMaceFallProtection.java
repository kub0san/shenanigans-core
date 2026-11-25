package com.hizeric.shenaniganscore.origins.minecraft.weapons.mace;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Short-lived, caster-scoped fall protection for mace movement. */
public final class MomentumMaceFallProtection implements Listener {
    private static final Map<UUID, Long> UNTIL = new ConcurrentHashMap<>();

    public static void grant(Player p, long millis) {
        UNTIL.put(p.getUniqueId(), System.currentTimeMillis() + millis);
    }

    public static void clear(Player p) { UNTIL.remove(p.getUniqueId()); }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) { clear(e.getPlayer()); }

    @EventHandler
    public void onDeath(PlayerDeathEvent e) { clear(e.getEntity()); }

    @EventHandler(ignoreCancelled = true)
    public void onFall(EntityDamageEvent e) {
        if (e.getCause() != EntityDamageEvent.DamageCause.FALL) return;
        if (!(e.getEntity() instanceof Player p)) return;
        Long until = UNTIL.get(p.getUniqueId());
        if (until == null) return;
        if (System.currentTimeMillis() < until) e.setCancelled(true);
        else UNTIL.remove(p.getUniqueId());
    }
}
