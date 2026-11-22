package com.hizeric.shenaniganscore.core.ability;

import java.util.Locale;
import java.util.Objects;

/**
 * Namespaced identifier for an ability, e.g. {@code jjk:blue}.
 *
 * <p>The namespace is the source franchise / module that owns the ability, so the battleground
 * shop can catalogue abilities from any number of sources without importing them.</p>
 */
public record AbilityId(String namespace, String value) {

    public AbilityId {
        Objects.requireNonNull(namespace, "namespace");
        Objects.requireNonNull(value, "value");
        if (namespace.isBlank() || value.isBlank()) {
            throw new IllegalArgumentException("AbilityId parts must not be blank");
        }
        namespace = namespace.toLowerCase(Locale.ROOT);
        value = value.toLowerCase(Locale.ROOT);
    }

    /** Parses {@code "namespace:value"}; an omitted namespace defaults to {@code core}. */
    public static AbilityId of(String raw) {
        Objects.requireNonNull(raw, "raw");
        String text = raw.toLowerCase(Locale.ROOT).trim();
        int separator = text.indexOf(':');
        if (separator < 0) return new AbilityId("core", text);
        return new AbilityId(text.substring(0, separator), text.substring(separator + 1));
    }

    @Override
    public String toString() {
        return namespace + ":" + value;
    }
}
