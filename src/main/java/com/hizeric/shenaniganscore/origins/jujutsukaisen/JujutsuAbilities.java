package com.hizeric.shenaniganscore.origins.jujutsukaisen;

import com.hizeric.shenaniganscore.core.ability.AbilityId;
import com.hizeric.shenaniganscore.core.ability.AbilityRegistry;

import java.util.List;

/**
 * Registration point for the Jujutsu Kaisen ability catalogue.
 *
 * <p>This is the only place where a franchise-specific ability list is assembled. Everything
 * downstream (shop, rail items, commands) reads the neutral registry instead of importing these
 * classes, so another source can be added without touching the battleground.</p>
 */
public final class JujutsuAbilities {

    public static final com.hizeric.shenaniganscore.origins.jujutsukaisen.techniques.blue.Ability BLUE =
            new com.hizeric.shenaniganscore.origins.jujutsukaisen.techniques.blue.Ability();
    public static final com.hizeric.shenaniganscore.origins.jujutsukaisen.techniques.red.Ability RED =
            new com.hizeric.shenaniganscore.origins.jujutsukaisen.techniques.red.Ability();
    public static final com.hizeric.shenaniganscore.origins.jujutsukaisen.techniques.fuga.Ability FUGA =
            new com.hizeric.shenaniganscore.origins.jujutsukaisen.techniques.fuga.Ability();
    public static final com.hizeric.shenaniganscore.origins.jujutsukaisen.techniques.piercingblood.Ability PIERCING_BLOOD =
            new com.hizeric.shenaniganscore.origins.jujutsukaisen.techniques.piercingblood.Ability();

    public static final com.hizeric.shenaniganscore.origins.jujutsukaisen.techniques.purple.Ability PURPLE =
            new com.hizeric.shenaniganscore.origins.jujutsukaisen.techniques.purple.Ability();
    public static final com.hizeric.shenaniganscore.origins.jujutsukaisen.techniques.worldslash.Ability WORLD_SLASH =
            new com.hizeric.shenaniganscore.origins.jujutsukaisen.techniques.worldslash.Ability();

    /** Hidden domains: castable from the debug path, never sold, bound or listed. */
    public static final List<AbilityId> DOMAINS = List.of(
            com.hizeric.shenaniganscore.origins.jujutsukaisen.domainexpansions.malevolentshrine.Ability.ID,
            com.hizeric.shenaniganscore.origins.jujutsukaisen.domainexpansions.unlimitedvoid.Ability.ID,
            com.hizeric.shenaniganscore.origins.jujutsukaisen.domainexpansions.timecellmoonpalace.Ability.ID,
            com.hizeric.shenaniganscore.origins.jujutsukaisen.domainexpansions.wombprofusion.Ability.ID);

    private static volatile boolean techniqueFxEnabled = true;

    private JujutsuAbilities() { }

    public static boolean isTechniqueFxEnabled() { return techniqueFxEnabled; }
    public static void setTechniqueFxEnabled(boolean enabled) { techniqueFxEnabled = enabled; }

    public static void register(AbilityRegistry registry) {
        register(registry, true);
    }

    public static void register(AbilityRegistry registry, boolean techniqueFxEnabled) {
        setTechniqueFxEnabled(techniqueFxEnabled);
        registry.register(BLUE.definition());
        registry.register(RED.definition());
        registry.register(FUGA.definition());
        registry.register(PIERCING_BLOOD.definition());
        registry.register(PURPLE.definition());
        registry.register(WORLD_SLASH.definition());
        registry.register(com.hizeric.shenaniganscore.origins.jujutsukaisen.domainexpansions.malevolentshrine.Ability.definition());
        registry.register(com.hizeric.shenaniganscore.origins.jujutsukaisen.domainexpansions.unlimitedvoid.Ability.definition());
        registry.register(com.hizeric.shenaniganscore.origins.jujutsukaisen.domainexpansions.timecellmoonpalace.Ability.definition());
        registry.register(com.hizeric.shenaniganscore.origins.jujutsukaisen.domainexpansions.wombprofusion.Ability.definition());
    }
}
