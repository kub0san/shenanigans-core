package com.hizeric.shenaniganscore.battleground.indicator;

import com.hizeric.shenaniganscore.ShenanigansCore;
import com.hizeric.shenaniganscore.core.session.SessionManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

import java.lang.reflect.Field;

import sun.misc.Unsafe;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Issue #42: a behavioural companion to {@link IndicatorRouterContractTest}.
 * The contract test only pins source strings; this test drives the real
 * {@code IndicatorRouter.send(...)} path under MockBukkit and proves two
 * consecutive notices both land in the player's action bar immediately — no
 * throttle, no de-duplication, no tick wait in between.
 */
class IndicatorRouterUnthrottledSendTest {

    private static final PlainTextComponentSerializer PLAIN = PlainTextComponentSerializer.plainText();

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void consecutiveNoticesBothReachTheActionBarImmediately() throws Exception {
        ServerMock server = MockBukkit.mock();
        PlayerMock player = server.addPlayer();

        IndicatorRouter router = routerWithNoActiveDomain();

        // Two different send overloads, back to back, with no ticks in between.
        router.send(player, "<red>cooldown");
        router.send(player, Component.text("sticky hint"));

        assertEquals("cooldown", PLAIN.serialize(player.nextActionBar()),
                "the first MiniMessage notice must be delivered, not cached or dropped");
        assertEquals("sticky hint", PLAIN.serialize(player.nextActionBar()),
                "the second notice must arrive immediately, with no throttle window");
        assertNull(player.nextActionBar(), "exactly two sends must produce exactly two action-bar packets");
    }

    @Test
    void repeatedIdenticalNoticesAreAllDelivered() throws Exception {
        ServerMock server = MockBukkit.mock();
        PlayerMock player = server.addPlayer();
        IndicatorRouter router = routerWithNoActiveDomain();

        for (int i = 0; i < 3; i++) {
            router.send(player, "<gray>repeat " + i);
        }

        for (int i = 0; i < 3; i++) {
            assertEquals("repeat " + i, PLAIN.serialize(player.nextActionBar()),
                    "identical consecutive notices must not be de-duplicated");
        }
        assertNull(player.nextActionBar());
    }

    /**
     * A real router over a real (empty) SessionManager — no production seams
     * altered. {@code ShenanigansCore} is final, so MockBukkit's loader cannot
     * proxy it and its constructor demands Paper's plugin classloader. The test
     * allocates the instance without running that constructor (only the
     * {@code sessionManager} field is read by the router) and installs a real
     * SessionManager by reflection.
     */
    private static IndicatorRouter routerWithNoActiveDomain() throws Exception {
        Field unsafeField = Unsafe.class.getDeclaredField("theUnsafe");
        unsafeField.setAccessible(true);
        Unsafe unsafe = (Unsafe) unsafeField.get(null);
        ShenanigansCore plugin = (ShenanigansCore) unsafe.allocateInstance(ShenanigansCore.class);
        Field sessions = ShenanigansCore.class.getDeclaredField("sessionManager");
        sessions.setAccessible(true);
        sessions.set(plugin, new SessionManager());
        return new IndicatorRouter(plugin);
    }
}
