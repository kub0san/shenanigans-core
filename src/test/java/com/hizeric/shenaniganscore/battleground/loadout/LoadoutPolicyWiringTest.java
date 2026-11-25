package com.hizeric.shenaniganscore.battleground.loadout;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Issue #65 wiring guards. Sanitisation must run on the authoritative deploy plan and again when
 * the loadout GUI saves, while the format-only codec and the market-delivery path stay untouched.
 */
class LoadoutPolicyWiringTest {

    private static String source(String... parts) throws IOException {
        return Files.readString(Path.of("src", "main", "java", "com", "hizeric", "shenaniganscore",
                "battleground").resolve(Path.of("", parts)), StandardCharsets.UTF_8);
    }

    @Test
    void deployPlanSanitisesWithTheCatalogAndOwnership() throws IOException {
        String plan = source("loadout", "LoadoutPlan.java");
        assertTrue(plan.contains("LoadoutPolicy.sanitize("), "compute must sanitise the decoded board");
        assertTrue(plan.contains("LoadoutPolicy.catalogLookup("), "use the enabled-catalog lookup");
        assertTrue(plan.contains("data::owns"), "ownership is checked by the player record");
        assertTrue(plan.contains("sanitized.slots()"), "the sanitised board is authoritative");
        assertTrue(plan.contains("warnings.addAll(sanitized.warnings())"),
                "the single summary warning is forwarded");
        assertTrue(plan.contains("getLogger().warning("), "log once with the replaced count");
        assertFalse(plan.contains(".addItem("), "no live inventory mutation in the plan");
    }

    @Test
    void theGuiSaveIsDefenseInDepth() throws IOException {
        String screen = source("menu", "shell", "InlineLoadoutScreen.java");
        int save = screen.indexOf("private void save(Player p)");
        assertTrue(save >= 0, "expected a save handler");
        int end = screen.indexOf("private void confirmReset", save);
        String body = screen.substring(save, end);
        assertTrue(body.contains("LoadoutPolicy.sanitize("), "GUI saves must sanitise too");
        assertTrue(body.contains("catalogLookup("), "same catalog policy as the deploy path");
        assertTrue(body.contains("current::owns"), "same ownership policy as the deploy path");
        assertTrue(body.contains("getLogger().warning("), "one warning, not per-slot spam");
        assertFalse(body.contains(".addItem("), "no live item delivery in the editor");
    }

    @Test
    void codecAndMarketDeliveryStayUntouched() throws IOException {
        String codec = source("loadout", "LoadoutCodec.java");
        assertFalse(codec.contains("LoadoutPolicy"), "the codec is format-only");
        assertFalse(codec.contains("owns("), "the codec must not decide ownership");

        String policy = source("loadout", "LoadoutPolicy.java");
        assertFalse(policy.contains(".addItem("), "the sanitizer is a pure replacement policy");

        String balance = source("command", "BattlegroundCommand.java");
        assertTrue(balance.contains("addItem(stack)"),
                "the unrelated admin giveaway path is untouched");
    }
}
