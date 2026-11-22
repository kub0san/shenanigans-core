package com.hizeric.shenaniganscore.battleground.command;

import io.papermc.paper.command.brigadier.CommandSourceStack;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.entity.Player;

import java.util.List;

/**
 * What a command handler receives: the raw Paper source, the parsed argument (when the node
 * declares one) and the shared MiniMessage helpers. Handlers stay free of source-unwrapping and
 * message boilerplate.
 */
public final class CommandContext {

    private static final MiniMessage MM = MiniMessage.miniMessage();

    private final CommandSourceStack source;
    private final String argument;
    private final String argument2;
    private final Integer argument2Int;

    private CommandContext(CommandSourceStack source, String argument, String argument2,
                           Integer argument2Int) {
        this.source = source;
        this.argument = argument;
        this.argument2 = argument2;
        this.argument2Int = argument2Int;
    }

    static CommandContext of(CommandSourceStack source) {
        return new CommandContext(source, null, null, null);
    }

    static CommandContext of(CommandSourceStack source, String argument) {
        return new CommandContext(source, argument, null, null);
    }

    static CommandContext of(CommandSourceStack source, String argument, String argument2) {
        return new CommandContext(source, argument, argument2, null);
    }

    static CommandContext of(CommandSourceStack source, String argument, String argument2,
                             Integer argument2Int) {
        return new CommandContext(source, argument, argument2, argument2Int);
    }

    /** The raw Paper source, for the rare handler that needs more than the helpers. */
    public CommandSourceStack source() {
        return source;
    }

    /** The parsed argument, or {@code null} for a no-argument invocation. */
    public String argument() {
        return argument;
    }

    /** The second parsed argument, or {@code null} when the node declares only one. */
    public String argument2() {
        return argument2;
    }

    /**
     * The parsed value of the second argument, or {@code null} when the node has no second
     * argument or it is not declared as an integer type. For two-argument leaves this is the
     * {@code IntegerArgumentType} value that Brigadier parsed — never a re-parse of a string.
     */
    public Integer intArgument2() {
        return argument2Int;
    }

    /**
     * The sender as a player, or {@code null} (with a "players only" message sent) when the
     * command was run from the console or a command block.
     */
    public Player player() {
        if (source.getSender() instanceof Player player) return player;
        reply("<red>Only players can use this.");
        return null;
    }

    public void reply(String miniMessage) {
        source.getSender().sendMessage(MM.deserialize(miniMessage));
    }

    /**
     * Escapes raw text so it can be interpolated into a MiniMessage string (e.g. a user-typed
     * ability id). Pair with {@link #reply(String)}, which is for trusted literals.
     */
    public String escape(String raw) {
        return raw == null ? "" : MM.escapeTags(raw);
    }

    public void reply(List<String> miniMessages) {
        for (String line : miniMessages) {
            source.getSender().sendMessage(MM.deserialize(line));
        }
    }
}