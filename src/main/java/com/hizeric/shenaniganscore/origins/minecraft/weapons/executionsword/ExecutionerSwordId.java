package com.hizeric.shenaniganscore.origins.minecraft.weapons.executionsword;

/**
 * The canonical id of the Executioner's Sword custom item and the one-time migration from its
 * legacy ids.
 *
 * <p>Mirrors {@code DashSwordId}: the legacy bare id and an intermediate namespaced spelling
 * are both translated to the canonical {@code minecraft:executioners_sword} id exactly once at
 * load time; no runtime alias is kept.</p>
 */
public final class ExecutionerSwordId {

    /** The legacy, unnamespaced catalogue id (migration source only). */
    public static final String OLD_ID = "executioners_sword";

    /** The canonical {@code origin:name} id used from now on. */
    public static final String NEW_ID = "minecraft:executioners_sword";

    /** The intermediate namespaced spelling, migrated once at load time. */
    public static final String PREVIOUS_ID = "minecraft:executionsword";

    private ExecutionerSwordId() {
    }

    /** Maps the legacy ids to the canonical one; leaves every other id untouched (idempotent). */
    public static String migrate(String id) {
        return OLD_ID.equals(id) || PREVIOUS_ID.equals(id) ? NEW_ID : id;
    }
}
