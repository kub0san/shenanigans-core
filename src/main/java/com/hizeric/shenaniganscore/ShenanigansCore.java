package com.hizeric.shenaniganscore;

import com.hizeric.shenaniganscore.battleground.BattlegroundListener;
import com.hizeric.shenaniganscore.battleground.BattlegroundManager;
import com.hizeric.shenaniganscore.battleground.RoundState;
import com.hizeric.shenaniganscore.battleground.arena.ArenaProtection;
import com.hizeric.shenaniganscore.battleground.arena.ArenaEntryGuard;
import com.hizeric.shenaniganscore.battleground.arena.ArenaRuntimeConfig;
import com.hizeric.shenaniganscore.battleground.arena.diff.AsyncDiffWriter;
import com.hizeric.shenaniganscore.battleground.arena.diff.DiffRecorder;
import com.hizeric.shenaniganscore.battleground.arena.WorldEditBridge;
import com.hizeric.shenaniganscore.battleground.arena.WorldEditSupport;
import com.hizeric.shenaniganscore.battleground.command.AbilityCommand;
import com.hizeric.shenaniganscore.battleground.command.BattlegroundCommand;
import com.hizeric.shenaniganscore.battleground.command.Cmd;
import com.hizeric.shenaniganscore.battleground.command.CommandNode;
import com.hizeric.shenaniganscore.battleground.command.CommandRegistrar;
import com.hizeric.shenaniganscore.battleground.command.Permissions;
import com.hizeric.shenaniganscore.battleground.command.SetupSession;
import com.hizeric.shenaniganscore.battleground.combat.KillCreditListener;
import com.hizeric.shenaniganscore.battleground.indicator.IndicatorRouter;
import com.hizeric.shenaniganscore.battleground.loadout.GlobalKitService;
import com.hizeric.shenaniganscore.battleground.menu.MenuListener;
import com.hizeric.shenaniganscore.battleground.menu.MenuManager;
import com.hizeric.shenaniganscore.battleground.menu.base.GuiChatPrompt;
import com.hizeric.shenaniganscore.battleground.menu.base.GuiLayoutChecks;
import com.hizeric.shenaniganscore.battleground.menu.base.GuiScreen;
import com.hizeric.shenaniganscore.battleground.menu.base.GuiStyle;
import com.hizeric.shenaniganscore.battleground.menu.shell.BattlegroundGui;
import com.hizeric.shenaniganscore.battleground.menu.shell.MenuLatch;
import com.hizeric.shenaniganscore.battleground.storage.Database;
import com.hizeric.shenaniganscore.battleground.storage.DatabaseException;
import com.hizeric.shenaniganscore.battleground.storage.Databases;
import com.hizeric.shenaniganscore.core.ability.AbilityCaster;
import com.hizeric.shenaniganscore.core.ability.AbilityRegistry;
import com.hizeric.shenaniganscore.core.cooldown.CooldownManager;
import com.hizeric.shenaniganscore.core.config.AbilityTuningConfig;
import com.hizeric.shenaniganscore.core.config.ConfigAutoUpdater;
import com.hizeric.shenaniganscore.core.display.CameraShake;
import com.hizeric.shenaniganscore.battleground.shop.ShopPolicyConfig;
import com.hizeric.shenaniganscore.core.domain.DomainRegistry;
import com.hizeric.shenaniganscore.core.domain.DomainRegionGuard;
import com.hizeric.shenaniganscore.core.terrain.DomainFloorRestoreLedger;
import com.hizeric.shenaniganscore.core.network.PacketEventsBridge;
import com.hizeric.shenaniganscore.core.network.PacketEventsTransport;
import com.hizeric.shenaniganscore.core.session.SessionManager;
import com.hizeric.shenaniganscore.core.util.ClientBlockUtils;
import com.hizeric.shenaniganscore.listeners.ActionLockListener;
import com.hizeric.shenaniganscore.listeners.DomainListener;
import com.hizeric.shenaniganscore.listeners.LifecycleListener;
import com.hizeric.shenaniganscore.battleground.player.lifecycle.PlayerLifecycleListener;
import com.hizeric.shenaniganscore.listeners.MovementListener;
import com.hizeric.shenaniganscore.origins.jujutsukaisen.JujutsuAbilities;
import com.hizeric.shenaniganscore.origins.minecraft.MinecraftAbilities;
import com.hizeric.shenaniganscore.origins.minecraft.weapons.dashsword.DashVisibilityMask;
import com.hizeric.shenaniganscore.origins.minecraft.weapons.dashsword.MannequinEchoRenderer;
import com.hizeric.shenaniganscore.resourcepack.ResourcePackManager;
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.HandlerList;
import org.bukkit.permissions.Permission;
import org.bukkit.permissions.PermissionDefault;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.logging.Level;

