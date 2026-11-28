package com.hizeric.shenaniganscore.battleground.command;

import com.hizeric.shenaniganscore.ShenanigansCore;
import com.hizeric.shenaniganscore.battleground.BattlegroundManager;
import com.hizeric.shenaniganscore.battleground.ability.AbilityRail;
import com.hizeric.shenaniganscore.battleground.ability.CastInput;
import com.hizeric.shenaniganscore.battleground.ability.HeldItemAbility;
import com.hizeric.shenaniganscore.battleground.ability.TechniqueHud;
import com.hizeric.shenaniganscore.battleground.economy.EconomyService;
import com.hizeric.shenaniganscore.battleground.player.BattlegroundPlayer;
import com.hizeric.shenaniganscore.core.ability.AbilityId;
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

    private static final long NOTICE_MILLIS = 3_000L;

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

        BattlegroundManager manager = plugin.getBattlegroundManager();
        BattlegroundPlayer data = manager.playerStore().get(player.getUniqueId());
        AbilityId id = data == null ? null : data.rail().get(slot);
        if (slot == HeldItemAbility.SLOT) {
            // Slot 1 is driven by the held custom item, never by a stale persisted bind.
            id = manager.heldItemAbility(player);
        }
        if (id == null) {
            TechniqueHud.showNotice(player, slot == HeldItemAbility.SLOT
                    ? "<yellow>Hold your custom item to use slot <white>1<yellow>."
                    : "<yellow>Slot <white>" + (slot + 1)
                            + "<yellow> is empty. Bind one from the hub's <white>Loadout<yellow> screen.",
                    NOTICE_MILLIS);
            return;
        }

        boolean deployed = manager.isDeployed(player.getUniqueId());
        EconomyService.CastGate gate = manager.economy().castGate(player, id, manager.shopCatalog());
        long remainingMillis = plugin.getCooldownManager()
                .getRemainingMillis(player.getUniqueId(), id.toString());
        boolean onCooldown = remainingMillis > 0;
        int cooldownSeconds = (int) Math.ceil(remainingMillis / 1000.0);

        boolean heldItemMatches = slot != AbilityRail.HELD_SLOT
                || HeldItemAbility.matches(id, manager.heldItemAbility(player));
        com.hizeric.shenaniganscore.battleground.shop.ShopEntry ultimateEntry =
                slot == AbilityRail.ULTIMATE_SLOT
                        ? manager.shopCatalog().entryForAbility(id.toString()) : null;
        boolean ultimateBound = ultimateEntry != null && ultimateEntry.isUltimate();

        CastInput.Result result = CastInput.decide(deployed, gate.owned(), onCooldown,
                gate.streakMet(), slot, heldItemMatches, ultimateBound, cooldownSeconds);

        switch (result.decision()) {
            case CAST -> plugin.getAbilityCaster().cast(player, id, CastSource.COMMAND);
            case NOT_READY -> {
                if (result.reason() == CastInput.Reason.STREAK_NOT_MET) {
                    TechniqueHud.showNotice(player, "<yellow>Need <white>" + gate.required()
                            + "<white> kills for <white>" + ctx.escape(id.value()), NOTICE_MILLIS);
                } else if (result.reason() == CastInput.Reason.ON_COOLDOWN) {
                    TechniqueHud.showNotice(player, "<yellow>Not ready yet — " + ctx.escape(id.value())
                            + " is on cooldown for <white>" + cooldownSeconds + "<white>s.",
                            NOTICE_MILLIS);
                } else {
                    TechniqueHud.showNotice(player, "<yellow>Not ready yet.", NOTICE_MILLIS);
                }
            }
            case IGNORE -> {
                switch (result.reason()) {
                    case NOT_DEPLOYED -> TechniqueHud.showNotice(player,
                            "<yellow>Join a round before casting.", NOTICE_MILLIS);
                    case NOT_OWNED -> TechniqueHud.showNotice(player, "<yellow>You have not unlocked <white>"
                            + ctx.escape(id.value()) + "<yellow>.", NOTICE_MILLIS);
                    case HELD_ITEM_MISMATCH -> TechniqueHud.showNotice(player,
                            "<yellow>Hold your custom item to use slot <white>1<yellow>.", NOTICE_MILLIS);
                    case ULTIMATE_NOT_BOUND -> TechniqueHud.showNotice(player,
                            "<yellow>Your ultimate slot is empty.", NOTICE_MILLIS);
                    case BAD_SLOT -> TechniqueHud.showNotice(player,
                            "<yellow>That cast slot does not exist.", NOTICE_MILLIS);
                    default -> TechniqueHud.showNotice(player,
                            "<yellow>You cannot cast that right now.", NOTICE_MILLIS);
                }
            }
        }
    }
}

