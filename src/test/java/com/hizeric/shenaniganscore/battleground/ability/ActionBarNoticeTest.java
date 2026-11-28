package com.hizeric.shenaniganscore.battleground.ability;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import net.kyori.adventure.title.Title;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;

import java.io.IOException;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** M7: a "not ready" nudge must outlive the 5-tick HUD refresh that currently overwrites it. */
class ActionBarNoticeTest {

    private static final PlainTextComponentSerializer PLAIN = PlainTextComponentSerializer.plainText();
    private static final Path HUD = Path.of("src", "main", "java", "com", "hizeric",
            "shenaniganscore", "battleground", "ability", "TechniqueHud.java");

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void noticeWinsUntilItExpires() {
        UUID uuid = UUID.randomUUID();
        ActionBarNotice notice = new ActionBarNotice();
        notice.show(uuid, "not ready", 1_000L, 1_500L);

        assertTrue(notice.active(uuid, 1_000L));
        assertEquals("not ready", notice.message(uuid, 2_000L));
        assertFalse(notice.active(uuid, 2_501L));
        assertNull(notice.message(uuid, 2_501L));
    }

    @Test
    void aNewNoticeReplacesTheOldOne() {
        UUID uuid = UUID.randomUUID();
        ActionBarNotice notice = new ActionBarNotice();
        notice.show(uuid, "first", 0L, 2_000L);
        notice.show(uuid, "second", 500L, 2_000L);

        assertEquals("second", notice.message(uuid, 600L));
    }

    @Test
    void higherPriorityWinsAndLowerPriorityCannotReplace() {
        UUID uuid = UUID.randomUUID();
        ActionBarNotice notice = new ActionBarNotice();
        notice.show(uuid, ActionBarNotice.Priority.HIGH, "high", 0L, 2_000L);
        notice.show(uuid, ActionBarNotice.Priority.LOW, "low", 100L, 2_000L);
        assertEquals("high", notice.message(uuid, 200L));

        notice.show(uuid, ActionBarNotice.Priority.CRITICAL, "critical", 300L, 2_000L);
        assertEquals("critical", notice.message(uuid, 400L));
    }

    @Test
    void repeatingTheSameMessageExtendsItsTtl() {
        UUID uuid = UUID.randomUUID();
        ActionBarNotice notice = new ActionBarNotice();
        notice.show(uuid, "again", 0L, 1_000L);
        notice.show(uuid, "again", 800L, 1_000L);

        assertTrue(notice.active(uuid, 1_500L));
        assertFalse(notice.active(uuid, 1_801L));
    }

    @Test
    void stickyNoticeSurvivesUntilCleared() {
        UUID uuid = UUID.randomUUID();
        ActionBarNotice notice = new ActionBarNotice();
        notice.show(uuid, ActionBarNotice.Priority.HIGH, "sticky", 0L, 100L, true);

        assertTrue(notice.active(uuid, 100_000L));
        notice.clear(uuid);
        assertFalse(notice.active(uuid, 100_000L));
    }

    /**
     * Issue #73: notices leave the action bar (the HUD owns it) and arrive as subtitles.
     *
     * <p>MockBukkit's PlayerMock does not record Adventure {@code showTitle}, so the sink is
     * observed on a recording Player proxy — the component is what production sends.</p>
     */
    @Test
    void showNoticeSendsTheComponentAsASubtitle() {
        RecordingPlayer player = new RecordingPlayer();

        TechniqueHud.showNotice(player.player, "<red>not ready", 1_500L);

        assertEquals(1, player.subtitles.size(), "exactly one subtitle must be sent");
        assertEquals("not ready", PLAIN.serialize(player.subtitles.get(0)),
                "the notice component must be delivered as a subtitle");
        assertTrue(player.actionBars.isEmpty(), "the action bar belongs to the persistent HUD");
    }

    /** Issue #73: clearing a notice blanks the subtitle instead of leaving the last text up. */
    @Test
    void clearNoticeSendsABlankSubtitle() {
        RecordingPlayer player = new RecordingPlayer();

        TechniqueHud.showNotice(player.player, "<red>not ready", 1_500L);
        TechniqueHud.clearNotice(player.player);

        assertEquals(2, player.subtitles.size(), "one notice plus one blank");
        assertEquals("", PLAIN.serialize(player.subtitles.get(1)),
                "clearing must blank the subtitle line");
    }

    /** Issue #73: the 5-tick refresh keeps the action bar for the persistent HUD, not notices. */
    @Test
    void refreshDoesNotWriteNoticesToTheActionBar() throws IOException {
        String hud = Files.readString(HUD, StandardCharsets.UTF_8);
        assertFalse(hud.contains("indicatorRouter().send(player, notice)"),
                "a live notice must not be re-sent to the action bar by the HUD refresh");
        assertTrue(hud.contains("ActionBarView.render(ActionBarView.build(held, normal, ultimate))"),
                "the persistent HUD must still be what the refresh loop sends");
    }

    private static final class RecordingPlayer implements InvocationHandler {

        private final List<Component> subtitles = new ArrayList<>();
        private final List<Component> actionBars = new ArrayList<>();
        private final Player player = (Player) Proxy.newProxyInstance(
                ActionBarNoticeTest.class.getClassLoader(),
                new Class<?>[]{Player.class}, this);

        @Override
        public Object invoke(Object proxy, Method method, Object[] args) {
            if (method.getName().equals("showTitle")) {
                subtitles.add(((Title) args[0]).subtitle());
                return null;
            }
            if (method.getName().equals("sendActionBar")) {
                actionBars.add((Component) args[0]);
                return null;
            }
            if (method.getName().equals("hashCode")) return System.identityHashCode(proxy);
            if (method.getName().equals("equals")) return proxy == args[0];
            if (method.getName().equals("toString")) return "RecordingPlayer";
            // Anything else (getUniqueId, getLocation, ...) is not needed by the notice sink.
            return defaultReturn(method.getReturnType());
        }

        private static Object defaultReturn(Class<?> type) {
            if (!type.isPrimitive()) return null;
            if (type == boolean.class) return false;
            if (type == void.class) return null;
            if (type == char.class) return (char) 0;
            if (type == byte.class) return (byte) 0;
            if (type == short.class) return (short) 0;
            if (type == int.class) return 0;
            if (type == long.class) return 0L;
            if (type == float.class) return 0f;
            return 0d;
        }
    }
}