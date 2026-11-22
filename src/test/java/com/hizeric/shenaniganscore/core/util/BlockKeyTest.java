package com.hizeric.shenaniganscore.core.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Locks the shared block-position key encoding used across domain and technique systems. */
class BlockKeyTest {

    @Test
    void matchesTheHistoricalEncoding() {
        long legacy = (((long) 31 & 0x3FFFFFFL) << 38)
                | (((long) -17 & 0x3FFFFFFL) << 12)
                | ((-64 + 2048L) & 0xFFFL);
        assertEquals(legacy, BlockKey.pack(31, -64, -17));
    }

    @Test
    void separatesEveryAxis() {
        long base = BlockKey.pack(0, 0, 0);
        assertNotEquals(base, BlockKey.pack(1, 0, 0));
        assertNotEquals(base, BlockKey.pack(0, 1, 0));
        assertNotEquals(base, BlockKey.pack(0, 0, 1));
    }

    @Test
    void negativeYStaysInTheUnsignedLowBits() {
        long key = BlockKey.pack(0, -2048, 0);
        assertTrue(key >= 0);
        assertEquals(0L, key);
    }
}
