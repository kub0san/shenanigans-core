package com.hizeric.shenaniganscore.battleground.combat;

import org.junit.jupiter.api.Test;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class CombatTrackerTest {

    @Test
    void oldDamageMustNotWinOverRecentDamage() {
        AtomicLong now = new AtomicLong(1_000_000L);
        CombatTracker tracker = new CombatTracker(now::get);
        UUID victim = UUID.randomUUID();
        UUID stale = UUID.randomUUID();
        UUID recent = UUID.randomUUID();

        tracker.record(victim, stale, 100.0);
        now.addAndGet(60_000L);
        tracker.record(victim, recent, 50.0);

        assertEquals(recent, tracker.primary(victim, 10));
    }

    @Test
    void recentHitsFromOneAttackerAccumulate() {
        AtomicLong now = new AtomicLong(2_000_000L);
        CombatTracker tracker = new CombatTracker(now::get);
        UUID victim = UUID.randomUUID();
        UUID attacker = UUID.randomUUID();
        UUID other = UUID.randomUUID();

        tracker.record(victim, attacker, 20.0);
        now.addAndGet(1_000L);
        tracker.record(victim, attacker, 30.0);
        tracker.record(victim, other, 45.0);

        assertEquals(attacker, tracker.primary(victim, 10));
    }

    @Test
    void clearingAVictimDropsAllAttribution() {
        CombatTracker tracker = new CombatTracker(System::currentTimeMillis);
        UUID victim = UUID.randomUUID();
        tracker.record(victim, UUID.randomUUID(), 5.0);

        tracker.clear(victim);

        assertNull(tracker.primary(victim, 10));
    }
}
