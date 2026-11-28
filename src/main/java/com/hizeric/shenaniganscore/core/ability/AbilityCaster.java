package com.hizeric.shenaniganscore.core.ability;

import com.hizeric.shenaniganscore.battleground.ability.TechniqueHud;
import com.hizeric.shenaniganscore.core.cooldown.CooldownManager;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.logging.Logger;

/**
 * The single gate every ability cast passes through, regardless of source.
 *
 * <p>Order: extra gates (battleground ownership/region), domain rule, cooldown, then the origin's
 * performer. Casts are limited by cooldowns alone — there is no mana bar anywhere in the plugin.</p>
 * <p>{@link CastSource#DEBUG} casts skip the gates, the domain-lock check and the cooldown claim —
 * they are admin debug casts from the plugin surface, not player-reachable paths.</p>
 */
public final class AbilityCaster {

    /** Extra checks installed by the battleground once it exists. */
    public interface Gate {
        /** {@link CastResult#OK} to allow the cast, anything else to deny it. */
        CastResult check(Player player, AbilityDefinition definition, CastSource source);
    }

    private final AbilityRegistry registry;
    private final CooldownManager cooldowns;
    private final Logger logger;
    private final List<Gate> gates = new CopyOnWriteArrayList<>();

    public AbilityCaster(AbilityRegistry registry, CooldownManager cooldowns) {
        this(registry, cooldowns, null);
    }

    public AbilityCaster(AbilityRegistry registry, CooldownManager cooldowns, Logger logger) {
        this.registry = Objects.requireNonNull(registry, "registry");
        this.cooldowns = Objects.requireNonNull(cooldowns, "cooldowns");
        this.logger = logger == null ? Logger.getLogger(AbilityCaster.class.getName()) : logger;
    }

    public AbilityRegistry registry() { return registry; }

    /** Installs an extra gate; called once by the battleground manager during enable. */
    public void addGate(Gate gate) {
        if (gate != null) gates.add(gate);
    }

    public CastResult cast(Player player, AbilityId id, CastSource source) {
        return cast(player, id, source, true);
    }

    /** @param feedback whether rejection/cast call-out messages should be sent to the player. */
    public CastResult cast(Player player, AbilityId id, CastSource source, boolean feedback) {
        if (player == null || id == null) return CastResult.UNKNOWN_ABILITY;
        AbilityDefinition definition = registry.get(id);
        if (definition == null) {
            if (feedback) fail(player, "<red>That ability is not registered.");
            return CastResult.UNKNOWN_ABILITY;
        }

        UUID uuid = player.getUniqueId();
        String cooldownKey = id.toString();
        int cooldownSeconds = definition.cooldownSeconds();
        boolean cooldownClaimed = false;
        if (source != CastSource.DEBUG) {
            for (Gate gate : gates) {
                CastResult result = gate.check(player, definition, source);
                if (result != null && result != CastResult.OK) {
                    if (feedback) sendGateFailure(player, result);
                    return result;
                }
            }

            if (definition.isDomainBlocked(player)) {
                if (feedback) fail(player, "<red>You cannot use a technique while your domain is active!");
                return CastResult.DOMAIN_LOCKED;
            }

            // Atomically check-and-claim (F-22): the cooldown is reserved before the
            // origin performs, so two concurrent casts cannot both pass the gate.
            if (!cooldowns.tryClaim(uuid, cooldownKey, cooldownSeconds)) {
                if (feedback) fail(player, "<red>Your ability is not ready yet. "
                        + "<gray>Ready in <white>" + cooldowns.getRemainingSeconds(uuid, cooldownKey) + "s");
                return CastResult.COOLDOWN;
            }
            cooldownClaimed = cooldownSeconds > 0;
        }

        try {
            definition.perform(player);
        } catch (Throwable t) {
            // Release the claimed cooldown so a failed cast does not lock the player
            // out for the full configured duration (audit #25).
            if (cooldownClaimed) cooldowns.clear(uuid, cooldownKey);
            throw t;
        }
        if (source != CastSource.DEBUG) {
            try {
                definition.flourish().play(player);
            } catch (RuntimeException exception) {
                logger.warning("Cast flourish failed for " + id + ": " + exception.getMessage());
            }
        }
        return CastResult.OK;
    }

    private void sendGateFailure(Player player, CastResult result) {
        switch (result) {
            case NOT_IN_ARENA -> fail(player, "<red>Abilities can only be used inside the battleground.");
            case NOT_OWNED -> fail(player, "<red>You have not unlocked that technique. <gray>Buy it in the market.");
            case HELD_ITEM_REQUIRED -> fail(player, "<red>Hold the matching item to use that ability.");
            default -> fail(player, "<red>You cannot use that ability right now.");
        }
    }

    private void fail(Player player, String message) {
        TechniqueHud.showNotice(player, message, 3_000L);
    }
}
