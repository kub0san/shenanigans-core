package com.hizeric.shenaniganscore.battleground.command;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** {@code /bg map} is an admin-only router Task 8 can extend with a switch leaf. */
class MapCommandTreeTest {

    private static CommandNode root() {
        return new BattlegroundCommand(null).root();
    }

    private static CommandNode child(CommandNode parent, String name) {
        for (CommandNode node : parent.children()) {
            if (node.name().equals(name)) return node;
        }
        return null;
    }

    @Test
    void mapCaptureExistsIsAdminOnlyAndKeepsTheGroupExtendable() {
        CommandNode map = child(root(), "map");
        assertNotNull(map, "/bg map must exist");
        assertEquals(Permissions.BG_ADMIN, map.permission());
        assertEquals(CommandNode.Group.ADMIN, map.group());
        assertNull(map.handler(), "the map node must stay a pure router for Task 8");

        CommandNode capture = child(map, "capture");
        assertNotNull(capture, "/bg map capture must exist");
        assertEquals(Permissions.BG_ADMIN, capture.permission());
        assertEquals(CommandNode.Group.ADMIN, capture.group());
        assertNotNull(capture.handler());
    }

    @Test
    void mapNodePassesStartupValidation() {
        assertEquals(List.of(), new CommandRegistrar().validate(List.of(root())));
        assertTrue(root().children().stream().anyMatch(n -> n.name().equals("map")));
    }
}
