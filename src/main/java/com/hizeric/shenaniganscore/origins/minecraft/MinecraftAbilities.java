package com.hizeric.shenaniganscore.origins.minecraft;

import com.hizeric.shenaniganscore.ShenanigansCore;
import com.hizeric.shenaniganscore.core.ability.AbilityRegistry;
import com.hizeric.shenaniganscore.origins.minecraft.weapons.dashsword.DashSwordAbility;
import com.hizeric.shenaniganscore.origins.minecraft.weapons.dashsword.DashSwordHitListener;

/**
 * Registration point for the Minecraft ability catalogue.
 *
 * <p>Mirrors {@code JujutsuAbilities}: this is the only place where Minecraft-specific abilities
 * and their listeners are assembled. Downstream systems read the neutral registry instead of
 * importing these classes.</p>
 */
public final class MinecraftAbilities {

    private MinecraftAbilities() { }

    public static void register(AbilityRegistry registry, ShenanigansCore plugin) {
        registry.register(DashSwordAbility.definition());
        plugin.getServer().getPluginManager().registerEvents(new DashSwordHitListener(plugin), plugin);
    }
}
