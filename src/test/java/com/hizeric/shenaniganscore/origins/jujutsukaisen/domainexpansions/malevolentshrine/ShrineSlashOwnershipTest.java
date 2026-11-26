package com.hizeric.shenaniganscore.origins.jujutsukaisen.domainexpansions.malevolentshrine;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Issue #78: shrine slash displays used to be fire-and-forget. A forced close
 * mid-choreography could strand display entities and delayed RAVAGE stages in
 * a restoring arena. Ownership, liveness and disposal are pure decisions here;
 * the live display plumbing needs a server and is source-pinned.
 */
class ShrineSlashOwnershipTest {

    /** Minimal stand-in for a display entity: validity plus an idempotent remove. */
    private static final class FakeDisplay {
        private boolean valid = true;
        private int removals;

        boolean isValid() { return valid; }
        void remove() { removals++; valid = false; }
    }

    @Test
    void registryOwnsOnlyLiveDisplaysAndDisposesThemOnce() {
        SlashDisplayRegistry registry = new SlashDisplayRegistry();
        FakeDisplay alive = new FakeDisplay();
        FakeDisplay alreadyGone = new FakeDisplay();
        alreadyGone.remove();

        registry.own(alive, FakeDisplay::isValid, FakeDisplay::remove);
        registry.own(alreadyGone, FakeDisplay::isValid, FakeDisplay::remove);
        assertEquals(1, registry.ownedCount(), "invalid displays must not be retained");

        registry.dispose();
        assertEquals(1, alive.removals, "a forced close must remove the owned display exactly once");
        assertEquals(0, registry.ownedCount());
        registry.dispose();
        assertEquals(1, alive.removals, "disposal must be idempotent");
    }

    @Test
    void selfRemovedDisplaysDropOutOfTheRegistry() {
        SlashDisplayRegistry registry = new SlashDisplayRegistry();
        FakeDisplay first = new FakeDisplay();
        FakeDisplay second = new FakeDisplay();
        registry.own(first, FakeDisplay::isValid, FakeDisplay::remove);
        first.remove();

        registry.own(second, FakeDisplay::isValid, FakeDisplay::remove);
        assertEquals(1, registry.ownedCount(), "the next registration must prune stale handles");

        registry.dispose();
        assertEquals(1, first.removals, "the self-removed display is not removed twice");
        assertEquals(1, second.removals);
    }

    @Test
    void disposeSkipsDisplaysThatSelfRemovedAfterRegistration() {
        SlashDisplayRegistry registry = new SlashDisplayRegistry();
        FakeDisplay ghost = new FakeDisplay();
        registry.own(ghost, FakeDisplay::isValid, FakeDisplay::remove);

        // The slash's own cleanup ran first; a later forced close must not remove it again.
        ghost.remove();
        registry.dispose();

        assertEquals(1, ghost.removals, "dispose must re-check validity before invoking the remover");
        assertEquals(0, registry.ownedCount());
    }

    @Test
    void ravageRunnableStopsWhenTheSessionIsNotActive() {
        assertTrue(ShrineSessionDecision.shouldKeepRavageRunnable(true, -5),
                "a pending per-victim stagger must still be allowed to wait");
        assertTrue(ShrineSessionDecision.shouldKeepRavageRunnable(true, 899));
        assertFalse(ShrineSessionDecision.shouldKeepRavageRunnable(true, 900),
                "the choreography's own window still ends at 900 ms");
        assertFalse(ShrineSessionDecision.shouldKeepRavageRunnable(false, 100),
                "a closed session must not spawn another RAVAGE stage");
        assertFalse(ShrineSessionDecision.shouldKeepRavageRunnable(false, 1200));
    }

    @Test
    void wiringOwnsDisposesAndGuardsSlashDisplays() throws IOException {
        String session = source("Session.java", "malevolentshrine");
        assertTrue(session.contains("public final SlashDisplayRegistry slashDisplays = new SlashDisplayRegistry();"),
                "ownership belongs to the shrine session, not a global map");
        assertTrue(session.contains("slashDisplays.dispose();"),
                "forced close must dispose every owned slash display");

        String cleave = source("Cleave.java", "malevolentshrine");
        assertTrue(cleave.contains("Slashes.ravage(session, session.slashDisplays,"),
                "RAVAGE must be linked to its owning session and registry");

        String slashes = source("Slashes.java", "malevolentshrine");
        assertTrue(slashes.contains("shouldKeepRavageRunnable(session == null || session.isActive(), elapsed)"),
                "the delayed runnable must check session liveness before every stage");
        assertTrue(slashes.contains("own(owner, core)") && slashes.contains("own(owner, glow)"),
                "slash blades must register both display halves with the owner");
    }

    private static String source(String name, String packageDir) throws IOException {
        Path path = Path.of("src", "main", "java", "com", "hizeric", "shenaniganscore",
                "origins", "jujutsukaisen", "domainexpansions", packageDir, name);
        return Files.readString(path, StandardCharsets.UTF_8);
    }
}
