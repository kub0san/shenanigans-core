package com.hizeric.shenaniganscore.battleground.arena;

import com.hizeric.shenaniganscore.battleground.arena.diff.DiffLog;
import com.hizeric.shenaniganscore.battleground.arena.snapshot.ArenaSnapshot;

/**
 * Snapshot/restore engine contract. Implementations restore the arena from captured snapshot
 * data; the tiered engine also consumes a durable diff for incremental recovery.
 */
public interface ArenaRestorer {

    String id();

    /** Loads any on-disk snapshot needed for a restore. */
    boolean prepare(ArenaRegion region);

    /** Captures the current arena into a snapshot. Used by {@code /bg setup}. */
    boolean capture(ArenaRegion region);

    /** Starts a budgeted restore. */
    boolean beginRestore(ArenaRegion region);

    /** Processes one tick's worth of work. @return true when the restore is complete. */
    boolean restoreStep(RestoreBudget budget);

    void cancel();

    boolean isRestoring();

    /** @return true when the last restore failed or was only partially applied. */
    default boolean restoreFailed() { return false; }

    /** @return 0..100 real progress from written positions/chunks; legacy engines report 0. */
    default int progressPercent() { return 0; }

    /**
     * Starts a restore with the durable diff and baseline snapshot. The default keeps legacy
     * restorers source-compatible; the tiered engine overrides it.
     */
    default boolean beginRestore(ArenaRegion region, String mapId,
                                 DiffLog.Decoded diff, ArenaSnapshot snapshot) {
        return beginRestore(region);
    }
}
