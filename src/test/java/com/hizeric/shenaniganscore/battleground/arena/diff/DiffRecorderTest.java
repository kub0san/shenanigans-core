package com.hizeric.shenaniganscore.battleground.arena.diff;

import com.hizeric.shenaniganscore.core.util.BlockKey;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The recorder is a cheap main-thread filter; every decision is injectable and server-free. */
class DiffRecorderTest {

    private static final DiffRecorder.Bounds ARENA = (x, y, z) ->
            x >= 0 && x <= 10 && y >= 60 && y <= 70 && z >= 0 && z <= 10;

    private static final class Capture implements Consumer<Set<Long>> {
        final Set<Long> seen = new HashSet<>();

        @Override
        public void accept(Set<Long> positions) {
            seen.addAll(positions);
        }
    }

    @Test
    void keepsValidPositionsAndEnqueuesThePackedKey() {
        Capture sink = new Capture();
        DiffRecorder recorder = new DiffRecorder(() -> ARENA, (x, y, z) -> false, sink);

        assertTrue(recorder.record(5, 64, 5));

        assertEquals(Set.of(BlockKey.pack(5, 64, 5)), sink.seen);
    }

    @Test
    void dropsPositionsOutsideTheInjectedRegion() {
        Capture sink = new Capture();
        DiffRecorder recorder = new DiffRecorder(() -> ARENA, (x, y, z) -> false, sink);

        assertFalse(recorder.record(11, 64, 5));
        assertFalse(recorder.record(5, 59, 5));
        assertFalse(recorder.record(5, 64, -1));
        assertTrue(sink.seen.isEmpty());
    }

    @Test
    void dropsDomainClaimedPositions() {
        Capture sink = new Capture();
        long claimed = BlockKey.pack(6, 65, 6);
        DiffRecorder recorder = new DiffRecorder(() -> ARENA,
                (x, y, z) -> BlockKey.pack(x, y, z) == claimed, sink);

        assertFalse(recorder.record(6, 65, 6), "domains restore their own terrain");
        assertTrue(recorder.record(5, 64, 5));
        assertEquals(Set.of(BlockKey.pack(5, 64, 5)), sink.seen);
    }

    @Test
    void aMissingActiveRegionDropsEverything() {
        Capture sink = new Capture();
        DiffRecorder recorder = new DiffRecorder(() -> null, (x, y, z) -> false, sink);

        assertFalse(recorder.record(5, 64, 5));
        assertTrue(sink.seen.isEmpty());
    }

    @Test
    void readsTheBoundsSupplierOnEveryEventSoTaskEightCanSwapRegions() {
        Capture sink = new Capture();
        AtomicReference<DiffRecorder.Bounds> active = new AtomicReference<>(ARENA);
        DiffRecorder recorder = new DiffRecorder(active::get, (x, y, z) -> false, sink);

        assertTrue(recorder.record(5, 64, 5), "starts inside the arena");
        active.set((x, y, z) -> x >= 100 && x <= 110 && y == 64 && z == 0);
        assertFalse(recorder.record(5, 64, 5), "same position is now outside the active region");
        assertTrue(recorder.record(105, 64, 0), "new region accepts its own positions");

        assertEquals(Set.of(BlockKey.pack(5, 64, 5), BlockKey.pack(105, 64, 0)), sink.seen);
    }

    @Test
    void claimCheckReceivesRawCoordinatesNotPackedKeys() {
        AtomicReference<String> last = new AtomicReference<>();
        Capture sink = new Capture();
        DiffRecorder recorder = new DiffRecorder(() -> ARENA, (x, y, z) -> {
            last.set(x + "," + y + "," + z);
            return false;
        }, sink);

        recorder.record(7, 64, 9);

        assertEquals("7,64,9", last.get(), "the filter must match DomainRegistry.ownerOf(x,y,z)");
    }
}
