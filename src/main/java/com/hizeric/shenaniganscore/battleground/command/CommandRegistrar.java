package com.hizeric.shenaniganscore.battleground.command;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.tree.LiteralCommandNode;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

/**
 * Turns a {@link CommandNode} tree into Brigadier nodes, and validates the tree before it is
 * registered. Both jobs read the same immutable tree, so the live commands and the startup checks
 * can never disagree.
 */
public final class CommandRegistrar {

    /** Builds the root Brigadier node for {@code node} (children included). */
    public LiteralCommandNode<CommandSourceStack> build(CommandNode node) {
        return build(node, null);
    }

    private LiteralCommandNode<CommandSourceStack> build(CommandNode node, String inheritedPermission) {
        String permission = node.permission() != null ? node.permission() : inheritedPermission;
        LiteralArgumentBuilder<CommandSourceStack> builder = Commands.literal(node.name());
        if (permission != null) {
            String required = permission;
            builder.requires(source -> source.getSender().hasPermission(required));
        }

        if (node.arg() != CommandNode.Arg.NONE) {
            String token = node.argName();
            ArgumentType<?> type = typeOf(node.arg());
            Consumer<CommandContext> argHandler = node.argHandler();
            var argumentBuilder = Commands.argument(token, type);
            if (node.suggestions() != null) {
                argumentBuilder.suggests((context, suggestions) -> {
                    for (String value : node.suggestions().get()) suggestions.suggest(value);
                    return CompletableFuture.completedFuture(suggestions.build());
                });
            }
            if (node.arg2() != CommandNode.Arg.NONE) {
                String token2 = node.argName2();
                ArgumentType<?> type2 = typeOf(node.arg2());
                Consumer<CommandContext> argHandler2 = node.argHandler2();
                var argumentBuilder2 = Commands.argument(token2, type2);
                if (node.suggestions2() != null) {
                    argumentBuilder2.suggests((context, suggestions) -> {
                        for (String value : node.suggestions2().get()) suggestions.suggest(value);
                        return CompletableFuture.completedFuture(suggestions.build());
                    });
                }
                argumentBuilder2.executes(context -> {
                    // Integer arguments are parsed once, here, by Brigadier; the parsed value
                    // travels with the context instead of being re-parsed in the handler.
                    Integer typed2 = parseInteger(context, token2, node.arg2());
                    String value = read(context, token, node.arg());
                    String value2 = typed2 != null ? String.valueOf(typed2)
                            : read(context, token2, node.arg2());
                    argHandler2.accept(CommandContext.of(context.getSource(), value, value2, typed2));
                    return Command.SINGLE_SUCCESS;
                });
                argumentBuilder.then(argumentBuilder2);
            } else {
                argumentBuilder.executes(context -> {
                    String value = read(context, token, node.arg());
                    argHandler.accept(CommandContext.of(context.getSource(), value));
                    return Command.SINGLE_SUCCESS;
                });
            }
            builder.then(argumentBuilder);
        }

        if (node.handler() != null) {
            Consumer<CommandContext> handler = node.handler();
            builder.executes(context -> {
                handler.accept(CommandContext.of(context.getSource()));
                return Command.SINGLE_SUCCESS;
            });
        }

        for (CommandNode child : node.children()) {
            builder.then(build(child, permission));
        }
        return builder.build();
    }

    private static ArgumentType<?> typeOf(CommandNode.Arg arg) {
        return switch (arg) {
            case WORD -> StringArgumentType.word();
            case INTEGER -> IntegerArgumentType.integer();
            case INTEGER_RANGE -> IntegerArgumentType.integer(arg.rangeMin(), arg.rangeMax());
            case NONE -> throw new IllegalArgumentException(
                    "NONE takes no argument type; call typeOf only for argument nodes");
        };
    }

    private static String read(com.mojang.brigadier.context.CommandContext<CommandSourceStack> context,
                               String token, CommandNode.Arg arg) {
        return arg == CommandNode.Arg.WORD
                ? StringArgumentType.getString(context, token)
                : String.valueOf(IntegerArgumentType.getInteger(context, token));
    }

