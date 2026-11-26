package com.hizeric.shenaniganscore.battleground.command;

import net.kyori.adventure.text.minimessage.MiniMessage;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Renders help from {@link CommandNode} declarations — the only help source, so help can never
 * drift from the tree that builds the Brigadier nodes. Players see the player surface; the admin
 * section is shown too but each admin command carries its own permission gate.
 */
public final class CommandHelp {

    private CommandHelp() {
    }

    /** Overview when {@code topic} is null/unknown; otherwise a detailed page for that topic. */
    public static List<String> render(List<CommandNode> roots, String topic) {
        if (topic != null && !topic.isBlank()) {
            CommandNode found = find(roots, topic.toLowerCase(Locale.ROOT));
            if (found != null) {
                return detail(roots, found);
            }
            List<String> lines = new ArrayList<>(overview(roots));
            lines.add("<red>Unknown topic: <white>" + MiniMessage.miniMessage().escapeTags(topic));
            return lines;
        }
        return overview(roots);
    }

    private static List<String> overview(List<CommandNode> roots) {
        List<String> lines = new ArrayList<>();
        lines.add("<aqua><bold>ShenanigansCore <gray>Commands");
        lines.add("<dark_gray>Use <white>/bg help <topic><dark_gray> for details.");
        lines.add("");

        lines.add("<gold><bold>Player Commands");
        for (CommandNode root : roots) {
            collect(root, "", CommandNode.Group.PLAYER, lines);
        }
        lines.add("");

        lines.add("<gold><bold>Admin Commands");
        for (CommandNode root : roots) {
            collect(root, "", CommandNode.Group.ADMIN, lines);
        }
        return lines;
    }

    private static void collect(CommandNode node, String prefix, CommandNode.Group wanted, List<String> lines) {
        String path = prefix.isEmpty() ? "/" + node.name() : prefix + " " + node.name();
        if (node.group() == wanted) {
            String arg = node.arg() == CommandNode.Arg.NONE ? "" : " <" + node.argName() + ">";
            if (node.arg2() != CommandNode.Arg.NONE) arg += " <" + node.argName2() + ">";
            lines.add("<gray>" + path + arg + " <dark_gray>— <white>" + node.description());
        }
        for (CommandNode child : node.children()) {
            collect(child, path, wanted, lines);
        }
    }

    private static List<String> detail(List<CommandNode> roots, CommandNode node) {
        List<String> lines = new ArrayList<>();
        lines.add("<aqua><bold>Command help");
        lines.add("<white>" + node.description());
        lines.add("<gray>Usage: <white>" + usage(roots, node));
        if (node.permission() != null) {
            lines.add("<gray>Permission: <white>" + node.permission());
        }
        return lines;
    }

    /** Reconstructs the full invocation path (with argument) for a node found anywhere in the tree. */
    private static String usage(List<CommandNode> roots, CommandNode target) {
        StringBuilder path = new StringBuilder();
        if (findPath(roots, target, path)) {
            String base = "/" + path;
            if (target.arg() != CommandNode.Arg.NONE) {
                String args = " <" + target.argName() + ">";
                if (target.arg2() != CommandNode.Arg.NONE) args += " <" + target.argName2() + ">";
                return base + args;
            }
            return base;
        }
        return "/" + target.name();
    }

    private static boolean findPath(List<CommandNode> nodes, CommandNode target, StringBuilder path) {
        for (CommandNode node : nodes) {
            int mark = path.length();
            if (path.length() > 0) path.append(' ');
            path.append(node.name());
            if (node == target) return true;
            if (findPath(node.children(), target, path)) return true;
            path.setLength(mark);
        }
        return false;
    }

    private static CommandNode find(List<CommandNode> nodes, String topic) {
        for (CommandNode node : nodes) {
            if (node.name().equalsIgnoreCase(topic)) return node;
            for (String alias : node.aliases()) {
                if (alias.equalsIgnoreCase(topic)) return node;
            }
            CommandNode nested = find(node.children(), topic);
            if (nested != null) return nested;
        }
        return null;
    }
}