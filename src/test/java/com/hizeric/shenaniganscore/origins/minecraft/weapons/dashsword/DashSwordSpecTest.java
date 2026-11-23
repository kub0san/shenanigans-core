package com.hizeric.shenaniganscore.origins.minecraft.weapons.dashsword;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

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
}
