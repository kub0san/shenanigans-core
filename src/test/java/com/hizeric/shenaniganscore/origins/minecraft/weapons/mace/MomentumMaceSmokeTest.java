package com.hizeric.shenaniganscore.origins.minecraft.weapons.mace;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;

class MomentumMaceSmokeTest {
    @Test
    void canonicalId() {
        assertEquals("minecraft:momentum_mace", MomentumMaceId.NEW_ID);
        assertEquals("minecraft:momentum_mace", MomentumMaceItem.ID);
        assertEquals("minecraft:momentum_mace", MomentumMaceAbility.ID.toString());
    }
    @Test
    void price() {
        assertEquals(650, MomentumMaceItem.PRICE);
    }
}