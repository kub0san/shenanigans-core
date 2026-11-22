package com.hizeric.shenaniganscore.battleground.menu.base;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Plain card vocabulary: a name, a state word and an optional short contents preview. */
public final class GuiCard {
    private static final int PREVIEW_LINES = 3;

    public enum CardState {
        READY, PREVIEW, EMPTY, LOCKED, STAFF_EDIT
    }

    private GuiCard() {
    }

    public static ItemStack card(Material icon, String name, ItemStack[] contents, CardState state) {
        List<String> lore = new ArrayList<>();
        lore.add(switch (state) {
            case READY -> GuiTheme.status(GuiTheme.GREEN, true, "Ready");
            case PREVIEW -> GuiTheme.status(GuiTheme.AQUA, true, "Premade");
            case EMPTY -> GuiTheme.status(GuiTheme.MUTED, false, "Empty");
            case LOCKED -> GuiTheme.status(GuiTheme.RED, false, "Locked");
            case STAFF_EDIT -> GuiTheme.status(GuiTheme.GOLD, true, "Staff");
        });

        if (state != CardState.LOCKED && contents != null) {
            Map<Material, Integer> totals = new LinkedHashMap<>();
            for (ItemStack stack : contents) {
                if (stack != null && !stack.getType().isAir()) {
                    totals.merge(stack.getType(), stack.getAmount(), Integer::sum);
                }
            }
            int shown = 0;
            for (Map.Entry<Material, Integer> e : totals.entrySet()) {
                if (shown++ == PREVIEW_LINES) break;
                lore.add(GuiTheme.entry(prettify(e.getKey()), e.getValue()));
            }
        }

        String color = switch (state) {
            case READY -> GuiTheme.TEXT;
            case PREVIEW -> GuiTheme.AQUA;
            case EMPTY -> GuiTheme.MUTED;
            case LOCKED -> GuiTheme.RED;
            case STAFF_EDIT -> GuiTheme.GOLD;
        };
        return GuiTheme.named(icon, color + "<bold>", SafeText.escape(name), lore.toArray(new String[0]));
    }

    /** A category tab: selected tabs glow, others show the entry count. */
    public static ItemStack filterTab(Material icon, String name, boolean selected, int count) {
        List<String> lore = new ArrayList<>();
        if (selected) {
            lore.add(GuiTheme.status(GuiTheme.accent(), true, "Viewing"));
        } else {
            lore.add(GuiTheme.value("Entries", String.valueOf(Math.max(0, count))));
        }
        ItemStack item = GuiTheme.named(icon, (selected ? GuiTheme.accent() : GuiTheme.TEXT) + "<bold>",
                SafeText.escape(name), lore.toArray(new String[0]));
        return selected ? GuiTheme.glow(item) : item;
    }

    private static String prettify(Material material) {
        StringBuilder out = new StringBuilder();
        for (String word : material.name().toLowerCase(Locale.ROOT).split("_")) {
            if (word.isEmpty()) continue;
            if (!out.isEmpty()) out.append(' ');
            out.append(Character.toUpperCase(word.charAt(0))).append(word.substring(1));
        }
        return out.toString();
    }
}
