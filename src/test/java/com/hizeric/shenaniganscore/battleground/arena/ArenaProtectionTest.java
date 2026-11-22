package com.hizeric.shenaniganscore.battleground.arena;

import com.hizeric.shenaniganscore.battleground.arena.snapshot.ArenaSnapshot;
import com.hizeric.shenaniganscore.battleground.arena.snapshot.ArenaSnapshot.ChunkPos;
import com.hizeric.shenaniganscore.core.domain.DomainRegionGuard;
import org.bukkit.event.EventHandler;
import org.bukkit.event.block.BlockBurnEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockFromToEvent;
import org.bukkit.event.block.BlockIgniteEvent;
import org.bukkit.event.block.BlockPistonExtendEvent;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ArenaProtectionTest {

    @TempDir
    Path folder;

    @AfterEach
    void restoreDefaults() {
        ArenaRuntimeConfig.load(null, null);
    }

    private static ArenaProtection withSnapshot(int... solidCoords) {
        ArenaProtection protection = new ArenaProtection();
        protection.reload(null, snapshotOf(solidCoords));
        return protection;
    }

    /** Builds a tiny snapshot where listed x,y,z triples are minecraft:stone and rest air. */
    private static ArenaSnapshot snapshotOf(int... solidCoords) {
        if (solidCoords.length % 3 != 0) throw new IllegalArgumentException("triples required");
        int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE, minZ = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE, maxY = Integer.MIN_VALUE, maxZ = Integer.MIN_VALUE;
        for (int i = 0; i < solidCoords.length; i += 3) {
            minX = Math.min(minX, solidCoords[i]); maxX = Math.max(maxX, solidCoords[i]);
            minY = Math.min(minY, solidCoords[i + 1]); maxY = Math.max(maxY, solidCoords[i + 1]);
            minZ = Math.min(minZ, solidCoords[i + 2]); maxZ = Math.max(maxZ, solidCoords[i + 2]);
        }
        // pad to avoid zero-size edge cases
        minX -= 1; minY -= 1; minZ -= 1; maxX += 1; maxY += 1; maxZ += 1;
        List<String> palette = List.of(ArenaSnapshot.AIR, "minecraft:stone");
        Map<ChunkPos, int[]> chunks = new LinkedHashMap<>();
        for (int cx = minX >> 4; cx <= maxX >> 4; cx++) {
            for (int cz = minZ >> 4; cz <= maxZ >> 4; cz++) {
                ChunkPos pos = new ChunkPos(cx, cz);
                int[] cells = new int[ArenaSnapshot.cellCount(minX, minY, minZ, maxX, maxY, maxZ, cx, cz)];
                chunks.put(pos, cells);
            }
        }
        for (int i = 0; i < solidCoords.length; i += 3) {
            int x = solidCoords[i], y = solidCoords[i + 1], z = solidCoords[i + 2];
            ChunkPos pos = new ChunkPos(x >> 4, z >> 4);
            int[] cells = chunks.get(pos);
            int x0 = Math.max(minX, pos.x() * 16);
            int x1 = Math.min(maxX, pos.x() * 16 + 15);
            int z0 = Math.max(minZ, pos.z() * 16);
            int z1 = Math.min(maxZ, pos.z() * 16 + 15);
            int xSpan = x1 - x0 + 1;
            int zSpan = z1 - z0 + 1;
            int idx = (y - minY) * (zSpan * xSpan) + (z - z0) * xSpan + (x - x0);
            cells[idx] = 1;
        }
        return new ArenaSnapshot("test", "world", minX, minY, minZ, maxX, maxY, maxZ,
                1L, solidCoords.length / 3L, "SNAPSHOT", palette, chunks);
    }

    private void writeConfig(boolean protectionEnabled) throws IOException {
        File file = new File(folder.toFile(), "config.yml");
        Files.writeString(file.toPath(), """
                arena:
                  protection:
                    enabled: %b
                """.formatted(protectionEnabled), StandardCharsets.UTF_8);
        ArenaRuntimeConfig.load(file, Logger.getLogger("test"));
    }

    private static List<Method> handlers(Class<?> listener, Class<?> eventType) {
        List<Method> found = new ArrayList<>();
        for (Method method : listener.getDeclaredMethods()) {
            if (method.getAnnotation(EventHandler.class) == null) continue;
            if (Arrays.asList(method.getParameterTypes()).contains(eventType)) found.add(method);
        }
        return found;
    }

    @Test
    void baselineBlockIsUnbreakable() {
        ArenaProtection protection = withSnapshot(10, 64, -20);

        assertTrue(protection.isProtectedAt(10, 64, -20));
        assertTrue(protection.cancelBreak(10, 64, -20, false),
                "captured structure must be cancelled by arena protection");
    }

    @Test
    void nonBaselineBlockIsBreakable() {
        ArenaProtection protection = withSnapshot(10, 64, -20);

        assertFalse(protection.isProtectedAt(11, 64, -20));
        assertFalse(protection.cancelBreak(11, 64, -20, false));
    }

    @Test
    void playerPlacedOverrideIsDestructibleAndClearsOnDestroy() {
        ArenaProtection protection = withSnapshot(3, 70, 3);
        protection.notePlaced(3, 70, 3);

        assertFalse(protection.isProtectedAt(3, 70, 3),
                "a player block on a captured coordinate must stay breakable");
        protection.noteDestroyed(3, 70, 3);
        assertTrue(protection.isProtectedAt(3, 70, 3),
                "once the player block is gone the coordinate is structure again");
    }

    @Test
    void fluidFlowIsNeverCancelledAndIgnitionHasNoArenaHandler() {
        List<Method> fluid = handlers(ArenaProtection.class, BlockFromToEvent.class);
        assertEquals(1, fluid.size(), "the fluid handler that drops stale overrides must be ported");
        assertTrue(fluid.get(0).getAnnotation(EventHandler.class).ignoreCancelled());
        assertTrue(handlers(ArenaProtection.class, BlockIgniteEvent.class).isEmpty(),
                "arena protection must not deny flint & steel; only a live domain may");

        // Domain enforcement is a separate listener and must stay in force when the arena gate is off.
        assertFalse(handlers(DomainRegionGuard.class, BlockFromToEvent.class).isEmpty());
        assertFalse(handlers(DomainRegionGuard.class, BlockIgniteEvent.class).isEmpty());
    }

    @Test
    void burnExplodeAndPistonHandlersArePorted() {
        assertFalse(handlers(ArenaProtection.class, BlockBurnEvent.class).isEmpty());
        assertFalse(handlers(ArenaProtection.class, BlockExplodeEvent.class).isEmpty());
        assertFalse(handlers(ArenaProtection.class, BlockPistonExtendEvent.class).isEmpty());
    }

    @Test
    void toggleOffAllowsBreaksAndReloadAppliesImmediately() throws IOException {
        ArenaProtection protection = withSnapshot(7, 8, 9);
        assertTrue(protection.cancelBreak(7, 8, 9, false));

        writeConfig(false);
        assertFalse(ArenaRuntimeConfig.current().protectionEnabled());
        assertFalse(protection.cancelBreak(7, 8, 9, false),
                "with arena protection off, a captured block must be breakable");
        assertTrue(protection.isProtectedAt(7, 8, 9),
                "the snapshot itself is untouched by the toggle");

        writeConfig(true);
        assertTrue(protection.cancelBreak(7, 8, 9, false),
                "a /bg reload must be visible to the next event without re-registering listeners");
    }

    @Test
    void domainStyleFluidAndIgniteDecisionsStayIndependentOfTheArenaGate() throws IOException {
        writeConfig(false);
        assertFalse(ArenaProtection.protectionEnabled());
        // These live decisions are in DomainRegionGuard, not here; the gate must not touch them.
        assertTrue(handlers(DomainRegionGuard.class, BlockFromToEvent.class).stream()
                .allMatch(m -> m.getAnnotation(EventHandler.class).ignoreCancelled()));
        assertTrue(handlers(DomainRegionGuard.class, BlockIgniteEvent.class).stream()
                .allMatch(m -> m.getAnnotation(EventHandler.class).ignoreCancelled()));
    }

    @Test
    void adminBypassNeverCancels() {
        ArenaProtection protection = withSnapshot(-5, 40, 12);

        assertTrue(protection.cancelBreak(-5, 40, 12, false));
        assertFalse(protection.cancelBreak(-5, 40, 12, true));
    }

    @Test
    void solidAtProviderSeamIsRepointableAndFallsBackToSnapshot() {
        ArenaProtection protection = withSnapshot(1, 2, 3);
        protection.setSolidAtProvider((x, y, z) -> x == 42);

        assertTrue(protection.isProtectedAt(42, 2, 3));
        assertFalse(protection.isProtectedAt(1, 2, 3),
                "the repointed provider wins over the snapshot lookup");

        protection.setSolidAtProvider(null);
        assertTrue(protection.isProtectedAt(1, 2, 3),
                "a null provider restores snapshot lookup");
    }

    @Test
    void reloadClearsPlayerPlacedOverrides() {
        ArenaProtection protection = withSnapshot(2, 3, 4);
        protection.notePlaced(2, 3, 4);
        assertFalse(protection.isProtectedAt(2, 3, 4));

        protection.reload(null, snapshotOf(2, 3, 4));
        assertTrue(protection.isProtectedAt(2, 3, 4),
                "a restore must not leave a stale player-placed override behind");
    }

    @Test
    void reloadPreservingPlacedKeepsPlayerPlacedOverrides() {
        ArenaProtection protection = withSnapshot(2, 3, 4);
        protection.notePlaced(2, 3, 4);
        assertFalse(protection.isProtectedAt(2, 3, 4));

        protection.reloadPreservingPlaced(snapshotOf(2, 3, 4));
        assertFalse(protection.isProtectedAt(2, 3, 4),
                "a live capture keeps the round running, so player-placed overrides must survive");
    }

    @Test
    void packLayoutIsVerbatim() {
        assertEquals(275_012_124_675L, ArenaProtection.pack(1, 2, 3),
                "26 bits x, 12 bits y, 26 bits z");
        assertEquals(ArenaProtection.pack(1, 2, 3), ArenaProtection.pack(1, 2, 3));
        assertTrue(ArenaProtection.pack(-1, 4097, -1) != 0);
    }
}
