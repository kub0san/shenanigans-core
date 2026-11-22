package com.hizeric.shenaniganscore.core.util;

/**
 * Stable packed {@code long} key for a block position.
 *
 * <p>Layout: 26 bits X (bits 38–63), 26 bits Z (bits 12–37), 12 bits Y biased by 2048 (bits 0–11).
 * The bias keeps negative world Y values in the unsigned low bits, so a key is always non-negative
 * for world-valid coordinates. This is the encoding shared by the domain overlay, vertical handler
 * and the client-side erase builders; keeping one copy prevents two systems from silently writing
 * incompatible keys when one of them is tuned.
 */
public final class BlockKey {

    private BlockKey() {}

    /** Pack a block coordinate into a collision-free set key for world-valid coordinates. */
    public static long pack(int x, int y, int z) {
        return (((long) x & 0x3FFFFFFL) << 38)
                | (((long) z & 0x3FFFFFFL) << 12)
                | ((y + 2048L) & 0xFFFL);
    }

    /** Signed 26-bit X, recovered without a second copy of the packing layout. */
    public static int unpackX(long key) {
        return (int) (((key >>> 38) << 38) >> 38);
    }

    /** Signed 26-bit Z. */
    public static int unpackZ(long key) {
        return (int) ((((key >>> 12) & 0x3FFFFFFL) << 38) >> 38);
    }

    /** Y after removing the 2048 bias. */
    public static int unpackY(long key) {
        return (int) ((key & 0xFFFL) - 2048L);
    }
}
