package com.hizeric.shenaniganscore.battleground.ability;

import com.hizeric.shenaniganscore.core.ability.AbilityId;

import java.util.HashSet;
import java.util.Set;
import java.util.function.Predicate;
import java.util.logging.Logger;

/**
 * Pure bind decisions for the three-slot rail. Slot 0 is the held item's ability and
 * is never planned here; slot 1 is the normal ability; slot 2 is the ultimate and is
 * only ever occupied by a catalog ultimate. An invalid ability persisted in the
 * ultimate slot is dropped rather than promoted — that invariant kills the
 * "red becomes the ultimate" bug at the source.
 */
public final class AbilityBindPlanner {

    private static final Logger LOG = Logger.getLogger(AbilityBindPlanner.class.getName());

    public record Plan(AbilityId[] slots, Set<AbilityId> dropped, boolean fallbackUsed) {
        public boolean droppedAnything() {
            return !dropped.isEmpty();
        }
    }

    private AbilityBindPlanner() {
    }

    /**
     * @param normal    currently persisted normal bind (may be stale)
     * @param ultimate  currently persisted ultimate bind (may be stale)
     * @param valid     ids that are castable and not bound to a custom item
     * @param isUltimate whether an id is a catalog ultimate (none exist today)
     * @param firstPick fallback bind when nothing is bound
     * @return the planned slots plus diagnostics for stale binds and fallback use
     */
    public static Plan plan(AbilityId normal, AbilityId ultimate, Set<AbilityId> valid,
                            Predicate<AbilityId> isUltimate, AbilityId firstPick) {
        AbilityId[] slots = new AbilityId[AbilityRail.TOTAL];
        Set<AbilityId> dropped = new HashSet<>();

        if (normal != null) {
            if (valid.contains(normal) && !isUltimate.test(normal)) {
                slots[AbilityRail.NORMAL_SLOT] = normal;
            } else {
                dropped.add(normal);
            }
        }
        if (ultimate != null) {
            if (valid.contains(ultimate) && isUltimate.test(ultimate)) {
                slots[AbilityRail.ULTIMATE_SLOT] = ultimate;
            } else {
                dropped.add(ultimate);
            }
        }

        boolean fallbackUsed = false;
        if (slots[AbilityRail.NORMAL_SLOT] == null && eligible(firstPick, valid, isUltimate)) {
            slots[AbilityRail.NORMAL_SLOT] = firstPick;
            fallbackUsed = true;
        }

        Set<AbilityId> rejected = Set.copyOf(dropped);
        if (!rejected.isEmpty()) {
            LOG.fine(() -> "Dropped stale ability binds: " + rejected);
        }
        return new Plan(slots, rejected, fallbackUsed);
    }

    private static boolean eligible(AbilityId id, Set<AbilityId> valid, Predicate<AbilityId> isUltimate) {
        return id != null && valid.contains(id) && !isUltimate.test(id);
    }
}
