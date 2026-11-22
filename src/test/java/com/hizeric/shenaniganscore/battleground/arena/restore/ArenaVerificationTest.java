package com.hizeric.shenaniganscore.battleground.arena.restore;

import com.hizeric.shenaniganscore.battleground.arena.restore.ArenaVerification.Source;
import com.hizeric.shenaniganscore.battleground.arena.snapshot.ArenaSnapshot;
import com.hizeric.shenaniganscore.battleground.arena.snapshot.ArenaSnapshot.ChunkPos;
import com.hizeric.shenaniganscore.core.util.BlockKey;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Pure verification decisions: no server, no Bukkit, no files. */
class ArenaVerificationTest {

    private static final ChunkPos CHUNK = new ChunkPos(0, 0);

    /** 2x2 arena, y=60..61; stone floor at y=60, air above. */
    private static ArenaSnapshot snapshot() {
        int[] cells = new int[ArenaSnapshot.cellCount(0, 60, 0, 1, 61, 1, 0, 0)];
        for (int z = 0; z <= 1; z++) {
            for (int x = 0; x <= 1; x++) {
                cells[(0 * 2 + z) * 2 + x] = 1;
            }
        }
        return new ArenaSnapshot("ffa", "world", 0, 60, 0, 1, 61, 1,
                7L, 4L, "TEST", List.of("minecraft:air", "minecraft:stone"),
                Map.of(CHUNK, cells));
    }

    @Test
    void byChunkCoversEverySnapshotPosition() {
        Map<ChunkPos, Set<Long>> positions = ArenaVerification.byChunk(snapshot());
        assertEquals(Set.of(CHUNK), positions.keySet());
        assertEquals(8, positions.get(CHUNK).size());
        assertTrue(positions.get(CHUNK).contains(BlockKey.pack(1, 61, 1)));
    }

    @Test
    void untrustedWhenWorldDiffersOutsideTheLedger() {
        long changed = BlockKey.pack(0, 60, 0);
        ArenaSnapshot snapshot = snapshot();
        ArenaVerification.StateProbe world = (x, y, z) ->
                x == 0 && y == 60 && z == 0 ? "minecraft:dirt" : snapshot.stateAt(x, y, z);

        assertEquals(Set.of(changed),
                ArenaVerification.unloggedChunk(snapshot, CHUNK, world, Set.of()));
        assertEquals(Set.of(),
                ArenaVerification.unloggedChunk(snapshot, CHUNK, world, Set.of(changed)),
                "a ledger entry makes the discrepancy trusted");
        assertEquals(Set.of(),
                ArenaVerification.unloggedChunk(snapshot, CHUNK, snapshot::stateAt, Set.of()),
                "a clean world has no discrepancies");
    }

    @Test
    void sourceForIsPerChunkAndHonoursBothToggles() {
        long changed = BlockKey.pack(0, 60, 0);
        ArenaSnapshot snapshot = snapshot();
        ArenaVerification.StateProbe world = (x, y, z) ->
                x == 0 && y == 60 && z == 0 ? "minecraft:dirt" : snapshot.stateAt(x, y, z);
        Map<ChunkPos, Set<Long>> unlogged = Map.of(CHUNK,
                ArenaVerification.unloggedChunk(snapshot, CHUNK, world, Set.of()));
        Set<ChunkPos> untrusted = ArenaVerification.untrustedChunks(unlogged);

        assertEquals(Set.of(CHUNK), untrusted);
        assertFalse(ArenaVerification.trusted(CHUNK, untrusted));
        assertTrue(ArenaVerification.trusted(new ChunkPos(1, 1), untrusted));

        assertEquals(Source.SNAPSHOT, ArenaVerification.sourceFor(CHUNK, true, true, untrusted));
        assertEquals(Source.DIFF, ArenaVerification.sourceFor(CHUNK, true, false, untrusted),
                "fallback=false makes verification diagnostic-only");
        assertEquals(Source.DIFF, ArenaVerification.sourceFor(CHUNK, false, true, untrusted),
                "verify=false trusts the ledger unconditionally");
        assertEquals(Source.DIFF, ArenaVerification.sourceFor(new ChunkPos(1, 1), true, true, untrusted));
    }
}
