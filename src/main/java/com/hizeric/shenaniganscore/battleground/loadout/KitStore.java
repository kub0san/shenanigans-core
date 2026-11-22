package com.hizeric.shenaniganscore.battleground.loadout;

import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

/**
 * The single server kit, owned by the plugin lifecycle instead of a static field.
 *
 * <p>Slots are cloned on read and on write, so no caller ever holds a reference into the store.
 * {@link #version()} is bumped only on a real change.</p>
 */
public final class KitStore {

    public record Snapshot(ItemStack[] slots, long version) {
        public Snapshot {
            slots = cloneItems(slots, slots == null ? 0 : slots.length);
        }
        @Override
        public ItemStack[] slots() {
            return cloneItems(slots, slots == null ? 0 : slots.length);
        }
    }

    private volatile ItemStack[] slots;
    private final AtomicLong version = new AtomicLong();
    private final int boardSize;
    private final ItemStack[] defaults;

    public KitStore(int boardSize, ItemStack[] defaults) {
        this.boardSize = Math.max(1, boardSize);
        this.defaults = cloneItems(defaults, this.boardSize);
        this.slots = cloneItems(defaults, this.boardSize);
    }

    public int boardSize() { return boardSize; }
    public long version() { return version.get(); }

    /** Defensive copy of the current kit. */
    public ItemStack[] kit() { return cloneItems(slots, boardSize); }

    public Snapshot snapshot() {
        return new Snapshot(slots, version.get());
    }

    /** Replaces the kit. Returns the new version, or the current one if nothing changed. */
    public long replace(ItemStack[] next) {
        ItemStack[] candidate = cloneItems(next, boardSize);
        if (sameContents(slots, candidate)) return version.get();
        slots = candidate;
        return version.incrementAndGet();
    }

    /** Resets to the built-in defaults. */
    public long reset() {
        return replace(defaults);
    }

    public boolean allowed(ItemStack candidate) {
        if (candidate == null || candidate.getType().isAir()) return false;
        for (ItemStack allowed : slots) {
            if (allowed != null && allowed.isSimilar(candidate)) return true;
        }
        return false;
    }

    public List<ItemStack> palette() {
        List<ItemStack> result = new ArrayList<>();
        for (ItemStack item : slots) {
            if (item != null && !item.getType().isAir()) result.add(item.clone());
        }
        return result;
    }

    private static ItemStack[] cloneItems(ItemStack[] source, int size) {
        ItemStack[] copy = new ItemStack[size];
        if (source == null) return copy;
        int limit = Math.min(size, source.length);
        for (int i = 0; i < limit; i++) {
            ItemStack item = source[i];
            copy[i] = (item == null || item.getType().isAir()) ? null : item.clone();
        }
        return copy;
    }

    private static boolean sameContents(ItemStack[] a, ItemStack[] b) {
        if (a.length != b.length) return false;
        for (int i = 0; i < a.length; i++) {
            ItemStack x = a[i], y = b[i];
            if (x == null && y == null) continue;
            if (x == null || y == null) return false;
            if (!x.isSimilar(y)) return false;
        }
        return true;
    }
}