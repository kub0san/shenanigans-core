package com.hizeric.shenaniganscore.core.session;

import com.hizeric.shenaniganscore.core.terrain.DomainFloorRestoreLedger;
import com.hizeric.shenaniganscore.testsupport.GlobalStateExtension;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Behavioural coverage for {@link SessionManager#startSession(DomainSession)} admission (F-11,
 * F-23, issue #35). The pure sphere decision is covered by {@code DomainOverlapTest}; here the
 * real manager is driven with MockBukkit worlds and players so the whole gate — replay latch,
 * duplicate caster, same-world overlap and activation — is exercised in order.
 */
@ExtendWith(GlobalStateExtension.class)
class SessionManagerAdmissionTest {

    private ServerMock server;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
    }

    @AfterEach
    void tearDown() {
        // The replay latch is static and the extension does not own it.
        DomainFloorRestoreLedger.cancelReplay();
        MockBukkit.unmock();
    }

    @Test
    void anOverlappingDomainInTheSameWorldIsRejected() {
        World world = server.addSimpleWorld("admission-overlap");
        PlayerMock firstCaster = server.addPlayer();
        PlayerMock secondCaster = server.addPlayer();
        SessionManager manager = new SessionManager();
        RecordingSession first = new RecordingSession(firstCaster, new Location(world, 0, 64, 0), 10);
        // Centers 15 apart, radii 10 + 10 = 20 → the spheres touch and overlap.
        RecordingSession second = new RecordingSession(secondCaster, new Location(world, 15, 64, 0), 10);

        assertTrue(manager.startSession(first));
        assertFalse(manager.startSession(second), "an overlapping live domain must be refused");
        assertFalse(second.isActive(), "a rejected session must never be activated");
        assertTrue(second.getCaptives().isEmpty(), "capture must not run on a rejected session");
        assertTrue(manager.hasActiveSession(firstCaster.getUniqueId()));
        assertFalse(manager.hasActiveSession(secondCaster.getUniqueId()));
        assertEquals(1, manager.getActiveSessions().size());
    }

    @Test
    void aSecondDomainForTheSameCasterIsRejectedEvenFarAway() {
        World world = server.addSimpleWorld("admission-caster");
        PlayerMock caster = server.addPlayer();
        SessionManager manager = new SessionManager();
        RecordingSession first = new RecordingSession(caster, new Location(world, 0, 64, 0), 4);
        // Well outside the first footprint: the only reason to reject is the duplicate caster.
        RecordingSession second = new RecordingSession(caster, new Location(world, 100, 64, 100), 4);

        assertTrue(manager.startSession(first));
        assertFalse(manager.startSession(second), "one live domain per caster, regardless of distance");
        assertFalse(second.isActive());
        assertEquals(1, manager.getActiveSessions().size());
    }

    @Test
    void aNonOverlappingDomainIsAdmitted() {
        World world = server.addSimpleWorld("admission-clear");
        PlayerMock firstCaster = server.addPlayer();
        PlayerMock secondCaster = server.addPlayer();
        SessionManager manager = new SessionManager();
        RecordingSession first = new RecordingSession(firstCaster, new Location(world, 0, 64, 0), 10);
        // Centers 25 apart, radii 10 + 10 = 20 → clear.
        RecordingSession second = new RecordingSession(secondCaster, new Location(world, 25, 64, 0), 10);

        assertTrue(manager.startSession(first));
        assertTrue(manager.startSession(second), "a separate footprint must be admitted");
        assertTrue(first.isActive());
        assertTrue(second.isActive());
        assertEquals(2, manager.getActiveSessions().size());
    }

    @Test
    void newDomainsWaitWhileThePreviousShutdownsFloorRestoreIsReplaying(@TempDir Path temp) {
        World world = server.addSimpleWorld("admission-replay");
        world.loadChunk(0, 0);
        UUID sessionId = UUID.randomUUID();
        DomainFloorRestoreLedger ledger = new DomainFloorRestoreLedger(temp.toFile());
        assertTrue(ledger.appendMerge(sessionId, world.getUID(), List.of(
                new DomainFloorRestoreLedger.Block(1, 64, 1, "minecraft:stone"))));

        Plugin plugin = MockBukkit.createMockPlugin("AdmissionReplayTest");
        DomainFloorRestoreLedger.replay(plugin, Logger.getLogger("AdmissionReplayTest"), temp.toFile());
        assertTrue(DomainFloorRestoreLedger.isReplaying(), "the test must actually hold the replay latch");

        SessionManager manager = new SessionManager();
        RecordingSession session = new RecordingSession(
                server.addPlayer(), new Location(world, 0, 64, 0), 4);

        assertFalse(manager.startSession(session),
                "a new floor must not be measured over stale originals during replay");
        assertFalse(session.isActive());
        assertTrue(manager.getActiveSessions().isEmpty());
    }

    /** Minimal session: activation/capture come from the base class, only close is stubbed. */
    private static final class RecordingSession extends DomainSession {
        boolean closed;

        RecordingSession(Player caster, Location center, double radius) {
            super(caster, center, radius);
        }

        @Override
        public void close() {
            closed = true;
            deactivate();
        }
    }
}
