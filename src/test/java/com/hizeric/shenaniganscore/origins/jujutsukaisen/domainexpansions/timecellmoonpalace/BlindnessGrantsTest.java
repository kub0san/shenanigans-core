package com.hizeric.shenaniganscore.origins.jujutsukaisen.domainexpansions.timecellmoonpalace;

import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tracks exactly which players the Time Cell's {@code cutToBlack} flash blinded,
 * so {@code close()} can take the blindness back from precisely those players.
 *
 * <p>The historical bug: the 22-tick BLINDNESS flash was applied to everyone in
 * {@code skyPlayers} but never tracked, so {@code finalizeCloseState} (which
 * restores sky/time/speed) never cleared it — a player who left the area inside
 * the ~1.1s window kept the blindness. Remembering who we blinded and removing
 * only from them (never a stranger's blindness) is the fix.</p>
 */
class BlindnessGrantsTest {

    @Test
    void nothingToClearWhenNobodyWasBlinded() {
        assertTrue(new BlindnessGrants().playersToClear().isEmpty(),
                "a session that never flashed anyone blinds no one on close");
    }

    @Test
    void clearsExactlyThePlayersThatWereGranted() {
        BlindnessGrants grants = new BlindnessGrants();
        UUID a = UUID.randomUUID();
        UUID b = UUID.randomUUID();
        grants.grant(a);
        grants.grant(b);

        assertEquals(Set.of(a, b), grants.playersToClear(),
                "close() must clear blindness from exactly the players we blinded");
    }

    @Test
    void grantingTheSamePlayerTwiceBlindsThemOnce() {
        BlindnessGrants grants = new BlindnessGrants();
        UUID a = UUID.randomUUID();
        grants.grant(a);
        grants.grant(a);

        assertEquals(Set.of(a), grants.playersToClear(),
                "a player blinded twice is still a single entry to clear");
    }

    @Test
    void aNullGrantIsIgnored() {
        BlindnessGrants grants = new BlindnessGrants();
        grants.grant(null);

        assertTrue(grants.playersToClear().isEmpty(),
                "a null id is never a player to clear");
    }

    @Test
    void clearingEmptiesTheGrantSoTheNextSessionStartsClean() {
        BlindnessGrants grants = new BlindnessGrants();
        grants.grant(UUID.randomUUID());
        Set<UUID> first = grants.playersToClear();
        grants.clear();

        assertTrue(grants.playersToClear().isEmpty(),
                "after clearing, nothing is left to un-blind");
        assertEquals(1, first.size(), "the pre-clear snapshot still lists the player");
    }
}