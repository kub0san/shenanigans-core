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
     * Registers a definition. Duplicate ids are rejected with a warning rather than an exception.
     * Icon materials may be shared: cooldowns are keyed by ability id in {@code CooldownManager},
     * not by the icon material.
     */
    public void register(AbilityDefinition definition) {
        if (byId.containsKey(definition.id())) {
            warningSink.accept("Duplicate ability id " + definition.id() + " ignored.");
            return;
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
