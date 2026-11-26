package com.hizeric.shenaniganscore.battleground;

import com.hizeric.shenaniganscore.battleground.BattlegroundDefaults;
import com.hizeric.shenaniganscore.battleground.arena.ArenaEntryGuard;
import com.hizeric.shenaniganscore.battleground.arena.ArenaRegion;
import com.hizeric.shenaniganscore.battleground.arena.RespawnTarget;

import com.hizeric.shenaniganscore.ShenanigansCore;
import com.hizeric.shenaniganscore.battleground.player.BattlegroundLock;
import com.hizeric.shenaniganscore.battleground.player.MoveClamp;
import com.hizeric.shenaniganscore.battleground.command.Permissions;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.FoodLevelChangeEvent;
import org.bukkit.event.player.PlayerBucketEmptyEvent;
import org.bukkit.event.player.PlayerBucketFillEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;

/** Enforces the staged state and the dive lock through one shared lock predicate. */
public final class BattlegroundListener implements Listener {

    private final ShenanigansCore plugin;

    public BattlegroundListener(ShenanigansCore plugin) {
        this.plugin = plugin;
    }

    private BattlegroundManager manager() {
        return plugin.getBattlegroundManager();
    }

    @EventHandler
    public void onRespawn(PlayerRespawnEvent event) {
        BattlegroundManager manager = manager();
        Player player = event.getPlayer();
        if (!manager.isDeployed(player.getUniqueId())) return;

        // Honour the admin's /bg setspawn return point when one is configured, otherwise fall
        // back to the world spawn. Set it here, before staging, so stageOnRespawn() keeps its
        // single job of teleporting the player to the staging anchor.
        ArenaRegion region = manager.arenaStore().region();
        Location configured = region == null ? null : region.spawn();
        event.setRespawnLocation(RespawnTarget.resolve(configured, player.getWorld().getSpawnLocation(), region));
        Bukkit.getScheduler().runTask(plugin, () -> manager.stageOnRespawn(player));
    }

    @EventHandler
    public void onDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;
        BattlegroundManager manager = manager();
        boolean diving = manager.dives().isDiving(player);
        // A diver takes no damage; nor does a staged player. There is no landing grace window.
        if (diving || BattlegroundLock.isLocked(player)) {
            event.setCancelled(true);
            return;
        }
        // A diver also deals no damage: cancel the hit they cause on the way down.
        if (event instanceof EntityDamageByEntityEvent byEntity
                && byEntity.getDamager() instanceof Player attacker
                && manager.dives().isDiving(attacker)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onBreakWhileDiving(BlockBreakEvent event) {
        if (manager().dives().isDiving(event.getPlayer())) event.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void onPlaceWhileDiving(BlockPlaceEvent event) {
        if (manager().dives().isDiving(event.getPlayer())) event.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void onBucketEmptyWhileDiving(PlayerBucketEmptyEvent event) {
        if (manager().dives().isDiving(event.getPlayer())) event.setCancelled(true);
    }
    @EventHandler(ignoreCancelled = true)
    public void onBucketFillWhileDiving(PlayerBucketFillEvent event) {
        if (manager().dives().isDiving(event.getPlayer())) event.setCancelled(true);
    }
    @EventHandler
    public void onMove(PlayerMoveEvent event) {
        BattlegroundManager manager = manager();
        if (BattlegroundLock.isLocked(event.getPlayer())) {
            Player player = event.getPlayer();
            Location anchor = manager.staging().anchor(player, manager.arenaStore().region());
            if (anchor == null) return;
            if (MoveClamp.outside(event.getTo(), anchor, 2.25)) {
                // Rewrite the move rather than teleporting every tick: keeps the client in sync
                // while still letting the player look around in staging.
                event.setTo(MoveClamp.clamp(event.getTo(), anchor));
            }
            return;
        }
        manager.checkRegion(event.getPlayer());
    }

    @EventHandler(ignoreCancelled = true)
    public void onFood(FoodLevelChangeEvent event) {
        if (event.getEntity() instanceof Player player && BattlegroundLock.isLocked(player)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onDrop(PlayerDropItemEvent event) {
        if (BattlegroundLock.isLocked(event.getPlayer())) event.setCancelled(true);
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        // Repairs flags leaked by an earlier session (or by a plugin removed while the player was
        // staged). Visibility of already-staged players is handled once by
        // PlayerLifecycleListener.onJoin -> StagingController.hideStagedFrom; duplicating that loop
        // here re-hid players and recorded duplicate viewers.
        manager().repair(event.getPlayer());
        // Issue #64: repair clears mode flags but cannot move a body that is already standing in
        // the arena. Sweep those stranded players to the return point with the same scheduled,
        // deduplicated ejection the move guard uses.
        ArenaEntryGuard.get().ejectIfStranded(event.getPlayer());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        plugin.battlegroundGui().forget(event.getPlayer());
        manager().onQuit(event.getPlayer());
    }

    @EventHandler
    public void onCommand(PlayerCommandPreprocessEvent event) {
        Player player = event.getPlayer();
        boolean inMode = BattlegroundLock.isLocked(player)
                || manager().isDeployed(player.getUniqueId());
        if (!inMode) return;

        if (!BattlegroundDefaults.COMMAND_POLICY_ENABLED) return;
        boolean bypass = player.hasPermission(Permissions.BG_ADMIN)
                || player.hasPermission("shenaniganscore.battleground.command.bypass");
        var decision = com.hizeric.shenaniganscore.battleground.combat.CommandPolicy.evaluate(
                new com.hizeric.shenaniganscore.battleground.combat.CommandPolicy.Input(
                        BattlegroundDefaults.COMMAND_POLICY_MODE,
                        com.hizeric.shenaniganscore.battleground.combat.CommandPolicy.DEFAULT_ALLOWED_ROOTS,
                        com.hizeric.shenaniganscore.battleground.combat.CommandPolicy.DEFAULT_BLOCKED_ROOTS,
                        event.getMessage(), bypass));
        if (decision.allowed()) return;
        event.setCancelled(true);
        player.sendMessage(net.kyori.adventure.text.minimessage.MiniMessage.miniMessage()
                .deserialize("<red>That command is disabled in the battleground. "
                        + "<gray>Try <white>/bg help<gray>."));
    }
}
