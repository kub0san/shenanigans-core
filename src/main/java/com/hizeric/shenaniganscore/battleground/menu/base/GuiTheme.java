package com.hizeric.shenaniganscore.battleground.menu.base;

import io.papermc.paper.datacomponent.DataComponentTypes;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.Arrays;
import java.util.Map;

/**
 * Instrument Panel design language.
 *
 * <p>A dark navy canvas painted across the whole chest, one steel-blue accent,
 * terse UPPERCASE labels and exactly one plain description line. No hints, no
 * glyphs, no decorative frames. A button's name is the command; its description
 * says what it does. Data items carry {@code Label: value} lines and nothing else.</p>
 */
public final class GuiTheme {
    private static final MiniMessage MM = MiniMessage.miniMessage();

    // ---- palette (accent is configurable via GuiStyle) ----
    public static final String TEXT = "<#e8eef5>";
    public static final String MUTED = "<#8b95a8>";
    public static final String FAINT = "<#5c6578>";
    public static final String GREEN = "<#6bcf8e>";
    public static final String RED = "<#e06c75>";
    public static final String GOLD = "<#d4b87a>";
    public static final String AQUA = "<#56b6c2>";

    private GuiTheme() {
    }

    // ------------------------------------------------------------ builders

    /** The accent tag from config (titles, headings, selected tab). */
    public static String accent() {
        return GuiStyle.accent();
    }

    /**
     * Pack model for a technique id; null keeps the vanilla icon. Shared by market/loadout.
     * Exact, normalized ids avoid substring collisions (e.g. {@code blood_red} matching red)
     * and handle underscored ids such as {@code jjk:time_cell_moon_palace}.
     */
    private static final Map<String, String> ABILITY_MODELS = Map.of(
            "jjk:blue", "ability_blue",
            "jjk:red", "ability_red",
            "jjk:purple", "ability_purple",
            "jjk:fuga", "ability_fuga",
            "jjk:piercing_blood", "ability_piercing_blood",
            "jjk:malevolent_shrine", "ultimate_shrine",
            "jjk:unlimited_void", "ultimate_void",
            "jjk:time_cell_moon_palace", "ultimate_timecell");

    public static String abilityModel(String abilityId) {
        if (abilityId == null) return null;
        return ABILITY_MODELS.get(abilityId.toLowerCase(java.util.Locale.ROOT).trim());
    }

    /** Theme-default canvas filler: vanilla configured pane, no tooltip. */
    public static ItemStack filler() {
        return filler(GuiStyle.glass());
    }

    /** Glass of an explicit material with no tooltip. */
    public static ItemStack filler(Material material) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setHideTooltip(true);
            item.setItemMeta(meta);
        }
        return item;
    }

    public static ItemStack named(Material material, String color, String name, String... lore) {
        return build(material, color + name, lore);
    }

    /** A button with no description. Use when the name is the whole instruction. */
    public static ItemStack plain(Material material, String color, String name) {
        return build(material, color + name);
    }

    /**
     * A button rendered by the bundled resource pack. Without the pack the base
     * material still shows, so the GUI degrades gracefully.
     */
    public static ItemStack model(Material material, String model, String name, String... lore) {
        ItemStack item = named(material, TEXT + "<bold>", name, lore);
        item.setData(DataComponentTypes.ITEM_MODEL, Key.key("shenanigans", model));
        return item;
    }

    /** Apply a pack model to an existing icon without changing its name/lore. */
    public static ItemStack model(ItemStack item, String model) {
        item.setData(DataComponentTypes.ITEM_MODEL, Key.key("shenanigans", model));
        return item;
    }

    /** An action button: name plus one plain description line. */
    public static ItemStack tool(Material material, String name, String description) {
        return named(material, TEXT + "<bold>", name, dim(description));
    }

    /** A destructive action button. */
    public static ItemStack dangerTool(Material material, String name, String description) {
        return named(material, RED + "<bold>", name, dim(description));
    }

    // ------------------------------------------------------------ text vocabulary

    public static String dim(String text) {
        return FAINT + text;
    }

    /** Plain {@code Label: value} data line. */
    public static String value(String label, String value) {
        return MUTED + label + FAINT + ": " + TEXT + value;
    }

    /** Plain status word, coloured by state. */
    public static String status(String color, boolean on, String text) {
        return color + text;
    }

    public static String entry(String label, int amount) {
        return MUTED + "– " + TEXT + label + (amount > 1 ? FAINT + " x" + amount : "");
    }

    public static String ok(String text) { return GREEN + text; }
    public static String warn(String text) { return GOLD + text; }
    public static String fail(String text) { return RED + text; }

    // ------------------------------------------------------------ nav items

    public static ItemStack glow(ItemStack item) {
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.addEnchant(Enchantment.MENDING, 1, true);
            meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ENCHANTS);
            item.setItemMeta(meta);
        }
        return item;
    }

    public static ItemStack back() {
        return plain(Material.ARROW, MUTED, "BACK");
    }

    public static ItemStack arrow(boolean next) {
        return named(Material.SPECTRAL_ARROW, accent(), next ? "NEXT" : "PREVIOUS");
    }

    public static ItemStack pageItem(int page, int pages) {
        ItemStack item = named(Material.PAPER, TEXT, "PAGE " + (page + 1) + " OF " + pages);
        item.setAmount(Math.max(1, Math.min(64, page + 1)));
        return item;
    }

    // ------------------------------------------------------------ internals

    private static ItemStack build(Material material, String name, String... lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return item;
        }
        if (name != null && !name.isEmpty()) {
            meta.displayName(MM.deserialize("<!italic>" + name));
        }
        if (lore != null && lore.length > 0) {
            meta.lore(Arrays.stream(lore)
                    .filter(line -> line != null && !line.isEmpty())
                    .map(line -> MM.deserialize("<!italic>" + line))
                    .toList());
        }
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ENCHANTS);
        item.setItemMeta(meta);
        return item;
    }
}
