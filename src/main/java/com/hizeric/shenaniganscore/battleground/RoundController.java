package com.hizeric.shenaniganscore.battleground;

import com.hizeric.shenaniganscore.battleground.admin.AdminRoundGate;
import com.hizeric.shenaniganscore.battleground.arena.ArenaProtection;
import com.hizeric.shenaniganscore.battleground.arena.ArenaRegion;
import com.hizeric.shenaniganscore.battleground.arena.ArenaRuntimeConfig;
import com.hizeric.shenaniganscore.battleground.arena.EntitySweeper;
import com.hizeric.shenaniganscore.battleground.arena.diff.AsyncDiffWriter;
import com.hizeric.shenaniganscore.battleground.arena.diff.DiffLog;
import com.hizeric.shenaniganscore.battleground.arena.snapshot.ArenaSnapshot;
import com.hizeric.shenaniganscore.battleground.command.Permissions;
import com.hizeric.shenaniganscore.battleground.player.ArenaInventories;
import com.hizeric.shenaniganscore.battleground.player.BattlegroundLock;
import com.hizeric.shenaniganscore.battleground.menu.base.GuiStyle;
import com.hizeric.shenaniganscore.core.domain.DomainRegistry;
import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.plugin.Plugin;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Level;

/**
 * Round lifecycle, tiered restore, diff-ledger bookkeeping, admin round actions and config reload.
 * Extracted from {@link BattlegroundManager} (C1): every round-boundary side effect lives here so a
 * deploy or capture change cannot silently break the ledger invariant.
 */
final class RoundController {
    private final BattlegroundManager m;

    // C2: three purpose-scoped tasks replace the old 1-tick "does nothing 19/20 ticks" timer.
    // The restore task is scheduled on demand only while a restore is in flight; HUD and clock
    // run at their natural cadences. Global-region scheduling is Paper/Folia compatible.
    private ScheduledTask restoreTask;
    private ScheduledTask hudTask;
    private ScheduledTask clockTask;

    /** One-shot tier-1 gate, rearmed exactly once per {@link #beginReset()}. */
    private final TierOneGate tierOneGateConsumed = new TierOneGate();

    /** Issue #7: deploy opens only when the FULL restore finishes, never at the tier-1 gate. */
    private final RoundReadiness readiness = new RoundReadiness();

    /**
     * The restore engine can fire the tier-1 gate synchronously from {@code beginRestore} when the
     * diff is empty. Only the first call for a generation may broadcast tier-1 status; the round
     * transition itself belongs to {@link #completeRestoreAndOpenRound(boolean)} (issue #7).
     */
    static final class TierOneGate {
        private boolean consumed;

        void reset() { consumed = false; }

        boolean tryOpen() {
            if (consumed) return false;
            consumed = true;
            return true;
        }

        boolean consumed() { return consumed; }
    }

    RoundController(BattlegroundManager manager) {
        this.m = manager;
    }

    // ------------------------------------------------------------------ scheduler

    /** Starts the steady-state HUD (4 Hz) and clock (1 Hz) tasks. */
    void startScheduler() {
        Plugin plugin = m.plugin;
        if (hudTask == null) {
            hudTask = Bukkit.getGlobalRegionScheduler().runAtFixedRate(plugin,
                    task -> tickHud(), 5L, 5L);
        }
        if (clockTask == null) {
            clockTask = Bukkit.getGlobalRegionScheduler().runAtFixedRate(plugin,
                    task -> tickClock(), 20L, 20L);
        }
    }

    /** Cancels every task, including an in-flight restore step. */
    void stopScheduler() {
        cancel(restoreTask); restoreTask = null;
        cancel(hudTask); hudTask = null;
        cancel(clockTask); clockTask = null;
    }

    private static void cancel(ScheduledTask task) {
        if (task != null) task.cancel();
    }

    /** 20 Hz only while a tiered restore is actually running. */
    private void startRestoreTask() {
        if (restoreTask != null && !restoreTask.isCancelled()) return;
        restoreTask = Bukkit.getGlobalRegionScheduler().runAtFixedRate(m.plugin,
                task -> tickRestore(), 1L, 1L);
    }

    private void stopRestoreTask() {
        cancel(restoreTask);
        restoreTask = null;
    }

    // ------------------------------------------------------------------ tick slices

