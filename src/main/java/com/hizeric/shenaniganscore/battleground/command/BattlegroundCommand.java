package com.hizeric.shenaniganscore.battleground.command;

import com.hizeric.shenaniganscore.ShenanigansCore;
import com.hizeric.shenaniganscore.battleground.BattlegroundManager;
import com.hizeric.shenaniganscore.battleground.shop.ShopCatalog;
import com.hizeric.shenaniganscore.battleground.shop.ShopEntry;
import com.hizeric.shenaniganscore.core.ability.AbilityDefinition;
import com.hizeric.shenaniganscore.core.ability.CastResult;
import com.hizeric.shenaniganscore.core.ability.CastSource;
import com.hizeric.shenaniganscore.core.domain.DomainRegistry;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * The {@code /battleground} tree (alias {@code /bg}): a short player surface plus admin setup and
 * debug tools, declared as one {@link Cmd} tree. Handlers receive a {@link CommandContext}, so there is
 * no source-unwrapping or messaging boilerplate left in the command bodies.
 */
public final class BattlegroundCommand {

    private final ShenanigansCore plugin;
    private final CommandNode root;

    public BattlegroundCommand(ShenanigansCore plugin) {
        this.plugin = plugin;
        this.root = buildTree();
    }

    /** The root node for this command tree; the registrar and help both read it. */
    public CommandNode root() {
        return root;
    }

    private BattlegroundManager manager() {
        return plugin.getBattlegroundManager();
    }

    private CommandNode buildTree() {
        return Cmd.root("battleground", "Battleground commands", Permissions.USE, CommandNode.Group.PLAYER)
                .aliases("bg")
                .runs(ctx -> ctx.reply(CommandHelp.render(List.of(this.root), null)))

                // ---- player ----
                .child(Cmd.leaf("join", "Stage for the next round", Permissions.USE, CommandNode.Group.PLAYER)
                        .runs(ctx -> {
                            Player p = ctx.player();
                            if (p != null) manager().enterStage(p);
                        }))
                .child(Cmd.leaf("leave", "Return to the battleground hub", Permissions.USE, CommandNode.Group.PLAYER)
                        .runs(ctx -> {
                            Player p = ctx.player();
                            if (p != null) manager().leave(p);
                        }))
                .child(Cmd.leaf("help", "Show this help, or help for one command", Permissions.USE, CommandNode.Group.PLAYER)
                        .runs(ctx -> ctx.reply(CommandHelp.render(List.of(this.root), null)))
                        .arg("topic", CommandNode.Arg.WORD,
                                ctx -> ctx.reply(CommandHelp.render(List.of(this.root), ctx.argument()))))

                // ---- admin ----
                .child(Cmd.leaf("setspawn", "Set the return point here", Permissions.BG_ADMIN, CommandNode.Group.ADMIN)
                        .runs(ctx -> {
                            Player p = ctx.player();
                            if (p != null) {
                                manager().arenaStore().setSpawn(p.getLocation());
                                ctx.reply("<green>Return point set to your current position.");
                            }
                        }))
                .child(Cmd.leaf("setup", "Set up the arena in one command", Permissions.BG_ADMIN, CommandNode.Group.ADMIN)
                        .runs(ctx -> {
                            Player p = ctx.player();
                            if (p != null) SetupSession.start(plugin, p);
                        }))
                .child(Cmd.leaf("reload", "Refresh the config stubs, ability tuning and arena data", Permissions.BG_ADMIN, CommandNode.Group.ADMIN)
                        .runs(ctx -> manager().reloadConfigAsync(ok -> ctx.reply(ok
                                ? "<green>Config stubs checked; ability tuning and arena data reloaded."
                                : "<red>Config reload failed — check the server log."))))
                .child(Cmd.group("round", "Control the live round",
                        Permissions.BG_ADMIN, CommandNode.Group.ADMIN)
                        .child(Cmd.leaf("end", "End the live round and begin reset",
                                Permissions.BG_ADMIN, CommandNode.Group.ADMIN)
                                .runs(ctx -> {
                                    var result = manager().adminRoundAction(ctx.source().getSender(),
                                            com.hizeric.shenaniganscore.battleground.admin.AdminRoundGate.Action.END, 0);
                                    ctx.reply(result.allowed() ? "<green>" + result.message() : "<red>" + result.message());
                                })))
                .child(Cmd.group("map", "Manage the active arena map",
                        Permissions.BG_ADMIN, CommandNode.Group.ADMIN)
                        .arg("id", CommandNode.Arg.WORD, ctx -> {
                            String id = ctx.argument();
                            if (id == null || id.isBlank()) {
                                ctx.reply("<red>Usage: /bg map <id|capture>");
                                return;
                            }
                            boolean switched = manager().switchActiveMap(id);
                            ctx.reply(switched
                                    ? "<green>Active map set to <white>" + id + "<green>."
                                    : "<red>Unknown or unavailable map: <white>" + id);
                        })
                        .suggestions(() -> manager().arenaStore().mapIds())
                        .child(Cmd.leaf("capture", "Capture the prepared arena into a snapshot",
                                Permissions.BG_ADMIN, CommandNode.Group.ADMIN)
                                .runs(ctx -> {
                                    boolean captured = manager().captureSnapshot();
                                    ctx.reply(captured
                                            ? "<green>Arena snapshot captured for the active map."
                                            : "<red>Could not capture the arena snapshot â€” see the console log.");
                                })))
                .child(BalanceCommands.credits(plugin))
                .child(BalanceCommands.streak(plugin))
                .child(Cmd.group("debug", "Inspect abilities and custom items",
                        Permissions.BG_ADMIN, CommandNode.Group.ADMIN)
                        .child(Cmd.leaf("ability", "Perform a registered ability by id (bypasses gates)",
                                Permissions.BG_ADMIN, CommandNode.Group.ADMIN)
                                .runs(ctx -> listAbilityIds(ctx))
                                .arg("id", CommandNode.Arg.WORD, ctx -> debugAbility(ctx))
                                .suggestions(() -> AdminTargetResolver.bareValueIds(abilityIds())))
                        .child(Cmd.leaf("item", "Grant a custom item by id (bypasses gates)",
                                Permissions.BG_ADMIN, CommandNode.Group.ADMIN)
                                .runs(ctx -> listCustomItemIds(ctx))
                                .arg("id", CommandNode.Arg.WORD, ctx -> debugItem(ctx))
                                .suggestions(() -> AdminTargetResolver.bareValueIds(customItemIds())))
                        .child(Cmd.leaf("clearclaims", "Drop stale domain claims when no domain is live",
                                Permissions.BG_ADMIN, CommandNode.Group.ADMIN)
                                .runs(ctx -> {
                                    if (plugin.getSessionManager().hasActiveSessions()) {
                                        ctx.reply("<red>A domain is still active â€” claims were left in place.");
                                        return;
                                    }
                                    int cleared = DomainRegistry.get().clearClaimsIfIdle();
                                    ctx.reply("<green>Cleared <white>" + cleared + "<green> stale domain claim(s).");
                                })))
                .build();
    }

