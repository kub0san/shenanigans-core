package com.hizeric.shenaniganscore.battleground.arena.restore;

import com.hizeric.shenaniganscore.battleground.arena.ArenaRegion;
import com.hizeric.shenaniganscore.battleground.arena.ArenaRestorer;
import com.hizeric.shenaniganscore.battleground.arena.ArenaRuntimeConfig;
import com.hizeric.shenaniganscore.battleground.arena.RestoreBudget;
import com.hizeric.shenaniganscore.battleground.arena.diff.DiffLog;
import com.hizeric.shenaniganscore.battleground.arena.restore.ArenaChunkWork.Policy;
import com.hizeric.shenaniganscore.battleground.arena.restore.ArenaChunkWork.TicketLedger;
import com.hizeric.shenaniganscore.battleground.arena.restore.ArenaChunkWork.WorkBudget;
import com.hizeric.shenaniganscore.battleground.arena.restore.ArenaRestoreQueue.Dispatch;
import com.hizeric.shenaniganscore.battleground.arena.restore.ArenaVerification.ChunkScan;
import com.hizeric.shenaniganscore.battleground.arena.snapshot.ArenaSnapshot;
import com.hizeric.shenaniganscore.battleground.arena.snapshot.ArenaSnapshot.ChunkPos;
import com.hizeric.shenaniganscore.battleground.arena.snapshot.ArenaSnapshotStore;
import com.hizeric.shenaniganscore.core.util.BlockKey;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.plugin.Plugin;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.function.LongSupplier;
import java.util.logging.Logger;

/**
 * Adaptive hybrid restore: a small tier-1 deployment gate, then budgeted per-chunk tier-2 writes
 * with a read-only completeness scan interleaved on the same chunks.
 *
 * <p>Tier 1 is applied from the diff but seeded with baseline states from the snapshot, inside the
 * planner's 23-block drop-column envelope. Every chunk is verified before its tier-2 writes:
 * unlogged world-vs-baseline discrepancies mark a chunk untrusted, and only a genuinely untrusted
 * chunk is restored from the whole snapshot, bounded by the same per-tick slice cap, with its
 * remainder re-queued. Verification is diagnostic only when fallback is disabled.</p>
 *
 * <p>Changes in this version:</p>
 * <ul>
 *   <li><b>Resumable verification.</b> A chunk is only called verified after every cell has been
 *       compared. Previously a scan cut short by the ms budget still counted as complete.</li>
 *   <li><b>Restore writes are never gated on player positions.</b></li>
 *   <li><b>Chunk tickets</b> parse their key from the right, so world names containing '/' work.</li>
 *   <li><b>capture()</b> no longer saves the snapshot twice.</li>
 *   <li><b>Animations</b> through {@link RestoreEffects}; they can never fail a restore.</li>
 * </ul>
 *
 * <p>All queue/accounting state lives in the pure {@link ArenaRestoreQueue} seam, and every Bukkit
 * side effect is behind {@link Deps}, so the dispatch and write path is directly unit-testable.</p>
 *
 * <p>Task 8 owns wiring the map-aware beginRestore overload, the STAGING gate via
 * {@link #tierOneComplete()} / {@link #onTierOneComplete()}, and round-boundary cancellation via
 * {@link #cancelTierTwo()}.</p>
 */
public final class ArenaRestoreEngine implements ArenaRestorer {

    private enum Phase { IDLE, TIER_ONE, TIER_TWO, DONE }

    private static final int MAX_IN_FLIGHT = 8;
    /** Cells compared per callback even when the tick budget is spent, so scans always advance. */
    private static final int MIN_SCAN_BLOCKS = 512;
    /** Progress effects fire every Nth restore step (about twice a second at 20 TPS). */
    private static final int PROGRESS_EVERY_STEPS = 10;

