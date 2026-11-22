package com.hizeric.shenaniganscore.battleground.indicator;

import com.hizeric.shenaniganscore.battleground.shop.ShopEntry;
import com.hizeric.shenaniganscore.core.ability.AbilityId;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.JoinConfiguration;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class ActionBarView {

    public enum State { EMPTY, LOCKED, COOLDOWN, READY }

    public record SlotSource(AbilityId ability, ShopEntry.Gate gate, int cooldown,
                             IndicatorIcon icon, int credits, String displayName) {
        public SlotSource(AbilityId ability, ShopEntry.Gate gate, int cooldown,
                          IndicatorIcon icon, int credits) {
            this(ability, gate, cooldown, icon, credits, null);
        }
        public static SlotSource none(IndicatorIcon icon) {
            return new SlotSource(null, null, 0, icon, 0, null);
        }
    }

    public record Slot(int number, State state, String label, IndicatorIcon icon,
                       int price, int required, int progress, int cooldown,
                       int credits, boolean owned) {
        public boolean visible() { return true; }
    }

    private ActionBarView() { }

    public static List<Slot> build(SlotSource held, SlotSource normal, SlotSource ultimate) {
        List<Slot> slots = new ArrayList<>(3);
        slots.add(slot(1, held));
        slots.add(slot(2, normal));
        slots.add(slot(3, ultimate));
        return List.copyOf(slots);
    }

    private static Slot slot(int number, SlotSource source) {
        AbilityId id = source.ability();
        IndicatorIcon icon = source.icon() == null ? IndicatorIcon.NONE : source.icon();
        if (id == null) {
            return new Slot(number, State.EMPTY, "\u2500", icon, 0, 0, 0, 0, 0, false);
        }
        ShopEntry.Gate gate = source.gate();
        int price    = gate == null ? 0 : gate.price();
        int required = gate == null ? 0 : gate.required();
        int progress = gate == null ? 0 : gate.progress();
        boolean owned = gate != null && gate.owned();
        int cooldown = Math.max(0, source.cooldown());
        State state = (gate != null && !gate.canUse()) ? State.LOCKED
                     : cooldown > 0 ? State.COOLDOWN
                     : State.READY;
        String label = displayName(source, id);
        return new Slot(number, state, label, icon, price, required, progress,
                cooldown, Math.max(0, source.credits()), owned);
    }

    private static String displayName(SlotSource source, AbilityId id) {
        String supplied = source.displayName();
        if (supplied != null && !supplied.isBlank()) return supplied;
        return humanize(id.value());
    }

    private static String humanize(String value) {
        String[] parts = value.toLowerCase(Locale.ROOT).split("_");
        StringBuilder sb = new StringBuilder();
        for (String part : parts) {
            if (part.isEmpty()) continue;
            if (!sb.isEmpty()) sb.append(' ');
            sb.append(Character.toUpperCase(part.charAt(0))).append(part.substring(1));
        }
        return sb.isEmpty() ? value : sb.toString();
    }

    public static Component render(List<Slot> slots) {
        List<Component> fragments = new ArrayList<>(slots.size());
        for (Slot slot : slots) {
            IndicatorIcon icon = slot.icon() == null ? IndicatorIcon.NONE : slot.icon();
            fragments.add(icon.render());
        }
        return Component.join(JoinConfiguration.separator(Component.space()), fragments);
    }
}