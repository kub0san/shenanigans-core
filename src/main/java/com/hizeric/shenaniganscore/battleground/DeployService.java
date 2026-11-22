package com.hizeric.shenaniganscore.battleground;

import com.hizeric.shenaniganscore.battleground.arena.ArenaEntryGuard;
import com.hizeric.shenaniganscore.battleground.arena.ArenaRegion;
import com.hizeric.shenaniganscore.battleground.arena.RespawnTarget;
import com.hizeric.shenaniganscore.battleground.deploy.DeployFeedback;
import com.hizeric.shenaniganscore.battleground.deploy.DropZone;
import com.hizeric.shenaniganscore.battleground.loadout.LoadoutApplier;
import com.hizeric.shenaniganscore.battleground.menu.MenuManager;
import com.hizeric.shenaniganscore.battleground.menu.shell.HubScreen;
import com.hizeric.shenaniganscore.battleground.menu.shell.MenuLatch;
import com.hizeric.shenaniganscore.battleground.player.ArenaInventories;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;

import java.util.UUID;

/**
 * Everything on the hub ↔ arena boundary: staging, deploy, exit, failed-drop recovery and quit
 * cleanup. Extracted from {@link BattlegroundManager} (C1) so roster/deploy changes cannot touch
 * round lifecycle code.
 */
final class DeployService {
    private final BattlegroundManager m;

    DeployService(BattlegroundManager manager) {
        this.m = manager;
    }

    /** Gives back exactly the items taken on first deploy; a no-op for anyone who never deployed. */
    void restoreInventory(Player player) {
        ArenaInventories.Snapshot snapshot = m.inventories.take(player.getUniqueId());
        if (snapshot == null) return;
        var inventory = player.getInventory();
        inventory.clear();
        inventory.setContents(snapshot.contents());
        player.setItemOnCursor(snapshot.cursor());
        player.updateInventory();
    }

    /** `/bg join` stages the player; only the DEPLOY button in the hub reaches {@link #join(Player)}. */
    void enterStage(Player player) {
        if (player == null) return;
        ArenaRegion region = m.arenaStore.region();
        RoundGates.Stage decision = RoundGates.stage(region != null && region.isCaptured(),
                m.isDeployed(player.getUniqueId()), m.staging.isStaged(player));
        switch (decision) {
            case NO_ARENA -> {
                player.sendMessage(MiniMessage.miniMessage().deserialize(
                        "<red>The arena has not been set up yet. Ask an admin to run /bg setup."));
                return;
            }
            case ALREADY_DEPLOYED -> {
                player.sendMessage(MiniMessage.miniMessage().deserialize(
                        "<yellow>You are already deployed — finish the fight first."));
                return;
            }
            case ALREADY_STAGED -> {
                new HubScreen(m.plugin, player).show();
                return;
            }
            case STAGE_READY -> { }
        }
        m.staging.enter(player);
        new HubScreen(m.plugin, player).show();
    }

    void join(Player player) {
        if (player == null) return;
        ArenaRegion region = m.arenaStore.region();
        RoundGates.Deploy decision = RoundGates.deploy(m.state,
                region != null && region.isCaptured(), m.isDeployed(player.getUniqueId()));
        if (decision != RoundGates.Deploy.READY) {
            switch (decision) {
                case NO_ARENA -> player.sendMessage(MiniMessage.miniMessage().deserialize(
                        "<red>The arena has not been set up yet. Ask an admin to run /bg setup."));
                case RESTORING -> {
                    String msg = m.state == RoundState.STAGING
                            ? "<yellow>The arena is preparing (tier 1). Deployment opens when ready."
                            : "<yellow>The arena is still restoring. Try again in a moment.";
                    player.sendMessage(MiniMessage.miniMessage().deserialize(msg));
                }
                case ALREADY_DEPLOYED -> player.sendMessage(MiniMessage.miniMessage().deserialize(
                        "<yellow>You are already deployed — finish the fight first."));
                case READY -> { }
            }
            return;
        }

        // Score the whole arena on demand, before any staging/inventory mutation. A failed search
        // leaves the player staged in the hub with a specific reason so the next Deploy can retry.
        DropZone.Selection selection = m.dropSelector.select(player, region);
        DeployFeedback.Result verdict = DeployFeedback.forSelection(selection);
        if (verdict.outcome() == DeployFeedback.Outcome.RESTAGE) {
            m.staging.enter(player); // idempotent for an already-staged player
            player.sendMessage(MiniMessage.miniMessage().deserialize(verdict.message()));
            new HubScreen(m.plugin, player).show();
            return;
        }
        DropZone.Candidate candidate = selection.candidate();

        // Captured once. A later deploy after death is a re-entry, not a new inventory.
        m.inventories.captureOnce(player.getUniqueId(),
                player.getInventory().getContents(), player.getItemOnCursor());
        player.setItemOnCursor(null);
        m.staging.release(player);
        MenuLatch.get().suppress(player);
        // Schedule the release immediately: the token only needs to survive this tick's GUI close
        // events. Scheduling it up front means an exception (or a quit) mid-deploy cannot strand
        // a suppression token and permanently stop the hub reopening for this player.
        Bukkit.getScheduler().runTask(m.plugin, () -> MenuLatch.get().release(player));
        player.closeInventory();
        MenuManager.get().clearIfOpen(player);
        // Multiverse may load the arena world after enable(); set the rule before the first deploy.
        if (region.world() != null) {
            region.world().setGameRule(org.bukkit.GameRule.DO_IMMEDIATE_RESPAWN, true);
        }
        m.deployed.add(player.getUniqueId());
        LoadoutApplier.apply(m.plugin, player);

        // Spawn height comes from the candidate's own surface + obstacle clearance, never the
        // arena midpoint (which sits inside a cliff on rugged maps).
        World world = region.world();
        Location start = new Location(world, candidate.x() + 0.5, candidate.spawnY() + 0.5,
                candidate.z() + 0.5, player.getLocation().getYaw(), player.getLocation().getPitch());
        ArenaEntryGuard.get().allowOnce(player);
        if (!player.teleport(start)) {
            m.deployed.remove(player.getUniqueId());
            m.dives.clear(player.getUniqueId());
            m.staging.enter(player);
            restoreInventory(player);
            MenuLatch.get().release(player);
            player.sendMessage(MiniMessage.miniMessage().deserialize(
                    "<red>Deploy failed to teleport you into the arena. Try Deploy again."));
            new HubScreen(m.plugin, player).show();
            return;
        }
        player.sendMessage(MiniMessage.miniMessage().deserialize(
                "<green>Airdropping to sector <white>" + candidate.x() + ", " + candidate.z()
                        + "<green>."));
        // No spawn protection: the dive lock covers the descent and the player is vulnerable the
        // instant they land — there is deliberately no grace window.
        m.skyDrop.begin(player, start, candidate.groundY());

        if (RoundGates.roundStartRequired(m.state)) {
            // Route through beginRound() so a round can never run without an open diff ledger,
            // a prewarmed next map and an incremented round number. The gate above only admits
            // IDLE/RUNNING/WARNING, so this call succeeds here; the IDLE case is the one this
            // defensive branch used to skip.
            m.rounds.beginRound("<green>Round <white>" + (m.roundNumber + 1) + "<green> has begun!");
        }
    }

