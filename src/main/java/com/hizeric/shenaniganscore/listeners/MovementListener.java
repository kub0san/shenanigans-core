package com.hizeric.shenaniganscore.listeners;

import com.hizeric.shenaniganscore.core.session.Immobilize;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.event.player.PlayerToggleFlightEvent;
import org.bukkit.event.player.PlayerVelocityEvent;
import org.bukkit.event.vehicle.VehicleEnterEvent;

/**
 * The Unlimited Void's hard lock — <b>no potion effects involved</b>, anywhere,
 * at any layer.
 *
 * <p><b>Design: defense in depth.</b> One check ({@link Immobilize#isMovementLocked},
 * a stateless radius test with no lists to leak) is consulted by five independent
 * enforcement layers, so no single event type is a single point of failure:</p>
 *
 * <ol>
 *   <li><b>Movement</b> ({@code PlayerMoveEvent}): translation is clamped to the
 *       origin every tick — the victim keeps looking around freely (yaw/pitch
 *       preserved) but cannot displace, not even one block.</li>
 *   <li><b>Velocity</b> ({@code PlayerVelocityEvent}): knockback, levitation,
 *       firework-boosted and explosion displacement are all swallowed. Without this
 *       layer, every hit would jerk the victim around and the server would snap
 *       them back on the next tick.</li>
 *   <li><b>Flight</b> ({@code PlayerToggleFlightEvent}): locked players cannot switch
 *       into (or stay in) flight mode. Enabling flight is denied; leaving it is
 *       always allowed.</li>
 *   <li><b>Vehicles</b> ({@code VehicleEnterEvent}): a locked player cannot mount
 *       minecarts, horses, boats, etc. — the one vanilla escape route left once
 *       walking and jumping are dead.</li>
 *   <li><b>Teleport</b> ({@code PlayerTeleportEvent}): gameplay escapes (ender
 *       pearls, chorus fruit, portals) are cancelled; admin/plugin teleports
 *       are left to the world's owner.</li>
 * </ol>
 *
 * <p><b>Teleport policy</b> ({@code PlayerTeleportEvent} extends {@code PlayerMoveEvent}
 * but declares its own handler list, so it needs its own handler): admin/plugin teleports
 * ({@code COMMAND}, {@code PLUGIN}, {@code SPECTATE}, {@code UNKNOWN},
 * {@code DISMOUNT}, {@code EXIT_BED}) are honored — operators and other plugins must
 * stay in charge of the world. <i>Gameplay</i> teleports ({@code ENDER_PEARL},
 * {@code CONSUMABLE_EFFECT} — which is what eating chorus fruit reports —,
 * {@code NETHER_PORTAL}, {@code END_PORTAL}, {@code END_GATEWAY}) are blocked —
 * paralysis means <i>that</i> kind of escape is unthinkable.</p>
 *
 * <p><b>Client prediction:</b> there are deliberately no Slowness effects to zero out
 * the client's walk speed, so the client will still try to run and the server clamps
 * it back every tick. Locked victims therefore stutter in place — that <i>is</i> the
 * no-effects trade-off, and thematically it reads as the void refusing to let anyone
 * move. Do not "fix" this by re-adding Slowness 254; it is the very hack this
 * redesign removes.</p>
 *
 * <p><b>Fail-open:</b> {@link Immobilize} never throws upward — a failure means
 * "not locked", so a plugin glitch can never trap a player forever.</p>
 *
 * <p>A player who is <i>already aboard a vehicle</i> when the void blooms is frozen
 * in place by the move layer (it clamps their absolute position), which is the
 * intended "unconscious while standing" look.</p>
 */
public class MovementListener implements Listener {

    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGH)
    public void onPlayerMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        // Cheap guard first: a pure look rotation cannot translate the player, so skip the
        // more expensive (fail-open) immobilize lookup entirely for head-only moves (F-17).
        // The two checks are independent reads, so the reorder is behaviour-preserving.
        if (!event.hasChangedPosition()) return; // pure look rotation — always free
        if (!Immobilize.isMovementLocked(player)) return;

        Location from = event.getFrom();
        Location to = event.getTo();
        // Only cancel translation; the client's own head rotation stays
        event.setTo(new Location(
                from.getWorld(),
                from.getX(), from.getY(), from.getZ(),
                to.getYaw(), to.getPitch()));
    }

    /**
     * Teleports are dispatched to {@link PlayerTeleportEvent}'s own handler list,
     * never to {@link #onPlayerMove(PlayerMoveEvent)}. Gameplay escapes are
     * cancelled; admin/plugin teleports remain under the world owner's control.
     */
    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGH)
    public void onPlayerTeleport(PlayerTeleportEvent event) {
        if (!Immobilize.isMovementLocked(event.getPlayer())) return;
        if (isGameplayTeleport(event.getCause())) {
            event.setCancelled(true);
        }
    }

    /** Knockback, levitation, firework boosts, explosions — swallowed whole. */
    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGH)
    public void onPlayerVelocity(PlayerVelocityEvent event) {
        if (Immobilize.isMovementLocked(event.getPlayer())) {
            event.setCancelled(true);
        }
    }

    /** Locked players cannot enable (or remain in) flight. Disabling always works. */
    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGH)
    public void onToggleFlight(PlayerToggleFlightEvent event) {
        if (event.isFlying() && Immobilize.isMovementLocked(event.getPlayer())) {
            event.setCancelled(true);
        }
    }

    /** The last vanilla escape hatch: mount a minecart and ride out. Sealed. */
    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGH)
    public void onVehicleEnter(VehicleEnterEvent event) {
        if (event.getEntered() instanceof Player player
                && Immobilize.isMovementLocked(player)) {
            event.setCancelled(true);
        }
    }

    /** Gameplay escapes are impossible in the void; admin/plugin mobility is not ours to police. */
    private static boolean isGameplayTeleport(PlayerTeleportEvent.TeleportCause cause) {
        // CHORUS_FRUIT is a deprecated alias that equals CONSUMABLE_EFFECT, so it's covered here.
        return switch (cause) {
            case ENDER_PEARL, CONSUMABLE_EFFECT,
                 NETHER_PORTAL, END_PORTAL, END_GATEWAY -> true;
            default -> false; // COMMAND, PLUGIN, SPECTATE, UNKNOWN, DISMOUNT, EXIT_BED
        };
    }
}
