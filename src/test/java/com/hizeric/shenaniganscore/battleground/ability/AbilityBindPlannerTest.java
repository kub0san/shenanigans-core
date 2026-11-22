package com.hizeric.shenaniganscore.battleground.ability;

import com.hizeric.shenaniganscore.core.ability.AbilityId;
import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.function.Predicate;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Bind invariants for the three-slot rail. An invalid ability persisted in the ultimate slot
 * is dropped, never promoted — the "red becomes the ultimate" bug.
 */
class AbilityBindPlannerTest {
    private static final AbilityId RED = AbilityId.of("jjk:red");
    private static final AbilityId BLUE = AbilityId.of("jjk:blue");
    private static final Predicate<AbilityId> NO_ULTIMATES = id -> false;

    private static AbilityBindPlanner.Plan plan(AbilityId normal, AbilityId ultimate,
                                                Set<AbilityId> valid,
                                                Predicate<AbilityId> isUltimate,
                                                AbilityId firstPick) {
        return AbilityBindPlanner.plan(normal, ultimate, valid, isUltimate, firstPick);
    }

    @Test
    void firstPickFillsTheNormalSlotNotTheUltimate() {
        AbilityId[] slots = plan(null, null, Set.of(RED, BLUE), NO_ULTIMATES, RED).slots();
        assertArrayEquals(new AbilityId[]{null, RED, null}, slots);
    }

    @Test
    void ultimateStaysEmptyWhenNormalIsAlreadyBound() {
        AbilityId[] slots = plan(RED, null, Set.of(RED, BLUE), NO_ULTIMATES, RED).slots();
        assertArrayEquals(new AbilityId[]{null, RED, null}, slots);
    }

    @Test
    void aNormalAbilityPersistedInUltimateSlotIsCleared() {
        AbilityId[] slots = plan(RED, RED, Set.of(RED), NO_ULTIMATES, RED).slots();
        assertArrayEquals(new AbilityId[]{null, RED, null}, slots);
    }

    @Test
    void aRealUltimateBindSurvives() {
        AbilityId[] slots = plan(RED, BLUE, Set.of(RED, BLUE), id -> id.equals(BLUE), RED).slots();
        assertArrayEquals(new AbilityId[]{null, RED, BLUE}, slots);
    }

    @Test
    void staleInvalidBindsAreClearedBeforeFilling() {
        AbilityId[] slots = plan(RED, BLUE, Set.of(), NO_ULTIMATES, null).slots();
        assertArrayEquals(new AbilityId[]{null, null, null}, slots);
    }

    @Test
    void anIneligibleFirstPickLeavesTheNormalSlotEmpty() {
        AbilityId[] slots = plan(null, null, Set.of(RED), NO_ULTIMATES, BLUE).slots();
        assertArrayEquals(new AbilityId[]{null, null, null}, slots);
    }

    @Test
    void droppedBindsAreReported() {
        AbilityBindPlanner.Plan result = plan(RED, BLUE, Set.of(RED), NO_ULTIMATES, RED);

        assertTrue(result.droppedAnything());
        assertTrue(result.dropped().contains(BLUE));
        assertFalse(result.fallbackUsed());
    }

    @Test
    void fallbackUseIsReported() {
        AbilityBindPlanner.Plan result = plan(null, null, Set.of(RED, BLUE), NO_ULTIMATES, RED);

        assertTrue(result.fallbackUsed());
        assertFalse(result.droppedAnything());
    }
}
