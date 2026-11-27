package com.hizeric.shenaniganscore.core.terrain;

import com.hizeric.shenaniganscore.ShenanigansCore;
import com.hizeric.shenaniganscore.core.domain.DomainRegistry;
import com.hizeric.shenaniganscore.testsupport.GlobalStateExtension;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.scheduler.BukkitSchedulerMock;
import org.mockbukkit.mockbukkit.world.WorldMock;
import sun.misc.Unsafe;

import java.lang.reflect.Field;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Issue #35, behavioural half: a floor whose chunk unloaded mid-domain cannot hand those blocks
 * back immediately. The restore must not report success — or drop its block claims — while work
 * is still owed; the pending columns go to the durable ledger and are retried at the next enable.
 * The pure completion decision is pinned by {@code DomainFloorRestoreTest}; this drives the real
 * floor through MockBukkit and checks the ledger/claim state it leaves behind.
 */
@ExtendWith(GlobalStateExtension.class)
class DomainFloorAsyncRestoreTest {

    private static final Unsafe UNSAFE = unsafe();

    private ServerMock server;
    private Path folder;
    private ShenanigansCore plugin;
    private Field instanceField;
    private ShenanigansCore previousInstance;

    @BeforeEach
    void setUp(@TempDir Path temp) throws Exception {
        server = MockBukkit.mock();
        folder = temp;
        // DomainFloor reads the plugin singleton for its data folder and logger. No real
        // enable() runs here: a bare instance with those two fields is enough, and it stays
        // disabled so the emergency restore takes its synchronous fallback path.
        plugin = (ShenanigansCore) UNSAFE.allocateInstance(ShenanigansCore.class);
        put(plugin, "dataFolder", temp.toFile());
        put(plugin, "logger", Logger.getLogger("DomainFloorAsyncRestoreTest"));
        instanceField = ShenanigansCore.class.getDeclaredField("instance");
        instanceField.setAccessible(true);
        previousInstance = (ShenanigansCore) instanceField.get(null);
        instanceField.set(null, plugin);
    }

    @AfterEach
    void tearDown() throws Exception {
        DomainFloorRestoreLedger.cancelReplay();
        if (instanceField != null) instanceField.set(null, previousInstance);
        MockBukkit.unmock();
    }

    @Test
    void anUnloadedChunkKeepsTheWorkInTheLedgerAndReleasesClaimsOnlyAtTheFallback() {
        WorldMock world = server.addSimpleWorld("floor-failed-restore");
        world.loadChunk(0, 0);
        UUID session = UUID.randomUUID();
        DomainFloor floor = builtFloor(world, session);
        assertTrue(DomainRegistry.get().claimCount() > 0, "a built floor must own real blocks");

        // The chunk unloads before the emergency restore: no column can be handed back now.
        world.unloadChunk(0, 0);
        assertFalse(world.isChunkLoaded(0, 0), "the test must really remove the chunk");

        floor.restoreNow();

        DomainFloorRestoreLedger ledger = new DomainFloorRestoreLedger(folder.toFile());
        assertFalse(ledger.isEmpty(), "unrestored blocks must survive for a later retry");
        List<DomainFloorRestoreLedger.Entry> entries = ledger.readAll();
        assertEquals(1, entries.size());
        assertEquals(session, entries.get(0).sessionId());
        assertEquals(world.getUID(), entries.get(0).worldId());
        assertFalse(entries.get(0).blocks().isEmpty(), "the pending columns must be written, not dropped");
        assertTrue(floor.isClosed(), "the explicit fallback closes the floor");
        assertEquals(0, DomainRegistry.get().claimCount(),
                "claims are released only at the explicit fallback, not while work is unaccounted for");
    }

    @Test
    void aLaterReplayDrainsTheWorkThatTheFailedRestoreRetained() {
        WorldMock world = server.addSimpleWorld("floor-retry-restore");
        world.loadChunk(0, 0);
        UUID session = UUID.randomUUID();
        DomainFloor floor = builtFloor(world, session);
        world.unloadChunk(0, 0);
        floor.restoreNow();

        DomainFloorRestoreLedger ledger = new DomainFloorRestoreLedger(folder.toFile());
        assertFalse(ledger.isEmpty(), "the retry needs the blocks the failed restore saved");

        // Next enable: the chunk is available and the driver hands the block states back.
        world.loadChunk(0, 0);
        org.bukkit.plugin.java.JavaPlugin schedulerPlugin =
                (org.bukkit.plugin.java.JavaPlugin) MockBukkit.createMockPlugin("FloorRetryTest");
        DomainFloorRestoreLedger.replay(schedulerPlugin, Logger.getLogger("FloorRetryTest"),
                folder.toFile());
        ((BukkitSchedulerMock) server.getScheduler()).performTicks(25);

        assertTrue(ledger.isEmpty(), "a completed retry must leave no work for a future enable");
        assertTrue(world.isChunkLoaded(0, 0));
    }

    @Test
    void theRestoreNeverReportsSuccessWhileBlocksOrLoadsRemain() {
        assertFalse(DomainFloor.readyToFinish(30, 40, 0),
                "work still in the world means the restore has not succeeded");
        assertFalse(DomainFloor.readyToFinish(40, 40, 2),
                "an in-flight chunk load may still touch the plan: success must wait");
        assertTrue(DomainFloor.readyToFinish(40, 40, 0));
    }

    private DomainFloor builtFloor(World world, UUID session) {
        DomainFloor floor = new DomainFloor(new Location(world, 0, 64, 0), 4, Material.STONE);
        floor.setSessionId(session);
        floor.prepare();
        floor.build(1.0);
        floor.tick();
        return floor;
    }

    private static void put(Object target, String name, Object value) throws Exception {
        Class<?> type = target.getClass();
        Field field = null;
        while (field == null && type != null) {
            try {
                field = type.getDeclaredField(name);
            } catch (NoSuchFieldException e) {
                type = type.getSuperclass();
            }
        }
        field.setAccessible(true);
        field.set(target, value);
    }

    private static Unsafe unsafe() {
        try {
            Field field = Unsafe.class.getDeclaredField("theUnsafe");
            field.setAccessible(true);
            return (Unsafe) field.get(null);
        } catch (ReflectiveOperationException e) {
            throw new ExceptionInInitializerError(e);
        }
    }
}