    /** The full exit: leave the mode and teleport to the persisted return point (or world spawn). */
    void leave(Player player) {
        if (player == null) return;
        UUID uuid = player.getUniqueId();
        boolean wasStaged = m.staging.isStaged(player);
        boolean wasDeployed = m.deployed.remove(uuid);
        if (!wasStaged && !wasDeployed) {
            player.sendMessage(MiniMessage.miniMessage().deserialize(
                    "<gray>You are not in the battleground."));
            return;
        }
        m.dives.clear(uuid);
        m.combat.clear(uuid);
        m.staging.exit(player); // teleports to the return point when staged
        restoreInventory(player);
        ArenaRegion region = m.arenaStore.region();
        Location destination = RespawnTarget.resolve(
                region == null ? null : region.spawn(), player.getWorld().getSpawnLocation(), region);
        if (!wasStaged) {
            // Outward teleport: no transit token (the guard ignores outside destinations).
            player.teleport(destination);
        }
        ArenaEntryGuard.get().clearTransit(uuid);
        MenuLatch.get().clear(player);
        player.closeInventory();
        MenuManager.get().clearIfOpen(player);
        player.sendMessage(MiniMessage.miniMessage().deserialize("<gray>You left the battleground."));
    }

    void stageOnRespawn(Player player) {
        if (player == null) return;
        MenuLatch.get().clear(player);
        m.deployed.remove(player.getUniqueId());
        m.dives.clear(player.getUniqueId());
        m.staging.enter(player);
        new HubScreen(m.plugin, player).show();
    }

    /** Called from the move listener: leaving the arena returns the player to the hub. */
    void checkRegion(Player player) {
        if (player == null || !m.isDeployed(player.getUniqueId())) return;
        ArenaRegion region = m.arenaStore.region();
        if (region == null || region.containsHorizontal(player.getLocation())) return;
        // Leaving the arena is a full exit back to the return point.
        leave(player);
    }

    /** A diver touched water or lava: undo the deploy and put them back in the hub. */
    void restageAfterLiquid(Player player) {
        restageAfterFailedDeploy(player, BattlegroundDefaults.DEPLOY_LIQUID_MESSAGE);
    }

    void restageAfterAbort(Player player) {
        restageAfterFailedDeploy(player,
                "<red>Your drop was interrupted — returned to the hub. Try Deploy again.");
    }

    private void restageAfterFailedDeploy(Player player, String message) {
        if (player == null) return;
        UUID uuid = player.getUniqueId();
        MenuLatch.get().clear(player);
        if (!m.deployed.remove(uuid)) {
            // Already cleaned up (death/leave); don't double-restore.
            return;
        }
        m.dives.clear(uuid);
        m.combat.clear(uuid);
        m.staging.enter(player);
        restoreInventory(player);
        player.closeInventory();
        MenuManager.get().clearIfOpen(player);
        if (message != null && !message.isBlank()) {
            player.sendMessage(MiniMessage.miniMessage().deserialize(message));
        }
        new HubScreen(m.plugin, player).show();
    }

    /** Called from the quit listener so staged state and the roster never leak. */
    void onQuit(Player player) {
        if (player == null) return;
        UUID uuid = player.getUniqueId();
        // A quit between MenuLatch.suppress() and its scheduled release in join() leaves no staged
        // state for staging.exit() to clear. Drop every token here so a reconnect can reopen the hub.
        MenuLatch.get().clear(uuid);
        m.staging.exit(player);
        restoreInventory(player);
        m.deployed.remove(uuid);
        m.dives.clear(uuid);
        m.combat.clear(uuid);
    }

    /** Safe on any player, staged or not. Clears leaked flags and restores a known-good state. */
    void repair(Player player) {
        repair(player, false);
    }

    /** @param force ignore the in-memory lock (it is empty after a restart) when flags leaked. */
    void repair(Player player, boolean force) {
        if (player == null) return;
        if (force) m.staging.repairLeaked(player);
        else m.staging.repair(player);
        restoreInventory(player);
        m.deployed.remove(player.getUniqueId());
        m.dives.clear(player.getUniqueId());
    }
}