    private void tickRestore() {
        if (m.restoreEngine == null || !m.restoreEngine.isRestoring()) {
            stopRestoreTask();
            // fail()/cancelTierTwo() can stop the engine from a load callback without another
            // restoreStep() return; a stranded STAGING/RESTORING round must still open deploy.
            if (m.state == RoundState.STAGING || m.state == RoundState.RESTORING) {
                boolean failed = m.restoreEngine != null
                        && RoundGates.restoreNotice(m.restoreEngine.restoreFailed())
                        == RoundGates.RestoreNotice.FAILED;
                completeRestoreAndOpenRound(failed);
            }
            return;
        }
        m.budget.beginTick();
        if (m.restoreEngine.restoreStep(m.budget)) {
            stopRestoreTask();
            onRestoreComplete();
        }
    }

    private void tickHud() {
        for (UUID uuid : m.deployed) {
            Player player = Bukkit.getPlayer(uuid);
            if (player != null && player.isOnline()) {
                com.hizeric.shenaniganscore.battleground.ability.TechniqueHud.refreshNotice(player);
                com.hizeric.shenaniganscore.battleground.ability.TechniqueHud.send(m.plugin, player);
            }
        }
    }

    private void tickClock() {
        // Issue #7: deploy opens only after the full restore completes, so STAGING and
        // RESTORING must not advance the clock; the guard stays.
        if (!isRunningLike()) return;

        // The clock pauses whenever nobody is deployed: time spent shopping or editing in the
        // hub never burns the round. RoundClock owns the arithmetic.
        RoundClock.Phase phase = m.clock.tick(m.deployed.size());
        if (!m.clock.isCounting()) {
            if (phase == RoundClock.Phase.ENDED) beginReset();
            return;
        }
        if (phase == RoundClock.Phase.WARNING && m.state == RoundState.RUNNING) {
            m.state = RoundState.WARNING;
            m.announce("<yellow>Round ending in <white>" + m.clock.secondsRemaining()
                    + "s<yellow> — return to staging!");
        }
        if (phase == RoundClock.Phase.ENDED) beginReset();
    }

    private boolean isRunningLike() {
        return m.state == RoundState.RUNNING || m.state == RoundState.WARNING;
    }

    // ------------------------------------------------------------------ lifecycle

    void beginReset() {
        if (m.restoreEngine != null && m.restoreEngine.isRestoring()) {
            // Issue #7: tier 2 is complete by the time deploy opens; cancel any residual writes
            // so the new reset can take over. A STAGING/tier-1 restore must not be interrupted.
            if (!isRunningLike()) return;
            m.restoreEngine.cancelTierTwo();
        }
        if (m.state == RoundState.RESTORING) return;
        m.state = RoundState.STAGING;
        tierOneGateConsumed.reset(); // one gate per reset generation
        readiness.reset();

        // Round end: pull every deployed player back to the hub — including one mid-fight or
        // mid-dive — and clear their dive/lock state so nothing holds them in the arena.
        for (UUID uuid : new ArrayList<>(m.deployed)) {
            Player player = Bukkit.getPlayer(uuid);
            boolean diving = m.dives.isDiving(uuid);
            m.dives.clear(uuid);
            if (player == null) continue;
            if (RoundGates.roundEnd(true, diving, m.staging.isStaged(player))
                    != RoundGates.RoundEnd.HUB) continue;
            com.hizeric.shenaniganscore.battleground.menu.shell.MenuLatch.get().clear(player);
            // Hand the hub inventory back BEFORE staging captures it: otherwise the staging
            // registry pins the arena kit and reissues it on the next deploy. Refreshing the
            // ArenaInventories snapshot keeps hub acquisitions made this round for the next one.
            m.deploys.restoreInventory(player);
            m.inventories.replace(uuid, new ArenaInventories.Snapshot(
                    player.getInventory().getContents(), player.getItemOnCursor()));
            m.staging.enter(player);
            // Evacuate staged players out of the arena footprint so nothing is written under them.
            ArenaRegion evacRegion = m.arenaStore.region();
            Location evacDest = evacuationDestination(player, evacRegion);
            if (evacDest == null || !player.teleport(evacDest)) {
                // A failed teleport means we must not pretend the player is clear of the arena.
                m.plugin.getLogger().warning("Could not evacuate " + player.getName()
                        + " from the arena before restore; leaving them where they are.");
            } else {
                player.setAllowFlight(true);
                player.setFlying(true);
                m.staging.registry().setAnchor(uuid, evacDest);
            }
            new com.hizeric.shenaniganscore.battleground.menu.shell.HubScreen(m.plugin, player).show();
        }
        m.deployed.clear();
        m.dives.clear();

        stopDomainsAndSweep();
        m.economy.rollNewRound();

        flushDiffLedger(m.arenaStore.activeMapId());
        DiffLog.Decoded diff = readDiffLog(m.arenaStore.activeMapId());
        if (startTieredRestore(m.arenaStore.activeMapId(), diff, false)) {
            m.announce("<gray>Restoring arena… <white>Shop is open.");
            return;
        }
        // No tiered restore ran: only an absent log or an empty, flushed diff is clean. Recorded
        // positions or an incomplete ledger are unrestored work and must not be announced as ready.
        boolean dirty = diff != null && (!diff.complete() || !diff.positions().isEmpty());
        completeRestoreAndOpenRound(dirty);
    }

