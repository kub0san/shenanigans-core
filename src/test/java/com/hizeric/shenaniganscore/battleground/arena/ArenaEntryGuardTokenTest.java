package com.hizeric.shenaniganscore.battleground.arena;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ArenaEntryGuardTokenTest {

    private final ArenaEntryGuard guard = ArenaEntryGuard.get();
    private final UUID playerId = UUID.randomUUID();

    @AfterEach
    void tearDown() {
        guard.clearTransit(playerId);
    }

    @Test
    void teleportClaimConsumesTokenSoALaterInwardTeleportIsNotAuthorised() {
        guard.clearTransit(playerId);
        guard.allowOnce(playerId);
        assertTrue(guard.hasTransit(playerId));

        // The first teleport consumes the token regardless of whether its destination
        // is inside the arena. A later teleport must not inherit it.
        assertTrue(guard.claimTransit(playerId));
        assertFalse(guard.hasTransit(playerId));
        assertFalse(guard.claimTransit(playerId));
    }
}
