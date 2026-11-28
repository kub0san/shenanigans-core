package com.hizeric.shenaniganscore.origins.minecraft.weapons.dashsword;

import com.hizeric.shenaniganscore.ShenanigansCore;
import com.hizeric.shenaniganscore.core.ability.CastRequest;
import com.hizeric.shenaniganscore.core.ability.CastSource;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

import java.util.function.Consumer;

/**
 * Right-clicking the held Dash Sword submits a normal-slot cast through the shared funnel.
 *
 * <p>Runs at MONITOR and ignores cancelled clicks: {@code ActionLockListener} owns cancellation,
 * so a locked click must never reach the funnel or produce a denial notice. The service is
 * resolved at event time because abilities register before the cast funnel is constructed.</p>
 */
public final class DashSwordRightClickListener implements Listener {

    private final Consumer<CastRequest> submit;

    public DashSwordRightClickListener(ShenanigansCore plugin) {
        this(request -> plugin.getCastTriggerService().submit(request));
    }

    DashSwordRightClickListener(Consumer<CastRequest> submit) {
        this.submit = submit;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onRightClick(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) return;

        Action action = event.getAction();
        if (action != Action.RIGHT_CLICK_AIR && action != Action.RIGHT_CLICK_BLOCK) return;

        Player player = event.getPlayer();
        ItemStack held = player.getInventory().getItemInMainHand();
        if (!DashSwordItem.isDashSword(held)) return;

        submit.accept(new CastRequest(player, 1, CastSource.ITEM));
    }
}
