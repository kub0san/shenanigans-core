package com.hizeric.shenaniganscore.core.ability;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Consumer;

/** Ordered catalogue of all abilities loaded on this server. Owned by {@code ShenanigansCore}. */
public final class AbilityRegistry {

    private final Map<AbilityId, AbilityDefinition> byId = new LinkedHashMap<>();
    private final Consumer<String> warningSink;

    public AbilityRegistry(Consumer<String> warningSink) {
        this.warningSink = warningSink == null ? message -> { } : warningSink;
    }

    /**
     * Registers a definition. Duplicate ids and duplicate icon materials are rejected with a
     * warning rather than an exception: {@code Player#setCooldown} is per material, so two
     * abilities sharing an icon would share a cooldown sweep.
     */
    public void register(AbilityDefinition definition) {
        if (byId.containsKey(definition.id())) {
            warningSink.accept("Duplicate ability id " + definition.id() + " ignored.");
            return;
        }
        for (AbilityDefinition other : byId.values()) {
            if (other.iconMaterial() == definition.iconMaterial()) {
                warningSink.accept("Ability " + definition.id() + " uses icon material "
                        + definition.iconMaterial() + " already used by " + other.id()
                        + "; it was not registered (cooldown sweeps would collide).");
                return;
            }
        }
        byId.put(definition.id(), definition);
    }

    public AbilityDefinition get(AbilityId id) {
        return id == null ? null : byId.get(id);
    }

    public Collection<AbilityDefinition> all() {
        return Collections.unmodifiableCollection(byId.values());
    }

    public int size() {
        return byId.size();
    }
}
