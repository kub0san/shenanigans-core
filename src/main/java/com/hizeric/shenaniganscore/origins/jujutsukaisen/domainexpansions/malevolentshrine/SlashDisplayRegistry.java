package com.hizeric.shenaniganscore.origins.jujutsukaisen.domainexpansions.malevolentshrine;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Predicate;

/**
 * Per-session ownership of the slash display entities the shrine spawns.
 *
 * <p>Slashes normally self-remove, but a forced close or caster death must not
 * leave any of their displays behind. The registry lives on the shrine
 * {@link Session} (no global map) and is disposed from its teardown path. The
 * handle is deliberately generic so the ownership decisions are testable
 * without a live server.</p>
 */
final class SlashDisplayRegistry {

    private final Set<Owned> owned = ConcurrentHashMap.newKeySet();

    /** Remembers one display. Already-invalid displays are never retained. */
    <T> void own(T display, Predicate<T> valid, Consumer<T> remover) {
        if (display == null || valid == null || remover == null) return;
        // Self-removing displays drop out of the registry naturally.
        owned.removeIf(entry -> !entry.valid());
        Owned entry = new Owned(() -> valid.test(display), () -> remover.accept(display));
        if (entry.valid()) owned.add(entry);
    }

    /** Diagnostics / tests: how many live displays this session still owns. */
    int ownedCount() {
        return owned.size();
    }

    /** Removes every owned display, including ones from delayed stages still in flight. */
    void dispose() {
        for (Owned entry : owned) {
            // A slash may have self-removed since registration; do not remove it twice.
            if (entry.valid()) entry.remove();
        }
        owned.clear();
    }

    private static final class Owned {
        private final BooleanSupplier valid;
        private final Runnable remover;

        Owned(BooleanSupplier valid, Runnable remover) {
            this.valid = valid;
            this.remover = remover;
        }

        boolean valid() {
            return valid.getAsBoolean();
        }

        void remove() {
            remover.run();
        }
    }
}
