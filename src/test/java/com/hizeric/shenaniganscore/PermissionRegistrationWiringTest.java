package com.hizeric.shenaniganscore;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Issue #45 (SP-F-002): mutating a registered {@code Permission}'s children must be followed by
 * {@code recalculatePermissibles()} so players who already hold the umbrella node see the change.
 * The registration path needs a live Bukkit server, so the contract is pinned on the source,
 * following the {@code ConfigReloadWiringTest} convention.
 */
class PermissionRegistrationWiringTest {

    private static final Path MAIN = Path.of("src", "main", "java", "com", "hizeric",
            "shenaniganscore", "ShenanigansCore.java");

    private static String block(String source, String start, String end) throws IOException {
        int from = source.indexOf(start);
        assertTrue(from >= 0, "missing block start: " + start);
        int to = source.indexOf(end, from + start.length());
        assertTrue(to > from, "missing block end after " + start + ": " + end);
        return source.substring(from, to);
    }

    @Test
    void childMutationRecalculatesPermissibles() throws IOException {
        assertTrue(Files.isRegularFile(MAIN), "expected source at " + MAIN.toAbsolutePath());
        String source = Files.readString(MAIN, StandardCharsets.UTF_8);
        String addPermission = block(source,
                "private static void addPermission(", "private void registerListeners(");

        assertTrue(addPermission.contains("putAll(children)"),
                "the children map is mutated on registration/reload");
        assertTrue(addPermission.contains("permission.recalculatePermissibles()"),
                "registered permissibles must be recalculated after mutating the children map");

        int mutation = addPermission.indexOf("putAll(children)");
        int recalc = addPermission.indexOf("permission.recalculatePermissibles()");
        assertTrue(recalc > mutation,
                "recalculation must happen after the mutation, not before it");
        String mutationPath = addPermission.substring(mutation, Math.min(addPermission.length(), mutation + 320));
        assertFalse(mutationPath.contains("return"),
                "no early return may skip recalculation after the mutation");
    }
}