    /**
     * Picks a hub-side destination outside the arena footprint that is inside the world border and
     * within the build height. {@code null} means no safe destination exists and evacuation must be
     * skipped rather than teleporting into the void or writing blocks under a player.
     */
    private static Location evacuationDestination(Player player, ArenaRegion region) {
        World world = region == null ? null : region.world();
        if (world == null) world = player.getWorld();
        if (world == null) return null;

        Location spawn = world.getSpawnLocation();
        if (safeEvacuation(spawn, region, world)) return spawn;
        if (region == null) return null; // no corners to try: skip an unsafe spawn

        int minX = region.min().getBlockX();
        int maxX = region.max().getBlockX();
        int minZ = region.min().getBlockZ();
        int maxZ = region.max().getBlockZ();
        int minY = world.getMinHeight();
        int maxY = world.getMaxHeight() - 1;
        int y = Math.max(minY, Math.min(region.max().getBlockY() + 5, maxY));
        int[][] offsets = {{-32, -32}, {-32, 32}, {32, -32}, {32, 32}};
        for (int[] offset : offsets) {
            int x = offset[0] < 0 ? minX + offset[0] : maxX + offset[0];
            int z = offset[1] < 0 ? minZ + offset[1] : maxZ + offset[1];
            Location candidate = new Location(world, x + 0.5, y + 0.5, z + 0.5);
            if (safeEvacuation(candidate, region, world)) return candidate;
        }
        return null;
    }

    private static boolean safeEvacuation(Location location, ArenaRegion region, World world) {
        if (location == null || location.getWorld() == null
                || !world.equals(location.getWorld())) return false;
        if (location.getBlockY() < world.getMinHeight()
                || location.getBlockY() >= world.getMaxHeight()) return false;
        if (!world.getWorldBorder().isInside(location)) return false;
        if (region != null) {
            int x = location.getBlockX();
            int z = location.getBlockZ();
            if (x >= region.min().getBlockX() && x <= region.max().getBlockX()
                    && z >= region.min().getBlockZ() && z <= region.max().getBlockZ()) return false;
        }
        return true;
    }

    private boolean startTieredRestore(String mapId, DiffLog.Decoded diff, boolean recovery) {
        if (m.restoreEngine == null) return false;
        ArenaRegion region = m.arenaStore.region(mapId);
        if (region == null) region = m.arenaStore.region();
        if (region == null) return false;
        ArenaSnapshot snapshot = loadSnapshotIfPresent(mapId);
        if (snapshot == null) {
            m.plugin.getLogger().warning("No arena snapshot for map '" + mapId
                    + "'; cannot run tiered restore" + (recovery ? " (recovery)" : "") + ".");
            return false;
        }
        DiffLog.Decoded safeDiff = diff == null
                ? new DiffLog.Decoded(mapId, nextRoundId(), true, Set.of(), null)
                : diff;
        if (!safeDiff.mapId().equals(mapId)) {
            safeDiff = new DiffLog.Decoded(mapId, safeDiff.roundId(), safeDiff.complete(),
                    safeDiff.positions(), safeDiff.problem());
        }
        m.restoreEngine.mapId(mapId);
        // Set STAGING before beginRestore(): an empty diff fires the tier-1 gate synchronously
        // from inside beginRestore, and the round must already be staged when it does.
        m.state = RoundState.STAGING;
        boolean started = m.restoreEngine.beginRestore(region, mapId, safeDiff, snapshot);
        if (started) {
            // Tier 2 is in flight whenever beginRestore succeeded, so RESTORING is always the live
            // state that disables Deploy in the hub. The tier-1 callback is status-only (issue #7):
            // a synchronous empty-diff gate consumes its one-shot without moving the round to
            // RUNNING, and joining players must hear about the verification and block writes.
            m.state = RoundState.RESTORING;
            startRestoreTask();
        }
        return started;
    }

