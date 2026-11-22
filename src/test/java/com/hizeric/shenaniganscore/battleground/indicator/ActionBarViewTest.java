package com.hizeric.shenaniganscore.battleground.indicator;

import com.hizeric.shenaniganscore.battleground.shop.ShopEntry;
import com.hizeric.shenaniganscore.core.ability.AbilityId;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ActionBarViewTest {

    private static final PlainTextComponentSerializer PLAIN = PlainTextComponentSerializer.plainText();

    private static ActionBarView.SlotSource source(AbilityId id) {
        return new ActionBarView.SlotSource(id, null, 0, IndicatorIcon.NONE, 0);
    }

    @Test
    void threeSlotsAlwaysRenderInDefinedOrderWithAPlainItemInHand() {
        List<ActionBarView.Slot> slots = ActionBarView.build(
                ActionBarView.SlotSource.none(IndicatorIcon.NONE),
                source(AbilityId.of("jjk:blue")),
                source(AbilityId.of("jjk:purple")));

        assertEquals(3, slots.size());
        assertEquals(List.of(1, 2, 3), slots.stream().map(ActionBarView.Slot::number).toList());
        assertEquals(ActionBarView.State.EMPTY, slots.get(0).state());
        assertTrue(slots.get(0).visible());
        assertEquals(ActionBarView.State.READY, slots.get(1).state());
        assertEquals("Blue", slots.get(1).label());
        assertEquals("Purple", slots.get(2).label());
    }

    @Test
    void buildMarksEveryUnboundSlotEmptyAndVisible() {
        List<ActionBarView.Slot> slots = ActionBarView.build(
                ActionBarView.SlotSource.none(IndicatorIcon.NONE),
                ActionBarView.SlotSource.none(IndicatorIcon.NONE),
                ActionBarView.SlotSource.none(IndicatorIcon.NONE));

        assertEquals(List.of(ActionBarView.State.EMPTY,
                        ActionBarView.State.EMPTY, ActionBarView.State.EMPTY),
                slots.stream().map(ActionBarView.Slot::state).toList());
        assertTrue(slots.stream().allMatch(ActionBarView.Slot::visible));
    }

    @Test
    void slotOneFollowsTheHeldCustomItem() {
        List<ActionBarView.Slot> slots = ActionBarView.build(
                source(AbilityId.of("item:dash")),
                ActionBarView.SlotSource.none(IndicatorIcon.NONE),
                ActionBarView.SlotSource.none(IndicatorIcon.NONE));

        assertEquals(ActionBarView.State.READY, slots.get(0).state());
        assertEquals("Dash", slots.get(0).label());
        assertEquals(ActionBarView.State.EMPTY, slots.get(1).state());
        assertEquals(ActionBarView.State.EMPTY, slots.get(2).state());
    }

    @Test
    void aSuppliedDisplayNameWinsOverTheHumanisedId() {
        ActionBarView.Slot slot = ActionBarView.build(
                ActionBarView.SlotSource.none(IndicatorIcon.NONE),
                new ActionBarView.SlotSource(AbilityId.of("jjk:blue"), null, 0,
                        IndicatorIcon.NONE, 0, "Aoi Blue"),
                ActionBarView.SlotSource.none(IndicatorIcon.NONE)).get(1);

        assertEquals("Aoi Blue", slot.label());
    }

    @Test
    void anUnderscoredIdBecomesAReadableDisplayName() {
        ActionBarView.Slot slot = ActionBarView.build(
                source(AbilityId.of("minecraft:dash_sword")),
                ActionBarView.SlotSource.none(IndicatorIcon.NONE),
                ActionBarView.SlotSource.none(IndicatorIcon.NONE)).get(0);

        assertEquals("Dash Sword", slot.label());
    }

    @Test
    void lockedSlotCarriesCatalogPriceStreakRequirementAndProgress() {
        ShopEntry.Gate gate = new ShopEntry.Gate(false, 1, 3, 750);
        ActionBarView.Slot slot = ActionBarView.build(
                ActionBarView.SlotSource.none(IndicatorIcon.NONE),
                new ActionBarView.SlotSource(AbilityId.of("jjk:blue"), gate, 0, IndicatorIcon.NONE, 300),
                ActionBarView.SlotSource.none(IndicatorIcon.NONE)).get(1);

        assertEquals(ActionBarView.State.LOCKED, slot.state());
        assertEquals(750, slot.price());
        assertEquals(3, slot.required());
        assertEquals(1, slot.progress());
        assertTrue(slot.visible());
    }

    @Test
    void anOwnedAbilityStaysLockedUntilTheLiveStreakIsMet() {
        ShopEntry.Gate gate = new ShopEntry.Gate(true, 2, 3, 750);
        ActionBarView.Slot slot = ActionBarView.build(
                ActionBarView.SlotSource.none(IndicatorIcon.NONE),
                new ActionBarView.SlotSource(AbilityId.of("jjk:blue"), gate, 0, IndicatorIcon.NONE, 750),
                ActionBarView.SlotSource.none(IndicatorIcon.NONE)).get(1);

        assertEquals(ActionBarView.State.LOCKED, slot.state());
        assertEquals(2, slot.progress());
    }

    @Test
    void aReadySlotOnceOwnershipAndStreakAreSatisfied() {
        ShopEntry.Gate gate = new ShopEntry.Gate(true, 3, 3, 750);
        ActionBarView.Slot slot = ActionBarView.build(
                ActionBarView.SlotSource.none(IndicatorIcon.NONE),
                new ActionBarView.SlotSource(AbilityId.of("jjk:blue"), gate, 0, IndicatorIcon.NONE, 750),
                ActionBarView.SlotSource.none(IndicatorIcon.NONE)).get(1);

        assertEquals(ActionBarView.State.READY, slot.state());
        assertTrue(slot.visible());
    }

    @Test
    void renderJoinsExactlyThreeIconFragmentsWithSingleSpaces() {
        IndicatorIcon first = () -> Component.text("\u25C6");
        IndicatorIcon second = () -> Component.text("\u25CF");
        IndicatorIcon third = () -> Component.text("\u25B2");
        Component comp = ActionBarView.render(ActionBarView.build(
                ActionBarView.SlotSource.none(first),
                new ActionBarView.SlotSource(AbilityId.of("jjk:blue"), null, 0, second, 0),
                new ActionBarView.SlotSource(AbilityId.of("jjk:purple"), null, 7, third, 0)));

        assertEquals("\u25C6 \u25CF \u25B2", PLAIN.serialize(comp));
    }

    @Test
    void renderJoinsLockedIconsExactlyLikeOtherStates() {
        IndicatorIcon first = () -> Component.text("\u25C6");
        IndicatorIcon second = () -> Component.text("\u25CF");
        IndicatorIcon third = () -> Component.text("\u25B2");
        ShopEntry.Gate gate = new ShopEntry.Gate(false, 1, 3, 750);
        Component comp = ActionBarView.render(ActionBarView.build(
                new ActionBarView.SlotSource(AbilityId.of("jjk:blue"), gate, 0, first, 0),
                new ActionBarView.SlotSource(AbilityId.of("jjk:purple"), gate, 0, second, 0),
                new ActionBarView.SlotSource(AbilityId.of("jjk:red"), gate, 0, third, 0)));

        assertEquals("\u25C6 \u25CF \u25B2", PLAIN.serialize(comp));
    }

    @Test
    void renderWithOnlyNoneIconsEmitsNoText() {
        Component comp = ActionBarView.render(ActionBarView.build(
                ActionBarView.SlotSource.none(IndicatorIcon.NONE),
                ActionBarView.SlotSource.none(IndicatorIcon.NONE),
                ActionBarView.SlotSource.none(IndicatorIcon.NONE)));

        assertEquals("  ", PLAIN.serialize(comp));
    }
}