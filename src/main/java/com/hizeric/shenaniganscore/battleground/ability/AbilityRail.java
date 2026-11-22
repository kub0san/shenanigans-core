package com.hizeric.shenaniganscore.battleground.ability;

import com.hizeric.shenaniganscore.core.ability.AbilityId;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * The loadout bind state: three command slots.
 *
 * <p>Slot 0 mirrors the held custom item's ability, slot 1 is the normal ability, and slot 2 is the
 * ultimate. Casting is command-only via {@code /ability 1..3}. No item or inventory slot is
 * involved in binding.</p>
 */
public final class AbilityRail {

    public static final int HELD_SLOT = 0;
    public static final int TOTAL = 3;
    public static final int NORMAL_SLOT = 1;
    public static final int ULTIMATE_SLOT = 2;

    private final AbilityId[] slots = new AbilityId[TOTAL];
    private int version;

    public AbilityId get(int slot) {
        return slots[check(slot)];
    }

    /** Versioned write; an equal value is a no-op so dirty tracking stays honest. */
    public void set(int slot, AbilityId id) {
        int index = check(slot);
        if (Objects.equals(slots[index], id)) return;
        slots[index] = id;
        version++;
    }

    public AbilityId held() {
        return get(HELD_SLOT);
    }

    public AbilityId normal() {
        return get(NORMAL_SLOT);
    }

    public AbilityId ultimate() {
        return get(ULTIMATE_SLOT);
    }

    public int version() {
        return version;
    }

    /** @return an independent snapshot with the same binds and version. */
    public AbilityRail copy() {
        AbilityRail copy = new AbilityRail();
        System.arraycopy(slots, 0, copy.slots, 0, TOTAL);
        copy.version = version;
        return copy;
    }

    public void clear() {
        for (int i = 0; i < TOTAL; i++) set(i, null);
    }

    public List<AbilityId> bound() {
        List<AbilityId> result = new ArrayList<>();
        for (int i = 0; i < TOTAL; i++) {
            AbilityId id = get(i);
            if (id != null) result.add(id);
        }
        return result;
    }

    public boolean contains(AbilityId id) {
        return id != null && indexOf(id) >= 0;
    }

    public int indexOf(AbilityId id) {
        if (id == null) return -1;
        for (int i = 0; i < TOTAL; i++) {
            if (id.equals(get(i))) return i;
        }
        return -1;
    }

    private static int check(int slot) {
        if (slot < 0 || slot >= TOTAL) throw new IndexOutOfBoundsException("rail slot " + slot);
        return slot;
    }
}
