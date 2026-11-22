package com.hizeric.shenaniganscore.battleground.player;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * M4: after a restart the in-memory lock is empty, so the enable-time sweep used to skip every
 * player with a leaked flight/invisibility flag. It must look at the player's actual state instead.
 */
class RestartLeakSweepTest {

    private static StagingContract.LeakFlags flags(boolean arena, boolean flying, boolean admin) {
        return new StagingContract.LeakFlags(arena, flying, flying, flying, flying, !flying,
                flying, admin);
    }

    @Test
    void leakedFlagsInTheArenaAreRepaired() {
        assertTrue(StagingContract.needsRepairAfterRestart(flags(true, true, false)));
    }

    @Test
    void cleanAndOrdinaryWorldPlayersAreLeftAlone() {
        assertFalse(StagingContract.needsRepairAfterRestart(flags(true, false, false)));
        assertFalse(StagingContract.needsRepairAfterRestart(flags(false, true, false)));
    }

    @Test
    void adminsKeepTheirFlight() {
        assertFalse(StagingContract.needsRepairAfterRestart(flags(true, true, true)));
    }
}
