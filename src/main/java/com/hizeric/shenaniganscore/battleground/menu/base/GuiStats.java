package com.hizeric.shenaniganscore.battleground.menu.base;

import net.kyori.adventure.text.minimessage.MiniMessage;

import java.util.Locale;

/**
 * Pure rendering data for the hub's single statistics item: the username at the top
 * of one lore block, then five values. No Bukkit runtime, so formatting is unit-tested.
 */
public final class GuiStats {

    private static final MiniMessage MM = MiniMessage.miniMessage();
    private static final String NAME = "<#6ea8d9><bold>";
    private static final String LABEL = "<#8b95a8>";
    private static final String SEP = "<#5c6578>: ";
    private static final String VALUE = "<#d4b87a>";

    private GuiStats() {
    }

    public record Snapshot(String username, int kills, int deaths, int credits, int bestStreak) {
    }

    /** Kills per death, two decimals; no deaths is ∞ (or — with no kills either). */
    public static String kd(int kills, int deaths) {
        if (deaths <= 0) return kills > 0 ? "∞" : "—";
        return String.format(Locale.ROOT, "%.2f", (double) kills / deaths);
    }

    public static String[] lore(Snapshot stats) {
        String username = stats.username() == null ? "Unknown" : stats.username();
        return new String[]{
                NAME + MM.escapeTags(username),
                line("Kills", stats.kills()),
                line("Deaths", stats.deaths()),
                line("K/D", kd(stats.kills(), stats.deaths())),
                line("Credits", stats.credits()),
                line("Highest streak", stats.bestStreak())
        };
    }

    private static String line(String label, Object value) {
        return LABEL + label + SEP + VALUE + value;
    }
}
