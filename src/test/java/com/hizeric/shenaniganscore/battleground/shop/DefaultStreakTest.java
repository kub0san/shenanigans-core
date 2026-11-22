package com.hizeric.shenaniganscore.battleground.shop;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The default killstreak curve the catalog seed stamps onto every ability row.
 *
 * <p>The spec's anti-goal is that the killstreak stays the balance lever. The seed used to write
 * {@code streakRequired = 0} for every ability, which silently disabled the gate; these tests pin the
 * builder's chosen curve ({@code max(1, price / 40)}) against the real ability prices so a regression
 * to zero fails loudly.</p>
 */
class DefaultStreakTest {

    // Real shipped prices, referenced from the ability definitions so the pin can't drift silently.
    private static final int BLUE = com.hizeric.shenaniganscore.origins.jujutsukaisen.techniques.blue.Ability.PRICE;
    private static final int PIERCING_BLOOD =
            com.hizeric.shenaniganscore.origins.jujutsukaisen.techniques.piercingblood.Ability.PRICE;
    private static final int RED = com.hizeric.shenaniganscore.origins.jujutsukaisen.techniques.red.Ability.PRICE;
    private static final int PURPLE = com.hizeric.shenaniganscore.origins.jujutsukaisen.techniques.purple.Ability.PRICE;
    private static final int FUGA = com.hizeric.shenaniganscore.origins.jujutsukaisen.techniques.fuga.Ability.PRICE;

    @Test
    void curveMatchesTheChosenValues() {
        assertEquals(1, ShopEntry.defaultStreak(BLUE));           // 60  -> 1
        assertEquals(2, ShopEntry.defaultStreak(PIERCING_BLOOD)); // 90  -> 2
        assertEquals(3, ShopEntry.defaultStreak(RED));            // 120 -> 3
        assertEquals(3, ShopEntry.defaultStreak(PURPLE));         // 150 -> 3
        assertEquals(5, ShopEntry.defaultStreak(FUGA));           // 200 -> 5
    }

    @Test
    void everySeededAbilityGatesOnAtLeastOneKill() {
        // The gate must engage for every priced ability: a zero would mean no killstreak needed.
        for (int price : new int[]{BLUE, PIERCING_BLOOD, RED, PURPLE, FUGA}) {
            assertTrue(ShopEntry.defaultStreak(price) >= 1,
                    "ability priced at " + price + " must require at least one kill");
        }
    }

    @Test
    void lowAndZeroPricesClampToOne() {
        // Even a free or cheap ability keeps a minimal gate rather than disabling the lever.
        assertEquals(1, ShopEntry.defaultStreak(0));
        assertEquals(1, ShopEntry.defaultStreak(1));
        assertEquals(1, ShopEntry.defaultStreak(39));
    }
}