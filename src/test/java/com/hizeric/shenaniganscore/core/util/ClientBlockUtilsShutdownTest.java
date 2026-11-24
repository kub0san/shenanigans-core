package com.hizeric.shenaniganscore.core.util;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Issue #57: at shutdown the scheduler no-op means a queued client-block revert never reaches the
 * client. The tracked overlays (and any queued reverts) must be flushed synchronously before the
 * state is cleared. The builders must also restore inline when their delayed task cannot be queued.
 */
class ClientBlockUtilsShutdownTest {

    private Object originalTransport;
    private Field transportField;

    @AfterEach
    void tearDown() throws Exception {
        if (transportField != null && originalTransport != null) {
            transportField.set(null, originalTransport);
        }
        MockBukkit.unmock();
    }

    @Test
    void shutdownFlushesTrackedOverlaysToTheirRealBlockData() throws Exception {
        ServerMock server = MockBukkit.mock();
        PlayerMock player = server.addPlayer();
        player.getWorld().loadChunk(0, 0);
        Location location = new Location(player.getWorld(), 3, 64, 5);
        player.getWorld().getBlockAt(location).setType(Material.STONE);
        BlockData real = player.getWorld().getBlockAt(location).getBlockData().clone();

        transportField = ClientBlockUtils.class.getDeclaredField("transport");
        transportField.setAccessible(true);
        originalTransport = transportField.get(null);
        List<Map<Location, BlockData>> sent = new ArrayList<>();
        ClientBlockUtils.setTransport((p, changes) -> sent.add(new HashMap<>(changes)));

        ClientBlockUtils.sendFakeBlock(player, location, Material.AIR.createBlockData());
        Field overlaysField = ClientBlockUtils.class.getDeclaredField("OVERLAYS");
        overlaysField.setAccessible(true);
        assertTrue(!((Map<?, ?>) overlaysField.get(null)).isEmpty(),
                "the fake block must be tracked in OVERLAYS (online=" + player.isOnline()
                        + ", world=" + player.getWorld().getName() + ")");
        assertTrue(sent.isEmpty(), "the fake block starts queued, not immediately sent");

        assertTrue(org.bukkit.Bukkit.getPlayer(player.getUniqueId()) != null,
                "the mock player must be resolvable by UUID");
        assertTrue(player.getWorld().isChunkLoaded(0, 0),
                "the mock world must report the target chunk loaded");
        ClientBlockUtils.shutdown();

        assertFalse(sent.isEmpty(), "shutdown must send a synchronous revert for the tracked overlay");
        BlockData reverted = sent.get(0).get(location);
        assertTrue(reverted != null && real.matches(reverted),
                "the revert must restore the real world block, not leave client-side air");
    }

    @Test
    void delayedEraseRestoreHandlesANullTask() throws IOException {
        for (String relative : List.of(
                "origins/jujutsukaisen/techniques/purple/PurpleBuilder.java",
                "origins/jujutsukaisen/techniques/worldslash/WorldSlashBuilder.java")) {
            Path path = Path.of("src", "main", "java", "com", "hizeric", "shenaniganscore")
                    .resolve(relative);
            assertTrue(Files.isRegularFile(path), "expected source at " + path.toAbsolutePath());
            String source = Files.readString(path, StandardCharsets.UTF_8);
            assertTrue(source.contains("if (task == null) TechniqueEraseOps.restore(locs, viewers);"),
                    relative + " must restore inline when runTaskLater returns null during disable");
        }
    }
}
