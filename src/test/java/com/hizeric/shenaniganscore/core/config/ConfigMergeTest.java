package com.hizeric.shenaniganscore.core.config;

import org.junit.jupiter.api.Test;
import org.spongepowered.configurate.CommentedConfigurationNode;
import org.spongepowered.configurate.serialize.SerializationException;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConfigMergeTest {

    private static CommentedConfigurationNode root() {
        return CommentedConfigurationNode.root();
    }

    private static void set(CommentedConfigurationNode root, String path, Object value) {
        try {
            CommentedConfigurationNode current = root;
            String[] parts = path.split("\\.");
            for (int i = 0; i < parts.length - 1; i++) {
                current = current.node((Object) parts[i]);
            }
            current.node((Object) parts[parts.length - 1]).set(value);
        } catch (SerializationException e) {
            throw new IllegalStateException(e);
        }
    }

    @Test
    void aMissingLeafIsAddedAndMarkedChanged() {
        CommentedConfigurationNode disk = root();
        set(disk, "alpha", 1);

        CommentedConfigurationNode bundled = root();
        set(bundled, "alpha", 1);
        set(bundled, "beta", 2);

        assertTrue(ConfigMerge.merge(disk, bundled, List.of()));
        assertEquals(2, disk.node("beta").getInt());
    }

    @Test
    void anAdminValueForAnExistingKeyIsKept() {
        CommentedConfigurationNode disk = root();
        set(disk, "alpha", 42);

        CommentedConfigurationNode bundled = root();
        set(bundled, "alpha", 1);

        assertFalse(ConfigMerge.merge(disk, bundled, List.of()));
        assertEquals(42, disk.node("alpha").getInt());
    }

    @Test
    void anObsoleteKeyIsRemovedAndMarkedChanged() {
        CommentedConfigurationNode disk = root();
        set(disk, "alpha", 1);
        set(disk, "obedient", 2);

        assertTrue(ConfigMerge.merge(disk, root(), List.of("obedient")));
        assertFalse(disk.hasChild("obedient"));
    }

    @Test
    void anEmptyBundleDoesNotTouchExistingValues() {
        CommentedConfigurationNode disk = root();
        set(disk, "alpha", 7);

        assertFalse(ConfigMerge.merge(disk, root(), List.of()));
        assertEquals(7, disk.node("alpha").getInt());
    }

    @Test
    void aDiskOnlyKeyNotInTheObsoleteListSurvives() {
        CommentedConfigurationNode disk = root();
        set(disk, "admin-private", "keep-me");
        set(disk, "alpha", 1);

        CommentedConfigurationNode bundled = root();
        set(bundled, "alpha", 1);

        assertFalse(ConfigMerge.merge(disk, bundled, List.of("someone-else")));
        assertEquals("keep-me", disk.node("admin-private").getString());
    }

    @Test
    void nestedObsoleteKeysAreRemovedNotJustTopLevel() {
        CommentedConfigurationNode disk = root();
        set(disk, "root.keep", 1);
        set(disk, "root.drop", 2);

        assertTrue(ConfigMerge.merge(disk, root(), List.of("root.drop")));
        assertEquals(1, disk.node("root", "keep").getInt());
        assertFalse(disk.hasChild("root", "drop"));
    }

    @Test
    void removingAnObsoleteKeyThatDoesNotExistIsANoOp() {
        CommentedConfigurationNode disk = root();
        set(disk, "alpha", 1);

        assertFalse(ConfigMerge.merge(disk, root(), List.of("nope", "root.nope-too")));
        assertEquals(1, disk.node("alpha").getInt());
    }

    @Test
    void commentIsCopiedForAnAddedKey() {
        CommentedConfigurationNode disk = root();
        set(disk, "alpha", 1);

        CommentedConfigurationNode bundled = root();
        set(bundled, "alpha", 1);
        set(bundled, "beta", 2);
        try {
            bundled.node("beta").comment(" new key");
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }

        assertTrue(ConfigMerge.merge(disk, bundled, List.of()));
        assertEquals(" new key", disk.node("beta").comment());
    }
}