    /** Side-effect seams; the production instance delegates to Bukkit, tests inject fakes. */
    interface ChunkLoader { CompletableFuture<Chunk> load(World world, int cx, int cz); }
    interface MainRunner { void run(Runnable task); }
    interface BlockWriter { void write(World world, long packed, String state, boolean physics); }
    interface StateProbe { String at(World world, int x, int y, int z); }
    interface PolicySource { Policy get(); }

    record Deps(ChunkLoader loader, MainRunner runner, BlockWriter writer,
                StateProbe stateProbe, PolicySource policy,
                TicketLedger<String> tickets, World forcedWorld) { }

    private final Plugin plugin;
    private final Logger logger;
    private final Runnable onTierOneComplete;
    private final LongSupplier nanoClock;
    private final Deps deps;
    private final ArenaRestoreQueue queue = new ArenaRestoreQueue();

    private final Set<ChunkPos> verified = new LinkedHashSet<>();
    private final Set<ChunkPos> untrusted = new LinkedHashSet<>();
    /** In-progress verification scans, resumed across ticks until every cell is compared. */
    private final Map<ChunkPos, ChunkScan> scans = new HashMap<>();
    private String mapId = ArenaSnapshotStore.DEFAULT_MAP_ID;

    private RestoreEffects effects;
    private boolean effectsFailureLogged;
    private int stepCounter;
    /** Bumped by beginRestore; a load callback from an older generation is discarded. */
    private long generation;

    private ArenaRegion region;
    private DiffLog.Decoded diff;
    private ArenaSnapshot snapshot;
    private Phase phase = Phase.IDLE;
    private boolean restoring;
    private int pendingLoads;
    private long writtenChunks;
    private long totalPositions = 1L;
    /** Rolling per-tick budget shared by every callback that executes inside the same window. */
    private WorkBudget callbackBudget;

    public ArenaRestoreEngine(Plugin plugin, Logger logger, Runnable onTierOneComplete) {
        this(plugin, logger, onTierOneComplete, null);
    }

    /** Test seam: the clock supplier is only used by {@link WorkBudget}. */
    ArenaRestoreEngine(Plugin plugin, Logger logger, Runnable onTierOneComplete, LongSupplier nanoClock) {
        this(plugin, logger, onTierOneComplete, nanoClock, null);
    }

    /** Test seam: injects every Bukkit side effect plus the live-policy source. */
    ArenaRestoreEngine(Deps deps, Logger logger, Runnable onTierOneComplete, LongSupplier nanoClock) {
        this(null, logger, onTierOneComplete, nanoClock, deps);
    }

    private ArenaRestoreEngine(Plugin plugin, Logger logger, Runnable onTierOneComplete,
                               LongSupplier nanoClock, Deps deps) {
        this.plugin = plugin;
        this.logger = logger;
        this.onTierOneComplete = onTierOneComplete;
        this.nanoClock = nanoClock;
        // Production gets animations by default; injected-Deps (tests) stay silent.
        this.effects = deps == null
                ? new RestoreAnimator(() -> RestoreAnimator.Settings.from(ArenaRuntimeConfig.current()))
                : RestoreEffects.NONE;
        this.deps = deps == null ? bukkitDeps(plugin) : deps;
    }

    private static Deps bukkitDeps(Plugin plugin) {
        TicketLedger<String> tickets = new TicketLedger<>(
                key -> applyTicket(plugin, key, true),
                key -> applyTicket(plugin, key, false));
        return new Deps(
                (world, cx, cz) -> world.getChunkAtAsync(cx, cz),
                task -> Bukkit.getScheduler().runTask(plugin, task),
                (world, packed, state, physics) -> {
                    BlockData data = Bukkit.createBlockData(state);
                    Block block = world.getBlockAt(BlockKey.unpackX(packed),
                            BlockKey.unpackY(packed), BlockKey.unpackZ(packed));
                    block.setBlockData(data, physics);
                },
                ArenaRestoreEngine::stateAt,
                () -> Policy.from(ArenaRuntimeConfig.current()),
                tickets,
                null);
    }

