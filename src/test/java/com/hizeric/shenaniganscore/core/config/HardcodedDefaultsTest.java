package com.hizeric.shenaniganscore.core.config;

import com.hizeric.shenaniganscore.battleground.BattlegroundDefaults;
import com.hizeric.shenaniganscore.battleground.arena.ArenaRuntimeConfig;
import com.hizeric.shenaniganscore.battleground.combat.CommandPolicy;
import com.hizeric.shenaniganscore.origins.jujutsukaisen.domainexpansions.malevolentshrine.Ability;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The gameplay values that used to live in the YAMLs are now constants. These assertions
 * pin the shipped values so a future edit has to be deliberate.
 */
class HardcodedDefaultsTest {

    @Test
    void representativeBattlegroundDefaultsMatchTheShippedValues() {
        // Round length and warning threshold now live in arena runtime config (Task 8).
        assertEquals(20 * 60, ArenaRuntimeConfig.current().roundDurationSeconds());
        assertEquals(15, ArenaRuntimeConfig.current().roundWarningSeconds());
        assertEquals(10, BattlegroundDefaults.KILL_REWARD);
        assertEquals("CLIPBOARD", BattlegroundDefaults.RESTORE_ENGINE);
        assertEquals(18, BattlegroundDefaults.RESTORE_BUDGET_MS);
    }

    @Test
    void commandRootsStayHardcoded() {
        assertTrue(CommandPolicy.DEFAULT_ALLOWED_ROOTS.contains("bg"));
        assertTrue(CommandPolicy.DEFAULT_ALLOWED_ROOTS.contains("ability"));
        assertFalse(CommandPolicy.DEFAULT_ALLOWED_ROOTS.contains("back"));
        assertTrue(CommandPolicy.TELEPORT_BLOCKED_ROOTS.contains("back"));
        assertTrue(CommandPolicy.TELEPORT_BLOCKED_ROOTS.contains("world"));
        assertTrue(CommandPolicy.DEFAULT_BLOCKED_ROOTS.contains("kit"));
    }

    @Test
    void techniqueCooldownDefaultsAreAllAtLeastThirtySeconds() {
        int[] defaults = {
                com.hizeric.shenaniganscore.origins.jujutsukaisen.techniques.blue.Ability.DEFAULT_COOLDOWN_SECONDS,
                com.hizeric.shenaniganscore.origins.jujutsukaisen.techniques.red.Ability.DEFAULT_COOLDOWN_SECONDS,
                com.hizeric.shenaniganscore.origins.jujutsukaisen.techniques.fuga.Ability.DEFAULT_COOLDOWN_SECONDS,
                com.hizeric.shenaniganscore.origins.jujutsukaisen.techniques.piercingblood.Ability.DEFAULT_COOLDOWN_SECONDS,
                com.hizeric.shenaniganscore.origins.jujutsukaisen.techniques.purple.Ability.DEFAULT_COOLDOWN_SECONDS,
                com.hizeric.shenaniganscore.origins.jujutsukaisen.techniques.worldslash.Ability.DEFAULT_COOLDOWN_SECONDS
        };
        assertEquals(30, defaults[0]);
        assertEquals(40, defaults[1]);
        assertEquals(75, defaults[2]);
        assertEquals(45, defaults[3]);
        assertEquals(60, defaults[4]);
        assertEquals(90, defaults[5]);
        for (int value : defaults) {
            assertTrue(value >= 30, "shipped cooldown must be >= 30s, got " + value);
        }
    }

    @Test
    void killstreakRequirementsAreOffByDefault() {
        assertFalse(com.hizeric.shenaniganscore.battleground.shop.ShopPolicyConfig.defaults().killstreakRequirements());
    }

    @Test
    void malevolentShrineIsCappedAtFifteenTimesAClosedDomain() {
        // Closed domains cap at 48m; 1.5x is 72m. The default keeps the same headroom
        // ratio below the cap, and no command or code path can request more than 72m.
        assertEquals(72.0, Ability.MAX_RADIUS);
        assertEquals(60.0, Ability.DEFAULT_RADIUS);
        assertTrue(Ability.DEFAULT_RADIUS <= Ability.MAX_RADIUS);
        assertEquals(1.5 * 48.0, Ability.MAX_RADIUS);
    }
}
