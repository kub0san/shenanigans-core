package com.hizeric.shenaniganscore.battleground.ability;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** M7: a "not ready" nudge must outlive the 5-tick HUD refresh that currently overwrites it. */
class ActionBarNoticeTest {

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
}
