package com.hizeric.shenaniganscore.battleground.command;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CommandTwoArgTest {

    private static CommandNode tree() {
        return Cmd.root("demo", "Demo", "shenaniganscore.use", CommandNode.Group.PLAYER)
                .child(Cmd.group("credits", "Credits", "shenaniganscore.use", CommandNode.Group.PLAYER)
                        .child(Cmd.leaf("add", "Add credits", "shenaniganscore.use", CommandNode.Group.PLAYER)
                                .arg("target", CommandNode.Arg.WORD, ctx -> { })
                                .suggestions(() -> List.of("all", "qcart"))
                                .arg2("amount", CommandNode.Arg.INTEGER, ctx -> { })
                                .suggestions2(() -> List.of("10", "100"))))
                .build();
    }

    @Test
    void secondArgumentIsCarriedByTheNode() {
        CommandNode leaf = tree().children().get(0).children().get(0);
        assertEquals(CommandNode.Arg.WORD, leaf.arg());
        assertEquals(CommandNode.Arg.INTEGER, leaf.arg2());
        assertEquals("target", leaf.argName());
        assertEquals("amount", leaf.argName2());
    }

    @Test
    void helpRendersBothArguments() {
        String text = String.join("\n", CommandHelp.render(List.of(tree()), "add"));
        assertTrue(text.contains("/demo credits add <target> <amount>"), text);
    }

    @Test
    void suggestionsAreCarriedForBothArguments() {
        CommandNode leaf = tree().children().get(0).children().get(0);
        assertEquals(List.of("all", "qcart"), leaf.suggestions().get());
        assertEquals(List.of("10", "100"), leaf.suggestions2().get());
    }

    @Test
    void treeWithSecondArgumentStillValidates() {
        assertEquals(List.of(), new CommandRegistrar().validate(List.of(tree())));
    }
}
