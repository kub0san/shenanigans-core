package com.hizeric.shenaniganscore.origins.minecraft.weapons.dashsword;

import com.hizeric.shenaniganscore.battleground.ability.AbilityRail;
import com.hizeric.shenaniganscore.battleground.item.BattlegroundItemKeys;
import com.hizeric.shenaniganscore.core.ability.CastRequest;
import com.hizeric.shenaniganscore.core.ability.CastSource;
import org.bukkit.Material;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.Plugin;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Behavioural wiring: only a right-click on the held dash sword reaches the cast funnel. */
class DashSwordRightClickTest {

    private ServerMock server;
    private PlayerMock player;
    private List<CastRequest> submissions;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        Plugin testPlugin = MockBukkit.createMockPlugin("DashSwordRightClickTest");
        player = server.addPlayer();
        submissions = new ArrayList<>();
        server.getPluginManager().registerEvents(
                new DashSwordRightClickListener(submissions::add), testPlugin);
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void aHeldDashSwordRightClickSubmitsOnce() {
        holdSword();

        fire(Action.RIGHT_CLICK_AIR);

        assertEquals(1, submissions.size());
        CastRequest request = submissions.get(0);
        assertSame(player, request.player());
        assertEquals(AbilityRail.NORMAL_SLOT, request.slot());
        assertEquals(CastSource.ITEM, request.source());
    }

    @Test
    void rightClickOnABlockSubmitsToo() {
        holdSword();

        fire(Action.RIGHT_CLICK_BLOCK);

        assertEquals(1, submissions.size());
    }

    @Test
    void aCancelledInteractSubmitsNothing() {
        holdSword();
        PlayerInteractEvent event = interact(Action.RIGHT_CLICK_AIR);
        event.setCancelled(true);

        server.getPluginManager().callEvent(event);

        assertTrue(submissions.isEmpty(), "a locked click must never reach the funnel");
    }

    @Test
    void aPlainSwordSubmitsNothing() {
        player.getInventory().setItemInMainHand(new ItemStack(Material.NETHERITE_SWORD));

        fire(Action.RIGHT_CLICK_AIR);

        assertTrue(submissions.isEmpty());
    }

    @Test
    void leftClicksSubmitNothing() {
        holdSword();

        fire(Action.LEFT_CLICK_AIR);
        fire(Action.LEFT_CLICK_BLOCK);

        assertTrue(submissions.isEmpty());
    }

    @Test
    void theHandlerRunsAtMonitorAndIgnoresCancellation() throws Exception {
        EventHandler annotation = DashSwordRightClickListener.class
                .getDeclaredMethod("onRightClick", PlayerInteractEvent.class)
                .getAnnotation(EventHandler.class);

        assertEquals(EventPriority.MONITOR, annotation.priority());
        assertTrue(annotation.ignoreCancelled());
    }

    private void holdSword() {
        ItemStack sword = new ItemStack(Material.NETHERITE_SWORD);
        ItemMeta meta = sword.getItemMeta();
        BattlegroundItemKeys.stamp(meta, DashSwordItem.ID);
        sword.setItemMeta(meta);
        player.getInventory().setItemInMainHand(sword);
    }

    private void fire(Action action) {
        server.getPluginManager().callEvent(interact(action));
    }

    private PlayerInteractEvent interact(Action action) {
        // Synthetic events with no clicked block start cancelled in Bukkit; uncancel so only the
        // cancelled-case test exercises ignoreCancelled.
        PlayerInteractEvent event = new PlayerInteractEvent(player, action, null, null, null);
        event.setCancelled(false);
        return event;
    }
}
