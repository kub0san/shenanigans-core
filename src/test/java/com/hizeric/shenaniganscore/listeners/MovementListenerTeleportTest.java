package com.hizeric.shenaniganscore.listeners;

import org.bukkit.event.EventHandler;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * F-47 regression: {@code PlayerTeleportEvent} owns its own handler list, so a
 * dedicated handler must exist and the documented cause policy must hold.
 */
class MovementListenerTeleportTest {

    @Test
    void teleportHasDedicatedEventHandler() {
        boolean found = Arrays.stream(MovementListener.class.getDeclaredMethods())
                .anyMatch(m -> m.isAnnotationPresent(EventHandler.class)
                        && m.getParameterCount() == 1
                        && m.getParameterTypes()[0] == PlayerTeleportEvent.class);
        assertTrue(found, "MovementListener must handle PlayerTeleportEvent directly");
    }

    @Test
    void causePolicyBlocksGameplayAndHonorsAdmin() throws Exception {
        Method policy = MovementListener.class.getDeclaredMethod(
                "isGameplayTeleport", PlayerTeleportEvent.TeleportCause.class);
        policy.setAccessible(true);

        for (PlayerTeleportEvent.TeleportCause cause : new PlayerTeleportEvent.TeleportCause[]{
                PlayerTeleportEvent.TeleportCause.ENDER_PEARL,
                PlayerTeleportEvent.TeleportCause.CONSUMABLE_EFFECT,
                PlayerTeleportEvent.TeleportCause.NETHER_PORTAL,
                PlayerTeleportEvent.TeleportCause.END_PORTAL,
                PlayerTeleportEvent.TeleportCause.END_GATEWAY}) {
            assertTrue((Boolean) policy.invoke(null, cause), cause + " must be blocked");
        }
        for (PlayerTeleportEvent.TeleportCause cause : new PlayerTeleportEvent.TeleportCause[]{
                PlayerTeleportEvent.TeleportCause.COMMAND,
                PlayerTeleportEvent.TeleportCause.PLUGIN,
                PlayerTeleportEvent.TeleportCause.SPECTATE,
                PlayerTeleportEvent.TeleportCause.UNKNOWN,
                PlayerTeleportEvent.TeleportCause.DISMOUNT,
                PlayerTeleportEvent.TeleportCause.EXIT_BED}) {
            assertFalse((Boolean) policy.invoke(null, cause), cause + " must be honored");
        }
    }
}