    /** Called by the restore engine when tier 1 completes: status only, never opens deploy. */
    void onTierOneGateOpen() {
        if (!tierOneGateConsumed.tryOpen()) return;
        if (readiness.onTierOneDone() == RoundReadiness.Outcome.ANNOUNCE_PENDING) {
            m.announce(RoundReadiness.statusAnnouncement());
        }
    }

    /**
     * Shared round-start invariants used by full-restore completion, admin START and the IDLE
     * deploy fallback. Increments the round counter, resets the clock, opens the diff ledger,
     * prewarms the next map and moves the battleground into RUNNING.
     */
    boolean beginRound(String announcement) {
        if (isRunningLike()) return false;
        m.state = RoundState.RUNNING;
        m.roundNumber++;
        m.clock.reset();
        openDiffLedger(m.arenaStore.activeMapId(), m.roundNumber);
        m.arenaStore.prewarmNextMap();
        if (announcement != null && !announcement.isBlank()) m.announce(announcement);
        return true;
    }

    private void onRestoreComplete() {
        stopRestoreTask();
        reloadProtectionNoRescan(m.arenaStore.activeMapId());
        boolean failed = m.restoreEngine != null
                && RoundGates.restoreNotice(m.restoreEngine.restoreFailed())
                == RoundGates.RestoreNotice.FAILED;
        completeRestoreAndOpenRound(failed);
    }

    /**
     * Issue #7: the sole round transition on the restore path. Deploy opens only when the FULL
     * restore reaches its terminal state — clean or dirty — never at the tier-1 gate. A dirty
     * open warns instead of claiming ready; failure is not a strand.
     */
    void completeRestoreAndOpenRound(boolean failed) {
        RoundReadiness.Outcome outcome = readiness.onRestoreFinished(failed);
        if (outcome == RoundReadiness.Outcome.NONE) return;
        int roundNumber = m.roundNumber + 1; // beginRound() increments the counter
        if (!beginRound(RoundReadiness.announcement(roundNumber, failed))) return;
        if (outcome == RoundReadiness.Outcome.OPEN_CLEAN) {
            m.announce(m.economy.roundSummary());
        }
    }

    void reloadProtectionNoRescan(String mapId) {
        ArenaSnapshot snapshot = loadSnapshotIfPresent(mapId);
        ArenaRegion region = m.arenaStore.region(mapId);
        if (region == null) region = m.arenaStore.region();
        if (snapshot == null) {
            m.plugin.getLogger().warning("Arena snapshot unavailable for map '" + mapId
                    + "'; keeping previous protection provider (no full-world rescan).");
            return;
        }
        ArenaProtection.get().reload(region, snapshot);
    }

    ArenaSnapshot loadSnapshotIfPresent(String mapId) {
        try {
            if (m.snapshotStore == null || mapId == null || !m.snapshotStore.exists(mapId)) return null;
            return m.snapshotStore.load(mapId);
        } catch (Exception e) {
            m.plugin.getLogger().log(Level.WARNING, "Could not load arena snapshot for " + mapId, e);
            return null;
        }
    }

    private DiffLog.Decoded readDiffLog(String mapId) {
        AsyncDiffWriter writer = m.plugin.diffWriter();
        try {
            if (writer == null || mapId == null) return null;
            java.nio.file.Path path = writer.logPath(mapId);
            if (!java.nio.file.Files.isRegularFile(path)) return null;
            return AsyncDiffWriter.read(path);
        } catch (Exception e) {
            m.plugin.getLogger().log(Level.WARNING, "Could not read arena diff log for " + mapId, e);
            return null;
        }
    }

