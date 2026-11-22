package com.hizeric.shenaniganscore.battleground.arena.diff;

import com.hizeric.shenaniganscore.core.util.BlockKey;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The async writer must merge every accepted batch and never strand a late one. */
class AsyncDiffWriterTest {

    @TempDir
    Path arenaRoot;

    private AsyncDiffWriter writer;

    @AfterEach
    void stopWriter() {
        if (writer != null) writer.shutdown();
    }

    @Test
    void flushMergesOverlappingBatchesCollapsesDuplicatesAndMarksComplete() throws Exception {
        writer = new AsyncDiffWriter(arenaRoot, Logger.getLogger("diff-test"));
        writer.open("ffa", 42L);

        long a = BlockKey.pack(1, 64, 1);
        long b = BlockKey.pack(2, 64, 2);
        long c = BlockKey.pack(3, 64, 3);
        writer.enqueue(Set.of(a, b));
        writer.enqueue(Set.of(b, c));
        writer.enqueue(Set.of(a, c));

        writer.flushAndClose();

        DiffLog.Decoded decoded = AsyncDiffWriter.read(writer.logPath("ffa"));
        assertTrue(decoded.complete(), String.valueOf(decoded.problem()));
        assertEquals("ffa", decoded.mapId());
        assertEquals(42L, decoded.roundId());
        assertEquals(Set.of(a, b, c), decoded.positions());
    }

    @Test
    void lateEnqueueDuringDrainIsRejectedNotStranded() throws Exception {
        writer = new AsyncDiffWriter(arenaRoot, Logger.getLogger("diff-test"));
        writer.open("ffa", 7L);

        long accepted = BlockKey.pack(10, 64, 10);
        long late = BlockKey.pack(11, 64, 11);
        writer.enqueue(Set.of(accepted));

        CountDownLatch inDrain = new CountDownLatch(1);
        CountDownLatch releaseDrain = new CountDownLatch(1);
        writer.beforeCommittedWrite = () -> {
            inDrain.countDown();
            try {
                releaseDrain.await(5L, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        };

        CompletableFuture<Void> flush = CompletableFuture.runAsync(writer::flushAndClose);
        assertTrue(inDrain.await(5L, TimeUnit.SECONDS), "writer never reached the drain hook");

        assertFalse(writer.tryEnqueue(Set.of(late)), "gate must reject a late enqueue");
        releaseDrain.countDown();
        flush.get(5L, TimeUnit.SECONDS);

        DiffLog.Decoded decoded = AsyncDiffWriter.read(writer.logPath("ffa"));
        assertTrue(decoded.complete(), String.valueOf(decoded.problem()));
        assertEquals(Set.of(accepted), decoded.positions());
        assertFalse(decoded.positions().contains(late));
    }
}
