package com.hizeric.shenaniganscore.core.terrain;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.plugin.Plugin;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.scheduler.BukkitSchedulerMock;
import org.mockbukkit.mockbukkit.world.WorldMock;

import java.io.File;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Issue #35: the enable-time replay driver must actually hand the persisted blocks back. */
class DomainFloorRestoreReplayTest {

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void replayRestoresLoadedChunkBlocksAndDrainsTheLedger(@TempDir Path temp) {
        ServerMock server = MockBukkit.mock();
        WorldMock world = server.addSimpleWorld("replay-world");
        world.loadChunk(0, 0);
        world.getBlockAt(new Location(world, 1, 64, 1)).setType(Material.BEDROCK);

        UUID session = UUID.randomUUID();
        DomainFloorRestoreLedger ledger = new DomainFloorRestoreLedger(temp.toFile());
        ledger.appendMerge(session, world.getUID(), List.of(
                new DomainFloorRestoreLedger.Block(1, 64, 1, "minecraft:stone")));

        assertTrue(org.bukkit.Bukkit.getWorld(world.getUID()) != null,
                "the mock world must be resolvable by UUID for replay");
        assertTrue(world.isChunkLoaded(0, 0), "the target chunk must be loaded");
        Plugin plugin = MockBukkit.createMockPlugin("DomainFloorReplayTest");
        DomainFloorRestoreLedger.replay(plugin, Logger.getLogger("DomainFloorReplayTest"), temp.toFile());
        ((BukkitSchedulerMock) server.getScheduler()).performTicks(25);

        assertEquals(Material.STONE, world.getBlockAt(1, 64, 1).getType(),
                "the persisted original block must be written back");
        File file = new File(new File(temp.toFile(), "domain"), "floor-restore.dat");
        assertTrue(!file.exists() || ledger.isEmpty(),
                "a drained ledger must not leave work for a future enable");
    }
}
