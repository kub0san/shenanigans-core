package com.hizeric.shenaniganscore.battleground.player;

import com.hizeric.shenaniganscore.battleground.storage.Database;
import com.hizeric.shenaniganscore.battleground.storage.PlayerRecord;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Issue #41: {@code preload} used a non-atomic containsKey → load → put sequence across the async
 * pre-login hook and the main-thread {@code get()} fallback, so several threads could load the same
 * UUID and the last writer won. One repository read per UUID must single-flight.
 */
class PlayerStorePreloadSingleFlightTest {

    @Test
    void concurrentPreloadsPerformOneLoadAndShareTheRecord(@TempDir Path temp) throws Exception {
        Database db = Database.open(temp.resolve("database.db").toFile());
        UUID uuid = UUID.fromString("44444444-4444-4444-4444-444444444444");
        AtomicInteger loads = new AtomicInteger();
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);

        PlayerStore store = new PlayerStore(null, db, id -> {
            loads.incrementAndGet();
            entered.countDown();
            try {
                assertTrue(release.await(5, TimeUnit.SECONDS), "test latch must be released");
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            return Optional.of(new PlayerRecord(id, 42, 1, 0, 0, 0,
                    List.of(), List.of(), ""));
        });

        try {
            List<BattlegroundPlayer> observed = new ArrayList<>();
            List<Thread> threads = new ArrayList<>();
            for (int i = 0; i < 4; i++) {
                Thread thread = new Thread(() -> observed.add(store.get(uuid)));
                threads.add(thread);
                thread.start();
            }
            assertTrue(entered.await(5, TimeUnit.SECONDS), "a load must have started");
            release.countDown();
            for (Thread thread : threads) thread.join(5_000);

            assertEquals(1, loads.get(), "only one repository load may run for a UUID");
            assertEquals(4, observed.size());
            BattlegroundPlayer first = observed.get(0);
            for (BattlegroundPlayer player : observed) {
                assertSame(first, player, "every caller must observe the same loaded record");
            }
            assertEquals(42, first.credits());
        } finally {
            store.disable();
            db.close();
        }
    }
}
