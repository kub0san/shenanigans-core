package com.hizeric.shenaniganscore.battleground;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Issue #38: an admin STOP committed the diff but left the arena dirty in IDLE. The next START
 * reopened the ledger and truncated the committed diff, so stale blocks were never restored and
 * protection overrides survived. A stop is a round boundary and must go through the same reset /
 * restore path as a natural round end. Needs a live manager, so the contract is source-pinned like
 * {@code RoundReadinessWiringTest}.
 */
class StopStartBoundaryWiringTest {

    private static final Path CONTROLLER = Path.of("src", "main", "java", "com", "hizeric",
            "shenaniganscore", "battleground", "RoundController.java");

    private static String block(String source, String start, String end) {
        int from = source.indexOf(start);
        assertTrue(from >= 0, "missing block start: " + start);
        int to = source.indexOf(end, from + start.length());
        assertTrue(to > from, "missing block end after " + start + ": " + end);
        return source.substring(from, to);
    }

    @Test
    void stopCommitsTheDiffAndRunsTheRestoreBoundary() throws IOException {
        assertTrue(Files.isRegularFile(CONTROLLER), "expected source at " + CONTROLLER.toAbsolutePath());
        String source = Files.readString(CONTROLLER, StandardCharsets.UTF_8);
        String stop = block(source, "void forceStop()", "private Collection<UUID> stagedUuids()");

        assertTrue(stop.contains("flushDiffLedger("),
                "STOP must commit the live diff before leaving the round");
        assertTrue(stop.contains("beginReset()"),
                "STOP must consume the committed diff through the reset/restore boundary");
        assertFalse(stop.contains("m.state = RoundState.IDLE;"),
                "STOP must not strand the arena in IDLE with a committed but unrestored diff");

        int flush = stop.indexOf("flushDiffLedger(");
        int reset = stop.indexOf("beginReset()");
        assertTrue(reset > flush, "the diff must be committed before the restore reads it");
    }

    @Test
    void restoreCompletionStillClearsProtectionOverrides() throws IOException {
        String source = Files.readString(CONTROLLER, StandardCharsets.UTF_8);
        String completion = block(source, "private void onRestoreComplete()",
                "void completeRestoreAndOpenRound(");
        assertTrue(completion.contains("reloadProtectionNoRescan("),
                "the restore boundary must clear in-memory protection overrides");
    }
}
