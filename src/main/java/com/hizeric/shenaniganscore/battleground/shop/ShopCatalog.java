package com.hizeric.shenaniganscore.battleground.shop;

import com.hizeric.shenaniganscore.battleground.ability.AbilityIdMigration;
import com.hizeric.shenaniganscore.battleground.item.BattlegroundItemKeys;
import com.hizeric.shenaniganscore.origins.minecraft.weapons.dashsword.DashSwordId;
import com.hizeric.shenaniganscore.origins.minecraft.weapons.dashsword.DashSwordItem;
import com.hizeric.shenaniganscore.origins.minecraft.weapons.executionsword.ExecutionerSwordId;
import com.hizeric.shenaniganscore.origins.minecraft.weapons.executionsword.ExecutionerSwordItem;
import com.hizeric.shenaniganscore.battleground.storage.Databases;
import com.hizeric.shenaniganscore.battleground.storage.Database;
import com.hizeric.shenaniganscore.battleground.storage.ShopRepository;
import com.hizeric.shenaniganscore.battleground.storage.StorageException;
import com.hizeric.shenaniganscore.core.ability.AbilityDefinition;
import com.hizeric.shenaniganscore.core.ability.AbilityId;
import com.hizeric.shenaniganscore.core.ability.AbilityRegistry;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import java.util.logging.Level;

/** The live shop catalogue, backed by SQL ({@code shop_entries}). Never reads {@code shop.yml}. */
public final class ShopCatalog {
    private final Plugin plugin;
    private final ShopRepository repository;
    private volatile List<ShopEntry> entries = List.of();

    /** Resolves the database installed by the plugin lifecycle. */
    public ShopCatalog(Plugin plugin) {
        this(plugin, Databases.require());
    }

    /** Explicit injection for callers/tests that own the database lifecycle. */
    public ShopCatalog(Plugin plugin, Database database) {
        this.plugin = plugin;
        this.repository = new ShopRepository(database);
    }

    /** Loads stored rows, or seeds a fresh database from the ability registry. */
    public void load(AbilityRegistry registry) {
        load(registry, seededItems());
    }

    /**
     * Same pipeline with an injected built-in-item seed. Package-private so JUnit can drive a
     * fresh load without constructing a Bukkit {@code ItemStack} for the production Dash Sword.
     */
    void load(AbilityRegistry registry, List<ShopEntry> seededItems) {
        boolean migrated = false;
        try {
            List<ShopEntry> stored = repository.load();
            migrated = stored.stream().anyMatch(entry -> entry != null && entry.abilityId() != null
                    && !AbilityIdMigration.apply(entry.abilityId()).equals(entry.abilityId()));
            entries = new ArrayList<>(stored.stream().map(ShopCatalog::migrateEntry).toList());
        } catch (StorageException e) {
            warn("Could not load the shop catalogue from the database", e);
            entries = new ArrayList<>();
        }
        if (entries.isEmpty() && registry != null) seed(registry);
        // Existing servers have a persisted shop: migrate legacy ids first, then merge in newly
        // added ability rows and the built-in custom item. All merges are idempotent and never
        // overwrite admin edits. Compute each one - || would short-circuit the others.
        boolean legacyMigrated = migrateLegacyIds(entries, seededItems);
        boolean upserted = upsertAll(entries, seededItems);
        boolean changed = migrated || legacyMigrated || upserted;
        if (registry != null && upsertAbilities(entries, seededAbilities(registry))) changed = true;
        if (changed) save();
    }

    /**
     * Adds catalogue rows for abilities that are not represented yet. An existing row (matched by
     * ability id, with or without namespace) is left untouched so an admin's price/streak edits
     * survive. This is how a newly connected technique reaches servers with an existing SQL shop.
     *
     * @return true when at least one row was added
     */
    static boolean upsertAbilities(List<ShopEntry> entries, List<ShopEntry> seeded) {
        boolean changed = false;
        for (ShopEntry ability : seeded) {
            boolean present = entries.stream().anyMatch(existing -> existing.abilityId() != null
                    && ShopEntry.abilityIdMatches(existing.abilityId(), ability.abilityId()));
            if (!present) {
                entries.add(ability);
                changed = true;
            }
        }
        return changed;
    }

