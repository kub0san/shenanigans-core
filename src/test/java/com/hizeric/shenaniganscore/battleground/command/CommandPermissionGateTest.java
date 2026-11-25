package com.hizeric.shenaniganscore.battleground.command;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.plugin.Plugin;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Issue #87: Brigadier prunes {@code requires(permission)} nodes for unpermitted senders, so the
 * denial has to be enforced — and explained — at execution time instead. These tests exercise the
 * gate against real MockBukkit permission state and captured sent messages; no CommandSourceStack.
 */
class CommandPermissionGateTest {

    private static final String PERMISSION = "shenaniganscore.battleground.admin";
    private static final Component EXPECTED_DENIAL = MiniMessage.miniMessage()
            .deserialize("<red>You do not have permission to use this command.");

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    private static PlayerMock permittedPlayer(ServerMock server) {
        PlayerMock player = server.addPlayer();
        Plugin plugin = MockBukkit.createMockPlugin("CommandPermissionGateTest");
        player.addAttachment(plugin, PERMISSION, true);
        return player;
    }

    @Test
    void permittedSenderIsAllowedAndReceivesNoDenial() {
        ServerMock server = MockBukkit.mock();
        PlayerMock player = permittedPlayer(server);
        assertTrue(player.hasPermission(PERMISSION), "test sender must really hold the permission");

        assertTrue(new CommandPermissionGate(PERMISSION).allows(player));
        assertNull(player.nextComponentMessage(), "a permitted sender gets no denial");
    }

    @Test
    void unpermittedSenderIsDeniedAndReceivesExactlyTheDenialMessage() {
        ServerMock server = MockBukkit.mock();
        PlayerMock player = server.addPlayer();
        assertFalse(player.hasPermission(PERMISSION), "test sender must really lack the permission");

        assertFalse(new CommandPermissionGate(PERMISSION).allows(player));
        assertEquals(EXPECTED_DENIAL, player.nextComponentMessage());
        assertNull(player.nextComponentMessage(), "exactly one denial message, nothing else");
    }

    @Test
    void nullPermissionAllowsEveryoneWithoutFeedback() {
        ServerMock server = MockBukkit.mock();
        PlayerMock player = server.addPlayer();
        assertFalse(player.hasPermission(PERMISSION));

        assertTrue(new CommandPermissionGate(null).allows(player));
        assertNull(player.nextComponentMessage(), "no permission means no gate and no feedback");
    }
}
