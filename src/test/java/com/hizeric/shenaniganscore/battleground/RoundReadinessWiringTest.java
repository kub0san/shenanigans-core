package com.hizeric.shenaniganscore.battleground;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Issue #7 source wiring: "Round N is ready" and the deploy open must happen only when the FULL
 * restore completes, never at the tier-1 gate. These paths need a live BattlegroundManager/Bukkit
 * to execute, so the wiring is pinned on the source, following the ConfigReloadWiringTest
 * convention.
 */
class RoundReadinessWiringTest {

    private static final Path CONTROLLER = Path.of("src", "main", "java", "com", "hizeric",
            "shenaniganscore", "battleground", "RoundController.java");
    private static final Path MANAGER = Path.of("src", "main", "java", "com", "hizeric",
            "shenaniganscore", "battleground", "BattlegroundManager.java");
    private static final Path DEPLOY_SERVICE = Path.of("src", "main", "java", "com", "hizeric",
            "shenaniganscore", "battleground", "DeployService.java");

    private static String source(Path path) throws IOException {
        assertTrue(Files.isRegularFile(path), "expected source at " + path.toAbsolutePath());
        return Files.readString(path, StandardCharsets.UTF_8);
    }

    /** The text from {@code start} (inclusive) up to {@code end} (exclusive). */
    private static String block(String source, String start, String end) {
        int from = source.indexOf(start);
        assertTrue(from >= 0, "missing block start: " + start);
        int to = source.indexOf(end, from + start.length());
        assertTrue(to > from, "missing block end after " + start + ": " + end);
        return source.substring(from, to);
    }

    private static int count(String source, String needle) {
        int count = 0;
        for (int i = source.indexOf(needle); i >= 0; i = source.indexOf(needle, i + needle.length())) {
            count++;
        }
        return count;
    }

    @Test
    void tierOneCallbackNeverCallsBeginRound() throws IOException {
        String callback = block(source(CONTROLLER), "void onTierOneGateOpen()", "boolean beginRound(");
        assertTrue(callback.contains("tierOneGateConsumed.tryOpen()"),
                "the PR #2 one-shot guard must stay first in the tier-1 callback");
        assertTrue(callback.contains("readiness.onTierOneDone()"),
                "tier 1 only feeds the readiness decision");
        assertFalse(callback.contains("beginRound("),
                "tier 1 must not start the round: deploy opens only at full restore completion");
    }

    @Test
    void restoreCompletionOwnsTheRoundTransition() throws IOException {
        String controller = source(CONTROLLER);
        assertTrue(controller.contains("void completeRestoreAndOpenRound(boolean failed)"),
                "the full-restore completion gate must exist");
        String gate = block(controller, "void completeRestoreAndOpenRound(boolean failed)",
                "void reloadProtectionNoRescan(");
        assertTrue(gate.contains("beginRound("), "the completion gate owns the round transition");
        assertTrue(gate.contains("RoundReadiness.announcement("),
                "the ready/dirty copy comes from RoundReadiness");
        String completion = block(controller, "private void onRestoreComplete()",
                "void completeRestoreAndOpenRound(");
        assertTrue(completion.contains("completeRestoreAndOpenRound(failed)"),
                "restore completion routes through the single gate");
        assertFalse(completion.contains("beginRound("),
                "onRestoreComplete must not start a round directly");
    }

    @Test
    void earlyStopStillOpensARoundAfterAFailedRestore() throws IOException {
        String tick = block(source(CONTROLLER), "private void tickRestore()", "private void tickHud()");
        assertTrue(tick.contains("!m.restoreEngine.isRestoring()"),
                "the early stop watches the engine's terminal state");
        assertTrue(tick.contains("completeRestoreAndOpenRound(failed)"),
                "a restore stopped from a load callback must still open the round");
    }

    @Test
    void recoveryAndFallbackPathsUseTheSameGate() throws IOException {
        String controller = source(CONTROLLER);
        assertTrue(count(controller, "readiness.reset()") >= 2,
                "beginReset() and restoreOnEnableIfNeeded() must arm a fresh readiness generation");
        String reset = block(controller, "void beginReset()",
                "private static Location evacuationDestination(");
        assertTrue(reset.contains("completeRestoreAndOpenRound(dirty)"),
                "the no-restore fallback must carry the recorded diff's dirty state, never claim a clean ready");
        assertFalse(reset.contains("completeRestoreAndOpenRound(false)"),
                "a skip with recorded unrestored work must not announce a clean ready");
        String recovery = block(controller, "void restoreOnEnableIfNeeded()", "void reloadConfig()");
        assertTrue(recovery.contains("completeRestoreAndOpenRound(true)"),
                "an unflushed recovery that cannot start opens dirty by definition");
        assertFalse(recovery.contains("completeRestoreAndOpenRound(false)"),
                "an unflushed recovery must not announce a clean ready");
    }

    @Test
    void synchronousEmptyDiffStaysRestoringDuringTierTwo() throws IOException {
        String restore = block(source(CONTROLLER), "private boolean startTieredRestore(",
                "void onTierOneGateOpen()");
        assertTrue(restore.contains("m.state = RoundState.STAGING;")
                        && restore.indexOf("m.state = RoundState.STAGING;")
                        < restore.indexOf("boolean started = m.restoreEngine.beginRestore("),
                "STAGING must be live before beginRestore so a synchronous tier-1 gate sees a staged round");
        assertTrue(restore.contains("m.state = RoundState.RESTORING;"),
                "a successful beginRestore always leaves tier 2 in flight, even when tier 1 fired synchronously");
        assertFalse(restore.contains("!tierOneGateConsumed.consumed()"),
                "the tier-1 one-shot must not gate the RESTORING state: tier 1 is status-only now");
    }

    @Test
    void tierOneGateSafetyFixIsStillWired() throws IOException {
        assertTrue(source(CONTROLLER).contains("tierOneGateConsumed.tryOpen()"),
                "the PR #2 one-shot tier-1 gate stays authoritative");
        assertTrue(source(MANAGER).contains("this.rounds::onTierOneGateOpen"),
                "the engine must still call back into the controller's tier-1 gate");
    }

    @Test
    void joinFeedbackDelegatesToTheReadinessGate() throws IOException {
        String restoring = block(source(DEPLOY_SERVICE), "case RESTORING -> {",
                "case ALREADY_DEPLOYED ->");
        assertTrue(restoring.contains("RoundReadiness.waitingMessage(m.state)"),
                "join feedback must come from the readiness gate's single source of copy");
        assertFalse(restoring.contains("tier 1"),
                "DeployService must not keep its own tier-1 join copy");
        assertFalse(restoring.contains("still restoring"),
                "DeployService must not keep its own restoring join copy");
    }
}
