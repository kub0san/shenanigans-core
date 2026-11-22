package com.hizeric.shenaniganscore.battleground.arena.diff;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Pure binary codec for one arena round's diff log.
 *
 * <p>Layout (all integers big-endian):</p>
 * <pre>
 *   int    magic        = {@link #MAGIC} ("SCD1")
 *   int    version      = {@link #VERSION}
 *   int    mapIdLength
 *   bytes  mapId        (UTF-8, mapIdLength bytes)
 *   long   roundId
 *   int    entryCount
 *   byte   completion   (0 = header written but round not flushed, 1 = committed)
 *   long[] entryCount packed {@link com.hizeric.shenaniganscore.core.util.BlockKey} positions
 * </pre>
 *
 * <p>The completion byte is part of the header. {@link com.hizeric.shenaniganscore.battleground.arena.diff.AsyncDiffWriter}
 * opens a round by writing a header with {@code completion = 0}; only a successful
 * {@code flushAndClose()} rewrites the file with {@code completion = 1}. A restart therefore
 * treats a zero flag, a missing/short body or any structural corruption as an unflushed round
 * and can fall back to the snapshot. Decoding never throws for bad bytes: it returns a
 * {@link Decoded} whose {@code complete()} is false and whose {@code problem()} explains why.</p>
 *
 * <p>No Bukkit, filesystem or server dependency — fully unit-testable.</p>
 */
public final class DiffLog {

    /** "SCD1" — Shenanigans Core Diff v1. */
    public static final int MAGIC = 0x53434431;
    public static final int VERSION = 1;

    private static final byte INCOMPLETE = 0;
    private static final byte COMPLETE = 1;
    /** magic + version + id length + round id + entry count + completion byte. */
    private static final int FIXED_HEADER_BYTES = 4 + 4 + 4 + Long.BYTES + Integer.BYTES + 1;

    private DiffLog() {}

    /**
     * Decoded header and payload. {@code complete} is true only for a fully flushed round;
     * {@code problem} is null in that case and a short reason otherwise.
     */
    public record Decoded(String mapId, long roundId, boolean complete, Set<Long> positions,
                          String problem) {
        public Decoded {
            positions = Set.copyOf(positions);
        }

        /** Alias used by the restart path in Task 8. */
        public boolean trusted() {
            return complete;
        }
    }

    /** Encodes a committed (fully flushed) round. */
    public static byte[] encode(String mapId, long roundId, Set<Long> positions) {
        return encode(mapId, roundId, positions, true);
    }

    /** Encodes a round, allowing the writer to emit the uncommitted opening header. */
    public static byte[] encode(String mapId, long roundId, Set<Long> positions, boolean complete) {
        if (mapId == null || mapId.isBlank()) {
            throw new IllegalArgumentException("mapId is required");
        }
        if (positions == null) {
            throw new IllegalArgumentException("positions is required");
        }
        byte[] id = mapId.getBytes(StandardCharsets.UTF_8);
        // Sorted so two runs over the same Set produce byte-identical logs (dedupe is inherent).
        long[] sorted = positions.stream().mapToLong(Long::longValue).sorted().toArray();
        ByteArrayOutputStream bytes = new ByteArrayOutputStream(
                FIXED_HEADER_BYTES + id.length + sorted.length * Long.BYTES);
        try (DataOutputStream out = new DataOutputStream(bytes)) {
            out.writeInt(MAGIC);
            out.writeInt(VERSION);
            out.writeInt(id.length);
            out.write(id);
            out.writeLong(roundId);
            out.writeInt(sorted.length);
            out.writeByte(complete ? COMPLETE : INCOMPLETE);
            for (long packed : sorted) {
                out.writeLong(packed);
            }
        } catch (IOException e) {
            // ByteArrayOutputStream never throws; keep the checked exception honest anyway.
            throw new IllegalStateException("Could not encode arena diff log", e);
        }
        return bytes.toByteArray();
    }

    /**
     * Decodes a log, reporting an unflushed round instead of throwing on truncated or corrupt
     * input. Header fields survive a truncated body so the restart path can still identify the
     * abandoned round.
     */
    public static Decoded decode(byte[] bytes) {
        if (bytes == null || bytes.length < FIXED_HEADER_BYTES) {
            return untrusted(null, 0L, Set.of(), "truncated header");
        }
        try {
            ByteBuffer in = ByteBuffer.wrap(bytes);
            if (in.getInt() != MAGIC) {
                return untrusted(null, 0L, Set.of(), "bad magic");
            }
            if (in.getInt() != VERSION) {
                return untrusted(null, 0L, Set.of(), "unsupported version");
            }
            int idLength = in.getInt();
            if (idLength < 0 || idLength > in.remaining()) {
                return untrusted(null, 0L, Set.of(), "bad map id length");
            }
            byte[] id = new byte[idLength];
            in.get(id);
            String mapId = new String(id, StandardCharsets.UTF_8);
            long roundId = in.getLong();
            int entryCount = in.getInt();
            if (entryCount < 0) {
                return untrusted(mapId, roundId, Set.of(), "bad entry count");
            }
            if (in.remaining() < 1) {
                return untrusted(mapId, roundId, Set.of(), "missing completion marker");
            }
            byte completion = in.get();
            if (in.remaining() != (long) entryCount * Long.BYTES) {
                return untrusted(mapId, roundId, Set.of(), "truncated body");
            }
            Set<Long> positions = new LinkedHashSet<>();
            for (int i = 0; i < entryCount; i++) {
                positions.add(in.getLong());
            }
            if (completion != COMPLETE) {
                return untrusted(mapId, roundId, positions, "unflushed round");
            }
            return new Decoded(mapId, roundId, true, positions, null);
        } catch (RuntimeException e) {
            return untrusted(null, 0L, Set.of(), "malformed: " + e.getClass().getSimpleName());
        }
    }

    private static Decoded untrusted(String mapId, long roundId, Set<Long> positions, String problem) {
        return new Decoded(mapId, roundId, false, positions, problem);
    }
}
