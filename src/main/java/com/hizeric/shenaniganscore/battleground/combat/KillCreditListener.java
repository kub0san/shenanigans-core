package com.hizeric.shenaniganscore.battleground.combat;

import com.hizeric.shenaniganscore.battleground.BattlegroundDefaults;

import com.hizeric.shenaniganscore.ShenanigansCore;
import com.hizeric.shenaniganscore.battleground.BattlegroundManager;
import com.hizeric.shenaniganscore.battleground.RoundGates;
import org.bukkit.Bukkit;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.PlayerDeathEvent;

import java.util.UUID;

/** Attributes kills inside the battleground, then feeds the economy. */
public final class KillCreditListener implements Listener {

    private final ShenanigansCore plugin;

    public KillCreditListener(ShenanigansCore plugin) {
        this.plugin = plugin;
    }

    private BattlegroundManager manager() {
        return plugin.getBattlegroundManager();
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDamage(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof Player victim)) return;
        Player attacker = attackerOf(event.getDamager());
        if (attacker == null || attacker.equals(victim)) return;

        BattlegroundManager manager = manager();
        if (!manager.isDeployed(victim.getUniqueId())) return;
        // Only deployed fighters can earn or deny kill credit. Without this, a hub player
        // shooting into the arena can out-damage the real killer and suppress their reward.
        if (!manager.isDeployed(attacker.getUniqueId())) return;
        manager.combat().record(victim.getUniqueId(), attacker.getUniqueId(), event.getFinalDamage());
    }

    @EventHandler
    public void onDeath(PlayerDeathEvent event) {
        BattlegroundManager manager = manager();
        Player victim = event.getEntity();
        boolean deployed = manager.isDeployed(victim.getUniqueId());
        boolean staged = manager.staging().isStaged(victim);
        if (RoundGates.deathDrops(deployed, staged) == RoundGates.Drops.CLEAR) {
            // The arena kit is reissued on respawn; dropping it is an unlimited gear farm.
            event.getDrops().clear();
        }
        if (!deployed && !staged) return;

        UUID victimId = victim.getUniqueId();
        // Paper 1.21 no longer exposes getKiller() on the death event, and technique damage
        // often uses a custom DamageSource. The combat tracker is the source of truth.
        UUID killerId = manager.combat().primary(victimId, BattlegroundDefaults.LAST_DAMAGER_WINDOW_SECONDS);
        Player killer = killerId == null ? null : Bukkit.getPlayer(killerId);
        if (killer != null && !killer.equals(victim) && manager.isDeployed(killerId)) {
            manager.economy().awardKill(killer, victim);
        }
        manager.combat().clear(victimId);
    }

    private static Player attackerOf(Entity damager) {
        if (damager instanceof Player player) return player;
        if (damager instanceof Projectile projectile
                && projectile.getShooter() instanceof Player player) return player;
        return null;
    }
}
