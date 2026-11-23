package com.hizeric.shenaniganscore.battleground.arena.restore;

import com.hizeric.shenaniganscore.battleground.arena.snapshot.ArenaSnapshot.ChunkPos;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Pure queue accounting: work-share progress for restores the ledger cannot measure.
 *
 * <p>Verification jobs are tier-2 gated, so each fixture opens the gate with
 * {@link ArenaRestoreQueue#markTierOneDone()} just as the engine does for a restore with no
 * tier-1 positions. That lets {@code next(int)} actually dispatch the job before it completes.</p>
 */
class ArenaRestoreQueueTest {

    @Test
    void workProgressStartsAtZeroAndClimbsWithCompletedSlices() {
        ArenaRestoreQueue q = new ArenaRestoreQueue();
        q.enqueueVerification(new ChunkPos(0, 0));
        q.enqueueVerification(new ChunkPos(1, 0));
        q.markTierOneDone(); // engine fires the gate for a tier-1-free restore
        assertEquals(0, q.progressPercentByWork(0));
        q.next(1); q.recordChunkDone();
        assertEquals(50, q.progressPercentByWork(0));
        q.next(1); q.recordChunkDone();
        assertEquals(100, q.progressPercentByWork(0));
    }

    @Test
    void workProgressCountsInFlightJobsAsRemaining() {
        ArenaRestoreQueue q = new ArenaRestoreQueue();
        q.enqueueVerification(new ChunkPos(0, 0));
        q.enqueueVerification(new ChunkPos(1, 0));
        q.markTierOneDone();
        q.next(1);                       // dispatched, callback not finished
        assertEquals(0, q.progressPercentByWork(1), "in-flight job is not done work");
        q.recordChunkDone();
        assertEquals(50, q.progressPercentByWork(0));
    }

    @Test
    void workProgressNeverClaimsCompleteWhileNewWorkRemains() {
        ArenaRestoreQueue q = new ArenaRestoreQueue();
        q.enqueueVerification(new ChunkPos(0, 0));
        q.markTierOneDone();
        q.next(1); q.recordChunkDone();
        assertEquals(100, q.progressPercentByWork(0));
        q.enqueueTierTwo(new ChunkPos(1, 0), List.of(1L, 2L)); // escalation discovered
        assertEquals(50, q.progressPercentByWork(0), "new work must lower the share below 100");
    }

    @Test
    void workProgressCapsBelowHundredWhenFailed() {
        ArenaRestoreQueue q = new ArenaRestoreQueue();
        q.enqueueVerification(new ChunkPos(0, 0));
        q.markTierOneDone();
        q.next(1); q.recordChunkDone();
        q.fail();
        assertEquals(99, q.progressPercentByWork(0), "a failed restore never reports 100%");
    }

    @Test
    void workProgressResetsBetweenRestoreGenerations() {
        ArenaRestoreQueue q = new ArenaRestoreQueue();
        q.enqueueVerification(new ChunkPos(0, 0));
        q.markTierOneDone();
        q.next(1); q.recordChunkDone();
        assertEquals(100, q.progressPercentByWork(0));
        q.reset();
        assertEquals(0, q.progressPercentByWork(0));
        assertEquals(0, q.jobsDone());
    }

    @Test
    void ledgerProgressUnchangedByWorkAccounting() {
        ArenaRestoreQueue q = new ArenaRestoreQueue();
        Set<Long> ledger = Set.of(10L, 20L, 30L, 40L);
        q.reset(); q.trackLedger(ledger);
        q.recordWritten(10L); q.recordWritten(11L); // 11 is not in the ledger
        assertEquals(25, q.progressPercent(ledger.size(), ledger));
    }
}