    /** Rewrites a stored row's ability id to its canonical spelling at load time. */
    private static ShopEntry migrateEntry(ShopEntry entry) {
        if (entry == null || entry.abilityId() == null) return entry;
        String migrated = AbilityIdMigration.apply(entry.abilityId());
        if (migrated.equals(entry.abilityId())) return entry;
        return new ShopEntry(entry.id(), entry.category(), entry.tags(), entry.icon(),
                entry.displayName(), entry.price(), entry.enabled(), migrated,
                entry.itemData(), entry.streakRequired());
    }

    /**
     * Every seeded custom item in one place: its catalogue id, the ability it grants, and a
     * factory for the delivered stack. Adding a custom item requires one entry here — both the
     * catalog seed and the "is this ability sold through an item?" check read this list.
     */
    private record SeededItem(String id, AbilityId abilityId, Supplier<ShopEntry> factory) { }

    private static final List<SeededItem> SEEDED_ITEMS = List.of(
            new SeededItem(DashSwordId.NEW_ID, DashSwordItem.ABILITY_ID, DashSwordItem::catalogEntry),
            new SeededItem(ExecutionerSwordId.NEW_ID, ExecutionerSwordItem.ABILITY_ID, ExecutionerSwordItem::catalogEntry));

    private static List<ShopEntry> seededItems() {
        return SEEDED_ITEMS.stream().map(item -> item.factory().get()).toList();
    }

    /** Pure id check for {@link #seededItems()} — callable in JUnit without a Paper registry. */
    private static boolean soldAsSeededItem(String fullId) {
        return SEEDED_ITEMS.stream()
                .anyMatch(item -> ShopEntry.abilityIdMatches(item.abilityId().toString(), fullId));
    }

    /**
     * One-time migration of the legacy Dash Sword catalogue id ({@code dash_sword}) to the
     * canonical {@code minecraft:dash_sword}. Retained as a pure, server-free seam for tests;
     * a fresh SQL database has no legacy ids to migrate.
     */
    static boolean migrateLegacyIds(List<ShopEntry> entries, List<ShopEntry> seeded) {
        ShopEntry seed = seeded.stream()
                .filter(e -> DashSwordId.NEW_ID.equals(e.id())).findFirst().orElse(null);
        boolean changed = false;
        for (int i = 0; i < entries.size(); i++) {
            ShopEntry entry = entries.get(i);
            String migratedId = DashSwordId.migrate(entry.id());
            if (migratedId.equals(entry.id())) continue;
            var itemData = seed != null ? seed.itemData() : entry.itemData();
            entries.set(i, new ShopEntry(
                    DashSwordId.NEW_ID, entry.category(), entry.tags(), entry.icon(),
                    entry.displayName(), entry.price(), entry.enabled(), entry.abilityId(),
                    itemData, entry.streakRequired()));
            changed = true;
        }
        return changed;
    }

    /**
     * Merges the built-in custom items into a loaded catalogue.
     *
     * <p>An older seed (or an admin experiment) can leave an ABILITY row for an ability that is
     * really carried by an item. That row must be replaced, not merely coexisting: otherwise the
     * item never reaches the market and the "free" ability is castable without owning it.</p>
     *
     * @return true when the catalogue actually changed
     */
    static boolean upsertAll(List<ShopEntry> entries, List<ShopEntry> seeded) {
        boolean changed = false;
        for (ShopEntry item : seeded) {
            boolean itemAlreadyPresent = false;
            for (int i = entries.size() - 1; i >= 0; i--) {
                ShopEntry existing = entries.get(i);
                if (existing.isCustomItem() && existing.id().equalsIgnoreCase(item.id())) {
                    itemAlreadyPresent = true;
                    // Refresh code-owned payload while preserving admin-edited fields, so item fixes
                    // (attributes, lore, enchantments) reach servers with an existing catalogue.
                    ShopEntry refreshed = new ShopEntry(existing.id(), item.category(), existing.tags(),
                            item.icon(), existing.displayName(), existing.price(), existing.enabled(),
                            item.abilityId(), item.itemData(), existing.streakRequired());
                    if (!refreshed.equals(existing)) {
                        entries.set(i, refreshed);
                        changed = true;
                    }
                    continue;
                }
                if (!existing.isCustomItem() && item.abilityId() != null
                        && existing.abilityId() != null
                        && ShopEntry.abilityIdMatches(existing.abilityId(), item.abilityId())) {
                    entries.remove(i);
                    changed = true;
                }
            }
            if (!itemAlreadyPresent) {
                entries.add(item);
                changed = true;
            }
        }
        return changed;
    }

