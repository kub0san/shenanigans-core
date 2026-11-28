package com.hizeric.shenaniganscore.origins.minecraft;

import com.hizeric.shenaniganscore.ShenanigansCore;
import com.hizeric.shenaniganscore.core.ability.AbilityRegistry;
import com.hizeric.shenaniganscore.core.ability.ListenerSink;
import com.hizeric.shenaniganscore.core.ability.RegistrationContext;
import com.hizeric.shenaniganscore.origins.minecraft.weapons.dashsword.DashSwordAbility;
import com.hizeric.shenaniganscore.origins.minecraft.weapons.dashsword.DashSwordHitListener;
import com.hizeric.shenaniganscore.origins.minecraft.weapons.dashsword.DashSwordRightClickListener;
import com.hizeric.shenaniganscore.origins.minecraft.weapons.executionsword.ExecutionerSwordAbility;
import com.hizeric.shenaniganscore.origins.minecraft.weapons.executionsword.ExecutionerSwordHitListener;
import com.hizeric.shenaniganscore.origins.minecraft.weapons.mace.MomentumMaceAbility;
import com.hizeric.shenaniganscore.origins.minecraft.weapons.mace.MomentumMaceFallProtection;
import com.hizeric.shenaniganscore.origins.minecraft.weapons.mace.MomentumMaceHitListener;

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
        ListenerSink sink = listener -> plugin.getServer().getPluginManager().registerEvents(listener, plugin);
        register(new RegistrationContext(registry, sink, plugin.getLogger()));
    }

    public static void register(RegistrationContext ctx) {
        ctx.abilities().register(DashSwordAbility.definition());
        ctx.listeners().add(new DashSwordHitListener(ShenanigansCore.getInstance()));
        ctx.listeners().add(new DashSwordRightClickListener(ShenanigansCore.getInstance()));

        ExecutionerSwordHitListener listener = new ExecutionerSwordHitListener();
        ctx.abilities().register(ExecutionerSwordAbility.definition(listener));
        ctx.listeners().add(listener);

        ctx.abilities().register(MomentumMaceAbility.definition());
        ctx.listeners().add(new MomentumMaceHitListener());
        ctx.listeners().add(new MomentumMaceFallProtection());
    }
}