    /** Full namespaced ids of every registered ability, hidden domains included. */
    private List<String> abilityIds() {
        List<String> ids = new ArrayList<>();
        for (AbilityDefinition definition : plugin.getAbilityRegistry().all()) {
            ids.add(definition.id().toString());
        }
        return ids;
    }

    /** {@code /bg debug ability} with no id: every registered id, hidden domains included. */
    private void listAbilityIds(CommandContext ctx) {
        ctx.reply("<aqua>Abilities: <white>" + String.join(", ", abilityIds()));
    }

    /**
     * {@code /bg debug ability <id>}: performs a registered ability on the admin directly,
     * bypassing the cooldown / ownership / rail gates. Both namespaced ({@code jjk:blue}) and bare
     * ({@code blue}) ids are accepted; a live domain owned by the admin blocks a second cast.
     */
    private void debugAbility(CommandContext ctx) {
        Player p = ctx.player();
        if (p == null) return;
        List<String> ids = abilityIds();
        String id = AdminTargetResolver.resolveAbility(ctx.argument(), ids);
        if (id == null) {
            ctx.reply("<red>No ability matches <white>" + MiniMessage.miniMessage().escapeTags(ctx.argument()) + "<red>. Known: <white>"
                    + String.join(", ", ids));
            return;
        }
        AbilityDefinition def = null;
        for (AbilityDefinition candidate : plugin.getAbilityRegistry().all()) {
            if (candidate.id().toString().equals(id)) {
                def = candidate;
                break;
            }
        }
        if (def == null) {
            ctx.reply("<red>No ability matches <white>" + MiniMessage.miniMessage().escapeTags(ctx.argument()) + "<red>. Known: <white>"
                    + String.join(", ", ids));
            return;
        }
        if (plugin.getSessionManager().hasActiveSession(p.getUniqueId())) {
            ctx.reply("<red>You already own a live domain â€” close it before casting another.");
            return;
        }
        CastResult result = plugin.getAbilityCaster().cast(p, def.id(), CastSource.DEBUG);
        if (!result.success()) {
            ctx.reply("<red>The cast was rejected: <white>" + result.name().toLowerCase(Locale.ROOT)
                    + "<red>.");
            return;
        }
        ctx.reply("<green>Performed <white>" + def.id() + "<green>. <gray>(gates bypassed)");
    }

    /** Full ids of every custom-item row in the market catalogue. */
    private List<String> customItemIds() {
        List<String> ids = new ArrayList<>();
        for (ShopEntry entry : manager().shopCatalog().entries()) {
            if (entry.isCustomItem()) ids.add(entry.id());
        }
        return ids;
    }

    /** {@code /bg debug item} with no id: every custom-item id in the market catalogue. */
    private void listCustomItemIds(CommandContext ctx) {
        ctx.reply("<aqua>Custom items: <white>" + String.join(", ", customItemIds()));
    }

    /** {@code /bg debug item <id>}: build the stamped stack and drop it into the admin's inventory. */
    private void debugItem(CommandContext ctx) {
        Player admin = ctx.player();
        if (admin == null) return;
        ShopCatalog catalog = manager().shopCatalog();
        List<String> itemIds = customItemIds();
        String itemId = AdminTargetResolver.resolveItem(ctx.argument(), itemIds);
        if (itemId == null) {
            ctx.reply("<red>No item matches <white>" + MiniMessage.miniMessage().escapeTags(ctx.argument()) + "<red>. Known: <white>"
                    + String.join(", ", itemIds));
            return;
        }
        for (ShopEntry entry : catalog.entries()) {
            if (entry.isCustomItem() && entry.id().equals(itemId)) {
                ItemStack stack = catalog.itemStack(entry);
                if (stack == null) {
                    ctx.reply("<red><white>" + itemId + "<red> has no item data in the shop.");
                    return;
                }
                Map<Integer, ItemStack> overflow = admin.getInventory().addItem(stack);
                for (ItemStack leftover : overflow.values()) {
                    admin.getWorld().dropItemNaturally(admin.getLocation(), leftover);
                }
                ctx.reply("<green>Gave <white>" + itemId + "<green> to your inventory.");
                return;
            }
        }
    }
}