    /** Key format {@code <world>/<chunkX>/<chunkZ>}, parsed from the right so world names may contain '/'. */
    private static void applyTicket(Plugin plugin, String key, boolean add) {
        int zSep = key.lastIndexOf('/');
        int xSep = zSep <= 0 ? -1 : key.lastIndexOf('/', zSep - 1);
        if (zSep <= 0 || xSep <= 0) return;
        World world = Bukkit.getWorld(key.substring(0, xSep));
        if (world == null) return;
        int cx = Integer.parseInt(key.substring(xSep + 1, zSep));
        int cz = Integer.parseInt(key.substring(zSep + 1));
        if (add) {
            world.addPluginChunkTicket(cx, cz, plugin);
        } else {
            world.removePluginChunkTicket(cx, cz, plugin);
        }
    }

    /** Swap or disable the animation layer, for example {@code RestoreEffects.NONE}. */
    public void effects(RestoreEffects effects) {
        this.effects = effects == null ? RestoreEffects.NONE : effects;
        this.effectsFailureLogged = false;
    }

    @Override
    public String id() { return "tiered"; }

    @Override
    public boolean prepare(ArenaRegion region) {
        this.region = region;
        return true;
    }

    /** Map-aware capture is owned by ArenaSnapshotStore; capture() already saves the file. */
    @Override
    public boolean capture(ArenaRegion region) {
        try {
            new ArenaSnapshotStore(arenaRoot(), logger).capture(mapId, region);
            return true;
        } catch (Exception exception) {
            logger.warning("Arena snapshot capture failed for map '" + mapId + "': "
                    + exception.getMessage());
            return false;
        }
    }

    /** The legacy Phase-A path is deliberately not implemented here; Task 8 wires the new overload. */
    @Override
    public boolean beginRestore(ArenaRegion region) {
        logger.warning("Tiered arena restore needs a diff log and snapshot; use "
                + "beginRestore(region, mapId, diff, snapshot).");
        return false;
    }

    @Override
    public boolean beginRestore(ArenaRegion region, String mapId,
                                DiffLog.Decoded diff, ArenaSnapshot snapshot) {
        cancel();
        generation++;
        if (region == null || diff == null || snapshot == null) {
            logger.warning("Tiered arena restore skipped: region, diff or snapshot is missing.");
            queue.fail();
            return false;
        }

        this.region = region;
        this.mapId = mapId == null || mapId.isBlank() ? ArenaSnapshotStore.DEFAULT_MAP_ID : mapId;
        this.diff = diff;
        this.snapshot = snapshot;
        queue.reset();
        queue.trackLedger(diff.positions());
        callbackBudget = null;
        this.pendingLoads = 0;
        this.writtenChunks = 0L;
        this.stepCounter = 0;
        this.totalPositions = Math.max(1L, diff.positions().size());
        verified.clear();
        untrusted.clear();
        scans.clear();

        if (logger != null && diff.complete() && deps.policy().get().verify()) {
            logger.info("Arena restore: committed diff for map '" + mapId + "' round "
                    + diff.roundId() + " uses a bounded " + ArenaVerification.CHEAP_SAMPLE_CELLS
                    + "-cell-per-chunk trusted-diff sample instead of an exhaustive cell scan.");
        }

        Set<Long> essentials = TierOnePlanner.select(diff.positions(), snapshot);
        Map<ChunkPos, List<Long>> tierOneByChunk = groupByChunk(essentials);
        for (Map.Entry<ChunkPos, List<Long>> entry : tierOneByChunk.entrySet()) {
            queue.enqueueTierOne(entry.getKey(), entry.getValue());
        }

        Set<Long> remaining = new LinkedHashSet<>(diff.positions());
        remaining.removeAll(essentials);
        Map<ChunkPos, List<Long>> remainingByChunk = groupByChunk(remaining);
        for (ChunkPos pos : snapshot.chunkPositions()) {
            List<Long> remainder = remainingByChunk.remove(pos);
            if (remainder == null || remainder.isEmpty()) {
                queue.enqueueVerification(pos);
            } else {
                queue.enqueueTierTwo(pos, remainder);
            }
        }
        for (Map.Entry<ChunkPos, List<Long>> entry : remainingByChunk.entrySet()) {
            queue.enqueueTierTwo(entry.getKey(), entry.getValue());
        }

        phase = Phase.TIER_ONE;
        restoring = true;
        World world = world();
        fx(() -> effects.onStart(world, this.region));
        if (queue.tierOneCount() == 0) fireTierOneGate();
        return true;
    }

