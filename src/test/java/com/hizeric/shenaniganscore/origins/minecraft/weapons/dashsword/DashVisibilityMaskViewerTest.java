package com.hizeric.shenaniganscore.origins.minecraft.weapons.dashsword;

import org.bukkit.entity.Player;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The packet-mask viewer policy: hide equipment for third parties, never for the caster. */
class DashVisibilityMaskViewerTest {

    private static final Path EFFECT = Path.of("src", "main", "java", "com", "hizeric",
            "shenaniganscore", "origins", "minecraft", "weapons", "dashsword",
            "DashSwordEffect.java");

    private ServerMock server;

    @BeforeEach
    void mockBukkit() {
        server = MockBukkit.mock();
    }

    @AfterEach
    void unmockBukkit() {
        MockBukkit.unmock();
    }

    @Test
    void casterViewerIsNotMasked() {
        Player caster = server.addPlayer("Caster");
        assertFalse(DashVisibilityMask.shouldMaskViewer(
                caster.getUniqueId(), 7, 7, caster));
    }

    @Test
    void thirdPartyViewerIsMasked() {
        Player caster = server.addPlayer("Caster");
        Player other = server.addPlayer("Other");
        assertTrue(DashVisibilityMask.shouldMaskViewer(
                caster.getUniqueId(), 7, 7, other));
    }

    @Test
    void nullViewerNonPlayerViewerAndUnrelatedEntityAreNotMasked() {
        Player caster = server.addPlayer("Caster");
        Player other = server.addPlayer("Other");
        assertFalse(DashVisibilityMask.shouldMaskViewer(caster.getUniqueId(), 7, 7, null));
        assertFalse(DashVisibilityMask.shouldMaskViewer(caster.getUniqueId(), 7, 7, new Object()));
        assertFalse(DashVisibilityMask.shouldMaskViewer(caster.getUniqueId(), 7, 8, other));
    }

    @Test
    void viewerPolicyStillExemptsCaster() {
        Player caster = server.addPlayer("Caster");
        Player other = server.addPlayer("Other");
        // The caster's own UUID is exempt even when the packet targets their own entity id.
        assertFalse(DashVisibilityMask.shouldMaskViewer(
                caster.getUniqueId(), 7, 7, caster), "self must never be masked");
        // A null viewer is not a player and cannot be masked.
        assertFalse(DashVisibilityMask.shouldMaskViewer(caster.getUniqueId(), 7, 7, null),
                "null viewer must not be masked");
        // A non-player viewer object is not a Player and must not be masked.
        assertFalse(DashVisibilityMask.shouldMaskViewer(caster.getUniqueId(), 7, 7, new Object()),
                "non-player viewer must not be masked");
        // A packet for a different entity id than the masked one is never masked.
        assertFalse(DashVisibilityMask.shouldMaskViewer(caster.getUniqueId(), 7, 8, other),
                "a mismatched packet entity id must not be masked");
        // A third-party player viewing the caster's entity is masked.
        assertTrue(DashVisibilityMask.shouldMaskViewer(
                caster.getUniqueId(), 7, 7, other), "third party must be masked");
    }

    @Test
    void everyTimelineEndReachesFinish() throws IOException {
        assertTrue(Files.isRegularFile(EFFECT), "expected source at " + EFFECT.toAbsolutePath());
        String source = Files.readString(EFFECT, StandardCharsets.UTF_8);

        // Locate the Timeline.run() body: from "public void run() {" up to the next method
        // declaration ("private Vector pathOffset(") that follows it in the source.
        int runStart = source.indexOf("public void run() {");
        assertTrue(runStart >= 0, "Timeline.run() must exist");
        int runEnd = source.indexOf("private Vector pathOffset(", runStart);
        assertTrue(runEnd > runStart, "could not delimit Timeline.run() body");
        String run = source.substring(runStart, runEnd);

        // Source-pin: no bare `return;` between the mask-creation site and any finish() call.
        // Every early-end path must call finish() (which runs clear() -> visibility.restore())
        // before returning; a bare return would skip restore and strand the invisibility flag.
        int maskCreation = run.indexOf("new DashVisibilityMask(");
        assertTrue(maskCreation < 0,
                "mask is created in the Timeline constructor, not in run(); adjust the pin if that changes");
        int firstFinish = run.indexOf("finish();");
        assertTrue(firstFinish >= 0, "Timeline.run() must call finish() on its early-end paths");
        String beforeFirstFinish = run.substring(0, firstFinish);
        assertFalse(beforeFirstFinish.contains("return;"),
                "a bare return before the first finish() would bypass visibility.restore(): "
                        + beforeFirstFinish);
        // Every `return;` in run() must be immediately preceded by `finish();` (ignoring
        // whitespace). A `return;` without a preceding finish() is a bypass of restore().
        int bareReturns = 0;
        int idx = run.indexOf("return;");
        while (idx >= 0) {
            String before = run.substring(0, idx).stripTrailing();
            assertTrue(before.endsWith("finish();"),
                    "every return in Timeline.run() must be preceded by finish(); found a bare "
                            + "return at offset " + idx + ": " + run.substring(Math.max(0, idx - 40), idx + 8));
            bareReturns++;
            idx = run.indexOf("return;", idx + "return;".length());
        }
        assertTrue(bareReturns > 0, "Timeline.run() must have at least one guarded early-end path");
    }
}