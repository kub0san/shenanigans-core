package com.hizeric.shenaniganscore.battleground.indicator;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Issue #42: the HUD throttle was dead code. {@code IndicatorRouter.send} always
 * called {@code player.sendActionBar} directly; no live caller ever used the
 * {@code HudController} cache, its {@code forget()} hook or {@code controller()}.
 *
 * <p>The deletion must not change the live contract: domain-HUD suppression and
 * unthrottled action-bar sends stay exactly as they were.</p>
 */
class IndicatorRouterContractTest {

    private static final Path ROUTER = Path.of("src", "main", "java", "com", "hizeric",
            "shenaniganscore", "battleground", "indicator", "IndicatorRouter.java");
    private static final Path CONTROLLER = Path.of("src", "main", "java", "com", "hizeric",
            "shenaniganscore", "battleground", "indicator", "HudController.java");
    private static final Path PLUGIN = Path.of("src", "main", "java", "com", "hizeric",
            "shenaniganscore", "ShenanigansCore.java");

    @Test
    void theDeadThrottleIsRemoved() throws IOException {
        assertFalse(Files.exists(CONTROLLER),
                "HudController had no live callers and must not be shipped");

        String router = Files.readString(ROUTER, StandardCharsets.UTF_8);
        assertFalse(router.contains("HudController"), "the router must not construct a throttle");
        assertFalse(router.contains("controller"), "the dead controller field/accessor must be gone");
        assertFalse(router.contains("forget("), "the dead forget() hook must be gone");
    }

    @Test
    void theLiveSendAndDomainSuppressionContractIsPreserved() throws IOException {
        String router = Files.readString(ROUTER, StandardCharsets.UTF_8);
        assertTrue(router.contains("hasActiveSession(player.getUniqueId())"),
                "a live domain must still suppress the battleground HUD");
        assertTrue(router.contains("player.sendActionBar(MM.deserialize(miniMessage))"),
                "MiniMessage sends must remain direct (no throttling)");
        assertTrue(router.contains("player.sendActionBar(component)"),
                "Component sends must remain direct (no throttling)");
        assertTrue(router.contains("hasActiveSession(playerId)"),
                "the suppression query must remain available to callers");

        String plugin = Files.readString(PLUGIN, StandardCharsets.UTF_8);
        assertTrue(plugin.contains("new IndicatorRouter(this)"),
                "the plugin must still install the router");
        assertTrue(plugin.contains("public IndicatorRouter indicatorRouter()"),
                "the router accessor must remain available to TechniqueHud");
    }
}