    @Override
    public boolean restoreStep(RestoreBudget legacyBudget) {
        if (!restoring || queue.cancelled()) return true;
        if (legacyBudget != null && !legacyBudget.canContinue()) return false;

        World world = world();
        if (world == null) {
            fail("arena world is not loaded");
            return true;
        }

        Policy policy = deps.policy().get();
        WorkBudget budget = new WorkBudget(policy.msPerTick(), policy.blocksPerTick(),
                policy.chunksPerTick(), policy.sectionsPerTick(), nanoClock);
        budget.beginTick();
        // One budget for the whole tick: dispatch, verification and writes all draw from it.
        callbackBudget = budget;

        int guard = queue.jobCount();
        while (!queue.jobsEmpty() && pendingLoads < MAX_IN_FLIGHT && guard-- > 0) {
            if (!budget.canStartChunk()
                    || !budget.canStartSections(ArenaChunkWork.SECTIONS_PER_CHUNK_CAP)) break;

            Dispatch dispatch = queue.next(policy.blocksPerTick());
            if (dispatch == null) continue; // gated this tick

            String ticketKey = ticketKey(world, dispatch.pos());
            deps.tickets().acquire(ticketKey);
            pendingLoads++;
            final Dispatch current = dispatch;
            final long dispatchedGeneration = generation;
            try {
                deps.loader().load(world, dispatch.pos().x(), dispatch.pos().z())
                        .whenComplete((chunk, error) -> deps.runner().run(() -> {
                            try {
                                if (dispatchedGeneration != generation) {
                                    // A newer restore owns the queue: this callback belongs to an
                                    // abandoned snapshot/diff and must never reach processChunk.
                                    return;
                                }
                                if (queue.failed() || queue.cancelled()) {
                                    // fail()/cancel() already ended the restore: drop in-flight work.
                                } else if (error != null) {
                                    fail("chunk " + current.pos() + " failed to load: "
                                            + error.getMessage());
                                } else if (queue.tierTwoCancelled() && !current.tierOne()) {
                                    // round boundary ended tier 2 while this chunk was loading.
                                } else {
                                    processChunk(world, chunk, current, policy);
                                }
                            } catch (RuntimeException exception) {
                                fail("chunk " + current.pos() + " restore failed: "
                                        + exception.getMessage());
                            } finally {
                                deps.tickets().release(ticketKey);
                                // Only the owning generation decrements its own in-flight count.
                                if (dispatchedGeneration == generation) {
                                    pendingLoads = Math.max(0, pendingLoads - 1);
                                    // A tier-2 boundary cancel may leave no stepper to drain the
                                    // queue. The last owning callback must finish that restore;
                                    // the guards keep stale/full-cancel callbacks from refiring
                                    // onFinished or interfering with a newer generation.
                                    if (restoring && !queue.cancelled()
                                            && queue.tierTwoCancelled()
                                            && queue.jobsEmpty() && pendingLoads == 0) {
                                        finish();
                                    }
                                }
                            }
                        }));
            } catch (RuntimeException exception) {
                deps.tickets().release(ticketKey);
                pendingLoads = Math.max(0, pendingLoads - 1);
                fail("chunk " + dispatch.pos() + " load could not be queued: "
                        + exception.getMessage());
                return true;
            }
            budget.recordChunk();
        }

        if (queue.jobsEmpty() && pendingLoads == 0) {
            finish();
            return true;
        }
        if (++stepCounter % PROGRESS_EVERY_STEPS == 0) {
            final int percent = progressPercent();
            fx(() -> effects.onProgress(world, percent));
        }
        return false;
    }