    void flushDiffLedger(String mapId) {
        AsyncDiffWriter writer = m.plugin.diffWriter();
        if (writer != null) {
            try {
                writer.flushAndClose();
            } catch (Exception e) {
                m.plugin.getLogger().log(Level.WARNING,
                        "Could not flush arena diff ledger for " + mapId, e);
            }
        }
    }

    private void openDiffLedger(String mapId, long roundId) {
        AsyncDiffWriter writer = m.plugin.diffWriter();
        if (writer != null && mapId != null && !mapId.isBlank()) writer.open(mapId, roundId);
    }

    /**
     * A capture flushes the active round's ledger even when the round stays live; without this
     * every later block change would be rejected as "round is already closed".
     */
    void reopenLedgerIfLive() {
        if (isRunningLike()) openDiffLedger(m.arenaStore.activeMapId(), m.roundNumber);
    }

    private long nextRoundId() {
        return ++m.roundIdCounter;
    }

    /** Domains claim their blocks; a capture/restore must never bake a live domain floor into the snapshot. */
    void stopDomainsAndSweep() {
        m.plugin.getSessionManager().stopAll();
        if (m.arenaStore.region() != null) EntitySweeper.sweep(m.arenaStore.region());
    }

    // ------------------------------------------------------------------ admin hooks

    /**
     * Authorization is enforced at the action site (not left to callers), because the SET TIME
     * prompt executes up to a minute after its permission-gated click.
     */
    AdminRoundGate.Result adminRoundAction(CommandSender actor, AdminRoundGate.Action action,
                                           int requestedSeconds) {
        if (actor == null) return AdminRoundGate.Result.deny("No actor for this action.");
        if (actor instanceof Player player && !player.hasPermission(Permissions.BG_ADMIN)) {
            return AdminRoundGate.Result.deny("You lack permission to control the round.");
        }
        var region = m.arenaStore.region();
        boolean captured = region != null && region.isCaptured();
        boolean restoring = m.restoreEngine != null && m.restoreEngine.isRestoring();
        AdminRoundGate.Result result = AdminRoundGate.decide(action, m.state, captured, restoring, requestedSeconds);
        if (!result.allowed()) return result;
        switch (result.effect()) {
            case START -> forceStart();
            case STOP -> forceStop();
            case END -> beginReset();
            case SET_TIME -> m.clock.setRemaining(result.seconds());
        }
        return result;
    }

    boolean forceStart() {
        var region = m.arenaStore.region();
        boolean captured = region != null && region.isCaptured();
        boolean restoring = m.restoreEngine != null && m.restoreEngine.isRestoring();
        if (!AdminRoundGate.mayForceStart(m.state, captured, restoring)) return false;
        return beginRound("<green>Round started by an admin.");
    }

    void forceStop() {
        // A stopped round is no longer live: commit and close its diff so late events are not
        // silently queued into a ledger the next beginRound() will clear anyway.
        flushDiffLedger(m.arenaStore.activeMapId());
        // A tier-2 restore can still be writing when the round is forced to IDLE. Stop both the
        // writes and their 20 Hz stepper so an IDLE deploy cannot start a round underneath them.
        if (m.restoreEngine != null) m.restoreEngine.cancelTierTwo();
        stopRestoreTask();
        for (UUID uuid : new ArrayList<>(m.deployed)) {
            Player player = Bukkit.getPlayer(uuid);
            if (player != null) m.deploys.leave(player);
        }
        for (UUID uuid : new ArrayList<>(stagedUuids())) {
            Player player = Bukkit.getPlayer(uuid);
            if (player != null) {
                // Hand back the hub kit before staging replays its own snapshot.
                m.deploys.restoreInventory(player);
                m.staging.exit(player);
            }
            else {
                BattlegroundLock.exit(uuid);
                m.staging.removeSnapshot(uuid);
            }
        }
        m.deployed.clear();
        m.dives.clear();
        m.combat.clearAll();
        m.clock.reset();
        m.announce("<yellow>Round stopped by an admin.");
        // Issue #38: STOP is a round boundary, not a way to park a dirty arena in IDLE. Going
        // through the reset consumes the committed diff and clears protection overrides; a later
        // START can no longer truncate an unrestored ledger. START during the restore is gated by
        // AdminRoundGate's isRestoring check, and completion opens a fresh round as usual.
        beginReset();
    }

