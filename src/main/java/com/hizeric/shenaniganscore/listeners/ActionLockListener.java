package com.hizeric.shenaniganscore.listeners;

import com.hizeric.shenaniganscore.core.session.Immobilize;
import io.papermc.paper.event.player.PlayerOpenSignEvent;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.block.SignChangeEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.entity.EntityToggleGlideEvent;
import org.bukkit.event.entity.PlayerLeashEntityEvent;
import org.bukkit.event.entity.ProjectileLaunchEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.player.PlayerArmorStandManipulateEvent;
import org.bukkit.event.player.PlayerBedEnterEvent;
import org.bukkit.event.player.PlayerBucketEmptyEvent;
import org.bukkit.event.player.PlayerBucketEntityEvent;
import org.bukkit.event.player.PlayerBucketFillEvent;
import org.bukkit.event.player.PlayerBucketFishEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerEditBookEvent;
import org.bukkit.event.player.PlayerFishEvent;
import org.bukkit.event.player.PlayerHarvestBlockEvent;
import org.bukkit.event.player.PlayerInteractAtEntityEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.event.player.PlayerShearEntityEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.event.player.PlayerTakeLecternBookEvent;
import org.bukkit.event.player.PlayerToggleFlightEvent;
import org.bukkit.event.player.PlayerToggleSneakEvent;
import org.bukkit.event.player.PlayerToggleSprintEvent;
import org.bukkit.event.player.PlayerUnleashEntityEvent;
import org.bukkit.event.vehicle.VehicleEnterEvent;
import org.bukkit.event.vehicle.VehicleExitEvent;

/**
 * Cancels player actions while an action lock is active.
 *
 * <p>The gate is {@link Immobilize#isMovementLocked(Player)}: the same live,
 * fail-open check the movement layers use, so the lock can never leak and needs
 * no cleanup. Any future ability that needs an action lock should check the
 * same central state; event cancellation alone cannot stop a handler that
 * deliberately ignores it.</p>
 *
 * <h3>Dispatch by exact class</h3>
 * <p>Bukkit looks up handlers by the event's <b>concrete</b> class, never by a
 * superclass. Listening for an abstract/ancestor event does nothing — it may
 * even fail at registration if the class has no {@code HandlerList}. That is
 * why this listener registers every concrete bucket event separately, and why
 * {@link PlayerInteractAtEntityEvent} needs its own handler next to
 * {@link PlayerInteractEntityEvent}.</p>
 *
 * <h3>Boundaries</h3>
 * <ul>
 *   <li><b>Chat is intentionally untouched.</b> Chat is not an action, and
 *   blocking it is usually a poor fit for a lock.</li>
 *   <li><b>Movement and camera stay in {@code MovementListener}.</b> This
 *   listener never touches {@code PlayerMoveEvent}/{@code PlayerTeleportEvent},
 *   so plugin teleports keep working.</li>
 *   <li><b>Commands are blocked</b> so a victim cannot escape with
 *   {@code /spawn} or {@code /home}. Delete {@link #onCommand} if commands
 *   should remain available on your server.</li>
 *   <li><b>Not exhaustive.</b> Custom abilities in other plugins must check
 *   the lock themselves; for packets with no Bukkit event, a targeted
 *   PacketEvents layer can be added later.</li>
 * </ul>
 */
public final class ActionLockListener implements Listener {

    /** One gate for every handler; fail-open, derived from live sessions. */
    private boolean isLocked(Player player) {
        return player != null && Immobilize.isMovementLocked(player);
    }

    private void cancelIfLocked(Cancellable event, Player player) {
        if (isLocked(player)) {
            event.setCancelled(true);
        }
    }

    /*
     * HIGHEST gives this listener a chance to enforce the lock after ordinary
     * handlers. ignoreCancelled=false means an already-cancelled event is still
     * checked. A later plugin can still deliberately uncancel the event, so
     * plugin abilities should also check the shared lock state.
     */