    private static Integer parseInteger(com.mojang.brigadier.context.CommandContext<CommandSourceStack> context,
                                        String token, CommandNode.Arg arg) {
        return arg == CommandNode.Arg.INTEGER || arg == CommandNode.Arg.INTEGER_RANGE
                ? IntegerArgumentType.getInteger(context, token)
                : null;
    }

    /**
     * Structural checks run at plugin enable, before any node is registered. Returns one message
     * per problem; an empty list means the tree is sound. Fails loud on duplicate names/aliases,
     * missing descriptions or permissions, and leaves that neither run nor route.
     */
    public List<String> validate(List<CommandNode> roots) {
        List<String> problems = new ArrayList<>();
        Set<String> rootNames = new HashSet<>();
        for (CommandNode root : roots) {
            if (root == null) {
                problems.add("Null root node.");
                continue;
            }
            if (root.name() != null && !rootNames.add(root.name().toLowerCase(Locale.ROOT))) {
                problems.add("Duplicate command root: " + root.name());
            }
            if (root.aliases() != null) for (String alias : root.aliases()) {
                if (alias == null) continue;
                String key = alias.toLowerCase(Locale.ROOT);
                if (key.equals(root.name() == null ? "" : root.name().toLowerCase(Locale.ROOT))) {
                    problems.add("Root '" + root.name() + "' is also its own alias.");
                }
                if (!rootNames.add(key)) {
                    problems.add("Duplicate root or alias: " + alias);
                }
            }
            validateNode(root, root.name(), true, problems);
        }
        return problems;
    }

    private void validateNode(CommandNode node, String path, boolean isRoot, List<String> problems) {
        if (node.name() == null || node.name().isBlank()) {
            problems.add("Node with empty name under " + path + ".");
        }
        if (node.description() == null || node.description().isBlank()) {
            problems.add("Missing description: " + path);
        }
        if (!isRoot && (node.permission() == null || node.permission().isBlank())) {
            // Non-root nodes inherit the permission, but each should still declare one for clarity.
            problems.add("Missing permission: " + path);
        }
        if (node.arg() != CommandNode.Arg.NONE
                && (node.argName() == null || node.argName().isBlank() || node.argHandler() == null)) {
            problems.add("Argument on " + path + " is missing a name or handler.");
        }
        if (node.arg2() != CommandNode.Arg.NONE && node.arg() == CommandNode.Arg.NONE) {
            problems.add("Second argument on " + path + " has no first argument.");
        }
        if (node.arg2() != CommandNode.Arg.NONE
                && (node.argName2() == null || node.argName2().isBlank() || node.argHandler2() == null)) {
            problems.add("Second argument on " + path + " is missing a name or handler.");
        }
        if (node.argName() != null && node.argName2() != null
                && node.argName().equalsIgnoreCase(node.argName2())) {
            problems.add("Duplicate argument name '" + node.argName() + "' on " + path
                    + " (first and second arguments must differ).");
        }
        if (node.handler() != null && node.arg2() != CommandNode.Arg.NONE) {
            // Not a hard error today (no node triggers it): both handlers are written, the
            // literal fires when the argument is omitted and the argument handler when present.
            // Flag it so a future runs()+arg2() node gets the two actions verified as equivalent.
            problems.add("Node " + path + " sets runs() and a second argument: the literal handler "
                    + "fires when the argument is omitted and the argument handler fires when present "
                    + "— verify the two actions are equivalent.");
        }
        boolean leaf = node.children().isEmpty() && node.arg() == CommandNode.Arg.NONE;
        if (leaf && node.handler() == null) {
            problems.add("Leaf has no handler: " + path);
        }

        Set<String> childNames = new HashSet<>();
        for (CommandNode child : node.children()) {
            if (child == null) {
                problems.add("Null child under " + path + ".");
                continue;
            }
            if (child.name() == null) {
                problems.add("Child with null name under " + path + ".");
                continue;
            }
            String childPath = path + " " + child.name();
            if (!childNames.add(child.name().toLowerCase(Locale.ROOT))) {
                problems.add("Duplicate child '" + child.name() + "' under " + path + ".");
            }
            validateNode(child, childPath, false, problems);
        }
    }
}