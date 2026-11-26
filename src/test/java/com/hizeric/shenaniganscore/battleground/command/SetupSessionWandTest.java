package com.hizeric.shenaniganscore.battleground.command;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SetupSessionWandTest {

    @Test
    void candidatesUseOnlyValidSpellingsAndPreferSlashWand() {
        List<String> candidates = SetupSession.wandCandidates();
        assertEquals("//wand", candidates.get(0));
        assertTrue(candidates.contains("worldedit:wand"));
        assertTrue(candidates.contains("fawe:wand"));
        assertTrue(candidates.contains("wand"));
        assertTrue(candidates.stream().noneMatch(c -> c.startsWith("worldedit ")));
        assertTrue(candidates.stream().noneMatch(c -> c.startsWith("// ")));
    }

    @Test
    void dispatchStopsAtFirstAcceptedCandidateInOrder() {
        List<String> tried = new ArrayList<>();
        boolean accepted = SetupSession.dispatchWand(null, (sender, command) -> {
            tried.add(command);
            return command.equals("fawe:wand");
        });
        assertTrue(accepted);
        assertEquals(List.of("//wand", "worldedit:wand", "fawe:wand"), tried);
    }
}