public final class ShenanigansCore extends JavaPlugin {

    private static volatile ShenanigansCore instance;

    /** Replay guard: PlugManX can call enable/disable more than once on the same instance. */
    private final AtomicBoolean enabled = new AtomicBoolean();
    /**
     * Paper keeps a plugin's COMMANDS lifecycle handler for the lifetime of the plugin instance.
     * Registering a second handler on a same-instance re-enable would register /bg and /ability
     * twice when PlugManX replays the lifecycle, so this flag is deliberately never cleared on
     * disable. A fresh instance (fresh classloader) starts with it false and registers normally.
     */
    private boolean commandsRegistered;

    private SessionManager sessionManager;
    private CooldownManager cooldownManager;
    private AbilityRegistry abilityRegistry;
    private AbilityCaster abilityCaster;
    private BattlegroundManager battlegroundManager;
    private DiffRecorder diffRecorder;
    private AsyncDiffWriter diffWriter;
    private GlobalKitService globalKitService;
    private IndicatorRouter indicatorRouter;
    private BattlegroundGui battlegroundGui;
    private ResourcePackManager resourcePackManager;

    public BattlegroundGui battlegroundGui() {
        return battlegroundGui;
    }

    public ResourcePackManager resourcePackManager() {
        return resourcePackManager;
    }

    /** MONITOR diff recorder; Task 8 swaps its bounds supplier when the active map changes. */
    public DiffRecorder diffRecorder() {
        return diffRecorder;
    }

    /** Async single writer; Task 8 opens/flushes it at round boundaries. */
    public AsyncDiffWriter diffWriter() {
        return diffWriter;
    }

    /** Live ability tuning, swapped by {@link #reloadTuning()}. */
    public AbilityTuningConfig abilityTuning() {
        return AbilityTuningConfig.current();
    }

    /** Live shop policy, swapped by {@link #reloadTuning()}. */
    public ShopPolicyConfig shopPolicy() {
        return ShopPolicyConfig.current();
    }

    /** Live arena/round policy, swapped by {@link #reloadTuning()}. */
    public ArenaRuntimeConfig arenaConfig() {
        return ArenaRuntimeConfig.current();
    }

    /** Re-reads both managed YAMLs after the auto-updater has merged their bundled keys. */
    public void reloadTuning() {
        File battlegroundConfig = new File(new File(getDataFolder(), "battleground"), "config.yml");
        AbilityTuningConfig.load(new File(getDataFolder(), "config.yml"), getLogger());
        ShopPolicyConfig.load(battlegroundConfig, getLogger());
        ArenaRuntimeConfig.load(battlegroundConfig, getLogger());
    }

    /**
     * Enable-time transport install, extracted so the readiness-before-install contract is
     * observable in a unit test without a Plugin instance or server. {@link #onEnable()} passes the
     * real guard and installer; the test passes recorders. No production behavior is changed.
     */
    static void installBlockChangeTransport(
            Runnable readinessCheck, Consumer<ClientBlockUtils.BlockChangeTransport> installer) {
        readinessCheck.run();
        installer.accept(new PacketEventsTransport());
    }

