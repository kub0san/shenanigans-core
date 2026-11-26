package com.hizeric.shenaniganscore.battleground;

import com.hizeric.shenaniganscore.ShenanigansCore;
import com.hizeric.shenaniganscore.battleground.arena.ArenaEntryGuard;
import com.hizeric.shenaniganscore.battleground.arena.ArenaRegion;
import com.hizeric.shenaniganscore.battleground.arena.ArenaStore;
import com.hizeric.shenaniganscore.battleground.combat.CombatTracker;
import com.hizeric.shenaniganscore.battleground.deploy.DiveService;
import com.hizeric.shenaniganscore.battleground.player.ArenaInventories;
import com.hizeric.shenaniganscore.battleground.player.StagingController;
import com.hizeric.shenaniganscore.battleground.player.lifecycle.PlayerStateRegistry;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.WorldCreator;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.util.BlockVector;
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
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Issue #64: the legitimate {@code /bg leave} exit must not be mistaken for an exploit by the
 * teleport guard. The real {@link DeployService#leave(Player)} path is driven with real MockBukkit
 * teleport events: the exit teleport goes through, no ejection is queued or messaged, no permit
 * leaks, and the normal leave feedback is still sent. The final classes are allocated without a
 * constructor and their few observed fields installed reflectively, exactly as the guard's own
 * behaviour test does — production code is unmodified by the harness.
 */
class DeployServiceLeaveTest {

    private static final PlainTextComponentSerializer PLAIN = PlainTextComponentSerializer.plainText();
    private static final Unsafe UNSAFE = unsafe();

    private ServerMock server;
    private ArenaEntryGuard guard;
    private DeployService service;
    private PlayerMock player;
    private Location inside;
    private Location outside;
    private Set<UUID> deployed;
    private Map<UUID, Long> transit;
    private Set<UUID> ejecting;
    private Field instanceField;
    private ShenanigansCore previousInstance;

    @BeforeEach
    void setUp(@TempDir Path temp) throws Exception {
        server = MockBukkit.mock();
        JavaPlugin testPlugin = (JavaPlugin) MockBukkit.createMockPlugin("DeployServiceLeaveTest");
        World world = server.createWorld(new WorldCreator("deploy-leave-world"));
        inside = new Location(world, 15, 67, 25);
        outside = new Location(world, 0, 67, 0);
        ArenaRegion region = new ArenaRegion(world.getUID(), world.getName(),
                new BlockVector(10, 64, 20), new BlockVector(20, 70, 30), 1L, 100L, "test")
                .spawn(outside);
        ArenaStore store = new ArenaStore(testPlugin, temp.toFile());
        store.maps().put("default", region);

        ShenanigansCore plugin = (ShenanigansCore) UNSAFE.allocateInstance(ShenanigansCore.class);
        JavaPlugin meta = (JavaPlugin) MockBukkit.createMockPlugin("ShenanigansCoreLeaveMeta");
        put(plugin, "pluginMeta", meta.getPluginMeta());
        BattlegroundManager manager =
                (BattlegroundManager) UNSAFE.allocateInstance(BattlegroundManager.class);
        put(manager, "plugin", plugin);
        put(manager, "arenaStore", store);
        put(manager, "staging", new StagingController(plugin, new PlayerStateRegistry(plugin)));
        put(manager, "inventories", new ArenaInventories());
        put(manager, "combat", new CombatTracker());
        put(manager, "dives", new DiveService());
        deployed = ConcurrentHashMap.newKeySet();
        put(manager, "deployed", deployed);

        Field managerField = ShenanigansCore.class.getDeclaredField("battlegroundManager");
        managerField.setAccessible(true);
        managerField.set(plugin, manager);
        instanceField = ShenanigansCore.class.getDeclaredField("instance");
        instanceField.setAccessible(true);
        previousInstance = (ShenanigansCore) instanceField.get(null);
        instanceField.set(null, plugin);

        guard = ArenaEntryGuard.get();
        server.getPluginManager().registerEvents(guard, testPlugin);
        service = new DeployService(manager);
        player = server.addPlayer();
        player.setLocation(inside); // setLocation fires no teleport event

        transit = fieldMap("internalTransit");
        ejecting = fieldSet("ejecting");
    }

    @AfterEach
    void tearDown() throws Exception {
        if (guard != null) {
            transit.clear();
            ejecting.clear();
            fieldMap("lastDenied").clear();
            fieldMap("lastEjected").clear();
        }
        if (instanceField != null) instanceField.set(null, previousInstance);
        MockBukkit.unmock();
    }

    @SuppressWarnings("unchecked")
    private Map<UUID, Long> fieldMap(String name) throws Exception {
        Field field = ArenaEntryGuard.class.getDeclaredField(name);
        field.setAccessible(true);
        return (Map<UUID, Long>) field.get(guard);
    }

    @SuppressWarnings("unchecked")
    private Set<UUID> fieldSet(String name) throws Exception {
        Field field = ArenaEntryGuard.class.getDeclaredField(name);
        field.setAccessible(true);
        return (Set<UUID>) field.get(guard);
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
            throw new ExceptionInInitializerError(e);
        }
    }

    private List<String> drainMessages() {
        List<String> messages = new ArrayList<>();
        for (Component component = player.nextComponentMessage();
             component != null;
             component = player.nextComponentMessage()) {
            messages.add(PLAIN.serialize(component));
        }
        return messages;
    }

    @Test
    void aDeployedLeaveIsAllowedOutWithoutAnEjection() {
        deployed.add(player.getUniqueId());

        service.leave(player);

        assertFalse(guard.isEjectionPending(player.getUniqueId()),
                "a legitimate leave must not queue the stranded-player exit");
        assertFalse(guard.hasTransit(player.getUniqueId()),
                "no transit permit may leak from the exit");
        assertEquals(outside.getBlockX(), player.getLocation().getBlockX(),
                "the outward teleport must actually go through");
        assertEquals(outside.getBlockZ(), player.getLocation().getBlockZ());
        assertEquals(List.of("You left the battleground."), drainMessages(),
                "only the normal leave feedback, with no ejection message");
        assertFalse(deployed.contains(player.getUniqueId()), "the roster entry is removed");

        server.getScheduler().performTicks(1);
        assertFalse(guard.isEjectionPending(player.getUniqueId()),
                "no delayed ejection may fire after the leave");
        assertEquals(outside.getBlockZ(), player.getLocation().getBlockZ());
        assertTrue(drainMessages().isEmpty(), "no late ejection feedback either");
    }

    @Test
    void aLeaveFromOutsideTheArenaIsStillAllowed() {
        player.setLocation(outside);
        deployed.add(player.getUniqueId());

        service.leave(player);

        assertFalse(guard.isEjectionPending(player.getUniqueId()));
        assertFalse(guard.hasTransit(player.getUniqueId()));
        assertEquals(List.of("You left the battleground."), drainMessages());
    }
}
