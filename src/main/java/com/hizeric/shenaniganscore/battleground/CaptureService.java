package com.hizeric.shenaniganscore.battleground;

import com.hizeric.shenaniganscore.battleground.arena.ArenaProtection;
import com.hizeric.shenaniganscore.battleground.arena.ArenaRegion;
import com.hizeric.shenaniganscore.battleground.arena.snapshot.ArenaSnapshot;
import com.hizeric.shenaniganscore.battleground.deploy.BukkitDropProbe;
import com.hizeric.shenaniganscore.battleground.deploy.DropZone;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.entity.Player;

import java.util.logging.Level;

/**
 * Snapshot capture and admin map switching, extracted from {@link BattlegroundManager} (C1).
 * All lifecycle side effects are delegated to {@link RoundController} so this class owns only the
 * capture pipeline and the map-switch gate.
 */
final class CaptureService {
    private final BattlegroundManager m;

    CaptureService(BattlegroundManager manager) {
        this.m = manager;
    }

    boolean capture() {
        ArenaRegion region = m.arenaStore.region();
        if (region == null) return false;
        if (!RoundGates.captureAllowed(!m.deployed.isEmpty() || nonAdminInsideRegion())) return false;
        return doCapture(m.arenaStore.activeMapId());
    }

    /** Explicit admin capture for {@code /bg map capture}. Captures active map snapshot. */
    boolean captureSnapshot() {
        return captureSnapshot(m.arenaStore.activeMapId());
    }

    boolean captureSnapshot(String mapId) {
        String id = mapId == null || mapId.isBlank() ? m.arenaStore.activeMapId() : mapId;
        if (m.arenaStore.region(id) == null) {
            m.plugin.getLogger().warning("No arena region is configured for map '" + id + "'; snapshot not written.");
            return false;
        }
        return doCapture(id);
    }

    /** Single pre-capture/snapshot/post-capture pipeline shared by both capture entry points. */
    private boolean doCapture(String id) {
        ArenaRegion region = m.arenaStore.region(id);
        m.rounds.stopDomainsAndSweep();
        m.rounds.flushDiffLedger(id);
        try {
            ArenaSnapshot snapshot = m.snapshotStore.capture(id, region);
            m.arenaStore.setCaptureInfo(id, region.capturedBlocks(), "snapshot");
            m.arenaStore.save();
            if (id.equals(m.arenaStore.activeMapId())) {
                ArenaProtection.get().reloadPreservingPlaced(snapshot);
            }
            m.plugin.getLogger().info("Captured arena snapshot for map '" + id + "'.");
            // Warn only — deploy selection now handles single-layer floors (issue #4). A captured map
            // must still succeed; this just nudges admins toward a 2-layer floor. The advisory is
            // isolated below so a probe failure can never flip an otherwise successful capture.
            try {
                World arenaWorld = region.world();
                if (arenaWorld != null && DropZone.hasThinFloor(new BukkitDropProbe(arenaWorld),
                        region.min().getBlockX(), region.max().getBlockX(),
                        region.min().getBlockZ(), region.max().getBlockZ(),
                        region.min().getBlockY(), BattlegroundDefaults.DEPLOY_SAMPLE_STEP * 4)) {
                    m.plugin.getLogger().warning("Arena map '" + id + "' has a single-block-thick floor; "
                            + "deploy works, but a 2-layer floor is recommended.");
                }
            } catch (Exception advisory) {
                m.plugin.getLogger().log(Level.FINE,
                        "Thin-floor advisory skipped for map '" + id + "': " + advisory.getMessage());
            }
            return true;
        } catch (Exception e) {
            m.plugin.getLogger().log(Level.SEVERE,
                    "Could not capture the arena snapshot for map " + id + ".", e);
            return false;
        } finally {
            // A live round keeps running across a capture, so its ledger must be open again
            // before the next block change is recorded.
            m.rounds.reopenLedgerIfLive();
        }
    }

    /** An admin selecting the region is usually standing in it; ordinary players are not allowed in. */
    private boolean nonAdminInsideRegion() {
        ArenaRegion region = m.arenaStore.region();
        if (region == null) return false;
        for (Player online : Bukkit.getOnlinePlayers()) {
            if (online.hasPermission("shenaniganscore.battleground.admin")) continue;
            if (region.contains(online.getLocation())) return true;
        }
        return false;
    }

    /** Admin-driven map switch. No automatic rotation while one map exists. */
    boolean switchActiveMap(String id) {
        if (id == null || id.isBlank()) return false;
        if (m.arenaStore.region(id) == null) return false;
        // A tier-1 restore is bound to the map it started on. Switching mid-restore would leave
        // the newly selected map unrestored while the old map's tier-1 keeps running; beginReset()
        // then early-returns on STAGING and never restores the new map. Reject the switch instead.
        if (!RoundGates.mapSwitchAllowed(m.state)) {
            m.plugin.getLogger().warning("Cannot switch maps while the arena is restoring.");
            return false;
        }
        String previous = m.arenaStore.activeMapId();
        if (previous.equalsIgnoreCase(id)) return true;
        m.rounds.flushDiffLedger(previous);
        if (m.restoreEngine != null) m.restoreEngine.cancelTierTwo();
        boolean switched = m.arenaStore.switchTo(id);
        if (!switched) return false;
        m.arenaStore.save();
        m.arenaStore.prewarmNextMap();
        m.rounds.reloadProtectionNoRescan(m.arenaStore.activeMapId());
        // Reset the live round onto the selected map. Never auto-capture.
        m.rounds.beginReset();
        return true;
    }
}
