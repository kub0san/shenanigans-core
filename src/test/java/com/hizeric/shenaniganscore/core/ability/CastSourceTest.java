package com.hizeric.shenaniganscore.core.ability;

import com.hizeric.shenaniganscore.battleground.ability.AbilityRail;
import com.hizeric.shenaniganscore.battleground.economy.EconomyService;
import com.hizeric.shenaniganscore.core.cooldown.CooldownManager;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The cast funnel's two entry points use one service: a request reaches the installed gate once,
 * performs once and claims the same cooldown key, whether it came from a command or an item.
 */
class CastSourceTest {

    private ServerMock server;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void sourcesAreCommandItemAndDebug() {
        assertArrayEquals(new CastSource[] { CastSource.COMMAND, CastSource.ITEM, CastSource.DEBUG },
                CastSource.values());
    }

    @Test
    void aCommandRequestReachesTheCasterExactlyOnce() {
        PlayerMock player = server.addPlayer();
        AbilityId id = AbilityId.of("test:trigger");
        AtomicInteger gateCalls = new AtomicInteger();
        AtomicInteger performs = new AtomicInteger();
        CooldownManager cooldowns = new CooldownManager();
        AbilityCaster caster = caster(id, cooldowns, gateCalls, performs);

        new CastTriggerService(state(player.getUniqueId(), id), caster, cooldowns)
                .submit(new CastRequest(player, AbilityRail.NORMAL_SLOT, CastSource.COMMAND));

        assertEquals(1, gateCalls.get(), "the installed gate must run once per request");
        assertEquals(1, performs.get(), "the ability must be performed once");
        assertTrue(cooldowns.isOnCooldown(player.getUniqueId(), id.toString()),
                "the cooldown key must be the ability id");
    }

    @Test
    void anItemRequestSharesTheCommandCooldownAndDoesNotCastTwice() {
        PlayerMock player = server.addPlayer();
        AbilityId id = AbilityId.of("test:trigger");
        AtomicInteger gateCalls = new AtomicInteger();
        AtomicInteger performs = new AtomicInteger();
        CooldownManager cooldowns = new CooldownManager();
        CastTriggerService service = new CastTriggerService(state(player.getUniqueId(), id),
                caster(id, cooldowns, gateCalls, performs), cooldowns);

        service.submit(new CastRequest(player, AbilityRail.NORMAL_SLOT, CastSource.ITEM));
        service.submit(new CastRequest(player, AbilityRail.NORMAL_SLOT, CastSource.COMMAND));

        assertEquals(1, gateCalls.get(), "the second request must be stopped before the caster");
        assertEquals(1, performs.get(), "both sources share one cooldown claim");
        assertTrue(cooldowns.isOnCooldown(player.getUniqueId(), id.toString()));
    }

    @Test
    void definitionCarriesOneFixedPower() {
        AbilityDefinition definition = new AbilityDefinition(
                AbilityId.of("test:fixed"),
                Component.text("Fixed"),
                Material.END_ROD,
                60,
                () -> 5,
                2.5,
                (player, power) -> { },
                player -> false,
                false);

        assertEquals(2.5, definition.power());
    }

    private static AbilityCaster caster(AbilityId id, CooldownManager cooldowns,
                                        AtomicInteger gateCalls, AtomicInteger performs) {
        AbilityRegistry registry = new AbilityRegistry(message -> { });
        registry.register(new AbilityDefinition(id, Component.text(id.toString()), Material.STONE,
                0, () -> 30, 1.0, (player, power) -> performs.incrementAndGet(),
                player -> false, false));
        AbilityCaster caster = new AbilityCaster(registry, cooldowns);
        caster.addGate((player, definition, source) -> {
            gateCalls.incrementAndGet();
            return CastResult.OK;
        });
        return caster;
    }

    private static CastTriggerService.State state(UUID uuid, AbilityId id) {
        return new CastTriggerService.State() {
            @Override public boolean deployed(UUID player) { return uuid.equals(player); }
            @Override public AbilityId bound(org.bukkit.entity.Player player, int slot) { return id; }
            @Override public EconomyService.CastGate castGate(org.bukkit.entity.Player player, AbilityId ability) {
                return new EconomyService.CastGate(true, 0, 0, true);
            }
            @Override public boolean ultimateBound(AbilityId ability) { return false; }
        };
    }
}
