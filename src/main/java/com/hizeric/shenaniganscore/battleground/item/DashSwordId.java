package com.hizeric.shenaniganscore.battleground.item;

/**
 * The canonical id of the Dash Sword custom item and the one-time migration from its legacy id.
 *
 * <p>The Dash Sword is a Minecraft-themed item, so its catalogue/PDC id follows the
 * {@code origin:name} scheme as {@code minecraft:dash_sword}. Earlier data used the bare
 * {@code dash_sword}. This class owns both spellings so the migration lives in exactly one place:
 * persistent data files are rewritten on load (with backups), and no runtime alias is kept —
 * {@code dash_sword} is never accepted as a live id, only translated once at load time.</p>
 *
 * <p>The dash <em>ability</em> the sword grants shares this canonical id; the old
 * {@code battleground:dash_sword} ability id is migrated on load.</p>
 */
public final class DashSwordId {

    /** The legacy, unnamespaced catalogue id (migration source only). */
    public static final String OLD_ID = "dash_sword";

    /** The canonical {@code origin:name} id used from now on. */
    public static final String NEW_ID = "minecraft:dash_sword";

    /** The intermediate namespaced spelling, migrated once at load time. */
    public static final String PREVIOUS_ID = "minecraft:dashsword";

    private DashSwordId() {
    }

    /** Maps the legacy id to the canonical one; leaves every other id untouched (idempotent). */
    public static String migrate(String id) {
        return OLD_ID.equals(id) || PREVIOUS_ID.equals(id) ? NEW_ID : id;
    }
}