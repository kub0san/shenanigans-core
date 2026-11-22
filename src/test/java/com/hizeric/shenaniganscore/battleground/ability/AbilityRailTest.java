package com.hizeric.shenaniganscore.battleground.ability;

import com.hizeric.shenaniganscore.core.ability.AbilityId;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** Three command slots; the third is the parked ultimate slot. */
class AbilityRailTest {

    @Test
    void threeCommandSlotsWithUltimateInTheThird() {
        assertEquals(3, AbilityRail.TOTAL);
        assertEquals(0, AbilityRail.HELD_SLOT);
        assertEquals(1, AbilityRail.NORMAL_SLOT);
        assertEquals(2, AbilityRail.ULTIMATE_SLOT);
    }

    @Test
    void ultimateLivesInTheThirdSlot() {
        AbilityRail rail = new AbilityRail();
        AbilityId blue = AbilityId.of("jjk:blue");
        AbilityId purple = AbilityId.of("jjk:purple");

        rail.set(0, blue);
        rail.set(AbilityRail.ULTIMATE_SLOT, purple);

        assertEquals(blue, rail.get(0));
        assertEquals(purple, rail.ultimate());
        assertEquals(purple, rail.get(AbilityRail.ULTIMATE_SLOT));
        assertEquals(List.of(blue, purple), rail.bound());
    }

    @Test
    void slotRangeIsThree() {
        AbilityRail rail = new AbilityRail();
        assertThrows(IndexOutOfBoundsException.class, () -> rail.set(3, AbilityId.of("test:x")));
        assertNull(rail.ultimate());
    }

    @Test
    void namedAccessorsReadTheThreeSlots() {
        AbilityRail rail = new AbilityRail();
        AbilityId dash = AbilityId.of("minecraft:dash_sword");
        AbilityId blue = AbilityId.of("jjk:blue");
        AbilityId purple = AbilityId.of("jjk:purple");

        rail.set(AbilityRail.HELD_SLOT, dash);
        rail.set(AbilityRail.NORMAL_SLOT, blue);
        rail.set(AbilityRail.ULTIMATE_SLOT, purple);

        assertEquals(dash, rail.held());
        assertEquals(blue, rail.normal());
        assertEquals(purple, rail.ultimate());
    }

    @Test
    void settingAnEqualValueDoesNotBumpTheVersion() {
        AbilityRail rail = new AbilityRail();
        int version = rail.version();

        rail.set(AbilityRail.NORMAL_SLOT, null);
        assertEquals(version, rail.version());

        rail.set(AbilityRail.NORMAL_SLOT, AbilityId.of("jjk:blue"));
        assertEquals(version + 1, rail.version());

        rail.set(AbilityRail.NORMAL_SLOT, AbilityId.of("jjk:blue"));
        assertEquals(version + 1, rail.version());
    }

    @Test
    void copyIsIndependentAndKeepsTheVersion() {
        AbilityRail rail = new AbilityRail();
        AbilityId blue = AbilityId.of("jjk:blue");
        rail.set(AbilityRail.NORMAL_SLOT, blue);

        AbilityRail copy = rail.copy();
        assertEquals(rail.version(), copy.version());
        assertEquals(blue, copy.normal());

        copy.set(AbilityRail.NORMAL_SLOT, null);
        assertEquals(blue, rail.normal());
    }
}
