package com.hizeric.shenaniganscore.core.ability;

import com.hizeric.shenaniganscore.battleground.BattlegroundManager;
import com.hizeric.shenaniganscore.battleground.ability.AbilityRail;
import com.hizeric.shenaniganscore.battleground.ability.CastInput;
import com.hizeric.shenaniganscore.battleground.ability.HeldItemAbility;
import com.hizeric.shenaniganscore.battleground.ability.TechniqueHud;
import com.hizeric.shenaniganscore.battleground.economy.EconomyService;
import com.hizeric.shenaniganscore.battleground.player.BattlegroundPlayer;
import com.hizeric.shenaniganscore.battleground.shop.ShopEntry;
import com.hizeric.shenaniganscore.core.cooldown.CooldownManager;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.entity.Player;

import java.util.Objects;
import java.util.UUID;

/**
 * The single cast funnel: every source submits one request, the service decides it with
 * {@link CastInput}'s precedence and calls {@link AbilityCaster} once. Rejections reuse the
 * command's one-shot notice text, so a command and an item trigger cannot diverge.
 */
public final class CastTriggerService {

    private static final long NOTICE_MILLIS = 3_000L;
    private static final MiniMessage MM = MiniMessage.miniMessage();

    /** The battleground reads the funnel needs, kept separate so decisions are unit-testable. */
    interface State {
        boolean deployed(UUID uuid);

        /** Slot 0 follows the held custom item; the other slots read the persisted rail. */
        AbilityId bound(Player player, int slot);

        EconomyService.CastGate castGate(Player player, AbilityId id);

        boolean ultimateBound(AbilityId id);
    }

    private final State state;
    private final AbilityCaster caster;
    private final CooldownManager cooldowns;

    public CastTriggerService(BattlegroundManager manager, AbilityCaster caster, CooldownManager cooldowns) {
        this(new FacadeState(manager), caster, cooldowns);
    }

    CastTriggerService(State state, AbilityCaster caster, CooldownManager cooldowns) {
        this.state = Objects.requireNonNull(state, "state");
        this.caster = Objects.requireNonNull(caster, "caster");
        this.cooldowns = Objects.requireNonNull(cooldowns, "cooldowns");
    }

    /** Decides and casts once; a rejected request never reaches the caster. */
    public void submit(CastRequest request) {
        if (request == null || request.player() == null) return;
        Player player = request.player();
        int slot = request.slot();
        if (slot < 0 || slot >= AbilityRail.TOTAL) {
            notice(player, "<yellow>That cast slot does not exist.");
            return;
        }

        AbilityId id = state.bound(player, slot);
        if (id == null) {
            notice(player, slot == AbilityRail.HELD_SLOT
                    ? "<yellow>Hold your custom item to use slot <white>1<yellow>."
                    : "<yellow>Slot <white>" + (slot + 1)
                            + "<yellow> is empty. Bind one from the hub's <white>Loadout<yellow> screen.");
            return;
        }

        long remainingMillis = cooldowns.getRemainingMillis(player.getUniqueId(), id.toString());
        int cooldownSeconds = (int) Math.ceil(remainingMillis / 1000.0);
        EconomyService.CastGate gate = state.castGate(player, id);
        boolean heldItemMatches = slot != AbilityRail.HELD_SLOT
                || HeldItemAbility.matches(id, state.bound(player, AbilityRail.HELD_SLOT));
        CastInput.Result result = CastInput.decide(state.deployed(player.getUniqueId()),
                gate.owned(), remainingMillis > 0, gate.streakMet(), slot, heldItemMatches,
                state.ultimateBound(id), cooldownSeconds);

        switch (result.decision()) {
            case CAST -> caster.cast(player, id, request.source());
            case NOT_READY -> {
                if (result.reason() == CastInput.Reason.STREAK_NOT_MET) {
                    notice(player, "<yellow>Need <white>" + gate.required()
                            + "<white> kills for <white>" + escape(id.value()));
                } else if (result.reason() == CastInput.Reason.ON_COOLDOWN) {
                    notice(player, "<yellow>Not ready yet — " + escape(id.value())
                            + " is on cooldown for <white>" + cooldownSeconds + "<white>s.");
                } else {
                    notice(player, "<yellow>Not ready yet.");
                }
            }
            case IGNORE -> {
                switch (result.reason()) {
                    case NOT_DEPLOYED -> notice(player, "<yellow>Join a round before casting.");
                    case NOT_OWNED -> notice(player, "<yellow>You have not unlocked <white>"
                            + escape(id.value()) + "<yellow>.");
                    case HELD_ITEM_MISMATCH -> notice(player,
                            "<yellow>Hold your custom item to use slot <white>1<yellow>.");
                    case ULTIMATE_NOT_BOUND -> notice(player, "<yellow>Your ultimate slot is empty.");
                    case BAD_SLOT -> notice(player, "<yellow>That cast slot does not exist.");
                    default -> notice(player, "<yellow>You cannot cast that right now.");
                }
            }
        }
    }

    private static void notice(Player player, String message) {
        TechniqueHud.showNotice(player, message, NOTICE_MILLIS);
    }

    private static String escape(String raw) {
        return raw == null ? "" : MM.escapeTags(raw);
    }

    /** Only the public BattlegroundManager facade; AbilityGateService stays package-private. */
    private record FacadeState(BattlegroundManager manager) implements State {
        @Override public boolean deployed(UUID uuid) { return manager.isDeployed(uuid); }

        @Override public AbilityId bound(Player player, int slot) {
            if (slot == AbilityRail.HELD_SLOT) return manager.heldItemAbility(player);
            BattlegroundPlayer data = manager.playerStore().get(player.getUniqueId());
            return data == null ? null : data.rail().get(slot);
        }

        @Override public EconomyService.CastGate castGate(Player player, AbilityId id) {
            return manager.economy().castGate(player, id, manager.shopCatalog());
        }

        @Override public boolean ultimateBound(AbilityId id) {
            ShopEntry entry = manager.shopCatalog().entryForAbility(id.toString());
            return entry != null && entry.isUltimate();
        }
    }
}
