package com.hizeric.shenaniganscore.core.config;

import org.spongepowered.configurate.CommentedConfigurationNode;

import java.util.List;

/**
 * Pure merge engine for the config auto-updater.
 *
 * <p>Rules, in order:</p>
 * <ol>
 *   <li>Every key in {@code obsoleteKeys} (dot-separated leaf paths) is removed, exactly those keys.</li>
 *   <li>{@code bundled} is merged into {@code disk}: leaves present in the bundled defaults but
 *       missing on disk are copied (value and comment), lists replaced, and an admin's own values
 *       are never overwritten.</li>
 *   <li>Every disk-only key that is not in {@code obsoleteKeys} is untouched.</li>
 * </ol>
 *
 * <p>Returns {@code true} when anything changed, so callers can decide whether to back up and write.</p>
 */
public final class ConfigMerge {

    private ConfigMerge() { }

    public static boolean merge(CommentedConfigurationNode disk, CommentedConfigurationNode bundled,
                                 List<String> obsoleteKeys) {
        if (disk == null || bundled == null) return false;

        boolean changed = false;
        if (obsoleteKeys != null) {
            for (String key : obsoleteKeys) {
                if (removePath(disk, key)) changed = true;
            }
        }

        // mergeFrom fills in what disk is missing and never lets bundled null values overwrite
        // a real disk value, so an admin's tuning survives.
        CommentedConfigurationNode before = disk.copy();
        disk.mergeFrom(bundled);
        if (!disk.equals(before)) changed = true;

        // mergeFrom does not carry the document header; keep the stub's banner when disk has none.
        if (disk.comment() == null && bundled.comment() != null) {
            disk.comment(bundled.comment());
            changed = true;
        }
        return changed;
    }

    /** Removes the leaf at {@code dotted}; bounded walk, so intermediate lists are never touched. */
    static boolean removePath(CommentedConfigurationNode root, String dotted) {
        if (dotted == null || dotted.isBlank()) return false;
        String[] parts = dotted.split("\\.");
        CommentedConfigurationNode current = root;
        for (int i = 0; i < parts.length - 1; i++) {
            CommentedConfigurationNode next = current.node((Object) parts[i]);
            if (next == null || next.virtual() || !next.isMap()) return false;
            current = next;
        }
        return current.removeChild(parts[parts.length - 1]);
    }
}
