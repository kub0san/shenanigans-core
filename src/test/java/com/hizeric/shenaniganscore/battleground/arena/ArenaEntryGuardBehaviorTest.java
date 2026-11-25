package com.hizeric.shenaniganscore.battleground.arena;

import com.hizeric.shenaniganscore.ShenanigansCore;
import com.hizeric.shenaniganscore.battleground.BattlegroundManager;
import com.hizeric.shenaniganscore.battleground.player.StagingController;
import com.hizeric.shenaniganscore.battleground.player.lifecycle.PlayerStateRegistry;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.WorldCreator;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.util.BlockVector;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;
import org.junit.jupiter.api.io.TempDir;
import sun.misc.Unsafe;

import java.lang.reflect.Field;
import java.nio.file.Path;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Issue #64 behavioural wiring. The decision matrix has its own pure tests; here the real
 * listener is driven with real MockBukkit events over a minimal manager: a stranded player cannot
 * teleport out, a same-block look still ejects, the join sweep queues one deduplicated exit, and
 * the queued teleport spends its own transit token. {@code ShenanigansCore} and
 * {@code BattlegroundManager} are final and demand Paper's plugin classloader, so both are
 * allocated without a constructor and their few observed fields are installed reflectively — the
 * guard's own code is completely unmodified by the harness.
 */
class ArenaEntryGuardBehaviorTest {

    private static final PlainTextComponentSerializer PLAIN = PlainTextComponentSerializer.plainText();
    private static final String EJECTION_MESSAGE = "Returned to battleground spawn.";
    private static final String INGRESS_MESSAGE =
            "You can only get into the arena by joining the battleground.";

    private static final Unsafe UNSAFE = unsafe();

    private ServerMock server;
    private ArenaEntryGuard guard;
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
        JavaPlugin testPlugin = (JavaPlugin) MockBukkit.createMockPlugin("ArenaEntryGuardBehaviorTest");
        World world = server.createWorld(new WorldCreator("arena-behavior"));
        inside = new Location(world, 15, 67, 25);
        outside = new Location(world, 0, 67, 0);
        ArenaRegion region = new ArenaRegion(world.getUID(), world.getName(),
                new BlockVector(10, 64, 20), new BlockVector(20, 70, 30), 1L, 100L, "test")
                .spawn(outside);
        ArenaStore store = new ArenaStore(testPlugin, temp.toFile());
        store.maps().put("default", region);

        ShenanigansCore plugin = (ShenanigansCore) UNSAFE.allocateInstance(ShenanigansCore.class);
        // The scheduler asks a task's plugin for its name when MockBukkit shuts down.
        JavaPlugin meta = (JavaPlugin) MockBukkit.createMockPlugin("ShenanigansCoreTestMeta");
        put(plugin, "pluginMeta", meta.getPluginMeta());
        BattlegroundManager manager =
                (BattlegroundManager) UNSAFE.allocateInstance(BattlegroundManager.class);
        StagingController staging = new StagingController(plugin, new PlayerStateRegistry(plugin));
        deployed = ConcurrentHashMap.newKeySet();
        put(manager, "plugin", plugin);
        put(manager, "arenaStore", store);
        put(manager, "staging", staging);
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

    private UUID id() {
        return player.getUniqueId();
    }

    @Test
    void aStrandedPlayerCannotTeleportOutAndGetsTheEjectionMessage() {
        PlayerTeleportEvent event = new PlayerTeleportEvent(player, inside, outside);

        guard.onTeleport(event);

        assertTrue(event.isCancelled(), "the outward teleport must be stopped");
        assertTrue(guard.isEjectionPending(id()));
        assertEquals(EJECTION_MESSAGE, PLAIN.serialize(player.nextComponentMessage()),
                "an ejection must not use the ingress denial message");

        server.getScheduler().performTicks(1);
        assertFalse(guard.isEjectionPending(id()));
        assertFalse(guard.hasTransit(id()), "the queued teleport consumes its own token");
        assertEquals(outside.getBlockX(), player.getLocation().getBlockX());
        assertEquals(outside.getBlockZ(), player.getLocation().getBlockZ());
    }

    @Test
    void repeatedEjectionAttemptsAreDeduplicated() {
        guard.ejectIfStranded(player);
        assertTrue(guard.isEjectionPending(id()));
        long firstDeadline = transit.get(id());

        guard.ejectIfStranded(player);

        assertEquals(firstDeadline, transit.get(id()), "a second attempt must not queue a new exit");
        assertTrue(guard.isEjectionPending(id()));
        server.getScheduler().performTicks(1);
        assertFalse(guard.isEjectionPending(id()));
        assertFalse(guard.hasTransit(id()));
    }

    @Test
    void aSameBlockLookInsideEjectsButAnOutsideLookDoesNot() {
        Location looked = inside.clone();
        looked.setYaw(looked.getYaw() + 30f);
        PlayerMoveEvent insideLook = new PlayerMoveEvent(player, inside, looked);

        guard.onMove(insideLook);

        assertTrue(guard.isEjectionPending(id()), "a stranded player looking around still ejects");

        ejecting.clear(); // drop the queued exit without running it for the outside case
        player.setLocation(outside);
        Location outsideLook = outside.clone();
        outsideLook.setYaw(outsideLook.getYaw() + 30f);
        PlayerMoveEvent event = new PlayerMoveEvent(player, outside, outsideLook);

        guard.onMove(event);

        assertFalse(guard.isEjectionPending(id()));
        assertFalse(event.isCancelled(), "an ordinary outside look must never be pinned");
    }

    @Test
    void theJoinSweepEjectsAStrandedNonDeployedPlayer() {
        guard.ejectIfStranded(player);

        assertTrue(guard.isEjectionPending(id()));
        assertEquals(EJECTION_MESSAGE, PLAIN.serialize(player.nextComponentMessage()));

        deployed.add(id());
        ejecting.clear();
        guard.ejectIfStranded(player);
        assertFalse(guard.isEjectionPending(id()), "a deployed player owns their own exit");
    }

    @Test
    void authorisedOutwardTeleportsRemainAllowed() {
        deployed.add(id());
        PlayerTeleportEvent deployedExit = new PlayerTeleportEvent(player, inside, outside);

        guard.onTeleport(deployedExit);

        assertFalse(deployedExit.isCancelled());
        assertFalse(guard.isEjectionPending(id()));

        deployed.remove(id());
        guard.allowOnce(id());
        PlayerTeleportEvent tokenExit = new PlayerTeleportEvent(player, inside, outside);

        guard.onTeleport(tokenExit);

        assertFalse(tokenExit.isCancelled(), "the scheduled exit is allowed by its token");
        assertFalse(guard.hasTransit(id()), "the token is spent on this teleport");
        assertFalse(guard.isEjectionPending(id()));
    }

    @Test
    void anInwardTeleportStillGetsTheIngressDenial() {
        player.setLocation(outside);
        PlayerTeleportEvent event = new PlayerTeleportEvent(player, outside, inside);

        guard.onTeleport(event);

        assertTrue(event.isCancelled());
        assertFalse(guard.isEjectionPending(id()));
        assertEquals(INGRESS_MESSAGE, PLAIN.serialize(player.nextComponentMessage()));
    }
}
