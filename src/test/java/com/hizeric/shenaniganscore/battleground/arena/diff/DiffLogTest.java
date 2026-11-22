package com.hizeric.shenaniganscore.battleground.arena.diff;

import com.hizeric.shenaniganscore.core.util.BlockKey;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Pure codec: a header (map + round + commit flag) followed by packed positions. */
class DiffLogTest {

    @Test
    void roundTripsMapRoundAndPositions() {
        Set<Long> positions = new LinkedHashSet<>();
        positions.add(BlockKey.pack(100, 70, -50));
        positions.add(BlockKey.pack(101, 71, -49));
        positions.add(BlockKey.pack(-3, -5, 9));

        DiffLog.Decoded decoded = DiffLog.decode(DiffLog.encode("ffa", 42L, positions));

        assertTrue(decoded.complete(), String.valueOf(decoded.problem()));
        assertNull(decoded.problem());
        assertEquals("ffa", decoded.mapId());
        assertEquals(42L, decoded.roundId());
        assertEquals(positions, decoded.positions());
    }

    @Test
    void duplicatePositionsCollapseBecauseThePayloadIsASet() {
        long packed = BlockKey.pack(5, 64, 7);
        // List.of allows repeats; the codec accepts a Set, so build one with duplicates removed.
        Set<Long> withDuplicates = new HashSet<>(List.of(packed, packed, packed));

        byte[] bytes = DiffLog.encode("ffa", 3L, withDuplicates);
        DiffLog.Decoded decoded = DiffLog.decode(bytes);

        assertTrue(decoded.complete());
        assertEquals(1, decoded.positions().size());
        assertEquals(Set.of(packed), decoded.positions());
    }

    @Test
    void emptyRoundRoundTrips() {
        DiffLog.Decoded decoded = DiffLog.decode(DiffLog.encode("ffa", 0L, Set.of()));

        assertTrue(decoded.complete());
        assertEquals(Set.of(), decoded.positions());
    }

    @Test
    void incompleteHeaderIsDetectedAsUnflushedButStillIdentifiesTheRound() {
        Set<Long> positions = Set.of(BlockKey.pack(1, 2, 3));

        byte[] bytes = DiffLog.encode("ffa", 7L, positions, false);
        DiffLog.Decoded decoded = DiffLog.decode(bytes);

        assertFalse(decoded.complete(), "a header written before the flush must not be trusted");
        assertNotNull(decoded.problem());
        assertEquals("ffa", decoded.mapId());
        assertEquals(7L, decoded.roundId());
    }

    @Test
    void truncatedBodyIsDetectedAsUnflushed() {
        byte[] bytes = DiffLog.encode("ffa", 7L, Set.of(BlockKey.pack(1, 2, 3)), true);
        byte[] truncated = Arrays.copyOf(bytes, bytes.length - 4);

        DiffLog.Decoded decoded = DiffLog.decode(truncated);

        assertFalse(decoded.complete(), "a partial flush must be detected on restart");
        assertNotNull(decoded.problem());
        assertEquals("ffa", decoded.mapId());
        assertEquals(7L, decoded.roundId());
    }

    @Test
    void truncatedHeaderIsDetectedAsUnflushed() {
        DiffLog.Decoded decoded = DiffLog.decode(new byte[]{1, 2, 3});

        assertFalse(decoded.complete());
        assertNotNull(decoded.problem());
    }

    @Test
    void corruptMagicIsDetectedAsUnflushed() {
        byte[] bytes = DiffLog.encode("ffa", 1L, Set.of());
        byte[] corrupt = bytes.clone();
        corrupt[0] ^= 0x7F;

        assertFalse(DiffLog.decode(corrupt).complete());
    }

    @Test
    void nonAsciiMapIdRoundTripsAsUtf8() {
        DiffLog.Decoded decoded = DiffLog.decode(DiffLog.encode("arène-日本", 9L, Set.of()));

        assertTrue(decoded.complete());
        assertEquals("arène-日本", decoded.mapId());
    }
}
