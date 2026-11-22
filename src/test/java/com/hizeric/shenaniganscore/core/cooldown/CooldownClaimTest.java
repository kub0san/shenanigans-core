package com.hizeric.shenaniganscore.core.cooldown;

import org.junit.jupiter.api.Test;

import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Regression coverage for {@link CooldownManager#tryClaim(UUID, String, int)} (F-22).
 *
 * <p>The old cast path did a non-atomic {@code isOnCooldown(...)} probe followed by
 * {@code setCooldown(...)}. Two threads could both observe "free" and double-fire the
 * same ability. {@code tryClaim} folds the check and the write into one atomic
 * {@link java.util.concurrent.ConcurrentHashMap#compute} per key, so exactly one
 * concurrent caller wins. These tests are pure Java and need no Bukkit runtime.</p>
 */
class CooldownClaimTest {

    @Test
    void firstClaimSucceedsAndSecondIsRejected() {
        CooldownManager manager = new CooldownManager();
        UUID player = UUID.randomUUID();

        assertTrue(manager.tryClaim(player, "blue", 30));
        assertTrue(manager.isOnCooldown(player, "blue"));
        assertFalse(manager.tryClaim(player, "blue", 30),
                "a second claim while the first is cooling must be rejected");
    }

    @Test
    void keysAreIndependent() {
        CooldownManager manager = new CooldownManager();
        UUID player = UUID.randomUUID();

        assertTrue(manager.tryClaim(player, "blue", 30));
        assertTrue(manager.tryClaim(player, "red", 30),
                "claiming Blue must not lock a different ability (keyed cooldowns)");
    }

    @Test
    void nonPositiveDurationIsAFreeClaim() {
        CooldownManager manager = new CooldownManager();
        UUID player = UUID.randomUUID();

        assertTrue(manager.tryClaim(player, "dash", 0),
                "no cooldown configured means the claim always succeeds");
        assertFalse(manager.isOnCooldown(player, "dash"),
                "a zero-duration claim must not write cooldown state");
    }

    @Test
    void concurrentClaimsHaveExactlyOneWinner() throws InterruptedException {
        CooldownManager manager = new CooldownManager();
        UUID player = UUID.randomUUID();
        int threads = 16;
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(threads);
        AtomicInteger winners = new AtomicInteger();

        for (int i = 0; i < threads; i++) {
            Thread t = new Thread(() -> {
                try {
                    start.await();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return;
                }
                if (manager.tryClaim(player, "fuga", 30)) winners.incrementAndGet();
                done.countDown();
            });
            t.start();
        }
        start.countDown();
        done.await();

        assertEquals(1, winners.get(),
                "exactly one of N simultaneous casts may claim the same cooldown (F-22)");
    }

    @Test
    void nullInputsAreRejected() {
        CooldownManager manager = new CooldownManager();
        assertFalse(manager.tryClaim(null, "blue", 30));
        assertFalse(manager.tryClaim(UUID.randomUUID(), null, 30));
    }
}
