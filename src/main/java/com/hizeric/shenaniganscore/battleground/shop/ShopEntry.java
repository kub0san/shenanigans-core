package com.hizeric.shenaniganscore.battleground.shop;

import org.bukkit.Material;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * One catalog row: a custom item, an ability, or an ultimate.
 *
 * <p>A purchase grants permanent ownership. Ability and ultimate rows additionally carry a
 * killstreak requirement that is checked at cast time; custom items have no streak gate.</p>
 */
public record ShopEntry(
        String id,
        Category category,
        List<String> tags,
        Material icon,
        String displayName,
        int price,
        boolean enabled,
        String abilityId,
        Map<String, Object> itemData,
        int streakRequired
) {
    public enum Category { ABILITY, ITEM, ULTIMATE }

    public enum Filter { ALL, CUSTOM_ITEMS, ABILITIES, ULTIMATES }

    /** The observable state of one entry for one player. */
    public record Gate(boolean owned, int currentStreak, int required, int price) {
        public boolean canUse() {
            return owned && currentStreak >= Math.max(0, required);
        }

        /** Clamped progress toward the streak requirement, for action-bar and lore display. */
        public int progress() {
            return Math.min(Math.max(0, currentStreak), Math.max(0, required));
        }
    }

    public ShopEntry {
        if (category == null) throw new IllegalArgumentException("category");
        tags = tags == null ? List.of() : List.copyOf(tags);
        itemData = itemData == null
                ? Map.of()
                : Collections.unmodifiableMap(new LinkedHashMap<>(itemData));
    }

    /**
     * Strips the namespace from an ability id so the catalog can match both the namespaced form
     * used by the registry/cast path ({@code jjk:blue}) and the bare form older shop files store.
     */
    public static String abilityKey(String idOrAbilityId) {
        if (idOrAbilityId == null) return null;
        int separator = idOrAbilityId.indexOf(':');
        return separator < 0 ? idOrAbilityId : idOrAbilityId.substring(separator + 1);
    }

    /**
     * Case-insensitive ability-id match. Bare queries match by value part so admin commands can
     * pass either form; two different namespaced ids never match just because their value parts
     * collide.
     */
    public static boolean abilityIdMatches(String query, String stored) {
        if (query == null || stored == null) return false;
        if (query.equalsIgnoreCase(stored)) return true;
        if (query.indexOf(':') < 0) {
            String storedKey = abilityKey(stored);
            return storedKey != null && query.equalsIgnoreCase(storedKey);
        }
        if (stored.indexOf(':') < 0) {
            String queryKey = abilityKey(query);
            return queryKey != null && queryKey.equalsIgnoreCase(stored);
        }
        return false;
    }

    public static Category parseCategory(String raw) {
        if (raw == null) return null;
        for (Category value : Category.values()) {
            if (value.name().equalsIgnoreCase(raw.trim())) return value;
        }
        return null;
    }

    public static ShopEntry ability(String id, Material icon, String displayName, int price, String abilityId,
                                    boolean jujutsu) {
        return new ShopEntry(id, Category.ABILITY, jujutsu ? List.of("jujutsu") : List.of(),
                icon, displayName, price, true, abilityId, Map.of(), 0);
    }

    public static ShopEntry ability(String id, Material icon, String displayName, int price,
                                    int streakRequired, String abilityId) {
        return new ShopEntry(id, Category.ABILITY, List.of(), icon, displayName, price,
                true, abilityId, Map.of(), streakRequired);
    }

    /**
     * The default killstreak requirement for a freshly seeded ability row: {@code max(1, price / 40)}.
     *
     * <p>Higher-priced techniques ask for a longer streak before they can be cast, so the killstreak
     * stays the balance lever the spec calls for. The minimum of 1 guarantees every seeded ability
     * engages the gate at all (a zero would disable it). Per-ability numbers are a builder's call
     * (design doc); this is the curve the shipped seed uses.</p>
     */
    public static int defaultStreak(int price) {
        return Math.max(1, price / 40);
    }

    public static ShopEntry item(String id, Material icon, String displayName, int price,
                                 Map<String, Object> itemData) {
        return customItem(id, icon, displayName, price, 0, itemData);
    }

    public static ShopEntry customItem(String id, Material icon, String displayName, int price,
                                       int streakRequired, Map<String, Object> itemData) {
        return customItem(id, icon, displayName, price, streakRequired, null, itemData);
    }

    /**
     * A custom item that carries its own ability. The ability is not sold separately: holding the
     * item is what binds and gates it, so the id lives with the item row.
     */
    public static ShopEntry customItem(String id, Material icon, String displayName, int price,
                                       String abilityId, Map<String, Object> itemData) {
        return customItem(id, icon, displayName, price, 0, abilityId, itemData);
    }

    public static ShopEntry customItem(String id, Material icon, String displayName, int price,
                                       int streakRequired, String abilityId,
                                       Map<String, Object> itemData) {
        return new ShopEntry(id, Category.ITEM, List.of(), icon, displayName, price,
                true, abilityId, itemData, streakRequired);
    }

    public static ShopEntry ultimate(String id, Material icon, String displayName, int price,
                                     int streakRequired, String abilityId) {
        return new ShopEntry(id, Category.ULTIMATE, List.of(), icon, displayName, price,
                true, abilityId, Map.of(), streakRequired);
    }

    public boolean isAbility() {
        return category == Category.ABILITY;
    }

    public boolean isUltimate() {
        return category == Category.ULTIMATE;
    }

    public boolean isCustomItem() {
        return category == Category.ITEM;
    }

    /** Evaluates both gates for one player: ownership and the current killstreak. */
    public Gate gate(boolean owned, int currentStreak) {
        return new Gate(owned, currentStreak, streakRequired, price);
    }

    public boolean matches(Filter filter) {
        return switch (filter) {
            case ALL -> true;
            case ABILITIES -> isAbility();
            case CUSTOM_ITEMS -> isCustomItem();
            case ULTIMATES -> isUltimate();
        };
    }

    /** Pure validation used before persisting or installing a catalogue. */
    public static List<String> validate(List<ShopEntry> entries) {
        List<String> problems = new ArrayList<>();
        Set<String> ids = new HashSet<>();
        for (ShopEntry entry : entries) {
            if (entry == null) {
                problems.add("A null entry was present.");
                continue;
            }
            if (entry.id() == null || entry.id().isBlank()) {
                problems.add("An entry has a blank id.");
            } else if (!ids.add(entry.id().toLowerCase(Locale.ROOT))) {
                problems.add("Duplicate id: " + entry.id());
            }
            if (entry.price() < 0) {
                problems.add("Negative price for " + entry.id());
            }
            if (entry.streakRequired() < 0) {
                problems.add("Negative streak requirement for " + entry.id());
            }
            if (entry.category() == null) {
                problems.add("No category for " + entry.id());
            }
            if (entry.icon() == null) {
                problems.add("No icon for " + entry.id());
            }
            if (entry.displayName() == null || entry.displayName().isBlank()) {
                problems.add("Blank display name for " + entry.id());
            }
            if (entry.isAbility() || entry.isUltimate()) {
                if (entry.abilityId() == null || entry.abilityId().isBlank()) {
                    problems.add((entry.isUltimate() ? "Ultimate " : "Ability ") + entry.id()
                            + " has no ability id.");
                }
            }
            if (entry.isCustomItem() && entry.itemData().isEmpty()) {
                problems.add("Custom item " + entry.id() + " has no item data.");
            }
        }
        return problems;
    }

    public static List<ShopEntry> filter(List<ShopEntry> entries, Filter filter) {
        return entries.stream().filter(e -> e.enabled() && e.matches(filter)).toList();
    }
}
