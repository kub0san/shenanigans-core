package com.hizeric.shenaniganscore.battleground.menu.base;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Pure paging maths — no Bukkit runtime required. */
class GuiPagerTest {

    @Test
    void clampsRequestedPageIntoRange() {
        assertEquals(0, GuiPager.of(-3, 20, 9).currentPage());
        assertEquals(2, GuiPager.of(99, 20, 9).currentPage());
        assertEquals(1, GuiPager.of(1, 20, 9).currentPage());
    }

    @Test
    void emptySourceStillHasOnePage() {
        GuiPager pager = GuiPager.of(5, 0, 9);
        assertEquals(1, pager.maxPages());
        assertEquals(0, pager.currentPage());
        assertTrue(pager.slice(List.of()).isEmpty());
    }

    @Test
    void slicesOnlyTheCurrentPage() {
        List<String> items = List.of("a", "b", "c", "d", "e", "f", "g", "h", "i", "j");
        GuiPager first = GuiPager.of(0, items.size(), 9);
        assertEquals(List.of("a", "b", "c", "d", "e", "f", "g", "h", "i"), first.slice(items));

        GuiPager second = GuiPager.of(1, items.size(), 9);
        assertEquals(List.of("j"), second.slice(items));
    }

    @Test
    void noArgConstructorIsRejectedInFavourOfExplicitFactories() {
        assertThrows(UnsupportedOperationException.class, () -> new GuiPager(0, 3));

        GuiPager disabled = GuiPager.disabled();
        assertEquals(1, disabled.maxPages());
        assertEquals(0, disabled.from());
        List<String> items = List.of("a", "b");
        assertEquals(items, disabled.slice(items));
    }

    @Test
    void fromAndToAreBoundedByTheSourceSize() {
        GuiPager pager = GuiPager.of(1, 4, 3);
        assertEquals(3, pager.from());
        assertEquals(4, pager.to(4));   // a 4-item list truncates the [3,6) window
        assertEquals(6, pager.to(100));  // a longer list fills the full [3,6) window
    }
}