    @Override
    public void cancel() {
        boolean wasActive = restoring || phase == Phase.TIER_ONE || phase == Phase.TIER_TWO;
        queue.cancel();
        scans.clear();
        restoring = false;
        phase = Phase.IDLE;
        deps.tickets().releaseAll();
        // End the cosmetic layer too, or a boss bar survives a disable/round-boundary cancel.
        if (wasActive) fx(() -> effects.onFinished(world(), false));
    }

    /** Round-boundary cancellation: tier 2 stops, tier 1 stays complete. */
    public void cancelTierTwo() {
        queue.cancelTierTwo();
        if (queue.jobsEmpty() && pendingLoads == 0) finish();
    }

    @Override
    public boolean isRestoring() { return restoring; }

    @Override
    public boolean restoreFailed() { return queue.failed(); }

    /**
     * Verification-aware progress accounting is owned by issue #3. Do not add a competing metric
     * here; this method intentionally keeps its written-ledger semantics until #3 lands.
     */
    @Override
    public int progressPercent() {
        if (phase == Phase.DONE && !queue.failed()) return 100;
        Set<Long> ledger = diff == null ? Set.of() : diff.positions();
        if (ledger.isEmpty()) return queue.progressPercentByWork(pendingLoads);
        return queue.progressPercent(totalPositions, ledger);
    }

    /** Visible to Task 8 so it can open deployment as soon as the gate completes. */
    public boolean tierOneComplete() { return queue.tierOneComplete(); }

    public Runnable onTierOneComplete() { return onTierOneComplete; }

    public Phase phase() { return phase; }

    /** Changes on every beginRestore; callbacks carrying an older value are ignored. */
    public long generation() { return generation; }

    public String mapId() { return mapId; }

    public void mapId(String mapId) {
        this.mapId = mapId == null || mapId.isBlank() ? ArenaSnapshotStore.DEFAULT_MAP_ID : mapId;
    }

    public int verifiedChunks() { return verified.size(); }
    public int untrustedChunks() { return untrusted.size(); }
    public long writtenChunks() { return writtenChunks; }

    // ---------------------------------------------------------------- internals

    private World world() {
        return deps.forcedWorld() != null ? deps.forcedWorld()
                : (region == null ? null : region.world());
    }

    /** Runs a cosmetic hook; the first failure disables effects so a bug can never hurt a restore. */
    private void fx(Runnable task) {
        try {
            task.run();
        } catch (RuntimeException exception) {
            effects = RestoreEffects.NONE;
            if (!effectsFailureLogged && logger != null) {
                effectsFailureLogged = true;
                logger.warning("Arena restore animations disabled after an error: " + exception);
            }
        }
    }

    /**
     * The per-tick budget installed by {@link #restoreStep}. Every callback in one tick draws from
     * this single allowance; it is never recreated mid-tick just because it ran out, otherwise the
     * ms/block caps could be bypassed by merely triggering another callback.
     */
    private WorkBudget sharedBudget(Policy policy) {
        if (callbackBudget == null) {
            callbackBudget = new WorkBudget(policy.msPerTick(), policy.blocksPerTick(),
                    policy.chunksPerTick(), policy.sectionsPerTick(), nanoClock);
            callbackBudget.beginTick();
        }
        return callbackBudget;
    }

