package com.hizeric.shenaniganscore.battleground.command;

import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Immutable description of one command node: a literal, its optional argument, its handler and
 * its children. Pure data — no Bukkit, no Brigadier — so the whole tree can be validated and
 * rendered without a running server.
 */
public final class CommandNode {

    public enum Group { PLAYER, ADMIN }

    /**
     * The optional argument a leaf accepts. {@link #NONE} means the literal takes no argument.
     * {@link #INTEGER_RANGE} declares a ranged integer that Brigadier validates at parse time
     * (client-side red underline + server-side parse error for out-of-range input).
     */
    public enum Arg {
        NONE, WORD, INTEGER, INTEGER_RANGE(Integer.MIN_VALUE, Integer.MAX_VALUE);

        private final int rangeMin;
        private final int rangeMax;

        Arg(int rangeMin, int rangeMax) {
            this.rangeMin = rangeMin;
            this.rangeMax = rangeMax;
        }

        Arg() {
            this(Integer.MIN_VALUE, Integer.MAX_VALUE);
        }

        /** Inclusive min of the range; only meaningful for {@link #INTEGER_RANGE}. */
        public int rangeMin() { return rangeMin; }

        /** Inclusive max of the range; only meaningful for {@link #INTEGER_RANGE}. */
        public int rangeMax() { return rangeMax; }
    }

    private final String name;
    private final List<String> aliases;
    private final String description;
    private final String permission;
    private final Group group;
    private final Consumer<CommandContext> handler;
    private final Arg arg;
    private final String argName;
    private final Consumer<CommandContext> argHandler;
    private final Arg arg2;
    private final String argName2;
    private final Consumer<CommandContext> argHandler2;
    private final Supplier<List<String>> suggestions;
    private final Supplier<List<String>> suggestions2;
    private final List<CommandNode> children;

    CommandNode(String name, List<String> aliases, String description, String permission, Group group,
                Consumer<CommandContext> handler, Arg arg, String argName,
                Consumer<CommandContext> argHandler, Arg arg2, String argName2,
                Consumer<CommandContext> argHandler2, Supplier<List<String>> suggestions,
                Supplier<List<String>> suggestions2, List<CommandNode> children) {
        this.name = name;
        this.aliases = List.copyOf(aliases);
        this.description = description;
        this.permission = permission;
        this.group = group;
        this.handler = handler;
        this.arg = arg;
        this.argName = argName;
        this.argHandler = argHandler;
        this.arg2 = arg2;
        this.argName2 = argName2;
        this.argHandler2 = argHandler2;
        this.suggestions = suggestions;
        this.suggestions2 = suggestions2;
        this.children = List.copyOf(children);
    }

    public String name() { return name; }

    public List<String> aliases() { return aliases; }

    public String description() { return description; }

    public String permission() { return permission; }

    public Group group() { return group; }

    /** The handler for the literal itself, or {@code null} when the node only routes to children. */
    public Consumer<CommandContext> handler() { return handler; }

    public Arg arg() { return arg; }

    public String argName() { return argName; }

    /** The handler for the optional argument, or {@code null} when {@link #arg()} is {@link Arg#NONE}. */
    public Consumer<CommandContext> argHandler() { return argHandler; }

    public Arg arg2() { return arg2; }

    public String argName2() { return argName2; }

    public Consumer<CommandContext> argHandler2() { return argHandler2; }

    public Supplier<List<String>> suggestions() { return suggestions; }

    public Supplier<List<String>> suggestions2() { return suggestions2; }

    public List<CommandNode> children() { return children; }
}