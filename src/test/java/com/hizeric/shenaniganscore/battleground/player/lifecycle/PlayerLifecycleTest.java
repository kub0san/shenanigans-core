package com.hizeric.shenaniganscore.battleground.player.lifecycle;

import com.hizeric.shenaniganscore.ShenanigansCore;
import com.hizeric.shenaniganscore.battleground.menu.base.GuiChatPrompt;
import com.hizeric.shenaniganscore.battleground.menu.shell.MenuLatch;
import com.hizeric.shenaniganscore.core.session.DomainSession;
import com.hizeric.shenaniganscore.core.session.SessionEndReason;
import com.hizeric.shenaniganscore.core.session.SessionManager;
import com.hizeric.shenaniganscore.listeners.LifecycleListener;
import com.hizeric.shenaniganscore.origins.jujutsukaisen.techniques.WindupSlowness;
import com.hizeric.shenaniganscore.origins.jujutsukaisen.techniques.WindupSlownessCleanup;
import com.hizeric.shenaniganscore.testsupport.GlobalStateExtension;
import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import net.kyori.adventure.text.Component;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;
import org.mockbukkit.mockbukkit.world.WorldMock;
import sun.misc.Unsafe;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Proxy;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Behavioural coverage for player-exit cleanup: leaving mid-ability, mid-domain, mid-menu or
 * mid-chat-prompt must not leave transient state behind for the next session. The real
 * {@link LifecycleListener} and {@link GuiChatPrompt} quit hook are driven against real state
 * holders ({@code DeployServiceLeaveTest} covers the leave command itself).
 *
 * <p>A kick reaches the server as a {@link PlayerQuitEvent} with reason {@code KICKED}, so it
 * shares the disconnect cleanup contract and is asserted here. World-change has no producer or
 * listener in production and is documented as {@link Disabled} below.</p>
 */
@ExtendWith(GlobalStateExtension.class)
class PlayerLifecycleTest {

    private static final Unsafe UNSAFE = unsafe();

    private ServerMock server;
    private SessionManager sessions;
    private ShenanigansCore plugin;
    private Field instanceField;
    private ShenanigansCore previousInstance;
    private Field ownersField;

    @BeforeEach
    void setUp() throws Exception {
        server = MockBukkit.mock();
        sessions = new SessionManager();
        plugin = (ShenanigansCore) UNSAFE.allocateInstance(ShenanigansCore.class);
        put(plugin, "sessionManager", sessions);
        instanceField = ShenanigansCore.class.getDeclaredField("instance");
        instanceField.setAccessible(true);
        previousInstance = (ShenanigansCore) instanceField.get(null);
        instanceField.set(null, plugin);

        ownersField = WindupSlownessCleanup.class.getDeclaredField("OWNERS");
        ownersField.setAccessible(true);
    }

    @AfterEach
    void tearDown() throws Exception {
        // Drops the seeded prompt and the registration flag before the mock server goes away.
        GuiChatPrompt.shutdown();
        MenuLatch.get().clearAll();
        ownersMap().clear();
        if (instanceField != null) instanceField.set(null, previousInstance);
        MockBukkit.unmock();
    }

    @Test
    void quittingDuringAnAbilityDomainMenuAndPromptRunsTheWholeCleanupContract() throws Exception {
        assertDisconnectCleanup(PlayerQuitEvent.QuitReason.DISCONNECTED);
    }

    @Test
    void aKickIsADisconnectAndRunsTheSameCleanupContract() throws Exception {
        assertDisconnectCleanup(PlayerQuitEvent.QuitReason.KICKED);
    }

    @Test
    void deathEndsTheCastersDomainWithTheShatterReason() {
        WorldMock world = server.addSimpleWorld("lifecycle-death");
        PlayerMock player = server.addPlayer();
        RecordingSession session = new RecordingSession(player, new Location(world, 0, 64, 0), 4);
        assertTrue(sessions.startSession(session));

        new LifecycleListener().onPlayerDeath(
                new PlayerDeathEvent(player, null, List.of(), 0, Component.text("died"), true));

        assertEquals(SessionEndReason.CASTER_DEATH, session.endReason,
                "a caster's death routes through the shattering outro, not the normal collapse");
        assertTrue(session.closed);
        assertFalse(sessions.hasActiveSession(player.getUniqueId()));
    }

    @Test
    @Disabled("No PlayerChangedWorldEvent handler exists in production; adding one is a production "
            + "change, outside this tests-only issue. Assert the quit contract here if world-change "
            + "cleanup is added.")
    void worldChangeWouldTriggerTheSameCleanupContract() {
        // Contract to pin when the listener exists: active domain ended, menu tokens cleared,
        // pending chat prompt cancelled, windup owner dropped.
    }

