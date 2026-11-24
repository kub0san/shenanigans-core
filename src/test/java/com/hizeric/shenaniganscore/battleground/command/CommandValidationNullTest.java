package com.hizeric.shenaniganscore.battleground.command;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Regression coverage for the startup validator NPE (#23): malformed nodes with null names
 * must be reported as validation problems, not crash plugin enable.
 */
class CommandValidationNullTest {

    @Test
    void nullRootNameIsReportedNotThrown() {
        CommandNode root = Cmd.leaf(null, "desc", "perm", CommandNode.Group.ADMIN)
                .runs(ctx -> { })
                .build();
        List<String> problems = new CommandRegistrar().validate(List.of(root));
        assertFalse(problems.isEmpty(), "a nameless root should be rejected");
        assertTrue(problems.stream().anyMatch(p -> p.contains("empty name")), problems.toString());
    }

    @Test
    void nullChildNameIsReportedNotThrown() {
        CommandNode badChild = Cmd.leaf(null, "desc", "perm", CommandNode.Group.ADMIN)
                .runs(ctx -> { })
                .build();
        CommandNode root = Cmd.group("root", "desc", "perm", CommandNode.Group.ADMIN)
                .child(badChild)
                .build();
        List<String> problems = new CommandRegistrar().validate(List.of(root));
        assertTrue(problems.stream().anyMatch(p -> p.contains("null name")), problems.toString());
    }
}
