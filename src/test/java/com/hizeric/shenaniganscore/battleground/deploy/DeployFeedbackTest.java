package com.hizeric.shenaniganscore.battleground.deploy;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The no-safe-spot seam. A failed selection must never abort the join into a full hub exit: the
 * player stays staged and is told a specific reason so they can retry the Deploy button. This is
 * verified at the pure decision level; the Bukkit teleport/GUI side effects are a live-server gap.
 */
class DeployFeedbackTest {

    private static DropZone.Selection failure(DropZone.Reason reason) {
        return DropZone.Selection.failure(reason);
    }

    @Test
    void everyFailureReasonKeepsThePlayerStaged() {
        for (DropZone.Reason reason : DropZone.Reason.values()) {
            if (reason == DropZone.Reason.NONE) continue;
            DeployFeedback.Result result = DeployFeedback.forSelection(failure(reason));
            assertEquals(DeployFeedback.Outcome.RESTAGE, result.outcome(),
                    "a failed selection must re-stage, never quit to the main world");
            assertNotNull(result.message(), "each failure must carry an explanation");
        }
    }

    @Test
    void eachFailureReasonHasADistinctSpecificMessage() {
        String noGround = DeployFeedback.message(DropZone.Reason.NO_SOLID_GROUND);
        String liquid = DeployFeedback.message(DropZone.Reason.ALL_LIQUID);
        String obstructed = DeployFeedback.message(DropZone.Reason.OBSTRUCTED);

        assertEquals(3, List.of(noGround, liquid, obstructed).stream().distinct().count());
        assertTrue(noGround.contains("ground"), "the void case names the missing ground");
        assertTrue(liquid.contains("water") || liquid.contains("liquid"),
                "the liquid case names the water/lava");
        assertTrue(obstructed.contains("rough") || obstructed.contains("cliff"),
                "the obstruction case names the terrain");
    }

    @Test
    void aSuccessfulSelectionNeedNoFeedbackAndDrops() {
        DropZone.Candidate candidate = new DropZone.Candidate(1, 30, 2, 50, 0, 0, 0.0, 0L);
        DeployFeedback.Result result = DeployFeedback.forSelection(DropZone.Selection.of(candidate, 0));

        assertEquals(DeployFeedback.Outcome.DROP, result.outcome());
        assertNull(result.message());
    }
}
