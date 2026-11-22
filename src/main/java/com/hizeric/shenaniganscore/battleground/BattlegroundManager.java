package com.hizeric.shenaniganscore.battleground;

import com.hizeric.shenaniganscore.ShenanigansCore;
import com.hizeric.shenaniganscore.battleground.admin.AdminRoundGate;
import com.hizeric.shenaniganscore.battleground.arena.ArenaRegion;
import com.hizeric.shenaniganscore.battleground.arena.ArenaRestorer;
import com.hizeric.shenaniganscore.battleground.arena.ArenaRuntimeConfig;
import com.hizeric.shenaniganscore.battleground.arena.ArenaStore;
import com.hizeric.shenaniganscore.battleground.arena.RestoreBudget;
import com.hizeric.shenaniganscore.battleground.arena.WorldEditSupport;
import com.hizeric.shenaniganscore.battleground.arena.restore.ArenaRestoreEngine;
import com.hizeric.shenaniganscore.battleground.arena.snapshot.ArenaSnapshot;
import com.hizeric.shenaniganscore.battleground.arena.snapshot.ArenaSnapshotStore;
import com.hizeric.shenaniganscore.battleground.combat.CombatTracker;
import com.hizeric.shenaniganscore.battleground.command.Permissions;
import org.bukkit.command.CommandSender;
import com.hizeric.shenaniganscore.battleground.deploy.DiveService;
import com.hizeric.shenaniganscore.battleground.deploy.DropSiteSelector;
import com.hizeric.shenaniganscore.battleground.deploy.SkyDrop;
import com.hizeric.shenaniganscore.battleground.economy.EconomyService;
import com.hizeric.shenaniganscore.battleground.player.ArenaInventories;
import com.hizeric.shenaniganscore.battleground.player.BattlegroundLock;
import com.hizeric.shenaniganscore.battleground.player.PlayerStore;
import com.hizeric.shenaniganscore.battleground.player.StagingContract;
import com.hizeric.shenaniganscore.battleground.player.StagingController;
import com.hizeric.shenaniganscore.battleground.shop.ShopCatalog;
import com.hizeric.shenaniganscore.core.ability.AbilityId;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.io.File;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Thin facade over the four battleground services (C1): {@link RoundController} owns lifecycle and
 * restore, {@link DeployService} owns the hub/arena boundary, {@link CaptureService} owns snapshots
 * and map switching, and {@link AbilityGateService} owns the cast gate. The public API below is
 * unchanged for commands, listeners and GUI code.
 *
 * <p><b>Threading contract:</b> this plugin does not declare {@code folia-supported}, so every
 * access below is expected on the main thread. {@link #state} and {@link #roundNumber} are
 * {@code volatile} as cheap insurance for region-threaded listeners introduced by a future
 * Folia migration, but the scheduler/HUD/clock calls are <em>not</em> region-safe yet.</p>
 */
public final class BattlegroundManager {

    final ShenanigansCore plugin;
    final ArenaStore arenaStore;
    final ArenaSnapshotStore snapshotStore;
    final RestoreBudget budget;
    final ArenaRestoreEngine restoreEngine;
    final ArenaRestorer restorer;
    final StagingController staging;
    final ArenaInventories inventories = new ArenaInventories();
    final PlayerStore playerStore;
    final CombatTracker combat;
    final EconomyService economy;
    final ShopCatalog shopCatalog;
    final Set<UUID> deployed = ConcurrentHashMap.newKeySet();
    final DiveService dives = new DiveService();
    final SkyDrop skyDrop;
    final DropSiteSelector dropSelector;

    volatile RoundState state = RoundState.IDLE;
    volatile int roundNumber;
    final RoundClock clock = new RoundClock();
    long roundIdCounter;

    final RoundController rounds;
    final DeployService deploys;
    final CaptureService captures;
    final AbilityGateService abilities;

    public BattlegroundManager(ShenanigansCore plugin) {
        this.plugin = plugin;
        File folder = new File(plugin.getDataFolder(), "battleground");
        this.arenaStore = new ArenaStore(plugin, folder);
        // Restore the persisted arena map/region so a server restart does not require re-running /bg setup.
        this.arenaStore.load();
        // The arena world may not be auto-loaded on restart (e.g. a non-default world). Load it now
        // so region.world() resolves and capture/restore/protection work without a manual visit.
        ArenaRegion persisted = this.arenaStore.region();
        if (persisted != null && persisted.world() == null) {
            String worldName = persisted.worldName();
            if (worldName != null && !worldName.isBlank()) {
                java.io.File worldFolder = new java.io.File(org.bukkit.Bukkit.getWorldContainer(), worldName);
                if (worldFolder.isDirectory()) {
                    try {
                        org.bukkit.World loaded = org.bukkit.Bukkit.createWorld(new org.bukkit.WorldCreator(worldName));
                        if (loaded != null) {
                            plugin.getLogger().info("Loaded arena world '" + worldName + "' on enable.");
                        }
                    } catch (Throwable t) {
                        plugin.getLogger().log(java.util.logging.Level.WARNING,
                                "Could not load arena world '" + worldName + "'.", t);
                    }
                }
            }
        }
        this.snapshotStore = new ArenaSnapshotStore(
                new File(folder, "arena").toPath(), plugin.getLogger());
        this.budget = new RestoreBudget(BattlegroundDefaults.RESTORE_BUDGET_MS);

        // Services are constructed before the engine/drop hooks that capture their method refs;
        // each constructor only stores the facade, so no service observes half-built collaborators.
        this.rounds = new RoundController(this);
        this.deploys = new DeployService(this);
        this.captures = new CaptureService(this);
        this.abilities = new AbilityGateService(this);

        ArenaRestoreEngine tiered = null;
        if (BattlegroundDefaults.ENABLED) {
            tiered = new ArenaRestoreEngine(plugin, plugin.getLogger(), this.rounds::onTierOneGateOpen);
            tiered.prepare(arenaStore.region());
        }
        this.restoreEngine = tiered;
        this.restorer = tiered;

        this.staging = new StagingController(plugin);
        this.playerStore = new PlayerStore(plugin, folder);
        this.combat = new CombatTracker();
        this.shopCatalog = new ShopCatalog(plugin);
        this.shopCatalog.load(plugin.getAbilityRegistry());
        this.economy = new EconomyService(playerStore, combat, shopCatalog);
        this.skyDrop = new SkyDrop(plugin, dives, this.deploys::restageAfterLiquid,
                this.deploys::restageAfterAbort);
        // Convergence hooks: strong/low-HP players pull drops toward the fight, and the last
        // significant damager is treated as an avoider so a fresh victim is not re-dropped on them.
        this.dropSelector = new DropSiteSelector(deployed, uuid -> {
            com.hizeric.shenaniganscore.battleground.player.BattlegroundPlayer data = playerStore.get(uuid);
            org.bukkit.entity.Player other = Bukkit.getPlayer(uuid);
            double hp = 1.0;
            if (other != null && other.getMaxHealth() > 0.0) {
                hp = Math.max(0.0, Math.min(1.0, other.getHealth() / other.getMaxHealth()));
            }
            int kills = data == null ? 0 : data.kills();
            return 1.0 + 0.15 * Math.min(kills, 20) + 0.4 * (1.0 - hp);
        }, uuid -> combat.primary(uuid, BattlegroundDefaults.LAST_DAMAGER_WINDOW_SECONDS));
        this.clock.reset();
    }

    public void enable() {
        if (!BattlegroundDefaults.ENABLED) {
            plugin.getLogger().info("Battleground module is disabled.");
            return;
        }
        String provider = WorldEditSupport.provider();
        if (provider == null) {
            String enabled = WorldEditSupport.enabledProvider();
            if (enabled != null) {
                plugin.getLogger().warning("Found " + enabled + " but its WorldEdit API classes are not on "
                        + "shenanigans-core's classpath. Check that paper-plugin.yml lists the dependency "
                        + "under its exact plugin name with join-classpath: true. Arena setup and restore "
                        + "are disabled.");
            } else {
                plugin.getLogger().warning("No WorldEdit API provider found (WorldEdit / FastAsyncWorldEdit): "
                        + "arena setup and restore are disabled.");
            }
        } else {
            plugin.getLogger().info("WorldEdit API provider: " + provider);
        }

        playerStore.enable();
        // A restart clears the in-memory lock, so a leaked flight/invisibility flag must be found
        // from the player's actual state rather than from the lock that no longer exists.
        var arenaRegion = arenaStore.region();
        World arenaWorld = arenaRegion == null ? null : arenaRegion.world();
        for (Player online : Bukkit.getOnlinePlayers()) {
            boolean leaked = StagingContract.needsRepairAfterRestart(new StagingContract.LeakFlags(
                    arenaWorld != null && online.getWorld().equals(arenaWorld),
                    online.getAllowFlight(), online.isFlying(), online.isInvulnerable(),
                    online.isInvisible(), online.isCollidable(),
                    online.getGameMode() == GameMode.SPECTATOR,
                    online.hasPermission("shenaniganscore.battleground.admin")));
            if (leaked || BattlegroundLock.isStaged(online) || staging.hasSnapshot(online.getUniqueId())) {
                deploys.repair(online, true);
            }
        }
        abilities.register();

        rounds.startScheduler();
        rounds.restoreOnEnableIfNeeded();
        // Drop sites are chosen on demand from the live arena; no capture-time pool to rebuild.
        plugin.getLogger().info("Battleground enabled (rounds of "
                + ArenaRuntimeConfig.current().roundDurationSeconds() + "s).");
    }

    public void disable() {
        rounds.stopScheduler();
        rounds.flushDiffLedger(arenaStore.activeMapId());
        if (restoreEngine != null) restoreEngine.cancelTierTwo();
        if (restorer != null) restorer.cancel();
        arenaStore.releasePrewarm();
        staging.clear();
        for (Player online : Bukkit.getOnlinePlayers()) deploys.restoreInventory(online);
        inventories.clear();
        deployed.clear();
        dives.clear();
        combat.clearAll();
        playerStore.disable();
        // Leave the lifecycle in a known-good state so a re-enable (PlugManX / LifecycleManager)
        // cannot observe a stale RUNNING round with no deployed players.
        state = RoundState.IDLE;
        roundNumber = 0;
        clock.reset();
    }

    void announce(String miniMessage) {
        broadcast(miniMessage);
    }

    public void broadcast(String miniMessage) {
        Bukkit.broadcast(MiniMessage.miniMessage().deserialize(
                "<gray>[Battleground] <reset>" + miniMessage));
    }

    // ------------------------------------------------------------------ accessors

    public ArenaStore arenaStore() { return arenaStore; }
    public StagingController staging() { return staging; }
    public PlayerStore playerStore() { return playerStore; }
    public com.hizeric.shenaniganscore.battleground.player.lifecycle.PlayerStateRegistry registry() {
        return staging.registry();
    }
    public ArenaInventories inventories() { return inventories; }
    public CombatTracker combat() { return combat; }
    public EconomyService economy() { return economy; }
    public ShopCatalog shopCatalog() { return shopCatalog; }
    public RoundState state() { return state; }
    public int roundNumber() { return roundNumber; }
    public long remainingSeconds() { return clock.secondsRemaining(); }
    public boolean isDeployed(UUID uuid) { return deployed != null && deployed.contains(uuid); }
    public int deployedCount() { return deployed.size(); }
    public DiveService dives() { return dives; }
    public boolean worldEditReady() { return restorer != null; }
    public ArenaSnapshot loadSnapshotIfPresent(String mapId) {
        return rounds.loadSnapshotIfPresent(mapId);
    }

    // ------------------------------------------------------------------ delegates

    public void enterStage(Player player) { deploys.enterStage(player); }
    public void join(Player player) { deploys.join(player); }
    public void leave(Player player) { deploys.leave(player); }
    public void stageOnRespawn(Player player) { deploys.stageOnRespawn(player); }
    public void checkRegion(Player player) { deploys.checkRegion(player); }
    public void onQuit(Player player) { deploys.onQuit(player); }
    public void repair(Player player) { deploys.repair(player); }
    public void repair(Player player, boolean force) { deploys.repair(player, force); }

    public void beginReset() { rounds.beginReset(); }
    public boolean forceStart() { return rounds.forceStart(); }
    public void forceStop() { rounds.forceStop(); }
    public boolean forceRestore() { return rounds.forceRestore(); }
    public AdminRoundGate.Result adminRoundAction(CommandSender actor, AdminRoundGate.Action action,
                                                  int requestedSeconds) {
        return rounds.adminRoundAction(actor, action, requestedSeconds);
    }
    public void reloadConfig() { rounds.reloadConfig(); }
    public void reloadConfigAsync(java.util.function.Consumer<Boolean> onComplete) {
        rounds.reloadConfigAsync(onComplete);
    }

    public boolean capture() { return captures.capture(); }
    public boolean captureSnapshot() { return captures.captureSnapshot(); }
    public boolean captureSnapshot(String mapId) { return captures.captureSnapshot(mapId); }
    public boolean switchActiveMap(String id) { return captures.switchActiveMap(id); }

    public AbilityId heldItemAbility(Player player) { return abilities.heldItemAbility(player); }
    public ItemStack heldCustomItem(Player player) { return abilities.heldCustomItem(player); }
    public AbilityId heldItemAbility(Player player, ItemStack held) {
        return abilities.heldItemAbility(player, held);
    }
}