    private void processChunk(World world, Chunk loaded, Dispatch dispatch, Policy policy) {
        if (queue.failed() || queue.cancelled()) return;
        if (queue.tierTwoCancelled() && !dispatch.tierOne()) return;
        if (loaded == null) {
            fail("chunk " + dispatch.pos() + " was not loaded in the restore callback");
            return;
        }
        WorkBudget work = sharedBudget(policy);

        boolean escalateToSnapshot = false;
        // An unflushed ledger is untrustworthy even when the verify toggle is off.
        boolean mustVerify = policy.verify() || (diff != null && !diff.complete());
        // Once a resumable exhaustive scan owns this chunk, its dirty sample must never be
        // re-evaluated: a transient clean re-sample could mark the chunk verified and discard
        // the full comparison (and its untrusted/snapshot fallback verdict) mid-flight.
        if (mustVerify && !verified.contains(dispatch.pos()) && !scans.containsKey(dispatch.pos())) {
            if (diff != null && diff.complete() && diff.positions().isEmpty()) {
                // A committed, empty ledger is authoritative: nothing to compare, so no probes.
                scans.remove(dispatch.pos());
                verified.add(dispatch.pos());
            } else if (diff != null && diff.complete() && policy.verify()) {
                Set<Long> sampled = ArenaVerification.sampledUnloggedChunk(snapshot, dispatch.pos(),
                        (x, y, z) -> deps.stateProbe().at(world, x, y, z),
                        diff.positions(), ArenaVerification.CHEAP_SAMPLE_CELLS);
                if (sampled.isEmpty()) {
                    // Committed ledger is authoritative for this chunk: no anomaly observed.
                    scans.remove(dispatch.pos());
                    verified.add(dispatch.pos());
                } else {
                    // PR #2's rule: an observed discrepancy is never written over without a full
                    // comparison and, when enabled, snapshot fallback. Escalate this chunk to the
                    // resumable exhaustive scan below; do not write until that verdict is in.
                    // Once per chunk only: scans holds an entry while a resumable scan is in
                    // progress, so later slices of the same chunk must not re-log the warning.
                    if (logger != null && !scans.containsKey(dispatch.pos())) {
                        logger.warning("Arena restore: chunk " + dispatch.pos()
                                + " failed the bounded trusted-diff check (" + sampled.size()
                                + " sampled discrepancy/ies); switching to the exhaustive scan before any write.");
                    }
                }
            }
        }

        if (mustVerify && !verified.contains(dispatch.pos())) {
            ChunkScan scan = scans.computeIfAbsent(dispatch.pos(),
                    pos -> new ChunkScan(snapshot, pos));
            boolean finished = scan.step((x, y, z) -> deps.stateProbe().at(world, x, y, z),
                    diff.positions(), work::canContinue, MIN_SCAN_BLOCKS);

            if (!finished && !dispatch.tierOne()) {
                // Not enough budget to reach a verdict yet: hold this slice, keep the scan cursor,
                // and retry next tick. Nothing is written to a chunk we have not fully inspected.
                // (Tier 1 never waits on verification, so the deployment gate is unaffected.)
                if (dispatch.writes().isEmpty()) {
                    // Verification-only jobs have no write slice to re-queue.
                    queue.enqueueVerification(dispatch.pos());
                } else {
                    queue.requeueUnwritten(dispatch.pos(), false, dispatch.writes());
                }
                // A resumable scan slice is completed work even though no verdict exists yet.
                queue.recordChunkDone();
                return;
            }
            if (finished) {
                scans.remove(dispatch.pos());
                verified.add(dispatch.pos());
                Set<Long> unlogged = scan.unlogged();
                if (!unlogged.isEmpty()) {
                    untrusted.add(dispatch.pos());
                    logger.warning("Arena restore: chunk " + dispatch.pos() + " has "
                            + unlogged.size() + " unlogged discrepancy/ies — "
                            + (policy.fallback() ? "restoring it from the snapshot"
                            : "diagnostic only (fallback disabled)"));
                    // Only a genuinely untrusted chunk is restored from the whole snapshot, and
                    // only when fallback is enabled. Escalating after this slice is written lets us
                    // exclude already-written cells from the re-queued remainder, so no cell is
                    // written twice. Unwritten tier-1 remainders stay in tier 1 and are excluded
                    // from this escalation.
                    escalateToSnapshot = policy.fallback();
                }
            }
        }

        // Tier 1 always uses its bounded planner slice with snapshot baseline states. A whole-chunk
        // snapshot restore is never used here: escalation re-queues the full chunk as ordinary
        // bounded tier-2 work, so the same per-tick slice cap covers every write.
        List<Long> targets = dispatch.writes();

        List<Long> unwritten = new ArrayList<>();
        List<Long> writtenHere = new ArrayList<>(targets.size());
        for (long packed : targets) {
            if (!work.canWriteBlock()) {
                unwritten.add(packed);
                continue;
            }
            int x = BlockKey.unpackX(packed);
            int y = BlockKey.unpackY(packed);
            int z = BlockKey.unpackZ(packed);
            String state = snapshot.stateAt(x, y, z);
            if (state == null) state = ArenaSnapshot.AIR;
            deps.writer().write(world, packed, state, false);
            work.recordBlock();
            queue.recordWritten(packed);
            writtenHere.add(packed);
            if (policy.relight() && ArenaChunkWork.needsRelight(state)) {
                deps.writer().write(world, packed, state, true);
                work.recordBlock(); // the forced re-apply is real, budgeted work too
            }
        }
        if (!unwritten.isEmpty()) queue.requeueUnwritten(dispatch.pos(), dispatch.tierOne(), unwritten);
        if (!writtenHere.isEmpty()) {
            writtenChunks++;
            fx(() -> effects.onChunkRestored(world, dispatch.pos(), writtenHere, dispatch.tierOne()));
        }
        queue.recordChunkDone();

        if (escalateToSnapshot && !queue.tierTwoCancelled()) {
            Set<Long> escalatePositions = ArenaVerification.positionsOf(snapshot, dispatch.pos());
            if (dispatch.tierOne() && !unwritten.isEmpty()) {
                escalatePositions.removeAll(unwritten);
            }
            queue.escalateTierTwoToChunk(dispatch.pos(), escalatePositions);
        }

        // finalSlice is per-job; markTierOneDone() enforces completion across all tier-1 chunks.
        if (dispatch.tierOne() && unwritten.isEmpty()) fireTierOneGate();
    }