    @Override
    public void onEnable() {
        // A PlugManX reload may replay enable on an instance that is already running. Refuse the
        // duplicate so listeners, the HTTP server, scheduled tasks and the command handler are not
        // installed twice; every install below is also safe to re-run after a clean disable.
        if (!enabled.compareAndSet(false, true)) {
            getLogger().warning("onEnable() called while already enabled; ignoring duplicate enable.");
            return;
        }
        instance = this;
        // Optional-provider caches are per-enable: a same-classloader re-enable must not inherit
        // the previous enable's class-visibility probe or its cached schematic format.
        WorldEditSupport.reset();
        // WorldEdit is optional — the bridge class references the WorldEdit API, so it
        // must never be loaded when the provider is absent (the plugin must still boot).
        if (WorldEditSupport.isAvailable()) {
            WorldEditBridge.setEnabled(true);
            WorldEditBridge.clearCachedFormat();
        }
        registerPermissions();

        // PacketEvents is a hard dependency (paper-plugin.yml: required: true, load: BEFORE).
        // The prebuilt PacketEvents plugin owns the API singleton — we never setAPI — so here
        // we only verify readiness and fail loudly instead of running half a plugin (D5).
        if (PacketEventsBridge.isReady()) {
            getLogger().info("PacketEvents " + PacketEventsBridge.version() + " ready (hard dependency).");
        } else {
            getLogger().severe("PacketEvents is not initialized. shenanigans-core cannot run without it. "
                    + "Install the PacketEvents plugin and restart. Disabling.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        // Targeted fake-block overlays go out as PacketEvents block-change packets rather than
        // whole-chunk refreshes. The readiness gate above already guarantees the singleton; the
        // extracted install helper still calls requireReady() explicitly and installs the transport
        // only after it returns, and the transport re-checks on every send so a later teardown
        // fails loudly instead of silently dropping overlays.
        installBlockChangeTransport(PacketEventsBridge::requireReady, ClientBlockUtils::setTransport);

        // Config stubs are maintained before anything reads a value; then the live tuning holders
        // read the merged files so every cast-time lookup sees the admin's values from boot.
        new ConfigAutoUpdater(this).run();
        reloadTuning();
        GuiStyle.apply(getDataFolder(), getLogger());
        this.resourcePackManager = new ResourcePackManager(this);
        this.resourcePackManager.enable();

        // SQL is a hard dependency: storage-backed services below resolve Databases.require(),
        // so a failed open must disable the plugin rather than leave a half-initialised store.
        try {
            Databases.install(Database.open(new File(getDataFolder(), "database.db")));
        } catch (DatabaseException e) {
            getLogger().log(Level.SEVERE, "Could not open SQL database; disabling.", e);
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        this.globalKitService = new GlobalKitService(this);
        this.globalKitService.load();

        this.sessionManager = new SessionManager();
        // Issue #35: hand back any domain floor blocks a previous shutdown could not restore
        // because their chunks were unloaded. Replay is scheduled, so it waits for worlds to load.
        DomainFloorRestoreLedger.replay(this, getLogger());
        this.cooldownManager = new CooldownManager();
        // Sweep finished cooldown entries periodically so the keyed maps stay bounded on long
        // uptimes (F-48). cancelTasks(this) in onDisable tears this task down with the plugin.
        Bukkit.getScheduler().runTaskTimer(this, () -> {
            CooldownManager manager = cooldownManager;
            if (manager != null) manager.clearExpired();
        }, 20L * 60L, 20L * 60L);

        // Ability catalogue. Casts are gated by cooldowns and, inside the mode, by
        // battleground catalog ownership and streak — never by a mana bar.
        this.abilityRegistry = new AbilityRegistry(message -> getLogger().warning(message));
        this.abilityCaster = new AbilityCaster(abilityRegistry, cooldownManager, getLogger());
        JujutsuAbilities.register(abilityRegistry, readTechniqueFxEnabled(getDataFolder(), getLogger()));
        MinecraftAbilities.register(abilityRegistry, this);
        getLogger().info("Registered " + abilityRegistry.size() + " abilities.");

        this.battlegroundManager = new BattlegroundManager(this);
        // Durable diff ledger (Task 4). The writer must exist before the manager enables:
        // enable() can open the first round synchronously (tier-1 gate fires immediately when
        // the diff is empty), and openDiffLedger() resolves plugin.diffWriter() at that moment.
        this.diffWriter = new AsyncDiffWriter(
                new File(new File(getDataFolder(), "battleground"), "arena").toPath(), getLogger());
        var arenaStore = battlegroundManager.arenaStore();
        this.diffRecorder = new DiffRecorder(
                () -> {
                    var region = arenaStore.region();
                    return region == null ? null : DiffRecorder.boundsOf(region);
                },
                DiffRecorder.domainClaims(),
                diffWriter::enqueue);
        this.battlegroundManager.enable();

        // Battleground GUI foundation: layout invariants + the shared session.
        GuiLayoutChecks.verify();
        this.battlegroundGui = new BattlegroundGui(this);
        this.battlegroundGui.register();
        this.indicatorRouter = new IndicatorRouter(this);

        registerListeners();
        registerCommands();

        // PlugManX 3.1+ replays Paper's COMMANDS lifecycle on reload, but dynamic Paper plugin
        // enabling is still best-effort. Point admins at the supported path.
        var pluginManager = Bukkit.getPluginManager();
        if (pluginManager.getPlugin("PlugManX") != null
                || pluginManager.getPlugin("PlugMan") != null) {
            getLogger().warning("PlugManX detected. Runtime reloads of shenanigans-core are "
                    + "best-effort only; a full server restart is strongly recommended "
                    + "instead of /plugman reload shenanigans-core.");
        }

        getLogger().info("ShenanigansCore has been enabled!");
    }

    /**
     * Registers one parent node for normal players and one for admins, with the leaf nodes as
     * Bukkit permission children. LuckPerms resolves children, so granting
     * {@code shenaniganscore.player} is enough for everything player-facing.
     */
    private void registerPermissions() {
        addPermission("shenaniganscore.use",
                "Use battleground commands and abilities", PermissionDefault.TRUE, null);
        addPermission("shenaniganscore.battleground.play",
                "Join and leave the battleground", PermissionDefault.TRUE, null);
        addPermission("shenaniganscore.player",
                "Everything a normal player needs",
                PermissionDefault.TRUE,
                Map.of("shenaniganscore.use", true,
                        "shenaniganscore.battleground.play", true));

        addPermission("shenaniganscore.battleground.kit",
                "Edit the shared server loadout", PermissionDefault.OP, null);
        addPermission("shenaniganscore.battleground.admin",
                "Set up the arena and control rounds", PermissionDefault.OP,
                Map.of("shenaniganscore.battleground.kit", true));
        addPermission("shenaniganscore.admin",
                "Everything an arena admin needs",
                PermissionDefault.OP,
                Map.of("shenaniganscore.battleground.admin", true));
    }

    private static void addPermission(String node, String description,
                                      PermissionDefault def, Map<String, Boolean> children) {
        Permission permission = Bukkit.getPluginManager().getPermission(node);
        if (permission == null) {
            permission = new Permission(node, description, def);
            Bukkit.getPluginManager().addPermission(permission);
        }
        if (children != null) {
            try {
                permission.getChildren().putAll(children);
                // Bukkit does not push child changes to already-registered Permissibles. Without
                // this, players online across a reload keep the old effective permission set.
                permission.recalculatePermissibles();
            } catch (UnsupportedOperationException e) {
                instance.getLogger().warning("Could not attach children to permission " + node
                        + "; grant the leaf nodes directly if needed.");
            }
        }
    }

    private void registerListeners() {
        getServer().getPluginManager().registerEvents(new LifecycleListener(), this);
        getServer().getPluginManager().registerEvents(new DomainListener(), this);
        getServer().getPluginManager().registerEvents(new MovementListener(), this);
        getServer().getPluginManager().registerEvents(new ActionLockListener(), this);
        getServer().getPluginManager().registerEvents(new DomainRegionGuard(), this);
        getServer().getPluginManager().registerEvents(new BattlegroundListener(this), this);
        if (battlegroundManager != null) {
            getServer().getPluginManager().registerEvents(
                    new PlayerLifecycleListener(this, battlegroundManager.registry(),
                            battlegroundManager.playerStore(), battlegroundManager.staging()), this);
        }
        getServer().getPluginManager().registerEvents(new KillCreditListener(this), this);
        getServer().getPluginManager().registerEvents(new MenuListener(this), this);
        if (diffRecorder != null) {
            getServer().getPluginManager().registerEvents(diffRecorder, this);
        }
        ArenaProtection.get().register(this);
        ArenaEntryGuard.get().register(this);
        // Casting is command-only (/ability 1..3); no item, keybind or sneak input listeners.
    }

    private void registerCommands() {
        // The lifecycle handler survives a same-instance disable/enable, and PlugManX replays the
        // COMMANDS lifecycle on load. Without this guard the roots would be registered twice.
        if (commandsRegistered) return;
        // One declarative tree per root; the same tree drives Brigadier, help and startup validation.
        List<CommandNode> roots = List.of(
                new BattlegroundCommand(this).root(),
                new AbilityCommand(this).root(),
                leaveRoot(this));

        List<String> problems = new CommandRegistrar().validate(roots);
        if (!problems.isEmpty()) {
            throw new IllegalStateException("Invalid command tree:\n  " + String.join("\n  ", problems));
        }

        CommandRegistrar registrar = new CommandRegistrar();
        commandsRegistered = true;
        this.getLifecycleManager().registerEventHandler(LifecycleEvents.COMMANDS, event -> {
            for (CommandNode root : roots) {
                event.registrar().register(registrar.build(root), root.description(), root.aliases());
            }
        });
    }

    /** {@code /leave} is a separate root, not an alias, so it works outside {@code /bg} too. */
    public static CommandNode leaveRoot(ShenanigansCore plugin) {
        return Cmd.root("leave", "Return to the battleground hub", Permissions.USE,
                CommandNode.Group.PLAYER)
                .runs(ctx -> {
                    Player p = ctx.player();
                    if (p != null) plugin.getBattlegroundManager().leave(p);
                })
                .build();
    }

    @Override
    public void onDisable() {
        // PlugManX can invoke disable twice in one reload cycle. Every step below is idempotent,
        // but a second pass would still repeat logs and teardown, so replay is a no-op.
        if (!enabled.compareAndSet(true, false)) return;
        // A reload mid-fight must never be silent: the graceful steps below close live state, but
        // admins still need to know that a restart is the supported path for an active domain/round.
        if ((sessionManager != null && sessionManager.hasActiveSessions())
                || (battlegroundManager != null && battlegroundManager.state() != RoundState.IDLE)) {
            getLogger().warning("Disabling with a live domain or round; active state is being closed "
                    + "before teardown. A full server restart is still safer than a mid-fight reload.");
        }
        // Defensive backstop for same-classloader reloads: Bukkit normally unregisters listeners in
        // disablePlugin(), but doing it here makes the seam symmetric and guarantees no handler
        // from this instance can observe a half-torn-down plugin.
        runSafely("listeners", () -> HandlerList.unregisterAll(this));
        // Graceful order: domains release their own blocks first, then shared
        // visual systems, then a hard flag/task sweep for PlugManX-style reloads. Each
        // step is isolated so one failure cannot strand the rest of the teardown.
        runSafely("resource pack", () -> {
            if (resourcePackManager != null) resourcePackManager.disable();
        });
        runSafely("battleground", () -> {
            if (battlegroundManager != null) battlegroundManager.disable();
        });
        runSafely("diff ledger", () -> {
            if (diffWriter != null) diffWriter.shutdown();
        });
        // Cancel any in-progress /bg setup wizards so they cannot outlive the plugin (F-48).
        // Use lambda bodies (not method references) so class resolution happens inside runSafely;
        // PlugManX reloads can leave the classloader in a state where eager resolution throws.
        runSafely("setup sessions", () -> SetupSession.cancelAll());
        // Must follow the battleground step: its sync flush still writes to the pool.
        runSafely("database", () -> {
            try {
                Databases.closeAndClear();
            } catch (DatabaseException e) {
                getLogger().log(Level.SEVERE, "Could not close SQL database", e);
            }
        });
        runSafely("domains", () -> {
            if (sessionManager != null) sessionManager.stopAll();
        });
        runSafely("client block overlays", () -> ClientBlockUtils.shutdown());
        // Client-side mannequin echoes live only in PacketEvents packets; a reload mid-dash would
        // otherwise strand them (and their glow team) on every viewer until relog.
        runSafely("mannequin echoes", () -> MannequinEchoRenderer.shutdown());
        // Same reason as the echoes: cancelTasks below kills dash timelines without running
        // their clear(), so any live visibility mask must be restored first.
        runSafely("dash visibility", () -> DashVisibilityMask.shutdown());
        runSafely("gui", () -> {
            if (battlegroundGui != null) battlegroundGui.shutdown();
        });
        runSafely("menu state", () -> {
            MenuManager.get().clearAll();
            MenuLatch.get().clearAll();
            GuiChatPrompt.shutdown();
            GuiScreen.clearRequired();
        });
        runSafely("domain claims", () -> DomainRegistry.get().clearAll());
        // A reload mid-shake would otherwise cancel the driver task and leave the player's
        // rotation stuck at the last shaken yaw/pitch until relog.
        runSafely("camera shake", () -> CameraShake.restoreAll());
        // Only touch the WorldEdit bridge when the provider is actually present; loading the
        // bridge class while WorldEdit is absent would crash a plugin-less shutdown (F-37).
        runSafely("worldedit bridge", () -> {
            if (WorldEditSupport.isAvailable()) WorldEditBridge.setEnabled(false);
        });

        // Release the replay latch explicitly: cancelTasks alone would leave the static guard set
        // and a same-classloader re-enable could never replay the pending ledger (issue #35).
        DomainFloorRestoreLedger.cancelReplay();
        // Final safety net: cancel anything that outlived its owner's teardown. This runs
        // after graceful close so it never cuts a domain's floor restore short.
        Bukkit.getScheduler().cancelTasks(this);

        battlegroundManager = null;
        diffRecorder = null;
        diffWriter = null;
        sessionManager = null;
        cooldownManager = null;
        abilityRegistry = null;
        abilityCaster = null;
        globalKitService = null;
        indicatorRouter = null;
        battlegroundGui = null;
        instance = null;

        getLogger().info("ShenanigansCore has been disabled.");
    }

    private void runSafely(String step, Runnable action) {
        try {
            action.run();
        } catch (Throwable t) {
            getLogger().log(Level.SEVERE,
                    "ShenanigansCore teardown step '" + step + "' failed; continuing.", t);
        }
    }

    /** Reads the {@code technique-fx.enabled} flag from {@code config.yml}; defaults to {@code true}. */
    public static boolean readTechniqueFxEnabled(java.io.File dataFolder, java.util.logging.Logger logger) {
        if (dataFolder == null) return true;
        java.io.File configFile = new java.io.File(dataFolder, "config.yml");
        if (!configFile.isFile()) return true;
        try {
            org.spongepowered.configurate.yaml.YamlConfigurationLoader loader =
                    org.spongepowered.configurate.yaml.YamlConfigurationLoader.builder()
                            .path(configFile.toPath()).build();
            org.spongepowered.configurate.ConfigurationNode root = loader.load();
            return root.node("technique-fx", "enabled").getBoolean(true);
        } catch (Exception e) {
            if (logger != null) logger.warning("Could not read technique-fx.enabled; defaulting to true: " + e.getMessage());
            return true;
        }
    }

    public static ShenanigansCore getInstance() {
        return instance;
    }

    /**
     * Shutdown-safe scheduling helpers.
     *
     * <p>During plugin disable {@link #getInstance()} becomes null, so calling
     * {@code Bukkit.getScheduler().runTask*(ShenanigansCore.getInstance(), ...)}
     * throws an NPE before the task is even queued. These helpers no-op when the
     * instance is gone, so a teardown-time scheduling attempt simply does nothing
     * instead of crashing the caller. They are thin wrappers and carry no extra
     * behaviour beyond the null guard.</p>
     */
    public static org.bukkit.scheduler.BukkitTask runTask(java.util.function.Consumer<ShenanigansCore> ignored, Runnable task) {
        ShenanigansCore p = getInstance();
        if (p == null) return null;
        return org.bukkit.Bukkit.getScheduler().runTask(p, task);
    }

    public static org.bukkit.scheduler.BukkitTask runTask(Runnable task) {
        ShenanigansCore p = getInstance();
        if (p == null) return null;
        return org.bukkit.Bukkit.getScheduler().runTask(p, task);
    }

    public static org.bukkit.scheduler.BukkitTask runTaskLater(Runnable task, long delayTicks) {
        ShenanigansCore p = getInstance();
        if (p == null) return null;
        return org.bukkit.Bukkit.getScheduler().runTaskLater(p, task, delayTicks);
    }

    public static org.bukkit.scheduler.BukkitTask runTaskTimer(Runnable task, long delayTicks, long periodTicks) {
        ShenanigansCore p = getInstance();
        if (p == null) return null;
        return org.bukkit.Bukkit.getScheduler().runTaskTimer(p, task, delayTicks, periodTicks);
    }

    public SessionManager getSessionManager() {
        return sessionManager;
    }

    public CooldownManager getCooldownManager() {
        return cooldownManager;
    }

    public AbilityRegistry getAbilityRegistry() {
        return abilityRegistry;
    }

    public AbilityCaster getAbilityCaster() {
        return abilityCaster;
    }

    public GlobalKitService globalKitService() { return globalKitService; }

    public IndicatorRouter indicatorRouter() {
        return indicatorRouter;
    }

    public BattlegroundManager getBattlegroundManager() {
        return battlegroundManager;
    }
}