    private Collection<UUID> stagedUuids() {
        return BattlegroundLock.staged();
    }

    boolean forceRestore() {
        if (m.restorer == null || m.arenaStore.region() == null) return false;
        beginReset();
        return (m.restoreEngine != null && m.restoreEngine.isRestoring())
                || m.state == RoundState.RESTORING || m.state == RoundState.STAGING;
    }

    /** M16: an unclean shutdown is healed at the next enable before anyone can deploy. */
    void restoreOnEnableIfNeeded() {
        if (!BattlegroundDefaults.ENABLED || m.restorer == null || m.arenaStore.region() == null) return;
        String mapId = m.arenaStore.activeMapId();
        DiffLog.Decoded diff = readDiffLog(mapId);
        boolean unflushed = diff != null && (!diff.complete() || diff.problem() != null);
        if (unflushed) {
            m.plugin.getLogger().warning("Unflushed arena diff detected for map '" + mapId
                    + "'; verifying against snapshot and restoring untrusted chunks.");
            tierOneGateConsumed.reset();
            readiness.reset();
            m.state = RoundState.STAGING;
            m.deployed.clear();
            m.dives.clear();
            // Recovery is a round boundary too: roll per-round state (streaks, combat, credit
            // counters) so a crash mid-round cannot carry them into the recovered round.
            m.economy.rollNewRound();
            m.clock.reset();
            stopDomainsAndSweep();
            if (startTieredRestore(mapId, diff, true)) {
                m.announce("<gray>Recovering arena after unclean shutdown… <white>Shop is open.");
                return;
            }
            m.plugin.getLogger().warning("Arena recovery could not start for map '" + mapId
                    + "'; starting a fresh round without auto-capture.");
            completeRestoreAndOpenRound(true);
            return;
        }
        beginReset();
    }

    // ------------------------------------------------------------------ config

    /** Synchronous reload, retained for callers that need the data applied before returning. */
    void reloadConfig() {
        ArenaRuntimeConfig.load(
                new File(new File(m.plugin.getDataFolder(), "battleground"), "config.yml"),
                m.plugin.getLogger());
        m.arenaStore.load();
        DomainRegistry.get().clearClaimsIfIdle();
    }

    /**
     * C5: config/updater file I/O runs on Paper's async scheduler so {@code /bg reload} never
     * blocks the command thread. Bukkit-side effects are marshalled back to the global region.
     *
     * @param onComplete receives {@code true} only when every file read succeeded.
     */
    void reloadConfigAsync(java.util.function.Consumer<Boolean> onComplete) {
        Bukkit.getAsyncScheduler().runNow(m.plugin, task -> {
            boolean success = true;
            try {
                new com.hizeric.shenaniganscore.core.config.ConfigAutoUpdater(m.plugin).run();
                m.plugin.reloadTuning();
                ArenaRuntimeConfig.load(
                        new File(new File(m.plugin.getDataFolder(), "battleground"), "config.yml"),
                        m.plugin.getLogger());
            } catch (RuntimeException e) {
                success = false;
                m.plugin.getLogger().log(Level.SEVERE, "Async config reload failed", e);
            }
            final boolean result = success;
            Bukkit.getGlobalRegionScheduler().execute(m.plugin, () -> {
                // ArenaStore.load() resolves Bukkit worlds and mutates unsynchronised registries;
                // it is main-thread work and must never stay on the async config thread.
                boolean ok = result;
                try {
                    // Refresh Bukkit's in-memory config.yml object and the GUI style, which
                    // are otherwise only initialised at enable (SP-LEAD-002 / AUDIT-13).
                    m.plugin.reloadConfig();
                    GuiStyle.apply(m.plugin.getDataFolder(), m.plugin.getLogger());
                } catch (RuntimeException e) {
                    ok = false;
                    m.plugin.getLogger().log(Level.SEVERE, "Bukkit config reload failed", e);
                }
                try {
                    m.arenaStore.load();
                } catch (RuntimeException e) {
                    ok = false;
                    m.plugin.getLogger().log(Level.SEVERE, "Arena data reload failed", e);
                }
                if (ok) DomainRegistry.get().clearClaimsIfIdle();
                if (onComplete != null) onComplete.accept(ok);
            });
        });
    }
}
