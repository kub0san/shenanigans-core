package com.hizeric.shenaniganscore.core.domain;

import com.hizeric.shenaniganscore.core.session.DomainSession;
import com.hizeric.shenaniganscore.core.session.SessionManager;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pins the domain-claim safety valves: the fluid gate must not build the live-owner set for
 * unclaimed positions, stale claims must survive a reload while a domain is live, and the
 * world-less {@code ownerOf} overload must let the diff ledger filter claimed positions.
 */
class DomainClaimValveTest {

    private static final UUID WORLD = UUID.randomUUID();

    private final DomainRegistry registry = DomainRegistry.get();

    @BeforeEach
    void clearClaims() {
        registry.clearAll();
    }

    @Test
    void nullOwnerNeverComputesLiveOwners() {
        AtomicInteger computations = new AtomicInteger();

        boolean denied = DomainRegionGuard.deniedByLiveClaim(null, () -> {
            computations.incrementAndGet();
            return Set.of(UUID.randomUUID());
        });

        assertFalse(denied, "an unclaimed position must never be denied");
        assertEquals(0, computations.get(), "the live-owner set must not be built for a null owner");
    }

    @Test
    void idleClearIsANoOpWhileSessionsAreActive() {
        UUID owner = UUID.randomUUID();
        registry.claim(owner, WORLD, 10, 64, -20);
        SessionManager active = new SessionManager();
        assertTrue(active.startSession(new RecordingSession(caster(UUID.randomUUID()))));

        int cleared = registry.clearClaimsIfIdle(active::hasActiveSessions);

        assertEquals(0, cleared, "claims must not be dropped while a domain is live");
        assertEquals(1, registry.claimCount(), "the stale claim must survive the safety valve");
        assertEquals(owner, registry.ownerOf(10, 64, -20));
    }

    @Test
    void idleClearDropsStaleClaimsAndReturnsTheirCount() {
        UUID owner = UUID.randomUUID();
        registry.claim(owner, WORLD, 1, 2, 3);
        registry.claim(owner, WORLD, 4, 5, 6);
        SessionManager idle = new SessionManager();

        int cleared = registry.clearClaimsIfIdle(idle::hasActiveSessions);

        assertEquals(2, cleared, "the returned count must report how many claims were cleared");
        assertEquals(0, registry.claimCount(), "no stale claim may survive an idle clear");
    }

    @Test
    void ownerOfOverloadResolvesAClaimedPosition() {
        UUID owner = UUID.randomUUID();
        registry.claim(owner, WORLD, 7, 70, 9);

        assertEquals(owner, registry.ownerOf(7, 70, 9), "the ledger filter must find the owner by position alone");
        assertNull(registry.ownerOf(8, 70, 9), "unclaimed positions have no owner");
    }

    private static Player caster(UUID id) {
        return (Player) Proxy.newProxyInstance(
                Player.class.getClassLoader(), new Class<?>[]{Player.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "getUniqueId" -> id;
                    case "getLocation" -> new Location(null, 0, 0, 0);
                    case "equals" -> proxy == args[0];
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "toString" -> "test-caster";
                    default -> null;
                });
    }

    private static final class RecordingSession extends DomainSession {
        RecordingSession(Player caster) {
            super(caster, new Location(null, 0, 0, 0), 1);
        }

        @Override
        public void close() {
            deactivate();
        }

        @Override
        public void closeForShutdown() {
            close();
        }
    }
}
