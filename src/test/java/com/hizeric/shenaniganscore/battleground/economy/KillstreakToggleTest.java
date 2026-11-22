package com.hizeric.shenaniganscore.battleground.economy;

import com.hizeric.shenaniganscore.battleground.shop.MarketView;
import com.hizeric.shenaniganscore.battleground.shop.ShopEntry;
import org.bukkit.Material;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class KillstreakToggleTest {

    @Test
    void applyPolicyNeutralizesTheGateWhenRequirementsAreOff() {
        EconomyService.CastGate gated = new EconomyService.CastGate(false, 5, 2, false);

        EconomyService.CastGate neutral = EconomyService.applyPolicy(gated, false);
        assertEquals(0, neutral.required());
        assertTrue(neutral.streakMet());
        assertEquals(gated.owned(), neutral.owned());
        assertEquals(gated.streak(), neutral.streak());

        assertEquals(gated, EconomyService.applyPolicy(gated, true));
    }

    @Test
    void marketRowsHideStreakRequirementsWhenPolicyIsOff() {
        List<ShopEntry> entries = List.of(
                ShopEntry.ability("blue", Material.LIGHT_BLUE_CONCRETE, "Blue", 40, 5, "jjk:blue"));

        List<MarketView.Row> rows = MarketView.page(
                entries, ShopEntry.Filter.ALL, 0, 12, 0, entry -> false, false);

        assertEquals(1, rows.size());
        assertEquals(0, rows.get(0).streakRequired());
        assertTrue(rows.get(0).streakMet());
    }
}
