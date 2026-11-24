package com.hizeric.shenaniganscore.battleground.arena.restore;

import com.hizeric.shenaniganscore.battleground.RoundReadiness;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Issue #40: the boss bar / action bar must not announce "Deployment ready" at the tier-1 gate,
 * because the deploy gate only opens when the FULL restore finishes. The safe copy must come from
 * the same {@link RoundReadiness} source that drives the chat announcement.
 */
class RestoreReadyCopyWiringTest {

    private static final Path ANIMATOR = Path.of("src", "main", "java", "com", "hizeric",
            "shenaniganscore", "battleground", "arena", "restore", "RestoreAnimator.java");
    private static final Path READINESS = Path.of("src", "main", "java", "com", "hizeric",
            "shenaniganscore", "battleground", "RoundReadiness.java");

    private static String block(String source, String start, String end) {
        int from = source.indexOf(start);
        assertTrue(from >= 0, "missing block start: " + start);
        int to = source.indexOf(end, from + start.length());
        assertTrue(to > from, "missing block end after " + start + ": " + end);
        return source.substring(from, to);
    }

    @Test
    void authoritativeTierOneCopyDoesNotClaimReady() {
        String status = RoundReadiness.statusAnnouncement();
        assertFalse(status.toLowerCase().contains("ready"),
                "tier-1 copy must not claim deployment is ready: " + status);
    }

    @Test
    void tierOneAnimationUsesAuthoritativeCopy() throws IOException {
        assertTrue(Files.isRegularFile(ANIMATOR), "expected source at " + ANIMATOR.toAbsolutePath());
        String animator = Files.readString(ANIMATOR, StandardCharsets.UTF_8);
        String tierOne = block(animator, "public void onTierOneComplete(", "public void onProgress(");

        assertFalse(tierOne.contains("Deployment ready"),
                "the tier-1 animation must not announce deployment readiness");
        assertTrue(tierOne.contains("RoundReadiness.TIER_ONE_STATUS"),
                "tier-1 animation copy must be sourced from RoundReadiness");

        String readiness = Files.readString(READINESS, StandardCharsets.UTF_8);
        assertTrue(readiness.contains("TIER_ONE_STATUS"),
                "RoundReadiness must expose the shared tier-1 status copy");
    }
}