    /** Read-only, chunk-local state probe; never triggers a synchronous chunk load. */
    private static String stateAt(World world, int x, int y, int z) {
        int cx = x >> 4;
        int cz = z >> 4;
        if (!world.isChunkLoaded(cx, cz)) return null;
        return world.getBlockAt(x, y, z).getBlockData().getAsString();
    }

    private static Map<ChunkPos, List<Long>> groupByChunk(Set<Long> positions) {
        Map<ChunkPos, List<Long>> grouped = new LinkedHashMap<>();
        for (long packed : positions) {
            ChunkPos pos = new ChunkPos(BlockKey.unpackX(packed) >> 4, BlockKey.unpackZ(packed) >> 4);
            grouped.computeIfAbsent(pos, ignored -> new ArrayList<>()).add(packed);
        }
        return grouped;
    }

    private static String ticketKey(World world, ChunkPos pos) {
        return world.getName() + "/" + pos.x() + "/" + pos.z();
    }

    private Path arenaRoot() {
        return plugin.getDataFolder().toPath().resolve("battleground").resolve("arena");
    }

    private void fireTierOneGate() {
        if (!queue.markTierOneDone()) return;
        phase = Phase.TIER_TWO;
        World world = world();
        fx(() -> effects.onTierOneComplete(world));
        if (onTierOneComplete != null) onTierOneComplete.run();
    }

    private void finish() {
        if (!restoring && phase == Phase.DONE) return;
        restoring = false;
        phase = Phase.DONE;
        scans.clear();
        deps.tickets().releaseAll();
        World world = world();
        fx(() -> effects.onFinished(world, false));
    }

    private void fail(String reason) {
        queue.fail();
        restoring = false;
        phase = Phase.DONE;
        scans.clear();
        deps.tickets().releaseAll();
        if (logger != null) logger.warning("Arena restore failed: " + reason);
        World world = world();
        fx(() -> effects.onFinished(world, true));
    }
}
