package com.hizeric.shenaniganscore.battleground.loadout;

import com.hizeric.shenaniganscore.battleground.item.BattlegroundItemKeys;
import com.hizeric.shenaniganscore.battleground.shop.ShopCatalog;
import com.hizeric.shenaniganscore.battleground.shop.ShopEntry;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.List;

/**
 * Pure sanitizer for a player's 41-slot loadout (issue #65).
 *
 * <p>A saved layout is untrusted data: it is edited in the player's real inventory and persisted
 * as a Base64 item array, so a tampered save could smuggle an arbitrary item (for example a mace)
 * into the arena. A slot is kept only when it is the kit's own default stack for that slot, or when
 * its persistent stamp resolves to an <em>enabled</em> catalog custom item the player <em>owns</em>
 * and whose stack <em>matches the canonical catalog stack</em>. Everything else is replaced with
 * that slot's kit default; null and air slots are left alone.</p>
 *
 * <p>No Bukkit server, plugin or inventory is touched here — catalog and ownership questions are
 * supplied as small functional seams so every branch is unit-testable. The caller wires the live
 * {@link ShopCatalog} through {@link #catalogLookup(ShopCatalog)} and the player record through its
 * {@code owns} method. This class never calls {@code addItem}: a rejected slot is simply replaced
 * in the returned board.</p>
 */
public final class LoadoutPolicy {

    /** Number of slots in a loadout board: hotbar, main inventory, armour and off-hand. */
    public static final int SLOTS = KitDefaults.BOARD_SIZE;

    /** Resolves a stamped item id to its enabled canonical catalog deliverable, or null. */
    @FunctionalInterface
    public interface CatalogLookup {
        CustomItem enabledCustomItem(String stampedId);
    }

    /** Ownership question for one catalog entry id. */
    @FunctionalInterface
    public interface OwnershipLookup {
        boolean owns(String entryId);
    }

    /** An enabled custom-item row: its catalog id and the canonical stack the catalog delivers. */
    public record CustomItem(String entryId, ItemStack stack) {
        public CustomItem {
            stack = stack == null ? null : stack.clone();
        }
        @Override
        public ItemStack stack() {
            return stack == null ? null : stack.clone();
        }
    }

    /** A sanitised board plus a single summary warning when anything was replaced. */
    public record Result(ItemStack[] slots, int replaced, List<String> warnings) {
        public Result {
            slots = slots == null ? new ItemStack[0] : slots.clone();
            warnings = warnings == null ? List.of() : List.copyOf(warnings);
        }
        @Override
        public ItemStack[] slots() {
            return slots.clone();
        }
    }

    private LoadoutPolicy() { }

    /** One deterministic message per sanitization, carrying the replaced count. */
    public static String warning(int replaced) {
        return "loadout: replaced " + replaced + " disallowed slot(s) with kit defaults";
    }

    /**
     * Replace every disallowed slot of {@code input} with the matching {@code kitDefault} slot.
     */
    public static Result sanitize(ItemStack[] input, ItemStack[] kitDefault,
                                  CatalogLookup catalog, OwnershipLookup ownership) {
        ItemStack[] out = new ItemStack[SLOTS];
        int replaced = 0;
        for (int i = 0; i < SLOTS; i++) {
            ItemStack item = input != null && i < input.length ? input[i] : null;
            ItemStack fallback = kitDefault != null && i < kitDefault.length ? kitDefault[i] : null;

            // An empty slot stays empty: there is nothing to smuggle and nothing to restore.
            if (item == null || item.getType().isAir()) {
                out[i] = cloneOrNull(item);
                continue;
            }
            if (allowed(item, fallback, catalog, ownership)) {
                out[i] = item.clone();
                continue;
            }
            out[i] = cloneOrNull(fallback);
            replaced++;
        }
        List<String> warnings = replaced == 0 ? List.of() : List.of(warning(replaced));
        return new Result(out, replaced, warnings);
    }

    private static boolean allowed(ItemStack item, ItemStack fallback,
                                   CatalogLookup catalog, OwnershipLookup ownership) {
        // isSimilar ignores the amount, so require it explicitly for both gates: a forged
        // 64-stack must not pass as the one-item deliverable the player actually owns.
        if (fallback != null && fallback.isSimilar(item)
                && fallback.getAmount() == item.getAmount()) return true;
        String stampedId = BattlegroundItemKeys.idOf(item);
        if (stampedId == null || catalog == null || ownership == null) return false;
        CustomItem custom = catalog.enabledCustomItem(stampedId);
        if (custom == null) return false;
        ItemStack canonical = custom.stack();
        if (canonical == null || canonical.getType().isAir()) return false;
        if (!ownership.owns(custom.entryId())) return false;
        // A pre-migration save carries the old stamp while the catalog row is canonical. Compare
        // against a canonical clone restamped with this item's own id so only the stamp is forgiven:
        // type, enchants, lore and every other metadata key still have to match the catalog stack.
        ItemStack expected = canonical.clone();
        ItemMeta meta = expected.getItemMeta();
        if (meta == null) return false;
        BattlegroundItemKeys.stamp(meta, stampedId);
        expected.setItemMeta(meta);
        return expected.isSimilar(item) && canonical.getAmount() == item.getAmount();
    }

    /**
     * Adapts the live catalog to the pure seam. Stamps from a pre-migration layout are resolved
     * through the catalog's own id migration, so an old sword still finds its canonical row;
     * disabled, absent and non-item rows still resolve to null.
     */
    public static CatalogLookup catalogLookup(ShopCatalog catalog) {
        return stampedId -> {
            if (catalog == null || stampedId == null) return null;
            ShopEntry entry = catalog.entry(ShopCatalog.canonicalCustomItemId(stampedId));
            if (entry == null || !entry.enabled() || !entry.isCustomItem()) return null;
            ItemStack canonical = catalog.itemStack(entry);
            if (canonical == null || canonical.getType().isAir()) return null;
            return new CustomItem(entry.id(), canonical);
        };
    }

    private static ItemStack cloneOrNull(ItemStack item) {
        return item == null ? null : item.clone();
    }
}
