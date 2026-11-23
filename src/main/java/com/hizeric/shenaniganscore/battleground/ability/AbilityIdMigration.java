package com.hizeric.shenaniganscore.battleground.ability;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/**
 * One-time migration of persisted ability ids to their canonical underscored spellings.
 *
 * <p>Old saved data is rewritten as it is loaded; no runtime alias is kept, so the live registry
 * only ever sees the canonical ids. Adding a new mapping here is enough to upgrade stored shop
 * rows, player rail bindings and legacy unlock records.</p>
 */
public final class AbilityIdMigration {

    private static final Map<String, String> RENAMES = Map.of(
            "battleground:dash_sword", "minecraft:dash_sword",
            "minecraft:dashsword", "minecraft:dash_sword",
            "executioner_sword", "minecraft:executioners_sword",
            "minecraft:executioner_sword", "minecraft:executioners_sword",
            "minecraft:executionsword", "minecraft:executioners_sword",
            "jjk:piercingblood", "jjk:piercing_blood",
            "jjk:worldslash", "jjk:world_slash");

    /** Canonical → one representative legacy spelling, for diagnostics and reverse migration. */
    private static final Map<String, String> LEGACY;

    static {
        Map<String, String> reverse = new LinkedHashMap<>();
        // Explicit insertion order: Map.of has no stable iteration order, so the first
        // legacy spelling for a canonical id must be chosen deterministically.
        reverse.put("minecraft:dash_sword", "battleground:dash_sword");
        reverse.put("minecraft:executioners_sword", "minecraft:executioner_sword");
        reverse.put("jjk:piercing_blood", "jjk:piercingblood");
        reverse.put("jjk:world_slash", "jjk:worldslash");
        LEGACY = Map.copyOf(reverse);
    }

    private AbilityIdMigration() { }

    /** @return the canonical id, or the input unchanged when it was never renamed. */
    public static String apply(String raw) {
        if (raw == null) return null;
        String key = raw.trim().toLowerCase(Locale.ROOT);
        return RENAMES.getOrDefault(key, raw);
    }

    public static boolean isLegacy(String raw) {
        if (raw == null) return false;
        String key = raw.trim().toLowerCase(Locale.ROOT);
        String canonical = RENAMES.get(key);
        return canonical != null && !canonical.equals(key);
    }

    /** @return a legacy spelling for a canonical id, or null when none is known. */
    public static String legacyOf(String canonical) {
        if (canonical == null) return null;
        return LEGACY.get(canonical.trim().toLowerCase(Locale.ROOT));
    }

    /** Canonicalises every map value in place without removing or nulling entries. */
    public static void applyAll(Map<String, String> values) {
        if (values == null) return;
        for (Map.Entry<String, String> entry : new HashMap<>(values).entrySet()) {
            String original = entry.getValue();
            String canonical = apply(original);
            if (!Objects.equals(original, canonical)) values.put(entry.getKey(), canonical);
        }
    }
}
