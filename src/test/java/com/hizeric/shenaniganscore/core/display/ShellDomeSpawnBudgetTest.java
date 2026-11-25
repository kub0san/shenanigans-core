package com.hizeric.shenaniganscore.core.display;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Issue #76: the shell dome used to spawn its entire plate plan (up to 280
 * BlockDisplays) inside one tick once the barrier completed. Amortising the
 * spawn keeps the same final geometry but bounds the per-tick work.
 */
class ShellDomeSpawnBudgetTest {

    @Test
    void aFullDomeIsSplitAcrossTicks() {
        assertEquals(24, ShellDomePlan.batch(280, 0));
        assertEquals(24, ShellDomePlan.batch(280, 24));
        assertEquals(16, ShellDomePlan.batch(280, 264));
        assertEquals(0, ShellDomePlan.batch(280, 280));
    }

    @Test
    void aSmallDomeFitsInOneTick() {
        assertEquals(10, ShellDomePlan.batch(10, 0));
        assertEquals(0, ShellDomePlan.batch(10, 10));
    }

    @Test
    void invalidCursorNeverSpawnsMoreThanThePlan() {
        assertEquals(0, ShellDomePlan.batch(280, 300));
        assertEquals(0, ShellDomePlan.batch(280, -1));
        assertEquals(0, ShellDomePlan.batch(0, 0));
    }
}
