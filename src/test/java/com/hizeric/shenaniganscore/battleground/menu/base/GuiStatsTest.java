package com.hizeric.shenaniganscore.battleground.menu.base;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Pure formatting for the single hub statistics item. No Bukkit runtime required. */
class GuiStatsTest {

    private static GuiStats.Snapshot snapshot() {
        return new GuiStats.Snapshot("Steve", 10, 4, 340, 7);
    }

    @Test
    void killDeathRatioUsesTwoDecimals() {
        assertEquals("2.50", GuiStats.kd(10, 4));
        assertEquals("0.33", GuiStats.kd(1, 3));
    }

    @Test
    void zeroDeathsShowInfinityOrDash() {
        assertEquals("∞", GuiStats.kd(5, 0));
        assertEquals("—", GuiStats.kd(0, 0));
    }

    @Test
    void loreStartsWithUsernameAndListsEveryStatOnce() {
        String[] lore = GuiStats.lore(snapshot());
        assertEquals(6, lore.length);
        assertTrue(lore[0].contains("Steve"));
        String joined = String.join("\n", lore);
        assertTrue(joined.contains("Kills"));
        assertTrue(joined.contains("Deaths"));
        assertTrue(joined.contains("K/D"));
        assertTrue(joined.contains("Credits"));
        assertTrue(joined.contains("Highest streak"));
        assertTrue(joined.contains("2.50"));
        assertTrue(joined.contains("340"));
        assertTrue(joined.contains("7"));
    }
}
