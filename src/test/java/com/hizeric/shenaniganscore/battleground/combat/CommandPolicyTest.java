package com.hizeric.shenaniganscore.battleground.combat;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CommandPolicyTest {

    private static final List<String> ALLOWED = List.of("battleground", "bg", "ability", "ab", "leave",
            "msg", "r", "reply", "tell", "w", "whisper");
    private static final List<String> BLOCKED = List.of("kit", "tpa", "tpahere");

    @Test
    void whitelistAllowsModeAndSocialRoots() {
        for (String command : List.of("/bg join", "/ability 1", "/msg Steve hi",
                "/flectonepulse:msg Steve hi", "/R hi")) {
            CommandPolicy.Decision d = CommandPolicy.evaluate(new CommandPolicy.Input(
                    CommandPolicy.Mode.WHITELIST, ALLOWED, BLOCKED, command, false));
            assertTrue(d.allowed(), command + " should be allowed");
        }
    }

    @Test
    void whitelistBlocksEverythingElse() {
        CommandPolicy.Decision d = CommandPolicy.evaluate(new CommandPolicy.Input(
                CommandPolicy.Mode.WHITELIST, ALLOWED, BLOCKED, "/kit", false));
        assertFalse(d.allowed());
        assertTrue(d.reason().contains("whitelist"));
    }

    @Test
    void blacklistBlocksOnlyListedRoots() {
        assertTrue(CommandPolicy.evaluate(new CommandPolicy.Input(
                CommandPolicy.Mode.BLACKLIST, ALLOWED, BLOCKED, "/tpa Steve", false)).allowed() == false);
        assertTrue(CommandPolicy.evaluate(new CommandPolicy.Input(
                CommandPolicy.Mode.BLACKLIST, ALLOWED, BLOCKED, "/bg join", false)).allowed());
    }

    @Test
    void whitelistAllowsTheLeaveRoot() {
        assertTrue(CommandPolicy.evaluate(new CommandPolicy.Input(
                CommandPolicy.Mode.WHITELIST, ALLOWED, BLOCKED, "/leave", false)).allowed());
        assertEquals("leave", CommandPolicy.root("/leave"));
    }

    @Test
    void bypassAndMalformedCommands() {
        assertTrue(CommandPolicy.evaluate(new CommandPolicy.Input(
                CommandPolicy.Mode.WHITELIST, ALLOWED, BLOCKED, "/kit", true)).allowed());
        assertFalse(CommandPolicy.evaluate(new CommandPolicy.Input(
                CommandPolicy.Mode.BLACKLIST, ALLOWED, BLOCKED, "/ ", false)).allowed());
        assertEquals("", CommandPolicy.root("/ "));
    }
}
