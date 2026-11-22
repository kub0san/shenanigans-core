package com.hizeric.shenaniganscore.battleground.player;

import org.bukkit.GameMode;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** D2/D4: exiting staging must assert known-good values, never replay a poisoned snapshot. */
class StagingContractTest {

    @Test
    void exitStateClearsEveryFlagThatLeaksToPlayerData() {
        StagingContract.ExitState state = StagingContract.exitState();

        assertFalse(state.invulnerable());
        assertFalse(state.invisible());
        assertTrue(state.collidable());
        assertFalse(state.allowFlight());
        assertFalse(state.flying());
        assertEquals(GameMode.SURVIVAL, state.gameMode());
    }

    @Test
    void commandAllowlistStripsTheLeadingSlash() {
        assertTrue(StagingContract.isAllowedCommand("/battleground quit"));
        assertTrue(StagingContract.isAllowedCommand("/bg join"));
        assertTrue(StagingContract.isAllowedCommand("/ability 2"));
        assertFalse(StagingContract.isAllowedCommand("/technique list"));
        assertFalse(StagingContract.isAllowedCommand("/spawn"));
    }

    @Test
    void neverStagedPlayersKeepTheirOwnFlags() {
        assertFalse(StagingContract.shouldExit(false, false));
        assertTrue(StagingContract.shouldExit(true, false));
        assertTrue(StagingContract.shouldExit(false, true));
    }
}
