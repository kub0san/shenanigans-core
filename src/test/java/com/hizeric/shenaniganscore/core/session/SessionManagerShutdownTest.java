package com.hizeric.shenaniganscore.core.session;

import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * F-46 regression: shutdown must route through the domain-aware close hook so
 * an in-flight shatter is torn down synchronously before the scheduler dies.
 */
class SessionManagerShutdownTest {

    @Test
    void stopAllUsesShutdownCloseHook() {
        UUID id = UUID.randomUUID();
        Player caster = (Player) Proxy.newProxyInstance(
                Player.class.getClassLoader(), new Class<?>[]{Player.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "getUniqueId" -> id;
                    case "getLocation" -> new Location(null, 0, 0, 0);
                    case "equals" -> proxy == args[0];
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "toString" -> "test-caster";
                    default -> null;
                });

        RecordingSession session = new RecordingSession(caster);
        SessionManager manager = new SessionManager();
        manager.startSession(session);
        manager.stopAll();

        assertTrue(session.shutdownClosed, "stopAll() must call closeForShutdown()");
        assertTrue(session.closed, "closeForShutdown() must still close the session");
        assertTrue(manager.getActiveSessions().isEmpty(), "stopAll() must clear the registry");
    }

    private static final class RecordingSession extends DomainSession {
        boolean closed;
        boolean shutdownClosed;

        RecordingSession(Player caster) {
            super(caster, new Location(null, 0, 0, 0), 1);
        }

        @Override
        public void close() {
            closed = true;
            deactivate();
        }

        @Override
        public void closeForShutdown() {
            shutdownClosed = true;
            close();
        }
    }
}
