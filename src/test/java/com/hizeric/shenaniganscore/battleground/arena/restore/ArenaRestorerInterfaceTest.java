package com.hizeric.shenaniganscore.battleground.arena.restore;

import com.hizeric.shenaniganscore.battleground.arena.ArenaRegion;
import com.hizeric.shenaniganscore.battleground.arena.ArenaRestorer;
import com.hizeric.shenaniganscore.battleground.arena.RestoreBudget;
import com.hizeric.shenaniganscore.battleground.arena.diff.DiffLog;
import com.hizeric.shenaniganscore.battleground.arena.snapshot.ArenaSnapshot;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Proof that the restorer interface remains source-compatible for the tiered engine. */
class ArenaRestorerInterfaceTest {

    @Test
    void newMethodsAreDefaultsOnArenaRestorer() throws Exception {
        Method progress = ArenaRestorer.class.getMethod("progressPercent");
        assertTrue(progress.isDefault());
        assertEquals(int.class, progress.getReturnType());

        Method begin = ArenaRestorer.class.getMethod("beginRestore", ArenaRegion.class,
                String.class, DiffLog.Decoded.class, ArenaSnapshot.class);
        assertTrue(begin.isDefault());
        assertEquals(boolean.class, begin.getReturnType());
    }

    @Test
    void legacyMethodsRemainAndTieredEngineImplementsTheInterface() throws Exception {
        ArenaRestorer.class.getMethod("id");
        ArenaRestorer.class.getMethod("prepare", ArenaRegion.class);
        ArenaRestorer.class.getMethod("capture", ArenaRegion.class);
        ArenaRestorer.class.getMethod("beginRestore", ArenaRegion.class);
        ArenaRestorer.class.getMethod("restoreStep", RestoreBudget.class);
        ArenaRestorer.class.getMethod("cancel");
        ArenaRestorer.class.getMethod("isRestoring");
        ArenaRestorer.class.getMethod("restoreFailed");

        assertTrue(ArenaRestorer.class.isAssignableFrom(ArenaRestoreEngine.class));
    }

    @Test
    void newDefaultBeginRestoreDelegatesToTheLegacyOverload() {
        LegacyRestorer legacy = new LegacyRestorer();
        DiffLog.Decoded diff = new DiffLog.Decoded("ffa", 1L, true, Set.of(), null);

        assertTrue(legacy.beginRestore(null, "ffa", diff, null));
        assertTrue(legacy.legacyBeginCalled);
        assertEquals(0, legacy.progressPercent(), "legacy restorers report no real progress");
    }

    /** A legacy-style implementation that only knows the legacy overload. */
    private static final class LegacyRestorer implements ArenaRestorer {
        private boolean legacyBeginCalled;

        @Override public String id() { return "legacy"; }
        @Override public boolean prepare(ArenaRegion region) { return true; }
        @Override public boolean capture(ArenaRegion region) { return true; }
        @Override public boolean beginRestore(ArenaRegion region) {
            legacyBeginCalled = true;
            return true;
        }
        @Override public boolean restoreStep(RestoreBudget budget) { return true; }
        @Override public void cancel() { }
        @Override public boolean isRestoring() { return false; }
    }
}
