package com.hizeric.shenaniganscore.battleground.command;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Fluent builder for {@link CommandNode} trees. The whole command surface is declared as a
 * tree literal at the call site — one builder per node — and the same tree drives the Brigadier
 * registration, the help renderer and the startup validation.
 *
 * <p>Each static factory ({@link #root}, {@link #group}, {@link #leaf}) returns a builder for one
 * node; the optional methods you call decide its shape:
 * <ul>
 *   <li>{@link #runs(Consumer)} — the handler for the literal itself (makes it a command, not a
 *       pure router).</li>
 *   <li>{@link #arg(String, CommandNode.Arg, Consumer)} — an optional trailing argument (e.g.
 *       {@code help [topic]}). The literal handler still runs when the argument is omitted.</li>
 *   <li>{@link #child(Cmd)} / {@link #child(CommandNode)} — a nested node.</li>
 *   <li>{@link #aliases(String...)} — root-only aliases, applied at registration.</li>
 * </ul>
 */
public final class Cmd {

    private final String name;
    private final String description;
    private final String permission;
    private final CommandNode.Group group;
    private final List<String> aliases = new ArrayList<>();
    private final List<CommandNode> children = new ArrayList<>();
    private Consumer<CommandContext> handler;
    private CommandNode.Arg arg = CommandNode.Arg.NONE;
    private String argName;
    private Consumer<CommandContext> argHandler;
    private CommandNode.Arg arg2 = CommandNode.Arg.NONE;
    private String argName2;
    private Consumer<CommandContext> argHandler2;
    private Supplier<List<String>> suggestions;
    private Supplier<List<String>> suggestions2;

    private Cmd(String name, String description, String permission, CommandNode.Group group) {
        this.name = name;
        this.description = description;
        this.permission = permission;
        this.group = group;
    }

    /** A top-level command: may run on its own and/or route to children. */
    public static Cmd root(String name, String description, String permission, CommandNode.Group group) {
        return new Cmd(name, description, permission, group);
    }

    /** A branch that only routes to children. */
    public static Cmd group(String name, String description, String permission, CommandNode.Group group) {
        return new Cmd(name, description, permission, group);
    }

    /** A terminal command that runs a handler. */
    public static Cmd leaf(String name, String description, String permission, CommandNode.Group group) {
        return new Cmd(name, description, permission, group);
    }

    public Cmd aliases(String... values) {
        this.aliases.clear();
        this.aliases.addAll(Arrays.asList(values));
        return this;
    }

    /** Runs when the literal is entered with no argument. */
    public Cmd runs(Consumer<CommandContext> handler) {
        this.handler = handler;
        return this;
    }

    /** Adds an optional trailing argument; {@code handler} runs when that argument is supplied. */
    public Cmd arg(String name, CommandNode.Arg type, Consumer<CommandContext> handler) {
        this.arg = type;
        this.argName = name;
        this.argHandler = handler;
        return this;
    }

    /**
     * Adds an optional second trailing argument, consumed after the first one. On a
     * two-argument node only the second handler runs, so the first handler must delegate to the
     * same action (or be a no-op).
     */
    public Cmd arg2(String name, CommandNode.Arg type, Consumer<CommandContext> handler) {
        this.arg2 = type;
        this.argName2 = name;
        this.argHandler2 = handler;
        return this;
    }

    /** Tab completions for the first argument. */
    public Cmd suggestions(Supplier<List<String>> values) {
        this.suggestions = values;
        return this;
    }

    /** Tab completions for the second argument. */
    public Cmd suggestions2(Supplier<List<String>> values) {
        this.suggestions2 = values;
        return this;
    }

    public Cmd child(Cmd child) {
        this.children.add(child.build());
        return this;
    }

    public Cmd child(CommandNode node) {
        this.children.add(node);
        return this;
    }

    public CommandNode build() {
        return new CommandNode(name, aliases, description, permission, group, handler, arg, argName,
                argHandler, arg2, argName2, argHandler2, suggestions, suggestions2, children);
    }
}