package com.hizeric.shenaniganscore.battleground.command;

import com.hizeric.shenaniganscore.ShenanigansCore;
import com.hizeric.shenaniganscore.battleground.ability.AbilityRail;
import com.hizeric.shenaniganscore.core.ability.CastRequest;
import com.hizeric.shenaniganscore.core.ability.CastSource;
import org.bukkit.entity.Player;

/**
 * The only cast path: <code>/ability 1|2|3</code> (alias <code>/ab</code>). No right-click,
 * keybind or item inputs. Declared on the shared {@link Cmd} tree.
 *
 * Slot precedence: {@code /ability 1} casts the held custom item's ability (rail slot 0) - the
 * cast is driven by the held item, so when nothing is held the slot reports empty and the
 * persisted rail binding for slot 0 is ignored. {@code /ability 2} and {@code /ability 3} cast
 * the rail's normal and ultimate bindings (slots 1 and 2).
 */
public final class AbilityCommand {

    private final ShenanigansCore plugin;
    private final CommandNode root;

    public AbilityCommand(ShenanigansCore plugin) {
        this.plugin = plugin;
        this.root = buildTree();
    }

    /** The root node for this command tree; the registrar and help both read it. */
    public CommandNode root() {
        return root;
    }

    private CommandNode buildTree() {
        return Cmd.root("ability", "Cast a bound ability", Permissions.USE, CommandNode.Group.PLAYER)
                .aliases("ab")
                .child(slot("1", 0))
                .child(slot("2", 1))
                .child(slot("3", 2))
                .build();
    }

    private Cmd slot(String label, int index) {
        String description = index == AbilityRail.ULTIMATE_SLOT
                ? "Cast your ultimate" : "Cast bound ability " + label;
        return Cmd.leaf(label, description, Permissions.USE, CommandNode.Group.PLAYER)
                .runs(ctx -> cast(ctx, index));
    }

    /** Casts for one slot. Slot 0 (the held-item slot) is driven by the held custom item - when
     * nothing is held, the slot reports empty and the persisted rail binding for slot 0 is
     * ignored. Slots 1 and 2 cast the rail's normal and ultimate bindings. */
    private void cast(CommandContext ctx, int slot) {
        Player player = ctx.player();
        if (player == null) return;
        plugin.getCastTriggerService().submit(new CastRequest(player, slot, CastSource.COMMAND));
    }
}

