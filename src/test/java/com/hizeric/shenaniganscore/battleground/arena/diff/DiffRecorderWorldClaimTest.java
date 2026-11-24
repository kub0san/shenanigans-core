package com.hizeric.shenaniganscore.battleground.arena.diff;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Issue #33: domain ownership is keyed by world, but the diff recorder used a worldless
 * "first match wins" query. A claim at the same coordinates in another world could suppress the
 * arena diff and leave mutated blocks un-restored. The claim check must carry world identity.
 */
class DiffRecorderWorldClaimTest {

    @Test
    void aClaimInAnotherWorldDoesNotSuppressThisWorldsDiff() {
        UUID arenaWorld = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
        UUID otherWorld = UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb");
        List<Long> sink = new ArrayList<>();
        DiffRecorder recorder = new DiffRecorder(
                () -> (x, y, z) -> x >= 0 && x <= 10 && y == 64 && z >= 0 && z <= 10,
                (worldId, x, y, z) -> otherWorld.equals(worldId) && x == 5 && z == 5,
                sink::addAll);

        assertTrue(recorder.record(arenaWorld, 5, 64, 5),
                "a claim in a different world must not suppress the arena diff");
        assertFalse(recorder.record(otherWorld, 5, 64, 5),
                "the owning world's claim must still be honoured");
        assertEquals(1, sink.size());
    }

    @Test
    void worldlessRecordPassesNullWorldIdForTheCheckToFailOpen() {
        List<Long> sink = new ArrayList<>();
        boolean[] sawNull = {false};
        DiffRecorder recorder = new DiffRecorder(
                () -> (x, y, z) -> true,
                (worldId, x, y, z) -> {
                    sawNull[0] = worldId == null;
                    return false; // mirrors DiffRecorder.domainClaims()'s null-world fail-open
                },
                sink::addAll);
        assertTrue(recorder.record(5, 64, 5));
        assertTrue(sawNull[0], "the worldless compatibility path must reach the check as null");
    }
}
