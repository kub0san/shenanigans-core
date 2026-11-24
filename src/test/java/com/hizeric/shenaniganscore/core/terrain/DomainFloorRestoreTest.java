package com.hizeric.shenaniganscore.core.terrain;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The restore-completion invariant for {@link DomainFloor}.
 *
 * <p>The end-of-domain restore hands every floor block back to the world, but a
 * column that lived in a chunk which unloaded mid-domain has to be re-loaded
 * asynchronously before it can be restored. The historical bug: {@code close()}
 * cleared the restore plan (and released the block claims) as soon as the
 * synchronous slice finished, while those async chunk loads were still in
 * flight. The async callback then indexed the emptied plan arrays
 * ({@code ArrayIndexOutOfBoundsException}) and the blocks in those chunks were
 * never handed back — permanent, stranded domain floor left in the world.</p>
 *
 * <p>The fix is the invariant this test pins: the restore may finish only once
 * <em>every</em> column is handed back <em>and</em> no async chunk load is still
 * pending. The heavy chunk/block I/O itself needs a live {@code World} and is
 * exercised in-game; this is the pure decision that orders it.</p>
 */
class DomainFloorRestoreTest {

    @Test
    void finishingWaitsForPendingAsyncChunkLoads() {
        // Columns handed back, but two chunk loads are still in flight: this is
        // exactly the state the old code finished in — and must now refuse to.
        assertFalse(DomainFloor.readyToFinish(90, 100, 2),
                "the restore must not finish while async chunk loads are still pending");
    }

    @Test
    void finishingWaitsForUnrestoredColumns() {
        // No async loads outstanding, but ten columns are still not handed back.
        assertFalse(DomainFloor.readyToFinish(90, 100, 0),
                "the restore must not finish while any column is still unrestored");
    }

    @Test
    void finishingProceedsWhenEveryColumnIsRestoredAndNothingIsPending() {
        assertTrue(DomainFloor.readyToFinish(100, 100, 0),
                "the restore finishes only when all columns are back and no load is pending");
    }

    @Test
    void finishingIsSafeWhenTheFloorWasEmpty() {
        // A floor that never built a column must still be able to finish.
        assertTrue(DomainFloor.readyToFinish(0, 0, 0),
                "an empty floor has nothing to wait for and must finish immediately");
    }

    @Test
    void pendingLoadsAlwaysBlockFinishingEvenIfEveryColumnLooksRestored() {
        // Defensive: a stray pending load must still hold the finish, because its
        // callback will touch the plan arrays.
        assertFalse(DomainFloor.readyToFinish(100, 100, 1),
                "any pending async load blocks finishing, regardless of the restored count");
    }

    /** Issue #35: the emergency shutdown restore must persist unrestored columns before clearing the plan. */
    @Test
    void restoreNowPersistsPendingBlocksBeforeClearingThePlan() throws IOException {
        Path source = Path.of("src", "main", "java", "com", "hizeric", "shenaniganscore",
                "core", "terrain", "DomainFloor.java");
        assertTrue(Files.isRegularFile(source), "expected source at " + source.toAbsolutePath());
        String text = Files.readString(source, StandardCharsets.UTF_8);
        int restoreNow = text.indexOf("public void restoreNow()");
        int clearPlan = text.indexOf("clearPlan();", restoreNow);
        int persist = text.indexOf("persistPendingRestore();");
        assertTrue(restoreNow >= 0 && clearPlan > restoreNow,
                "restoreNow must reach clearPlan");
        assertTrue(persist > restoreNow && persist < clearPlan,
                "unrestored columns must be written to the ledger before clearPlan destroys the plan");
    }
}