package com.hizeric.shenaniganscore.origins.minecraft;

import com.hizeric.shenaniganscore.core.ability.AbilityRegistry;
import com.hizeric.shenaniganscore.origins.minecraft.weapons.dashsword.DashSwordAbility;

/**
 * Registration point for the Minecraft ability catalogue.
 *
 * <p>Mirrors {@code JujutsuAbilities}: this is the only place where Minecraft-specific abilities
 * are assembled. Downstream systems read the neutral registry instead of importing these classes.</p>
 */
public final class MinecraftAbilities {

    private MinecraftAbilities() { }

    public static void register(AbilityRegistry registry) {
        registry.register(DashSwordAbility.definition());
    }
}
