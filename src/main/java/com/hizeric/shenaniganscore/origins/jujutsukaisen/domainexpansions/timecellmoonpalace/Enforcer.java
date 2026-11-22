package com.hizeric.shenaniganscore.origins.jujutsukaisen.domainexpansions.timecellmoonpalace;

import com.destroystokyo.paper.event.player.PlayerJumpEvent;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerAnimationEvent;
import org.bukkit.event.player.PlayerAnimationType;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerTeleportEvent;

/**
 * The hard edges of the domain, as a listener owned by one {@link Session}.
 *
 * <p>The session registers it in {@code start()} and unregisters it in {@code close()},
 * so no global state exists and nothing outlives the domain. Every handler is
 * fail-open: an exception here must never break a player's movement.</p>
 *
 * <ul>
 *   <li><b>Barrier lock</b>: closed barrier. Nobody crosses the sphere in either direction.</li>
 *   <li><b>Frozen clamp</b>: frozen cells cannot step or jump (camera still turns).</li>
 *   <li><b>Stutter</b>: a moving victim's step is dropped on every Nth frame of the
 *       24 FPS clock, so motion reads as frame-by-frame.</li>
 *   <li><b>Triggers</b>: arm swings cost cells; a frozen victim acting or being hit
 *       severs a limb (see {@link Rule24}).</li>
 * </ul>
 */
final class Enforcer implements Listener {

    private final Session s;

    Enforcer(Session s) {
        this.s = s;
    }

    private static Location hold(Location from, Location to) {
        Location l = from.clone();
        l.setYaw(to.getYaw());
        l.setPitch(to.getPitch());
        return l;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onMove(PlayerMoveEvent e) {
        try {
            if (!s.isActive() || !e.hasChangedPosition()) return;
            Location from = e.getFrom();
            Location to = e.getTo();
            if (to == null || !from.getWorld().equals(s.world())) return;
            Player p = e.getPlayer();

            // 1) Closed barrier: crossing the sphere in either direction is refused
            if (s.cfg.barrierLock && s.barrierApplies(p) && s.inside(from) != s.inside(to)) {
                e.setTo(hold(from, to));
                return;
            }

            if (!s.rule.active()) return;
            Rule24.Subject sub = s.rule.subject(p.getUniqueId());
            if (sub == null || !sub.injected) return;

            // 2) Frozen cells: no stepping, no rising; falling is allowed
            if (sub.frozen(System.currentTimeMillis())) {
                Location t = to.clone();
                t.setX(from.getX());
                t.setZ(from.getZ());
                if (t.getY() > from.getY()) t.setY(from.getY());
                e.setTo(t);
                return;
            }

            // 3) 24 FPS stutter: drop this step on every Nth frame
            int every = s.cfg.stutterEvery;
            if (every > 1 && !p.isGliding() && !p.isFlying() && p.getVehicle() == null) {
                double dx = to.getX() - from.getX();
                double dz = to.getZ() - from.getZ();
                boolean rising = to.getY() > from.getY() + 0.001;
                if (!rising && dx * dx + dz * dz > 0.0025 && s.frame() % every == every - 1) {
                    e.setTo(hold(from, to));
                    if (Math.random() < 0.3) {
                        p.playSound(p.getLocation(), Sound.BLOCK_COMPARATOR_CLICK, 0.4f, 1.8f);
                    }
                }
            }
        } catch (Throwable ignored) {
            // fail-open
        }
    }

    /**
     * {@link PlayerTeleportEvent} has its own handler list, so the move handler
     * never sees teleports. Refuse gameplay escapes that cross the barrier or
     * drag a frozen captive out; admin/plugin teleports stay untouched.
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onTeleport(PlayerTeleportEvent e) {
        try {
            if (!s.isActive() || !isGameplayTeleport(e.getCause())) return;
            Location to = e.getTo();
            if (to == null || !e.getFrom().getWorld().equals(s.world())) return;
            Player p = e.getPlayer();

            if (s.cfg.barrierLock && s.barrierApplies(p)
                    && s.inside(e.getFrom()) != s.inside(to)) {
                e.setCancelled(true);
                return;
            }
            if (s.rule.active()) {
                Rule24.Subject sub = s.rule.subject(p.getUniqueId());
                if (sub != null && sub.injected && sub.frozen(System.currentTimeMillis())) {
                    e.setCancelled(true);
                }
            }
        } catch (Throwable ignored) {
            // intentional fail-open
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onJump(PlayerJumpEvent e) {
        try {
            if (s.rule.isFrozen(e.getPlayer().getUniqueId())) e.setCancelled(true);
        } catch (Throwable ignored) {
            // intentional fail-open: a frozen-rule bookkeeping error must never break the event.
        }
    }

    /**
     * Gameplay escapes are impossible inside the cell; admin and plugin
     * teleports are not ours to police.
     */
    private static boolean isGameplayTeleport(PlayerTeleportEvent.TeleportCause cause) {
        // CHORUS_FRUIT is a deprecated alias that equals CONSUMABLE_EFFECT, so it is covered here.
        return switch (cause) {
            case ENDER_PEARL, CONSUMABLE_EFFECT,
                 NETHER_PORTAL, END_PORTAL, END_GATEWAY -> true;
            default -> false; // COMMAND, PLUGIN, SPECTATE, UNKNOWN, DISMOUNT, EXIT_BED
        };
    }

    /** Any arm swing: a free victim pays cells, a frozen one loses the hand. */
    @EventHandler(ignoreCancelled = true)
    public void onSwing(PlayerAnimationEvent e) {
        try {
            if (!s.rule.active() || e.getAnimationType() != PlayerAnimationType.ARM_SWING) return;
            Player p = e.getPlayer();
            if (s.rule.isFrozen(p.getUniqueId())) {
                e.setCancelled(true);
                s.rule.attempt(p);
            } else {
                s.rule.swing(p);
            }
        } catch (Throwable ignored) {
            // intentional fail-open: a frozen-rule bookkeeping error must never break the event.
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onInteract(PlayerInteractEvent e) {
        try {
            Player p = e.getPlayer();
            if (!s.rule.isFrozen(p.getUniqueId())) return;
            e.setCancelled(true);
            s.rule.attempt(p);
        } catch (Throwable ignored) {
            // intentional fail-open: a frozen-rule bookkeeping error must never break the event.
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDamage(EntityDamageByEntityEvent e) {
        try {
            // The rule's own cuts are never "forced movement"
            if (!s.rule.active() || s.rule.internal()) return;

            Entity damager = e.getDamager();
            if (damager instanceof Player dp && s.rule.isFrozen(dp.getUniqueId())) {
                e.setCancelled(true);
                s.rule.attempt(dp);
                return;
            }
            if (e.getEntity() instanceof LivingEntity victim && s.rule.isFrozen(victim.getUniqueId())) {
                s.rule.forced(victim);
            }
        } catch (Throwable ignored) {
            // intentional fail-open: a frozen-rule bookkeeping error must never break the event.
        }
    }
}
