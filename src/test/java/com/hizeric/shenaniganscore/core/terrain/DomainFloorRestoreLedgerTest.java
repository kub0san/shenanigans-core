package com.hizeric.shenaniganscore.core.terrain;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Issue #35: a domain floor whose chunk unloads before the shutdown restore cannot hand those
 * blocks back. They must be persisted before the plan is cleared and replayed at the next enable.
 */
class DomainFloorRestoreLedgerTest {

    private static final UUID WORLD = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID SESSION = UUID.fromString("22222222-2222-2222-2222-222222222222");

    private static DomainFloorRestoreLedger.Block block(int x, int y, int z, String data) {
        return new DomainFloorRestoreLedger.Block(x, y, z, data);
    }

    @Test
    void appendMergeAndReadRoundTrip(@TempDir Path temp) {
        DomainFloorRestoreLedger ledger = new DomainFloorRestoreLedger(temp.toFile());
        assertTrue(ledger.isEmpty());

        ledger.appendMerge(SESSION, WORLD, List.of(
                block(1, 60, 2, "minecraft:stone"), block(3, 61, 4, "minecraft:air")));
        ledger.appendMerge(SESSION, WORLD, List.of(
                block(3, 61, 4, "minecraft:air"), block(5, 62, 6, "minecraft:dirt")));

        List<DomainFloorRestoreLedger.Entry> entries = ledger.readAll();
        assertEquals(1, entries.size());
        DomainFloorRestoreLedger.Entry entry = entries.get(0);
        assertEquals(SESSION, entry.sessionId());
        assertEquals(WORLD, entry.worldId());
        assertEquals(3, entry.blocks().size(), "overlapping positions must be merged, not duplicated");
        assertTrue(entry.blocks().contains(block(5, 62, 6, "minecraft:dirt")));
        assertFalse(ledger.isEmpty());
    }

    @Test
    void theSameSessionInDifferentWorldsStaysSeparate(@TempDir Path temp) {
        UUID otherWorld = UUID.fromString("33333333-3333-3333-3333-333333333333");
        DomainFloorRestoreLedger ledger = new DomainFloorRestoreLedger(temp.toFile());
        ledger.appendMerge(SESSION, WORLD, List.of(block(1, 60, 2, "minecraft:stone")));
        ledger.appendMerge(SESSION, otherWorld, List.of(block(9, 60, 9, "minecraft:dirt")));

        assertEquals(2, ledger.readAll().size(),
                "one caster's pending floors in two worlds must never share coordinates");
    }

    @Test
    void removeDropsTheEntryAndDeletesTheFileWhenEmpty(@TempDir Path temp) {
        DomainFloorRestoreLedger ledger = new DomainFloorRestoreLedger(temp.toFile());
        ledger.appendMerge(SESSION, WORLD, List.of(block(1, 60, 2, "minecraft:stone")));
        File file = new File(new File(temp.toFile(), "domain"), "floor-restore.dat");
        assertTrue(file.isFile());

        ledger.remove(SESSION, WORLD);
        assertTrue(ledger.isEmpty());
        assertFalse(file.exists(), "a drained ledger must not leave a stale file behind");
    }

    @Test
    void aCorruptFileNeverBreaksEnable(@TempDir Path temp) throws Exception {
        File folder = new File(temp.toFile(), "domain");
        assertTrue(folder.mkdirs());
        Files.writeString(new File(folder, "floor-restore.dat").toPath(),
                "not a ledger", StandardCharsets.UTF_8);

        assertTrue(new DomainFloorRestoreLedger(temp.toFile()).readAll().isEmpty());
    }

    @Test
    void collectPendingOnlyIncludesUnrestoredColumnsWithOriginals() {
        // Two columns: column 0 is restored, column 1 still has a block to hand back.
        int[] columnX = {1, 5};
        int[] columnZ = {2, 6};
        int[] changeStart = {0, 2, 3};
        int[] changeY = {60, 61, 62};
        String[] original = {"minecraft:stone", null, "minecraft:dirt"};
        boolean[] restored = {true, false};

        List<DomainFloorRestoreLedger.Block> pending = DomainFloorRestoreLedger.collectPending(
                columnX, columnZ, changeStart, changeY, original, restored, 2);

        assertEquals(1, pending.size());
        assertEquals(block(5, 62, 6, "minecraft:dirt"), pending.get(0));
    }

    @Test
    void collectPendingHandlesAnEmptyPlan() {
        assertTrue(DomainFloorRestoreLedger.collectPending(
                new int[0], new int[0], new int[1], new int[0], new String[0],
                new boolean[0], 0).isEmpty());
    }
}
