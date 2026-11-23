package com.hizeric.shenaniganscore.origins.minecraft.weapons.dashsword;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DashSwordSpecTest {

    @Test
    void theShippedEnchantmentsArePinned() {
        assertEquals(5, DashSwordItem.SHARPNESS_LEVEL);
        assertEquals(3, DashSwordItem.UNBREAKING_LEVEL);
        assertEquals(2, DashSwordItem.FIRE_ASPECT_LEVEL);
    }

    @Test
    void theDescriptionLineIsVerbatim() {
        assertEquals("Self explanatory...", DashSwordItem.LORE_LINE);
    }

    @Test
    void thePassiveLoreNamesTheCooldownRefund() {
        String lore = String.join("\n", DashSwordItem.lore());
        assertTrue(lore.contains("-1s dash cooldown per hit"), "expected refund in lore: " + lore);
        assertFalse(lore.contains("Speed I"), "the stacking Speed passive must be gone: " + lore);
    }
}
