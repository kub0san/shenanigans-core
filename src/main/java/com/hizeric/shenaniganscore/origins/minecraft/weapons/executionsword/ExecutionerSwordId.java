package com.hizeric.shenaniganscore.origins.minecraft.weapons.executionsword;

import java.util.Locale;
import java.util.Set;

/**
 * The canonical id of the Executioner's Sword custom item and the one-time migration from its
 * earlier spellings.
 *
 * <p>The id shipped as {@code minecraft:executioner_sword}; the canonical spelling is
 * {@code minecraft:executioners_sword}. Both that old spelling, the bare {@code executioner_sword}
 * and the intermediate {@code minecraft:executionsword} are translated to the canonical id once at
 * load time. Existing swords carry their old id in persistent data, so recognition keeps accepting
 * every legacy spelling — without that, an already-owned sword stops resolving to its shop row.</p>
 */
public final class ExecutionerSwordId {

    /** The original, unnamespaced catalogue id (migration source only). */
    public static final String OLD_ID = "executioner_sword";

    /** The canonical {@code origin:name} id used from now on. */
    public static final String NEW_ID = "minecraft:executioners_sword";

    /** The intermediate namespaced spelling, migrated once at load time. */
    public static final String PREVIOUS_ID = "minecraft:executionsword";

    /** Every spelling that maps to {@link #NEW_ID}, including the original canonical one. */
    public static final Set<String> LEGACY_IDS = Set.of(
            OLD_ID,
            "minecraft:executioner_sword",
            PREVIOUS_ID);

    private ExecutionerSwordId() {
    }

    /** True for any historical spelling of this item's id. */
    public static boolean isLegacy(String id) {
        return id != null && LEGACY_IDS.contains(id.trim().toLowerCase(Locale.ROOT));
    }

    /** Maps a legacy id to the canonical one; leaves every other id untouched (idempotent). */
    public static String migrate(String id) {
        return isLegacy(id) ? NEW_ID : id;
    }
}
