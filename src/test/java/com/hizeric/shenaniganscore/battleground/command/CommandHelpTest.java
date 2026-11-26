package com.hizeric.shenaniganscore.battleground.command;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

/** Help is rendered from the same {@link CommandNode} tree that builds the Brigadier nodes. */
class CommandHelpTest {

    private static CommandNode bgTree() {
        return Cmd.root("bg", "Battleground commands", "shenaniganscore.use", CommandNode.Group.PLAYER)
                .aliases("battleground")
                .child(Cmd.leaf("join", "Stage for the next round", "shenaniganscore.use", CommandNode.Group.PLAYER)
                        .runs(ctx -> { }))
                .child(Cmd.leaf("setspawn", "Set the return point here",
                        "shenaniganscore.battleground.admin", CommandNode.Group.ADMIN)
                        .runs(ctx -> { }))
                .child(Cmd.leaf("setup", "Set up the arena in one command",
                        "shenaniganscore.battleground.admin", CommandNode.Group.ADMIN)
                        .runs(ctx -> { }))
                .child(Cmd.leaf("reload", "Refresh the config stubs and arena data",
                        "shenaniganscore.battleground.admin", CommandNode.Group.ADMIN)
                        .runs(ctx -> { }))
                .child(Cmd.group("debug", "Inspect abilities and custom items",
                        "shenaniganscore.battleground.admin", CommandNode.Group.ADMIN)
                        .child(Cmd.leaf("ability", "Perform a registered ability by id (bypasses gates)",
                                "shenaniganscore.battleground.admin", CommandNode.Group.ADMIN)
                                .runs(ctx -> { }))
                        .child(Cmd.leaf("item", "Grant a custom item by id (bypasses gates)",
                                "shenaniganscore.battleground.admin", CommandNode.Group.ADMIN)
                                .runs(ctx -> { })))
                .build();
    }

    private static CommandNode abilityTree() {
        return Cmd.root("ability", "Cast a bound ability", "shenaniganscore.use", CommandNode.Group.PLAYER)
                .aliases("ab")
                .child(Cmd.leaf("1", "Cast bound ability 1", "shenaniganscore.use", CommandNode.Group.PLAYER)
                        .runs(ctx -> { }))
                .build();
    }

    private static CommandNode leaveTree() {
        return Cmd.root("leave", "Return to the battleground hub", "shenaniganscore.use", CommandNode.Group.PLAYER)
                .build();
    }

    private static List<String> overview() {
        return CommandHelp.render(List.of(bgTree(), abilityTree(), leaveTree()), null);
    }

    @Test
    void overviewHasAPlayerAndAdminSectionPerRoot() {
        List<String> lines = overview();
        String text = String.join("\n", lines);
        assertTrue(text.contains("ShenanigansCore"), "overview names the plugin, not a single command");
        assertTrue(text.contains("Player Commands"));
        assertTrue(text.contains("Admin Commands"));
    }

    @Test
    void overviewListsPlayerAndAdminCommands() {
        String text = String.join("\n", overview());
        assertTrue(text.contains("/bg"), "root command shown");
        assertTrue(text.contains("/bg join"), "player leaf shown");
        assertTrue(text.contains("/ability"), "second root shown");
        assertTrue(text.contains("/leave"), "leave root shown");
        assertTrue(text.contains("/bg setspawn"), "admin leaf shown");
        assertTrue(text.contains("/bg debug ability"), "nested admin leaf shown");
    }

    @Test
    void detailTopicResolvesByNameAndShowsUsage() {
        List<String> lines = CommandHelp.render(List.of(bgTree(), abilityTree(), leaveTree()), "join");
        String text = String.join("\n", lines);
        assertTrue(text.contains("Stage for the next round"));
        assertTrue(text.contains("Usage:"), "detail page shows the reconstructed usage");
        assertTrue(text.contains("/bg join"));
    }

    @Test
    void unknownTopicShowsAnExplicitLineAfterTheOverview() {
        List<String> lines = CommandHelp.render(List.of(bgTree(), abilityTree(), leaveTree()), "nonesuch");
        String text = String.join("\n", lines);
        assertTrue(text.contains("Unknown topic"), "explicit unknown-topic line");
        assertTrue(text.contains("nonesuch"), "the requested topic is echoed");
        assertTrue(text.contains("/bg join"), "still falls back to the overview");
        assertTrue(lines.get(lines.size() - 1).contains("Unknown topic"), "line is at the end");
    }

    @Test
    void renderWithAllRootsIncludesTheAbilityTree() {
        String text = String.join("\n", CommandHelp.render(List.of(bgTree(), abilityTree(), leaveTree()), null));
        assertTrue(text.contains("/bg join"));
        assertTrue(text.contains("/ability"), "second root shown when all are passed");
        assertTrue(text.contains("/ability 1"), "second root's leaf shown");
    }

    @Test
    void overviewShowsBothBranchesAndTheirLeaves() {
        String text = String.join("\n", overview());
        // The debug group is listed as a routing node, and its nested leaves are listed too.
        assertTrue(text.contains("/bg debug"), "debug group shown");
        assertTrue(text.contains("/bg debug ability"), "nested admin leaf under the group shown");
    }
}