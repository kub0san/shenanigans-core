package com.hizeric.shenaniganscore.battleground.arena.snapshot;

import com.hizeric.shenaniganscore.battleground.arena.snapshot.ArenaSnapshot.ChunkPos;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Per-map path, atomic write and the no-snapshot warning. */
class ArenaSnapshotStoreTest {

    @TempDir
    Path temp;

    private ArenaSnapshotStore store(Logger logger) {
        return new ArenaSnapshotStore(temp.resolve("battleground/arena"), logger);
    }

    private static ArenaSnapshot sample() {
        return new ArenaSnapshot("ffa", "world", 0, 0, 0, 0, 0, 0,
                10L, 20L, "TEST", List.of("minecraft:stone"),
                Map.of(new ChunkPos(0, 0), new int[]{0}));
    }

    @Test
    void snapshotPathIsPerMapAndRejectsTraversal() {
        ArenaSnapshotStore store = store(Logger.getLogger("snapshot-path-test"));
        String path = store.snapshotPath("ffa").toString().replace('\\', '/');
        assertTrue(path.endsWith("/arena/ffa/snapshot.bin"), path);
        assertThrows(IllegalArgumentException.class, () -> store.snapshotPath("../escape"));
    }

    @Test
    void saveWritesAtomicallyAndLoadRoundTrips() throws Exception {
        ArenaSnapshotStore store = store(Logger.getLogger("snapshot-save-test"));
        ArenaSnapshot original = sample();

        store.save("ffa", original);

        Path file = store.snapshotPath("ffa");
        assertTrue(Files.exists(file));
        assertFalse(Files.exists(file.resolveSibling("snapshot.bin.tmp")), "temp file must be moved away");
        ArenaSnapshot loaded = store.load("ffa");
        assertEquals("ffa", loaded.mapId());
        assertEquals("world", loaded.worldId());
        assertArrayEquals(original.cells(new ChunkPos(0, 0)), loaded.cells(new ChunkPos(0, 0)));
        assertEquals("minecraft:stone", loaded.stateAt(0, 0, 0));
    }

    @Test
    void missingSnapshotWarnsAndReturnsNullWithoutCapturing() throws Exception {
        List<LogRecord> records = new java.util.ArrayList<>();
        Logger logger = Logger.getLogger("snapshot-missing-test");
        Handler collector = new Handler() {
            @Override public void publish(LogRecord record) { records.add(record); }
            @Override public void flush() { }
            @Override public void close() { }
        };
        logger.addHandler(collector);
        try {
            ArenaSnapshotStore store = store(logger);
            assertNull(store.load("ffa"));
            assertFalse(Files.exists(store.snapshotPath("ffa")));
            assertTrue(records.stream().anyMatch(r -> r.getLevel().intValue() >= Level.WARNING.intValue()),
                    "a missing snapshot must log a warning instead of auto-capturing");
        } finally {
            logger.removeHandler(collector);
        }
    }
}