    // -------------------------------------------------------------------------
    // Interaction
    // -------------------------------------------------------------------------

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onInteract(PlayerInteractEvent event) {
        if (!isLocked(event.getPlayer())) return;

        // The event fires once per hand and carries two independent results.
        // Deny both, then cancel, so neither the clock nor the item is used.
        event.setUseInteractedBlock(Event.Result.DENY);
        event.setUseItemInHand(Event.Result.DENY);
        event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onEntityInteract(PlayerInteractEntityEvent event) {
        cancelIfLocked(event, event.getPlayer());
    }

    /**
     * Fired for interactions that have a precise hit position (item frames,
     * armor stands, ...). It is a sibling of {@link PlayerInteractEntityEvent},
     * not a subtype, so it needs its own handler.
     */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onInteractAtEntity(PlayerInteractAtEntityEvent event) {
        cancelIfLocked(event, event.getPlayer());
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onArmorStandManipulate(PlayerArmorStandManipulateEvent event) {
        cancelIfLocked(event, event.getPlayer());
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onShear(PlayerShearEntityEvent event) {
        cancelIfLocked(event, event.getPlayer());
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onLeash(PlayerLeashEntityEvent event) {
        cancelIfLocked(event, event.getPlayer());
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onUnleash(PlayerUnleashEntityEvent event) {
        cancelIfLocked(event, event.getPlayer());
    }

    // -------------------------------------------------------------------------
    // Blocks, signs and containers
    // -------------------------------------------------------------------------

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onBreak(BlockBreakEvent event) {
        cancelIfLocked(event, event.getPlayer());
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onPlace(BlockPlaceEvent event) {
        cancelIfLocked(event, event.getPlayer());
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onSignChange(SignChangeEvent event) {
        cancelIfLocked(event, event.getPlayer());
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onOpenSign(PlayerOpenSignEvent event) {
        cancelIfLocked(event, event.getPlayer());
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onHarvest(PlayerHarvestBlockEvent event) {
        cancelIfLocked(event, event.getPlayer());
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onTakeLecternBook(PlayerTakeLecternBookEvent event) {
        cancelIfLocked(event, event.getPlayer());
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onInventoryOpen(InventoryOpenEvent event) {
        if (event.getPlayer() instanceof Player player) {
            cancelIfLocked(event, player);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onInventoryClick(InventoryClickEvent event) {
        if (event.getWhoClicked() instanceof Player player) {
            cancelIfLocked(event, player);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onInventoryDrag(InventoryDragEvent event) {
        if (event.getWhoClicked() instanceof Player player) {
            cancelIfLocked(event, player);
        }
    }

    // -------------------------------------------------------------------------
    // Items and buckets
    // -------------------------------------------------------------------------

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onDrop(PlayerDropItemEvent event) {
        cancelIfLocked(event, event.getPlayer());
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onConsume(PlayerItemConsumeEvent event) {
        cancelIfLocked(event, event.getPlayer());
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onHeldSlotChange(PlayerItemHeldEvent event) {
        cancelIfLocked(event, event.getPlayer());
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onHandSwap(PlayerSwapHandItemsEvent event) {
        cancelIfLocked(event, event.getPlayer());
    }

    /*
     * PlayerBucketEvent is ABSTRACT and has no handler list: a handler taking
     * it would never be invoked (and can break listener registration). Every
     * concrete bucket event therefore gets its own handler.
     */

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onBucketEmpty(PlayerBucketEmptyEvent event) {
        cancelIfLocked(event, event.getPlayer());
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onBucketFill(PlayerBucketFillEvent event) {
        cancelIfLocked(event, event.getPlayer());
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onBucketEntity(PlayerBucketEntityEvent event) {
        cancelIfLocked(event, event.getPlayer());
    }

    /**
     * {@code PlayerBucketFishEvent} extends {@code PlayerBucketEntityEvent}
     * but is fired as its own event, so the entity handler above would not see
     * it.
     */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onBucketFish(PlayerBucketFishEvent event) {
        cancelIfLocked(event, event.getPlayer());
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onFish(PlayerFishEvent event) {
        cancelIfLocked(event, event.getPlayer());
    }

    // -------------------------------------------------------------------------
    // Combat and movement toggles
    // -------------------------------------------------------------------------

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onProjectileLaunch(ProjectileLaunchEvent event) {
        if (event.getEntity().getShooter() instanceof Player player) {
            cancelIfLocked(event, player);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onAttack(EntityDamageByEntityEvent event) {
        Player attacker = getPlayerAttacker(event.getDamager());
        if (attacker != null) {
            cancelIfLocked(event, attacker);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onPickup(EntityPickupItemEvent event) {
        if (event.getEntity() instanceof Player player) {
            cancelIfLocked(event, player);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onToggleSneak(PlayerToggleSneakEvent event) {
        cancelIfLocked(event, event.getPlayer());
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onToggleSprint(PlayerToggleSprintEvent event) {
        cancelIfLocked(event, event.getPlayer());
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onToggleFlight(PlayerToggleFlightEvent event) {
        cancelIfLocked(event, event.getPlayer());
    }

    /** Gliding is an entity event (players and mobs share it). */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onToggleGlide(EntityToggleGlideEvent event) {
        if (event.getEntity() instanceof Player player) {
            cancelIfLocked(event, player);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onVehicleEnter(VehicleEnterEvent event) {
        if (event.getEntered() instanceof Player player) {
            cancelIfLocked(event, player);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onVehicleExit(VehicleExitEvent event) {
        if (event.getExited() instanceof Player player) {
            cancelIfLocked(event, player);
        }
    }

    // -------------------------------------------------------------------------
    // Beds, books and commands
    // -------------------------------------------------------------------------

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onBedEnter(PlayerBedEnterEvent event) {
        cancelIfLocked(event, event.getPlayer());
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onEditBook(PlayerEditBookEvent event) {
        cancelIfLocked(event, event.getPlayer());
    }

    /**
     * Blocks slash commands while locked, so the domain cannot be escaped with
     * {@code /spawn}, {@code /home} or a warp. Delete this handler if commands
     * such as {@code /help} or staff commands should stay available.
     */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onCommand(PlayerCommandPreprocessEvent event) {
        cancelIfLocked(event, event.getPlayer());
    }

    private Player getPlayerAttacker(Entity damager) {
        if (damager instanceof Player player) {
            return player;
        }

        if (damager instanceof Projectile projectile
                && projectile.getShooter() instanceof Player player) {
            return player;
        }

        return null;
    }
}
