package com.hizeric.shenaniganscore.battleground.arena.restore;

import com.hizeric.shenaniganscore.battleground.arena.snapshot.ArenaSnapshot.ChunkPos;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Deque;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Pure, server-free queue and accounting seam for {@link ArenaRestoreEngine}.
 *
 * <p>Every decision that used to lose data lives here: a slice is removed from a job only when it
 * is actually dispatched, and the job carrying its bounded remainder is re-queued in the same
 * operation. Tier 1 and tier 2 use separate deques so a re-queued tier-1 remainder can never be
 * starved behind gated tier-2 jobs.</p>
 *
 * <p>Performance notes versus the first version:</p>
 * <ul>
 *   <li>{@link #next} no longer calls {@code iterator.remove()} on an {@code ArrayList}. That was a
 *       head-shift per removed element, which is quadratic on a whole-chunk (~100k cell) job.
 *       A slice is now cut with one forward pass and one remainder copy.</li>
 *   <li>The tier-1 gate tracks a set of <i>remaining</i> positions, so
 *       {@link #markTierOneDone()} is O(1) instead of {@code containsAll} over everything.</li>
 *   <li>Ledger progress is counted incrementally once {@link #trackLedger(Set)} is called.</li>
 * </ul>
 */
final class ArenaRestoreQueue {

    record Job(ChunkPos pos, List<Long> pending, boolean tierOne) { }

    /** One bounded unit of work handed to the engine for a single chunk-load callback. */
    record Dispatch(ChunkPos pos, List<Long> writes, boolean tierOne, boolean finalSlice) { }

    private final Deque<Job> tierOneJobs = new ArrayDeque<>();
    private final Deque<Job> tierTwoJobs = new ArrayDeque<>();
    /** Chunks with no ledger remainder still need one completeness scan. */
    private final Deque<ChunkPos> verificationJobs = new ArrayDeque<>();
    /** Distinct positions actually written, the only honest source for progress. */
    private final Set<Long> written = new LinkedHashSet<>();
    /** Tier-1 positions enqueued and not yet written. Empty means the gate may open. */
    private final Set<Long> tierOneRemaining = new LinkedHashSet<>();

    private Set<Long> trackedLedger;
    private long trackedLedgerWritten;

    private boolean tierOneComplete;
    private boolean tierOneGateFired;
    private boolean failed;
    private boolean cancelled;
    private boolean tierTwoCancelled;
    private int jobsDone;

    void reset() {
        tierOneJobs.clear();
        tierTwoJobs.clear();
        verificationJobs.clear();
        written.clear();
        tierOneRemaining.clear();
        trackedLedger = null;
        trackedLedgerWritten = 0L;
        tierOneComplete = false;
        tierOneGateFired = false;
        failed = false;
        cancelled = false;
        tierTwoCancelled = false;
        jobsDone = 0;
    }

    /**
     * Opts into O(1) progress: from now on every newly written position that the ledger contains is
     * counted as it is recorded. Call right after {@link #reset()}.
     */
    void trackLedger(Set<Long> ledger) {
        this.trackedLedger = ledger;
        this.trackedLedgerWritten = 0L;
        if (ledger != null) {
            for (long packed : written) {
                if (ledger.contains(packed)) trackedLedgerWritten++;
            }
        }
    }

    void enqueueTierOne(ChunkPos pos, List<Long> positions) {
        if (pos != null && positions != null && !positions.isEmpty()) {
            tierOneJobs.addLast(new Job(pos, new ArrayList<>(positions), true));
            for (long packed : positions) {
                if (!written.contains(packed)) tierOneRemaining.add(packed);
            }
        }
    }

    void enqueueTierTwo(ChunkPos pos, List<Long> positions) {
        if (pos != null && positions != null && !positions.isEmpty()) {
            tierTwoJobs.addLast(new Job(pos, new ArrayList<>(positions), false));
        }
    }

    /** One bounded verification job for a snapshot chunk the ledger cannot account for. */
    void enqueueVerification(ChunkPos pos) {
        if (pos != null) verificationJobs.addLast(pos);
    }

    int jobCount() { return tierOneJobs.size() + verificationJobs.size() + tierTwoJobs.size(); }
    boolean jobsEmpty() {
        return tierOneJobs.isEmpty() && verificationJobs.isEmpty() && tierTwoJobs.isEmpty();
    }
    int tierOneCount() { return tierOneJobs.size(); }
    int jobsDone() { return jobsDone; }
    int writtenCount() { return written.size(); }

    boolean tierOneComplete() { return tierOneComplete; }
    boolean failed() { return failed; }
    boolean cancelled() { return cancelled; }
    boolean tierTwoCancelled() { return tierTwoCancelled; }
    boolean canDispatchTierTwo() {
        return tierOneComplete && !tierTwoCancelled && !failed && !cancelled;
    }

    /**
     * Returns true exactly once, and only after every tier-1 position across every chunk is
     * written. A per-job final slice can never open the gate while another tier-1 job still has
     * unwritten positions.
     */
    boolean markTierOneDone() {
        if (tierOneGateFired || !tierOneFullyWritten()) return false;
        tierOneGateFired = true;
        tierOneComplete = true;
        return true;
    }

    /** True when no tier-1 position remains unwritten; vacuously true when tier 1 is empty. */
    boolean tierOneFullyWritten() {
        return tierOneRemaining.isEmpty();
    }

    void fail() { failed = true; }

    void cancel() {
        cancelled = true;
        tierTwoCancelled = true;
        tierOneJobs.clear();
        verificationJobs.clear();
        tierTwoJobs.clear();
    }

    void cancelTierTwo() {
        tierTwoCancelled = true;
        verificationJobs.clear();
        tierTwoJobs.clear();
    }

    /**
     * Picks the next bounded slice. The picked positions are removed from the job; the job holding
     * the bounded remainder is re-queued before this method returns, so nothing can be dropped when
     * the engine polls the dispatch.
     */
    Dispatch next(int blockCap) {
        if (failed || cancelled) return null;
        if (!tierOneJobs.isEmpty()) return nextFrom(tierOneJobs, blockCap);
        if (!canDispatchTierTwo()) return null;
        if (!verificationJobs.isEmpty()) {
            return new Dispatch(verificationJobs.removeFirst(), List.of(), false, true);
        }
        return tierTwoJobs.isEmpty() ? null : nextFrom(tierTwoJobs, blockCap);
    }

    private Dispatch nextFrom(Deque<Job> source, int blockCap) {
        Job job = source.peekFirst();
        List<Long> pending = job.pending();
        int cap = Math.max(1, blockCap);
        List<Long> slice = new ArrayList<>(Math.min(cap, pending.size()));

        int index = 0;
        int size = pending.size();
        while (index < size && slice.size() < cap) {
            slice.add(pending.get(index++));
        }

        List<Long> remainder = new ArrayList<>(size - index);
        if (index < size) remainder.addAll(pending.subList(index, size));

        source.removeFirst();
        boolean finalSlice = remainder.isEmpty();
        if (!finalSlice) source.addLast(new Job(job.pos(), remainder, job.tierOne()));
        return new Dispatch(job.pos(), slice, job.tierOne(), finalSlice);
    }

    /**
     * Escalates a genuinely untrusted chunk to a whole-snapshot restore by superseding its ledger
     * remainder with every snapshot cell not yet written. The re-queued job then flows through the
     * same bounded {@link #next} dispatch as any other tier-2 job, so the per-tick slice cap still
     * applies and the remainder can never be dropped.
     */
    void escalateTierTwoToChunk(ChunkPos pos, Collection<Long> fullChunkPositions) {
        if (tierTwoCancelled || pos == null || fullChunkPositions == null) return;
        tierTwoJobs.removeIf(job -> job.pos().equals(pos));
        verificationJobs.remove(pos); // one job per chunk: escalation supersedes its scan
        List<Long> pending = new ArrayList<>(fullChunkPositions.size());
        for (long packed : fullChunkPositions) {
            if (!written.contains(packed)) pending.add(packed);
        }
        enqueueTierTwo(pos, pending);
    }

    /** Re-queues writes that a per-tick ms guard refused, at the front so they retry promptly. */
    void requeueUnwritten(ChunkPos pos, boolean tierOne, List<Long> positions) {
        if (positions == null || positions.isEmpty()) return;
        (tierOne ? tierOneJobs : tierTwoJobs).addFirst(
                new Job(pos, new ArrayList<>(positions), tierOne));
    }

    void recordWritten(Collection<Long> positions) {
        if (positions == null) return;
        for (long packed : positions) recordWritten(packed);
    }

    void recordWritten(long packed) {
        if (written.add(packed)) {
            tierOneRemaining.remove(packed);
            if (trackedLedger != null && trackedLedger.contains(packed)) trackedLedgerWritten++;
        }
    }

    void recordChunkDone() { jobsDone++; }

    /**
     * Distinct ledger positions written over the total ledger size, never above 100. Positions
     * written by a whole-chunk snapshot fallback are excluded unless the ledger asked for them, so
     * an over-broad fallback can never fake 100%.
     */
    int progressPercent(long total, Set<Long> ledger) {
        long done;
        if (ledger == null) {
            done = 0L;
        } else if (ledger == trackedLedger) {
            done = trackedLedgerWritten;
        } else {
            done = 0L;
            for (long packed : written) {
                if (ledger.contains(packed)) done++;
            }
        }
        return (int) Math.min(100L, done * 100L / Math.max(1L, total));
    }
}
