package com.hizeric.shenaniganscore.battleground.command;

import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.command.CommandSender;

/**
 * Executor-side permission check for one node of the immutable command tree. Takes a plain Bukkit
 * {@link CommandSender} so the decision and its feedback are testable without a Brigadier source.
 */
public final class CommandPermissionGate {

    /** The single denial line sent when a sender lacks the node's permission. */
    public static final String DENIAL_MESSAGE = "<red>You do not have permission to use this command.";

    private static final MiniMessage MM = MiniMessage.miniMessage();

    private final String permission;

    public CommandPermissionGate(String permission) {
        this.permission = permission;
    }

    /**
     * @return {@code true} when the sender may run the handler; {@code false} after sending denial
     *         feedback, in which case the caller must not invoke the real handler.
     */
    public boolean allows(CommandSender sender) {
        if (permission == null || sender.hasPermission(permission)) return true;
        sender.sendMessage(MM.deserialize(DENIAL_MESSAGE));
        return false;
    }
}
