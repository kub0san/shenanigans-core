package com.hizeric.shenaniganscore.battleground;

import com.hizeric.shenaniganscore.ShenanigansCore;
import com.hizeric.shenaniganscore.battleground.arena.ArenaEntryGuard;
import com.hizeric.shenaniganscore.battleground.arena.ArenaStore;
import com.hizeric.shenaniganscore.battleground.combat.CombatTracker;
import com.hizeric.shenaniganscore.battleground.deploy.DiveService;
import com.hizeric.shenaniganscore.battleground.menu.shell.MenuLatch;
import com.hizeric.shenaniganscore.battleground.player.ArenaInventories;
import com.hizeric.shenaniganscore.battleground.player.StagingController;
import com.hizeric.shenaniganscore.battleground.player.lifecycle.PlayerStateRegistry;
import com.hizeric.shenaniganscore.core.session.DomainSession;
import com.hizeric.shenaniganscore.core.session.SessionManager;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;
import sun.misc.Unsafe;

import java.lang.reflect.Field;
import java.nio.file.Path;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A domain-locked player must not escape via the bare {@code /spawn} leave alias:
 * {@code ActionLockListener} runs later at HIGHEST and can only cancel a command, not undo a
 * leave already performed at NORMAL. The real listener, manager and {@link Immobilize} gate are
 * driven with MockBukkit; only the final classes' observed fields are installed reflectively
 * (the same fixture shape as DeployServiceLeaveTest).
 */
class BattlegroundListenerSpawnGuardTest {

    private static final Unsafe UNSAFE = unsafe();

    private ServerMock server;
    private ShenanigansCore plugin;
    private BattlegroundManager manager;
    private SessionManager sessions;
    private PlayerMock player;
    private Set<UUID> deployed;
    private Field instanceField;
    private ShenanigansCore previousInstance;

    @BeforeEach
    void setUp(@TempDir Path temp) throws Exception {
        server = MockBukkit.mock();
        JavaPlugin testPlugin = (JavaPlugin) MockBukkit.createMockPlugin("SpawnGuardTest");
        plugin = (ShenanigansCore) UNSAFE.allocateInstance(ShenanigansCore.class);
        JavaPlugin meta = (JavaPlugin) MockBukkit.createMockPlugin("SpawnGuardMeta");
        put(plugin, "pluginMeta", meta.getPluginMeta());

        manager = (BattlegroundManager) UNSAFE.allocateInstance(BattlegroundManager.class);
        put(manager, "plugin", plugin);
        put(manager, "arenaStore", new ArenaStore(testPlugin, temp.toFile()));
        put(manager, "staging", new StagingController(plugin, new PlayerStateRegistry(plugin)));
        put(manager, "inventories", new ArenaInventories());
        put(manager, "combat", new CombatTracker());
        put(manager, "dives", new DiveService());
        deployed = ConcurrentHashMap.newKeySet();
        put(manager, "deployed", deployed);
        DeployService deploys = (DeployService) UNSAFE.allocateInstance(DeployService.class);
        put(deploys, "m", manager);
        put(manager, "deploys", deploys);

        put(plugin, "battlegroundManager", manager);
        sessions = new SessionManager();
        put(plugin, "sessionManager", sessions);
        instanceField = ShenanigansCore.class.getDeclaredField("instance");
        instanceField.setAccessible(true);
        previousInstance = (ShenanigansCore) instanceField.get(null);
        instanceField.set(null, plugin);

        player = server.addPlayer();
        // OP grants the command-policy bypass, so only the /spawn alias can cancel this event.
        player.setOp(true);
        deployed.add(player.getUniqueId());
    }

    @AfterEach
    void tearDown() throws Exception {
        if (player != null) {
            ArenaEntryGuard.get().clearTransit(player.getUniqueId());
            MenuLatch.get().clear(player);
        }
        if (instanceField != null) instanceField.set(null, previousInstance);
        MockBukkit.unmock();
    }

    @Test
    void lockedPlayerIsNotInterceptedAndStaysDeployed() throws Exception {
        installLockSession();

        PlayerCommandPreprocessEvent event = new PlayerCommandPreprocessEvent(player, "/spawn");
        new BattlegroundListener(plugin).onCommand(event);

        assertFalse(event.isCancelled(), "the domain lock wins over the /spawn alias");
        assertTrue(manager.isDeployed(player.getUniqueId()), "leave must not run while locked");
    }

    @Test
    void unlockedPlayerIsStillIntercepted() {
        PlayerCommandPreprocessEvent event = new PlayerCommandPreprocessEvent(player, "/spawn");
        new BattlegroundListener(plugin).onCommand(event);

        assertTrue(event.isCancelled(), "the alias still cancels the vanilla /spawn");
        assertFalse(manager.isDeployed(player.getUniqueId()), "leave runs when no domain lock is active");
    }

    /** A session whose lock answer comes from the live map Immobilize reads, without a domain timeline. */
    private void installLockSession() throws Exception {
        DomainSession session = new DomainSession(player, player.getLocation(), 1.0) {
            @Override
            public boolean isMovementLocked(Player candidate) {
                return candidate.equals(player);
            }

            @Override
            public void close() {
            }
        };
        Field active = SessionManager.class.getDeclaredField("activeSessions");
        active.setAccessible(true);
        @SuppressWarnings("unchecked")
        Map<UUID, DomainSession> map = (Map<UUID, DomainSession>) active.get(sessions);
        map.put(player.getUniqueId(), session);
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
        UNSAFE.putObject(target, UNSAFE.objectFieldOffset(field), value);
    }

    private static Unsafe unsafe() {
        try {
            Field field = Unsafe.class.getDeclaredField("theUnsafe");
            field.setAccessible(true);
            return (Unsafe) field.get(null);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Unsafe is required by the test harness", e);
        }
    }
}
