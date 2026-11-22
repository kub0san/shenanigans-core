package com.hizeric.shenaniganscore.battleground.command;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pure-tree tests over the real command surface. The tree is built from the actual
 * {@link BattlegroundCommand} and {@link AbilityCommand} classes — no Bukkit, no server — so
 * these assertions guard the live command set: nothing deleted sneaks back, nothing required goes
 * missing, and the startup validator accepts the tree.
 */
class CommandTreeTest {

    private static List<CommandNode> realRoots() {
        // Handlers are never invoked here, so a null plugin is safe for tree construction.
        return List.of(new BattlegroundCommand(null).root(), new AbilityCommand(null).root());
    }

    /** Every literal name and alias anywhere in the forest, lower-cased. */
    private static Set<String> namesAndAliases(List<CommandNode> roots) {
        Set<String> out = new HashSet<>();
        collectNames(roots, out);
        return out;
    }

    private static void collectNames(List<CommandNode> nodes, Set<String> out) {
        for (CommandNode node : nodes) {
            out.add(node.name().toLowerCase(Locale.ROOT));
            for (String alias : node.aliases()) {
                out.add(alias.toLowerCase(Locale.ROOT));
            }
            collectNames(node.children(), out);
        }
    }

    /** The full invocation path for every node (e.g. {@code /battleground admin spawn add}). */
    private static List<String> paths(List<CommandNode> roots) {
        List<String> out = new ArrayList<>();
        collectPaths(roots, "", out);
        return out;
    }

    private static void collectPaths(List<CommandNode> nodes, String prefix, List<String> out) {
        for (CommandNode node : nodes) {
            String path = prefix.isEmpty() ? "/" + node.name() : prefix + " " + node.name();
            out.add(path);
            collectPaths(node.children(), path, out);
        }
    }

    private static CommandNode node(List<CommandNode> roots, String path) {
        for (CommandNode root : roots) {
            CommandNode found = find(root, "/" + root.name(), path);
            if (found != null) return found;
        }
        assertNotNull(null, "no node at " + path);
        return null;
    }

    private static CommandNode find(CommandNode node, String here, String wanted) {
        if (here.equals(wanted)) return node;
        for (CommandNode child : node.children()) {
            CommandNode found = find(child, here + " " + child.name(), wanted);
            if (found != null) return found;
        }
        return null;
    }

    @Test
    void treeValidatesCleanAtStartup() {
        assertEquals(List.of(), new CommandRegistrar().validate(realRoots()),
                "the real command tree must pass startup validation");
    }

    @Test
    void requiredRootsAndAliasesArePresent() {
        Set<String> names = namesAndAliases(realRoots());
        assertTrue(names.contains("battleground"));
        assertTrue(names.contains("bg"), "/bg alias");
        assertTrue(names.contains("ability"));
        assertTrue(names.contains("ab"), "/ab alias");
    }

    @Test
    void requiredNodesArePresent() {
        List<String> all = paths(realRoots());
        for (String path : List.of(
                "/battleground help", "/battleground join", "/battleground leave",
                "/battleground setspawn", "/battleground setup", "/battleground reload",
                "/battleground debug ability", "/battleground debug item",
                "/battleground credits add", "/battleground credits remove", "/battleground credits set",
                "/battleground streak add", "/battleground streak remove", "/battleground streak set",
                "/ability 1", "/ability 2", "/ability 3")) {
            assertTrue(all.contains(path), "missing command: " + path);
        }
    }

    @Test
    void deletedNodesAreGone() {
        List<String> all = paths(realRoots());
        for (String gone : List.of("/battleground gui", "/battleground staging",
                "/battleground capture", "/battleground capture confirm", "/battleground restore",
                "/battleground round start", "/battleground round stop", "/battleground round skip",
                "/battleground round status", "/battleground lobby", "/battleground status",
                "/battleground commandcheck", "/battleground admin", "/battleground test",
                "/battleground debug player", "/battleground repair player")) {
            assertFalse(all.contains(gone), "deleted command still present: " + gone);
        }
    }

    @Test
    void adminLeavesCarryTheAdminPermission() {
        for (String path : List.of("/battleground setspawn", "/battleground setup",
                "/battleground reload", "/battleground debug ability", "/battleground debug item",
                "/battleground credits add", "/battleground credits remove", "/battleground credits set",
                "/battleground streak add", "/battleground streak remove", "/battleground streak set")) {
            assertEquals(Permissions.BG_ADMIN, node(realRoots(), path).permission(), path);
        }
    }

    @Test
    void balanceLeavesTakeTwoTypedArguments() {
        CommandNode add = node(realRoots(), "/battleground credits add");
        assertEquals(CommandNode.Arg.WORD, add.arg());
        assertEquals(CommandNode.Arg.INTEGER, add.arg2());
        assertEquals("target", add.argName());
        assertEquals("amount", add.argName2());
        CommandNode set = node(realRoots(), "/battleground streak set");
        assertEquals(CommandNode.Arg.WORD, set.arg());
        assertEquals(CommandNode.Arg.INTEGER, set.arg2());
    }

    @Test
    void playerLeavesCarryThePlayerPermission() {
        for (String path : List.of("/battleground help", "/battleground join", "/battleground leave")) {
            assertEquals(Permissions.USE, node(realRoots(), path).permission(), path);
        }
    }

    @Test
    void rootAliasesAreNotSharedWithOtherRoots() {
        List<CommandNode> roots = realRoots();
        Set<String> seen = new HashSet<>();
        for (CommandNode root : roots) {
            assertTrue(seen.add(root.name().toLowerCase(Locale.ROOT)), "duplicate root name");
            for (String alias : root.aliases()) {
                assertTrue(seen.add(alias.toLowerCase(Locale.ROOT)), "duplicate root alias");
            }
        }
    }
}
