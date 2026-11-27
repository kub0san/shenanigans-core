package com.hizeric.shenaniganscore.core.ability;

import com.hizeric.shenaniganscore.core.cooldown.CooldownManager;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

import java.lang.reflect.Proxy;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Behavioural coverage for the cast gate's failure paths (audit #25, F-22).
 *
 * <p>The cooldown is claimed <em>before</em> the performer runs so two concurrent casts cannot
 * both pass. That means a throwing performer must hand the claim back, or the player is locked out
 * for the full duration by a cast that never happened. Conversely, the post-success flourish sits
 * outside the performer's try/catch on purpose: once the ability has fired, a cosmetic failure
 * must neither undo the cast nor free the cooldown. DEBUG is the admin-only path that skips every
 * gate, the domain lock and the claim.</p>
 */
class AbilityCasterFailureTest {

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
    void aThrowingPerformerReleasesTheClaimAndPropagatesTheException() {
        PlayerMock player = server.addPlayer();
        UUID uuid = player.getUniqueId();
        AbilityId id = AbilityId.of("test:boom");
        CooldownManager cooldowns = new CooldownManager();
        AtomicBoolean claimedDuringPerform = new AtomicBoolean();

        registryWith(id, 30, (p, power) -> {
            claimedDuringPerform.set(cooldowns.isOnCooldown(uuid, id.toString()));
            throw new IllegalStateException("performer failed");
        }, p -> false);

        IllegalStateException thrown = assertThrows(IllegalStateException.class,
                () -> caster(cooldowns).cast(player, id, CastSource.COMMAND));

        assertEquals("performer failed", thrown.getMessage());
        assertTrue(claimedDuringPerform.get(), "the cooldown must be claimed before the performer runs");
        assertFalse(cooldowns.isOnCooldown(uuid, id.toString()),
                "a failed cast must release its claim instead of locking the player out");
        assertEquals(0, cooldowns.getRemainingSeconds(uuid, id.toString()));
        // The released claim is genuinely reusable: a second cast reaches the performer again.
        assertThrows(IllegalStateException.class,
                () -> caster(cooldowns).cast(player, id, CastSource.COMMAND),
                "the released cooldown must allow an immediate retry");
    }

    @Test
    void aDebugCastBypassesGatesTheDomainLockAndTheCooldown() {
        PlayerMock player = server.addPlayer();
        UUID uuid = player.getUniqueId();
        AbilityId id = AbilityId.of("test:debug");
        CooldownManager cooldowns = new CooldownManager();
        // An existing cooldown must not stop a DEBUG cast, and must not be cleared by it either.
        cooldowns.setCooldown(uuid, id.toString(), 300);

        AtomicInteger gateCalls = new AtomicInteger();
        AtomicInteger domainChecks = new AtomicInteger();
        AtomicInteger performs = new AtomicInteger();
        AbilityRegistry registry = new AbilityRegistry(message -> { });
        registry.register(new AbilityDefinition(id, Component.text("Debug"), Material.STONE,
                0, () -> 30, 1.0,
                (p, power) -> performs.incrementAndGet(),
                p -> {
                    domainChecks.incrementAndGet();
                    return true; // a live domain would block a player cast
                }, false));
        AbilityCaster caster = new AbilityCaster(registry, cooldowns);
        caster.addGate((p, definition, source) -> {
            gateCalls.incrementAndGet();
            return CastResult.NOT_OWNED; // an ordinary cast would be denied here
        });

        assertEquals(CastResult.OK, caster.cast(player, id, CastSource.DEBUG));
        assertEquals(1, performs.get(), "a DEBUG cast must still perform the ability");
        assertEquals(0, gateCalls.get(), "DEBUG must skip every extra gate");
        assertEquals(0, domainChecks.get(), "DEBUG must skip the domain-lock check");
        assertTrue(cooldowns.isOnCooldown(uuid, id.toString()),
                "DEBUG must not claim or clear a cooldown");

        // The gate really is installed and effective for a player-reachable cast.
        assertEquals(CastResult.NOT_OWNED, caster.cast(player, id, CastSource.COMMAND));
        assertEquals(1, performs.get(), "the denied normal cast must not perform");
    }

    @Test
    void aFlourishFailureDoesNotUndoTheCastOrReleaseTheCooldown() {
        UUID uuid = UUID.randomUUID();
        // "blue" is a real technique key, so TechniqueFx.burst proceeds past its fast-path and
        // reaches the player. Throwing from getLocation() forces the cosmetic flourish to fail
        // after the performer has already succeeded.
        Player player = (Player) Proxy.newProxyInstance(
                Player.class.getClassLoader(), new Class<?>[]{Player.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "getUniqueId" -> uuid;
                    case "getLocation" -> throw new RuntimeException("flourish boom");
                    case "equals" -> proxy == args[0];
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "toString" -> "flourish-player";
                    default -> null;
                });
        AbilityId id = AbilityId.of("test:blue");
        CooldownManager cooldowns = new CooldownManager();
        AtomicInteger performs = new AtomicInteger();

        registryWith(id, 45, (p, power) -> performs.incrementAndGet(), p -> false);

        assertThrows(RuntimeException.class,
                () -> caster(cooldowns).cast(player, id, CastSource.COMMAND));

        assertEquals(1, performs.get(), "the cast succeeded; a flourish failure must not undo it");
        assertTrue(cooldowns.isOnCooldown(uuid, id.toString()),
                "a successful cast keeps its claimed cooldown even when the flourish throws");
    }

    /** Installs a fresh registry whose only definition uses the supplied performer/predicate. */
    private void registryWith(AbilityId id, int cooldownSeconds,
                              java.util.function.BiConsumer<Player, Double> performer,
                              java.util.function.Predicate<Player> domainBlocked) {
        registry = new AbilityRegistry(message -> { });
        registry.register(new AbilityDefinition(id, Component.text(id.toString()), Material.STONE,
                0, () -> cooldownSeconds, 1.0, performer, domainBlocked, false));
    }

    private AbilityCaster caster(CooldownManager cooldowns) {
        return new AbilityCaster(registry, cooldowns);
    }

    private AbilityRegistry registry;
}
