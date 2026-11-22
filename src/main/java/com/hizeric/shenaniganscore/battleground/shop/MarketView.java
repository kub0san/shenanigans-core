package com.hizeric.shenaniganscore.battleground.shop;

import com.hizeric.shenaniganscore.battleground.menu.base.GuiPager;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

/**
 * The market's pure view model: filters the catalogue by category, paginates the
 * filtered list, and computes each row's display data (price, ownership, streak
 * requirement and progress) for one player.
 *
 * <p>The GUI renders these rows and nothing else; this class holds no Bukkit state, so the
 * market's data-shaping is verified at the catalogue seam rather than by clicking a chest.</p>
 */
public final class MarketView {

    private MarketView() {
    }

    /** One rendered market row: the catalogue entry plus everything the card needs to draw it. */
    public record Row(
            ShopEntry entry,
            int price,
            boolean owned,
            int streakRequired,
            int streakProgress,
            boolean streakMet,
            boolean usable
    ) {
        /** A row the player cannot use right now — either unowned or short of the streak. */
        public boolean locked() {
            return !usable;
        }
    }

    /**
     * The requested page of rows for a category filter.
     *
     * @param catalog the full catalogue (enabled/disabled filtering happens here)
     * @param filter  the category tab the player is viewing
     * @param page    the zero-based page index; values past the end clamp to the last page
     * @param perPage rows per page
     * @param streak  the player's current killstreak
     * @param owned   predicate deciding whether the player permanently owns an entry
     */
    public static List<Row> page(List<ShopEntry> catalog, ShopEntry.Filter filter,
                                 int page, int perPage, int streak, Predicate<ShopEntry> owned) {
        return page(catalog, filter, page, perPage, streak, owned,
                ShopPolicyConfig.current().killstreakRequirements());
    }

    /** Page rows plus the pager used to compute them, so callers do not filter twice. */
    public record Page(List<Row> rows, GuiPager pager) { }

    /**
     * Same as {@link #page(List, ShopEntry.Filter, int, int, int, Predicate)} but with the
     * killstreak policy supplied by the caller. When requirements are off, every row reports
     * no streak requirement and a met gate, so the HUD and market hide the requirement.
     */
    public static List<Row> page(List<ShopEntry> catalog, ShopEntry.Filter filter,
                                 int page, int perPage, int streak, Predicate<ShopEntry> owned,
                                 boolean killstreakRequirements) {
        return pageAndPager(catalog, filter, page, perPage, streak, owned, killstreakRequirements).rows();
    }

    /** Computes rows and pager in one pass. */
    public static Page pageAndPager(List<ShopEntry> catalog, ShopEntry.Filter filter,
                                    int page, int perPage, int streak, Predicate<ShopEntry> owned,
                                    boolean killstreakRequirements) {
        List<ShopEntry> visible = ShopEntry.filter(catalog, filter);
        GuiPager pager = GuiPager.of(page, visible.size(), perPage);
        List<ShopEntry> slice = pager.slice(visible);

        List<Row> rows = new ArrayList<>(slice.size());
        for (ShopEntry entry : slice) {
            int required = killstreakRequirements ? Math.max(0, entry.streakRequired()) : 0;
            boolean own = owned != null && owned.test(entry);
            int progress = Math.min(Math.max(0, streak), required);
            boolean met = !killstreakRequirements || streak >= required;
            rows.add(new Row(entry, Math.max(0, entry.price()), own, required, progress, met,
                    own && met));
        }
        return new Page(List.copyOf(rows), pager);
    }

    /** The pager for a filter, so the GUI can draw the page controls and clamp the page index. */
    public static GuiPager pager(List<ShopEntry> catalog, ShopEntry.Filter filter, int page, int perPage) {
        return GuiPager.of(page, ShopEntry.filter(catalog, filter).size(), perPage);
    }
}