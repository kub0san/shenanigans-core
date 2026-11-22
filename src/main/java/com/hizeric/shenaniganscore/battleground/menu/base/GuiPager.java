package com.hizeric.shenaniganscore.battleground.menu.base;

import java.util.List;

/**
 * Pure paging state: clamps the page and slices lists. Draws nothing;
 * {@link GuiDock} renders the controls.
 */
public final class GuiPager {
    private final int currentPage;
    private final int maxPages;
    private final int perPage;

    /**
     * @deprecated A pager without a page size silently disables slicing ({@link #slice(List)}
     *             returns the whole list and {@link #from()} is always 0). Use
     *             {@link #of(int, int, int)} for paged lists, or {@link #disabled()} when a
     *             non-paging pager is genuinely intended.
     */
    @Deprecated
    public GuiPager(int currentPage, int maxPages) {
        throw new UnsupportedOperationException("Use of(requestedPage, totalItems, perPage) or disabled()");
    }

    private GuiPager(int currentPage, int maxPages, int perPage) {
        this.maxPages = Math.max(1, maxPages);
        this.currentPage = Math.max(0, Math.min(currentPage, this.maxPages - 1));
        this.perPage = perPage;
    }

    /** A single-page, non-paging pager: {@link #slice(List)} returns the whole list. */
    public static GuiPager disabled() {
        return new GuiPager(0, 1, 0);
    }

    public static GuiPager of(int requestedPage, int totalItems, int perPage) {
        int per = Math.max(1, perPage);
        int pages = Math.max(1, (totalItems + per - 1) / per);
        return new GuiPager(requestedPage, pages, per);
    }

    public int currentPage() {
        return currentPage;
    }

    public int maxPages() {
        return maxPages;
    }

    /** First index (inclusive) of the current page. */
    public int from() {
        return currentPage * perPage;
    }

    /** Last index (exclusive) of the current page for a list of {@code total} items. */
    public int to(int total) {
        return perPage <= 0 ? total : Math.min(total, from() + perPage);
    }

    public <T> List<T> slice(List<T> items) {
        if (perPage <= 0) return items;
        int start = Math.min(from(), items.size());
        return items.subList(start, to(items.size()));
    }
}
