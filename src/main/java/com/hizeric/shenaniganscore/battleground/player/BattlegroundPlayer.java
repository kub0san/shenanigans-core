package com.hizeric.shenaniganscore.battleground.player;

import com.hizeric.shenaniganscore.battleground.ability.AbilityRail;

import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

/** Persistent per-player battleground record. All mutation happens on the main thread. */
public final class BattlegroundPlayer {

    /** Outcome of trying to buy a catalog entry. */
    public enum PurchaseResult { SUCCESS, ALREADY_OWNED, INSUFFICIENT_CREDITS, INVALID_ENTRY }

    private final UUID uuid;
    private int credits;
    private int kills;
    private int deaths;
    private int streak;
    private int bestStreak;
    private final Set<String> ownedEntries = new HashSet<>();
    private final AbilityRail rail = new AbilityRail();
    private String layoutData = "";

    public BattlegroundPlayer(UUID uuid) {
        this.uuid = uuid;
    }

    public UUID uuid() { return uuid; }
    public int credits() { return credits; }
    public int kills() { return kills; }
    public int deaths() { return deaths; }
    public int streak() { return streak; }
    /** Highest streak ever reached — display-only, never gates a cast. */
    public int bestStreak() { return bestStreak; }
    public Set<String> ownedEntries() { return Set.copyOf(ownedEntries); }
    public AbilityRail rail() { return rail; }
    public String layoutData() { return layoutData; }

    public void setCredits(int credits) { this.credits = Math.max(0, credits); }
    public void addCredits(int amount) { this.credits = Math.max(0, this.credits + amount); }
    /** Deducts {@code amount} credits. A non-positive price is a free purchase. */
    public boolean spend(int amount) {
        if (amount <= 0) return true;
        if (credits < amount) return false;
        credits -= amount;
        return true;
    }

    public void recordKill() {
        kills++;
        streak++;
        recordBestStreak(streak);
    }

    /** Load-only: restores the record; a lower value never overwrites a higher one. */
    public void recordBestStreak(int value) {
        bestStreak = Math.max(bestStreak, Math.max(0, value));
    }

    public void recordDeath() {
        deaths++;
        resetRound();
    }

    /** Round boundary: the streak is rented, ownership and credits are permanent. */
    public void resetRound() {
        streak = 0;
    }

    /** Load-only helpers; normal play uses {@link #recordKill()} / {@link #recordDeath()}. */
    public void recordKills(int value) { this.kills = Math.max(0, value); }
    public void recordDeaths(int value) { this.deaths = Math.max(0, value); }
    public void setStreak(int value) { this.streak = Math.max(0, value); }

    // --------------------------------------------------------------- catalog ownership

    private static String normalizeEntryId(String entryId) {
        return entryId == null || entryId.isBlank()
                ? null : entryId.toLowerCase(Locale.ROOT);
    }

    public boolean owns(String entryId) {
        String normalized = normalizeEntryId(entryId);
        return normalized != null && ownedEntries.contains(normalized);
    }

    /**
     * Buys a catalog entry. Ownership is permanent: it is never cleared by death or round reset.
     * The price is deducted only on the first successful purchase.
     */
    public PurchaseResult purchase(String entryId, int price) {
        String normalized = normalizeEntryId(entryId);
        if (normalized == null) return PurchaseResult.INVALID_ENTRY;
        if (ownedEntries.contains(normalized)) return PurchaseResult.ALREADY_OWNED;
        if (!spend(price)) return PurchaseResult.INSUFFICIENT_CREDITS;
        ownedEntries.add(normalized);
        return PurchaseResult.SUCCESS;
    }

    /** Rolls back a purchase whose item delivery failed, so ownership cannot outlive the charge. */
    public void revokeEntry(String entryId) {
        String normalized = normalizeEntryId(entryId);
        if (normalized != null) ownedEntries.remove(normalized);
    }
    /** Load-only: restores a purchased entry without charging credits. */
    public void ownEntry(String entryId) {
        String normalized = normalizeEntryId(entryId);
        if (normalized != null) ownedEntries.add(normalized);
    }

    public void setLayoutData(String layoutData) {
        this.layoutData = layoutData == null ? "" : layoutData;
    }
}