    private void seed(AbilityRegistry registry) {
        // The load() pipeline below merges into entries in place (set/add). An immutable seed
        // made every fresh database throw UnsupportedOperationException at plugin enable.
        entries = new ArrayList<>(seededAbilities(registry));
    }

    /**
     * The ability rows a fresh shop is seeded with, also used to backfill existing shops.
     * Package-private so the seed test can pin that hidden abilities never reach the market.
     */
    static List<ShopEntry> seededAbilities(AbilityRegistry registry) {
        List<ShopEntry> seeded = new ArrayList<>();
        for (AbilityDefinition definition : registry.all()) {
            if (definition.hidden()) continue;
            String value = definition.id().value();
            String fullId = definition.id().toString();
            // An item's ability is sold through the item row, never as a free standalone ability.
            if (soldAsSeededItem(fullId)) continue;
            String name = PlainTextComponentSerializer.plainText().serialize(definition.displayName());
            // An ability's row is an ability row (not a custom item), so it carries the killstreak gate.
            // The default requirement scales with price; see ShopEntry.defaultStreak.
            int streak = ShopEntry.defaultStreak(definition.price());
            seeded.add(new ShopEntry(value.replace(':', '_'), ShopEntry.Category.ABILITY,
                    fullId.startsWith("jjk:") ? List.of("jujutsu") : List.of(),
                    definition.iconMaterial(), name, definition.price(), true, fullId, Map.of(), streak));
        }
        return seeded;
    }

    public List<ShopEntry> entries() {
        return entries;
    }

    /** The live catalog row for an exact entry id, or null if it was removed. Ids are case-sensitive. */
    public ShopEntry entry(String id) {
        if (id == null) return null;
        for (ShopEntry entry : entries) {
            if (id.equals(entry.id())) return entry;
        }
        return null;
    }

    /**
     * The catalog row that grants an ability id, if one exists. Item rows are included: a custom
     * item's ability is sold through the item, not as a separate ability row. Disabled rows are
     * returned so callers can decide whether disabled means hidden or blocked.
     */
    public ShopEntry entryForAbility(String abilityId) {
        if (abilityId == null) return null;
        for (ShopEntry entry : entries) {
            if (entry.abilityId() != null && ShopEntry.abilityIdMatches(abilityId, entry.abilityId())) {
                return entry;
            }
        }
        return null;
    }

    /**
     * The custom-item row a stack belongs to. Identity comes from the persistent-data stamp, not
     * {@code isSimilar}, so catalogue edits cannot silently break held-item recognition.
     */
    public ShopEntry customItemFor(ItemStack stack) {
        String itemId = BattlegroundItemKeys.idOf(stack);
        if (itemId == null) return null;
        return entries.stream()
                .filter(e -> e.isCustomItem() && e.enabled() && itemId.equals(e.id()))
                .findFirst().orElse(null);
    }

    /** Writes the current entries as one atomic replace. */
    public boolean save() {
        List<ShopEntry> snapshot = entries;
        List<String> problems = ShopEntry.validate(snapshot);
        if (!problems.isEmpty()) {
            warn("Refusing to save an invalid shop catalogue: " + String.join("; ", problems), null);
            return false;
        }
        try {
            repository.save(snapshot);
            return true;
        } catch (StorageException e) {
            warn("Could not save the shop catalogue to the database", e);
            return false;
        }
    }

    /** Rebuild a card's icon stack from an item entry, or null for abilities/bad data. */
    public ItemStack itemStack(ShopEntry entry) {
        if (entry == null || entry.isAbility() || entry.itemData().isEmpty()) return null;
        try {
            return ItemStack.deserialize(entry.itemData());
        } catch (RuntimeException e) {
            warn("Could not rebuild item stack for entry " + entry.id(), e);
            return null;
        }
    }

    private void warn(String message, Throwable cause) {
        if (plugin != null) plugin.getLogger().log(Level.SEVERE, message, cause);
        else { System.err.println(message); cause.printStackTrace(System.err); }
    }
}
