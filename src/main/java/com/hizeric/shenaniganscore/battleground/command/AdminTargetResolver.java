package com.hizeric.shenaniganscore.battleground.command;

import java.util.LinkedHashSet;
import java.util.Collection;
import java.util.List;
import java.util.Locale;

/**
 * Pure id resolution for {@code /bg debug ability|item <id>}, free of Bukkit.
 *
 * <p>An admin should be able to type an ability or item by either convention â€” a fully
 * namespaced id ({@code jjk:blue}, {@code minecraft:dash_sword}) or a bare value
 * ({@code blue}, {@code dash_sword}) â€” and have it resolve against the live catalogue.
 * Matching is case-insensitive and whitespace-tolerant. An exact namespaced match wins;
 * otherwise the value part is matched namespace-insensitively, so a wrong or omitted
 * namespace still finds the row. See {@code AdminTargetResolverTest}.</p>
 */
final class AdminTargetResolver {

    private AdminTargetResolver() {
    }

    /**
     * Resolves a raw admin id to a canonical ability id from {@code knownAbilityIds}.
     *
     * @return the matching catalogue id, or {@code null} if nothing matches.
     */
    static String resolveAbility(String raw, Collection<String> knownAbilityIds) {
        return resolve(raw, knownAbilityIds);
    }

    /**
     * Resolves a raw admin id to a canonical custom-item id from {@code knownItemIds}.
     *
     * @return the matching catalogue id, or {@code null} if nothing matches.
     */
    static String resolveItem(String raw, Collection<String> knownItemIds) {
        return resolve(raw, knownItemIds);
    }

    private static String resolve(String raw, Collection<String> knownIds) {
        if (raw == null || knownIds == null) return null;
        String needle = raw.trim().toLowerCase(Locale.ROOT);
        if (needle.isEmpty()) return null;

        // Pass 1: exact namespaced match.
        for (String id : knownIds) {
            if (id != null && id.toLowerCase(Locale.ROOT).equals(needle)) {
                return id;
            }
        }
        // Pass 2: namespace-insensitive match on the value part.
        for (String id : knownIds) {
            if (id == null) continue;
            String value = valueOf(id.toLowerCase(Locale.ROOT));
            if (value.equals(needle) || valueOf(needle).equals(value)) {
                return id;
            }
        }
        return null;
    }

    /**
     * Bare value ids for tab completion: admins type {@code blue}, not {@code jjk:blue}, so the
     * completion list must offer the same form the command expects on input.
     */
    static List<String> bareValueIds(Collection<String> knownIds) {
        // Dedup: tab-complete the same bare value once even when several catalog ids share it.
        var values = new LinkedHashSet<String>();
        for (String id : knownIds) {
            if (id != null) values.add(valueOf(id));
        }
        return List.copyOf(values);
    }

    /** The part after the first {@code ':'}, or the whole string when there is no namespace. */
    private static String valueOf(String id) {
        int colon = id.indexOf(':');
        return colon < 0 ? id : id.substring(colon + 1);
    }
}