    @SuppressWarnings("unchecked")
    private void assertDisconnectCleanup(PlayerQuitEvent.QuitReason reason) throws Exception {
        WorldMock world = server.addSimpleWorld("lifecycle-" + reason.name().toLowerCase());
        PlayerMock player = server.addPlayer();
        UUID id = player.getUniqueId();
        RecordingSession session = new RecordingSession(player, new Location(world, 0, 64, 0), 4);
        assertTrue(sessions.startSession(session), "the domain under test must be live");

        // Mid-ability: a windup root is owned by this caster.
        WindupSlownessCleanup.grant(player, new WindupSlowness(), 40, 1);
        assertTrue(ownersMap().containsKey(id));
        // Mid-menu: the hub reopen is suppressed while a screen is open.
        MenuLatch.get().suppress(player);
        // Mid-chat-prompt: the pending answer owns a cancel callback.
        AtomicInteger cancels = new AtomicInteger();
        JavaPlugin promptOwner = (JavaPlugin) MockBukkit.createMockPlugin("LifecyclePromptTest");
        seedChatPrompt(player, promptOwner, cancels);
        assertFalse(MenuLatch.get().shouldReopen(player, true));

        PlayerQuitEvent quit = new PlayerQuitEvent(player, Component.text("bye"), reason);
        new LifecycleListener().onPlayerQuit(quit);
        // Fire through Bukkit so the real chat-prompt quit hook runs as it does on a live server.
        server.getPluginManager().callEvent(quit);

        assertEquals(SessionEndReason.NORMAL, session.endReason,
                "disconnects collapse normally; only death shatters");
        assertTrue(session.closed, "the domain must be torn down on disconnect");
        assertFalse(sessions.hasActiveSession(id), "no domain may survive the quitter");
        assertFalse(ownersMap().containsKey(id), "the windup owner map must not retain the quitter");
        assertFalse(MenuLatch.get().isSuppressed(id), "menu tokens must not leak across reconnect");
        assertTrue(MenuLatch.get().shouldReopen(player, true));

        // discard() bounces the displaced prompt's cleanup to the entity scheduler.
        server.getScheduler().performTicks(1);
        assertEquals(1, cancels.get(), "a disconnect must run the pending prompt's cancel callback");
    }

    /**
     * Installs the same pending prompt that {@code ask()} installs, and registers the real
     * listener. The timeout is a no-op stub because MockBukkit's {@code PaperScheduledTask.cancel}
     * is unimplemented; the cleanup under test is {@code onQuit -> discard -> onCancel}.
     */
    @SuppressWarnings("unchecked")
    private void seedChatPrompt(PlayerMock player, JavaPlugin owner, AtomicInteger cancels)
            throws Exception {
        Constructor<?> pending = Class.forName(GuiChatPrompt.class.getName() + "$Pending")
                .getDeclaredConstructor(Player.class, org.bukkit.plugin.Plugin.class,
                        Consumer.class, Runnable.class, ScheduledTask.class);
        pending.setAccessible(true);
        ScheduledTask noopTimeout = (ScheduledTask) Proxy.newProxyInstance(
                ScheduledTask.class.getClassLoader(), new Class<?>[]{ScheduledTask.class},
                (proxy, method, args) -> null);
        Object entry = pending.newInstance(player, owner,
                (Consumer<String>) text -> { }, (Runnable) cancels::incrementAndGet, noopTimeout);

        Field pendingField = GuiChatPrompt.class.getDeclaredField("PENDING");
        pendingField.setAccessible(true);
        ((Map<UUID, Object>) pendingField.get(null)).put(player.getUniqueId(), entry);

        Constructor<GuiChatPrompt> listener = GuiChatPrompt.class.getDeclaredConstructor();
        listener.setAccessible(true);
        server.getPluginManager().registerEvents(listener.newInstance(), owner);
    }

    @SuppressWarnings("unchecked")
    private Map<UUID, WindupSlowness> ownersMap() throws Exception {
        return (Map<UUID, WindupSlowness>) ownersField.get(null);
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
        field.setAccessible(true);
        field.set(target, value);
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

    /** Records the outro the lifecycle listener selected, without needing a real domain session. */
    private static final class RecordingSession extends DomainSession {
        boolean closed;
        SessionEndReason endReason;

        RecordingSession(Player caster, Location center, double radius) {
            super(caster, center, radius);
        }

        @Override
        public void end(SessionEndReason reason) {
            endReason = reason;
            close();
        }

        @Override
        public void close() {
            closed = true;
            deactivate();
        }
    }
}
