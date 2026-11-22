package com.hizeric.shenaniganscore.battleground.command;

import com.hizeric.shenaniganscore.ShenanigansCore;
import com.hizeric.shenaniganscore.battleground.player.BattlegroundPlayer;
import com.hizeric.shenaniganscore.battleground.player.PlayerStore;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Admin-only giveaway commands for credits and kill streaks. Targets are online players only:
 * a name resolves with {@link Bukkit#getPlayerExact(String)}, and {@code all} means every player
 * currently connected. Nothing here touches the shop catalogue.
 */
public final class BalanceCommands {

    public enum Operation { ADD, REMOVE, SET }

    private BalanceCommands() { }

    /** Negative or missing amounts must never produce a negative balance. */
    public static int normalizeAmount(int amount) {
        return Math.max(0, amount);
    }

    public static boolean isAll(String target) {
        return "all".equalsIgnoreCase(target);
    }

    public static CommandNode credits(ShenanigansCore plugin) {
        return group(plugin, "credits", "Give credits to online players", false);
    }

    public static CommandNode streak(ShenanigansCore plugin) {
        return group(plugin, "streak", "Give kill streaks to online players", true);
    }

    private static CommandNode group(ShenanigansCore plugin, String name, String description,
                                     boolean streak) {
        String field = streak ? "streak" : "credits";
        return Cmd.group(name, description, Permissions.BG_ADMIN, CommandNode.Group.ADMIN)
                .child(leaf(plugin, "add", "Add " + field + " to a player or all online players",
                        streak, Operation.ADD))
                .child(leaf(plugin, "remove", "Remove " + field + " from a player or all online players",
                        streak, Operation.REMOVE))
                .child(leaf(plugin, "set", "Set " + field + " for a player or all online players",
                        streak, Operation.SET))
                .build();
    }

    private static Cmd leaf(ShenanigansCore plugin, String name, String description,
                            boolean streak, Operation operation) {
        return Cmd.leaf(name, description, Permissions.BG_ADMIN, CommandNode.Group.ADMIN)
                .arg("target", CommandNode.Arg.WORD, ctx -> apply(ctx, plugin, streak, operation))
                .suggestions(BalanceCommands::targetSuggestions)
                .arg2("amount", CommandNode.Arg.INTEGER, ctx -> apply(ctx, plugin, streak, operation));
    }

    /** Tab-complete {@code all} plus every connected player's name. */
    private static List<String> targetSuggestions() {
        List<String> values = new ArrayList<>();
        values.add("all");
        for (Player player : Bukkit.getOnlinePlayers()) values.add(player.getName());
        return values;
    }

    private static void apply(CommandContext ctx, ShenanigansCore plugin, boolean streak,
                              Operation operation) {
        String target = ctx.argument();
        // The INTEGER argument was parsed once by Brigadier; use the parsed value — the old
        // `Integer.parseInt(rawAmount)` re-parsed the stringified form and could throw an
        // uncaught NumberFormatException.
        Integer requestedObj = ctx.intArgument2();
        if (target == null || requestedObj == null) {
            ctx.reply("<red>Usage: /bg " + (streak ? "streak" : "credits") + " "
                    + operation.name().toLowerCase(Locale.ROOT) + " <player|all> <amount>");
            return;
        }
        int requested = requestedObj;
        int amount = normalizeAmount(requested);

        List<Player> targets = new ArrayList<>();
        if (isAll(target)) {
            targets.addAll(Bukkit.getOnlinePlayers());
        } else {
            Player exact = Bukkit.getPlayerExact(target);
            if (exact == null) {
                ctx.reply("<red>No player named <white>" + MiniMessage.miniMessage().escapeTags(target)
                        + "<red> is online.");
                return;
            }
            targets.add(exact);
        }
        if (targets.isEmpty()) {
            ctx.reply("<gray>No online players to update.");
            return;
        }

        PlayerStore store = plugin.getBattlegroundManager().playerStore();
        for (Player player : targets) {
            BattlegroundPlayer data = store.get(player.getUniqueId());
            if (streak) {
                applyStreak(data, amount, operation);
            } else {
                applyCredits(data, amount, operation);
            }
            store.markDirty(player.getUniqueId());
        }

        String field = streak ? "Streak" : "Credits";
        String verb = operation == Operation.ADD ? "added to"
                : operation == Operation.REMOVE ? "removed from" : "set for";
        String clamped = requested < 0 ? " <gray>(amount clamped to 0)" : "";
        ctx.reply("<green>" + field + " " + verb + " <white>" + targets.size()
                + " <green>online player(s)." + clamped);
    }

    /** Saturating addition so an extreme grant clamps at 0..MAX_VALUE instead of overflowing. */
    static int saturatedAdd(int base, int amount) {
        long sum = (long) base + amount;
        return (int) Math.max(0, Math.min(Integer.MAX_VALUE, sum));
    }

    static void applyCredits(BattlegroundPlayer data, int amount, Operation operation) {
        switch (operation) {
            case ADD -> data.setCredits(saturatedAdd(data.credits(), amount));
            case REMOVE -> data.setCredits(saturatedAdd(data.credits(), -amount));
            case SET -> data.setCredits(amount);
        }
    }

    static void applyStreak(BattlegroundPlayer data, int amount, Operation operation) {
        switch (operation) {
            case ADD -> {
                data.setStreak(saturatedAdd(data.streak(), amount));
                data.recordBestStreak(data.streak());
            }
            case REMOVE -> data.setStreak(saturatedAdd(data.streak(), -amount));
            case SET -> {
                data.setStreak(amount);
                data.recordBestStreak(amount);
            }
        }
    }
}
