package com.hizeric.shenaniganscore.core.ability;

import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.entity.Player;

import java.util.Objects;
import java.util.function.BiConsumer;
import java.util.function.IntSupplier;
import java.util.function.Predicate;

/**
 * Media-agnostic description of a castable ability.
 *
 * <p>Every origin registers definitions into the {@link AbilityRegistry}; the battleground market,
 * rail binds and {@code /ability} command all read from that one catalogue. This class owns no
 * gameplay state: cooldowns and gates live in {@link AbilityCaster}. There is exactly one
 * fixed power per ability — maximum/minimum-output variants were removed to keep casting simple.</p>
 *
 * <p>Hidden abilities are castable but never sold, bound or listed.</p>
 */
public final class AbilityDefinition {

    private final AbilityId id;
    private final Component displayName;
    private final Material iconMaterial;
    private final int price;
    private final IntSupplier cooldownSeconds;
    private final double power;
    private final BiConsumer<Player, Double> performer;
    private final Predicate<Player> domainBlocked;
    private final boolean hidden;

    /**
     * @param domainBlocked predicate returning {@code true} when a live domain prevents this cast.
     *                      Supplied by the owning origin so core never reaches into domain code.
     */
    public AbilityDefinition(AbilityId id, Component displayName, Material iconMaterial, int price,
                             IntSupplier cooldownSeconds, double power,
                             BiConsumer<Player, Double> performer, Predicate<Player> domainBlocked,
                             boolean hidden) {
        this.id = Objects.requireNonNull(id, "id");
        this.displayName = Objects.requireNonNull(displayName, "displayName");
        this.iconMaterial = Objects.requireNonNull(iconMaterial, "iconMaterial");
        this.price = Math.max(0, price);
        this.cooldownSeconds = Objects.requireNonNull(cooldownSeconds, "cooldownSeconds");
        this.power = power;
        this.performer = Objects.requireNonNull(performer, "performer");
        this.domainBlocked = Objects.requireNonNull(domainBlocked, "domainBlocked");
        this.hidden = hidden;
    }

    public AbilityId id() { return id; }
    public Component displayName() { return displayName; }
    public Material iconMaterial() { return iconMaterial; }
    public int price() { return price; }
    public int cooldownSeconds() { return Math.max(0, cooldownSeconds.getAsInt()); }
    public double power() { return power; }
    public boolean isDomainBlocked(Player player) { return domainBlocked.test(player); }
    public boolean hidden() { return hidden; }
    public void perform(Player player) { performer.accept(player, power); }
}
