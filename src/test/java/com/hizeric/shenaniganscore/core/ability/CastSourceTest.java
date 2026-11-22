package com.hizeric.shenaniganscore.core.ability;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

/** Activation v2: ITEM and MAX_OUTPUT are gone; nothing can request a scaled cast any more. */
class CastSourceTest {

    @Test
    void commandIsTheOnlyPlayerCastSource() {
        // DEBUG is admin-only (bg debug ability) and not a player-reachable source.
        assertArrayEquals(new CastSource[] { CastSource.COMMAND, CastSource.DEBUG }, CastSource.values());
    }

    @Test
    void definitionCarriesOneFixedPower() {
        AbilityDefinition definition = new AbilityDefinition(
                AbilityId.of("test:fixed"),
                net.kyori.adventure.text.Component.text("Fixed"),
                org.bukkit.Material.END_ROD,
                60,
                () -> 5,
                2.5,
                (player, power) -> { },
                player -> false,
                false);

        assertEquals(2.5, definition.power());
    }
}
