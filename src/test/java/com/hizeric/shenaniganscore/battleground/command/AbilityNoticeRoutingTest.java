package com.hizeric.shenaniganscore.battleground.command;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Issue #73: every one-shot denial and caster failure keeps its exact text but arrives as a
 * subtitle notice instead of a chat reply. The command tree has no live test seam for
 * {@code ctx.player()}, so the files are pinned at the source level — the same style as
 * {@code IndicatorRouterContractTest}. Since Task 2 the cast service owns the denial switch and
 * {@code /ability} only submits a request; source location is not behavior, text routing is.
 */
class AbilityNoticeRoutingTest {

    private static final Path COMMAND = Path.of("src", "main", "java", "com", "hizeric",
            "shenaniganscore", "battleground", "command", "AbilityCommand.java");
    private static final Path SERVICE = Path.of("src", "main", "java", "com", "hizeric",
            "shenaniganscore", "core", "ability", "CastTriggerService.java");
    private static final Path CASTER = Path.of("src", "main", "java", "com", "hizeric",
            "shenaniganscore", "core", "ability", "AbilityCaster.java");

    /** Every denial/cooldown message the cast service can emit, including both held-item paths. */
    private static final List<String> SERVICE_DENIALS = List.of(
            "<yellow>Hold your custom item to use slot <white>1<yellow>.",
            "<yellow> is empty. Bind one from the hub's <white>Loadout<yellow> screen.",
            "<yellow>Need <white>",
            "<yellow>Not ready yet — ",
            " is on cooldown for <white>",
            "<yellow>Not ready yet.",
            "<yellow>Join a round before casting.",
            "<yellow>You have not unlocked <white>",
            "<yellow>Your ultimate slot is empty.",
            "<yellow>That cast slot does not exist.",
            "<yellow>You cannot cast that right now.");

    /** Every message AbilityCaster.fail() emits, from unknown ability to the default gate denial. */
    private static final List<String> CASTER_DENIALS = List.of(
            "<red>That ability is not registered.",
            "<red>You cannot use a technique while your domain is active!",
            "<red>Your ability is not ready yet. ",
            "<gray>Ready in <white>",
            "<red>Abilities can only be used inside the battleground.",
            "<red>You have not unlocked that technique. <gray>Buy it in the market.",
            "<red>Hold the matching item to use that ability.",
            "<red>You cannot use that ability right now.");

    @Test
    void everyDenialKeepsItsTextAndReachesShowNoticeInTheService() throws IOException {
        String service = Files.readString(SERVICE, StandardCharsets.UTF_8);

        for (String denial : SERVICE_DENIALS) {
            assertTrue(service.contains(denial), "denial text changed or was lost: " + denial);
        }
        assertEquals(2, count(service, "<yellow>Hold your custom item to use slot <white>1<yellow>."),
                "both empty-held-slot paths must still send their message");
        assertEquals(1, count(service, "TechniqueHud.showNotice("),
                "the cast service is the single notice producer for casts");
    }

    @Test
    void commandSubmitsToTheServiceWithoutItsOwnNoticeSwitch() throws IOException {
        String command = Files.readString(COMMAND, StandardCharsets.UTF_8);

        assertTrue(command.contains("plugin.getCastTriggerService().submit("),
                "/ability must submit a request to the cast service");
        assertTrue(command.contains("CastRequest(player, slot, CastSource.COMMAND)"),
                "the request must carry the slot and the command source");
        assertFalse(command.contains("TechniqueHud.showNotice("),
                "the command must not produce notices; the service owns denial routing");
        assertFalse(command.contains("ctx.reply("), "no /ability denial may still go to chat");
        assertFalse(command.contains("CastInput.decide("),
                "the command must not duplicate the service's decision");
    }

    @Test
    void everyCasterFailureKeepsItsTextAndGoesToShowNotice() throws IOException {
        String source = Files.readString(CASTER, StandardCharsets.UTF_8);

        for (String denial : CASTER_DENIALS) {
            assertTrue(source.contains(denial), "failure text changed or was lost: " + denial);
        }
        assertFalse(source.contains("player.sendMessage("),
                "AbilityCaster.fail() must not write failures to chat");
        assertTrue(source.contains("TechniqueHud.showNotice(player, message"),
                "AbilityCaster.fail() must hand its message to the notice sink");
    }

    private static int count(String text, String needle) {
        int total = 0;
        for (int at = text.indexOf(needle); at >= 0; at = text.indexOf(needle, at + needle.length())) {
            total++;
        }
        return total;
    }
}
