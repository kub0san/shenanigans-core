package com.hizeric.shenaniganscore.battleground.arena;

import org.bukkit.util.BlockVector;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Issue #64 wiring and geometry guards. The bounds helper is pure (block coordinates only), and
 * the listener contracts are pinned as source assertions because a live Paper server is not
 * available to a unit test — the point is to catch a regression that moves ejection back inside a
 * move event or drops the join sweep, not to fake a Bukkit event bus.
 */
class ArenaEntryGuardEjectTest {

    private static final ArenaRegion REGION = new ArenaRegion(
            null, "arena", new BlockVector(10, 64, 20), new BlockVector(20, 70, 30),
            0L, 0L, "test");

    private static String source(String... parts) throws IOException {
        return Files.readString(Path.of("src", "main", "java", "com", "hizeric", "shenaniganscore")
                .resolve(Path.of("", parts)), StandardCharsets.UTF_8);
    }

    // ------------------------------------------------------------ pure bounds

    @Test
    void boundsIncludeTheArenaItself() {
        assertTrue(ArenaEntryGuard.withinGuardedVolume(REGION, 10, 64, 20));
        assertTrue(ArenaEntryGuard.withinGuardedVolume(REGION, 20, 70, 30));
        assertTrue(ArenaEntryGuard.withinGuardedVolume(REGION, 15, 67, 25));
    }

    @Test
    void boundsIncludeTheBelowArenaAndStagingSlack() {
        assertTrue(ArenaEntryGuard.withinGuardedVolume(REGION, 15, 48, 25)); // 64 - 16
        assertTrue(ArenaEntryGuard.withinGuardedVolume(REGION, 15, 110, 25)); // 70 + 40
        assertFalse(ArenaEntryGuard.withinGuardedVolume(REGION, 15, 47, 25));
        assertFalse(ArenaEntryGuard.withinGuardedVolume(REGION, 15, 111, 25));
    }

    @Test
    void boundsRejectHorizontalOutsides() {
        assertFalse(ArenaEntryGuard.withinGuardedVolume(REGION, 9, 67, 25));
        assertFalse(ArenaEntryGuard.withinGuardedVolume(REGION, 21, 67, 25));
        assertFalse(ArenaEntryGuard.withinGuardedVolume(REGION, 15, 67, 19));
        assertFalse(ArenaEntryGuard.withinGuardedVolume(REGION, 15, 67, 31));
    }

    // ------------------------------------------------------------ wiring

    @Test
    void moveGuardComputesBothSidesAndSchedulesEjectionOffEvent() throws IOException {
        String src = source("battleground", "arena", "ArenaEntryGuard.java");
        assertTrue(src.contains("boolean toInside = inside(region, to);"),
                "destination containment must be computed before the early return");
        assertTrue(src.contains("boolean fromInside = inside(region, from);"),
                "origin containment must be computed before the early return");
        assertTrue(src.contains("decide(plugin, player, false, fromInside, toInside)"),
                "the pure decision must receive both containment flags");
        assertTrue(src.contains("if (!toInside && !fromInside) return;"),
                "fail open when neither endpoint is guarded");
        assertTrue(src.contains("scheduleEjection(plugin, player, region)"),
                "EJECT must queue a teleport, not teleport inline");

        int moveStart = src.indexOf("public void onMove(PlayerMoveEvent event)");
        int moveEnd = src.indexOf("@EventHandler", moveStart + 1);
        assertTrue(moveStart >= 0 && moveEnd > moveStart, "expected an onMove handler to inspect");
        String onMove = src.substring(moveStart, moveEnd);
        assertFalse(onMove.contains(".teleport("), "no live teleport inside PlayerMoveEvent");
        assertTrue(onMove.contains("event.setTo(from)"), "both DENY and EJECT pin the player first");
    }

    @Test
    void ejectionIsDeduplicatedAuthorisedAndRateLimited() throws IOException {
        String src = source("battleground", "arena", "ArenaEntryGuard.java");
        assertTrue(src.contains("if (!ejecting.add(uuid)) return;"), "no double teleport");
        assertTrue(src.contains("allowOnce(player)"), "the queued teleport needs a transit token");
        assertTrue(src.contains("Bukkit.getScheduler().runTask(plugin"),
                "the teleport must be scheduled, not inline");
        assertTrue(src.contains("RespawnTarget.resolve("), "eject to the persisted return point");
        assertTrue(src.contains("deny(player)"), "reuse the rate-limited denial feedback");
        assertTrue(src.contains("ejecting.remove(uuid)"), "clear the queue flag on quit and on run");
        assertTrue(src.contains("public void ejectIfStranded(Player player)"),
                "join-time sweep must be reachable from the listener");
    }

    @Test
    void joinSweepsAfterRepairAndDeployExitIsUntouched() throws IOException {
        String listener = source("battleground", "BattlegroundListener.java");
        assertTrue(listener.contains("manager().repair(event.getPlayer());"), "repair still runs on join");
        assertTrue(listener.indexOf("ArenaEntryGuard.get().ejectIfStranded(event.getPlayer());")
                        > listener.indexOf("manager().repair(event.getPlayer());"),
                "the sweep must run after repair clears leaked mode flags");

        String deploy = source("battleground", "DeployService.java");
        assertTrue(deploy.contains("if (player == null || !m.isDeployed(player.getUniqueId())) return;"),
                "checkRegion must remain unchanged");
        assertFalse(deploy.contains("EJECT"), "deployed exits must never fire EJECT");
    }
}
