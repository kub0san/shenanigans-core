package com.hizeric.shenaniganscore.origins.jujutsukaisen.domainexpansions.timecellmoonpalace;

import com.hizeric.shenaniganscore.ShenanigansCore;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.damage.DamageSource;
import org.bukkit.damage.DamageType;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Item;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Vector;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * The 24 FPS rule: this domain's sure-hit.
 *
 * <h3>Lore</h3>
 * <ol>
 *   <li>Everyone in the domain gets two lines of film injected into the neck. Nothing
 *       seems to happen.</li>
 *   <li>Anyone who moves without obeying the 24 FPS rule suffers numerous bloody
 *       injuries with every movement.</li>
 *   <li>The domain works on individual cells: freezing them tears small cuts all over
 *       the body and completely immobilises the target.</li>
 *   <li>Forcing a movement can cut an appendage off.</li>
 * </ol>
 *
 * <h3>Translation to Minecraft</h3>
 * <ul>
 *   <li><b>Torn cells meter</b> (0-100) fills with every block a victim moves (plus jumps
 *       and arm swings) and drains while they stand still.</li>
 *   <li><b>Bleed</b>: the same movement is pooled as small cuts, applied every 4 ticks
 *       as armour-ignoring magic damage credited to the caster.</li>
 *   <li><b>Freeze</b>: a full meter freezes the victim solid (hard-locked by
 *       {@link Enforcer}) and the cuts keep opening.</li>
 *   <li><b>Sever</b>: any attempt to act while frozen (swing, use item, attack) or any
 *       forced movement (being hit) removes a limb: big percentage damage and a dropped
 *       held item, rate-limited by a cooldown.</li>
 * </ul>
 *
 * <p>Movement is measured by polling positions every tick, so mobs obey the rule too
 * (frozen mobs lose their AI, always restored). All state lives in this object, which
 * lives in the {@link Session}.</p>
 */
final class Rule24 {

    /** Ignore sub-pixel jitter; sneaking is ~0.065 blocks per tick. */
    private static final double MIN_MOVE = 0.03;
    /** More than this in a single tick is a teleport, not walking. */
    private static final double TELEPORT = 6.0;
    private static final double MAX_BLEED_HIT = 3.0;
    private static final MiniMessage MM = MiniMessage.miniMessage();

    /** Per-victim state. */
    static final class Subject {
        final UUID id;
        Location last;
        double meter;
        double bleed;
        long frozenUntil;
        long graceUntil;
        long lastSever;
        boolean aiFrozen;
        /** Film has been injected (the scan line has passed them). Nothing applies before this. */
        boolean injected;
        /** The film frame boxing a frozen victim in. */
        Film.Cell cell;

        Subject(UUID id) {
            this.id = id;
        }

        boolean frozen(long now) {
            return now < frozenUntil;
        }
    }

    private final Session s;
    private final Settings cfg;
    private final Map<UUID, Subject> subjects = new HashMap<>();
    private boolean active;
    private boolean scanDone;
    private boolean internalDamage;
    private int tickCount;

    Rule24(Session s) {
        this.s = s;
        this.cfg = s.cfg;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Lifecycle / queries
    // ─────────────────────────────────────────────────────────────────────────

    void begin() {
        active = true;
        scanDone = false;
        sweep();
    }

    /**
     * The projector's scan line has reached local depth {@code lineZ}: inject every victim it
     * has passed. The line travels from the eye toward the enemy side.
     */
    void scanTo(double lineZ) {
        if (!active) return;
        for (Subject sub : subjects.values()) {
            if (sub.injected) continue;
            if (Bukkit.getEntity(sub.id) instanceof LivingEntity le && s.toLocal(le.getLocation())[1] <= lineZ) {
                inject(sub, le);
            }
        }
    }

    /** The line has crossed the whole arena: anyone still waiting is injected now. */
    void finishScan() {
        scanDone = true;
        for (Subject sub : subjects.values()) {
            if (!sub.injected && Bukkit.getEntity(sub.id) instanceof LivingEntity le) inject(sub, le);
        }
    }

    private void inject(Subject sub, LivingEntity le) {
        sub.injected = true;
        sub.last = le.getLocation();
        Film.inject(s, le);
        if (le instanceof Player p) {
            ShenanigansCore.runTaskLater( () -> {
                if (!active || !p.isOnline()) return;
                p.showTitle(Title.title(Component.empty(),
                        MM.deserialize("<gray><italic>Nothing seems to have happened..."),
                        Title.Times.times(Duration.ofMillis(200), Duration.ofMillis(1800), Duration.ofMillis(500))));
            }, 24L);
        }
    }

    /** Rule off: thaw everyone, give every mob its AI back. */
    void end() {
        active = false;
        for (Subject sub : subjects.values()) {
            release(sub, Bukkit.getEntity(sub.id));
        }
        subjects.clear();
    }

    boolean active() {
        return active;
    }

    /** True while the rule itself is damaging someone, so the listener ignores those hits. */
    boolean internal() {
        return internalDamage;
    }

    Subject subject(UUID id) {
        return subjects.get(id);
    }

    boolean isFrozen(UUID id) {
        Subject sub = subjects.get(id);
        return active && sub != null && sub.frozen(System.currentTimeMillis());
    }

    /** Live victims, for the eye's gaze. */
    List<LivingEntity> victims() {
        List<LivingEntity> out = new ArrayList<>();
        for (UUID id : subjects.keySet()) {
            if (Bukkit.getEntity(id) instanceof LivingEntity le && le.isValid()) out.add(le);
        }
        return out;
    }

    private boolean exempt(LivingEntity le) {
        if (le.equals(s.casterPlayer())) return true;
        if (le instanceof ArmorStand) return true;
        if (le.isDead() || !le.isValid()) return true;
        if (le instanceof Player p) {
            GameMode gm = p.getGameMode();
            if (gm == GameMode.CREATIVE || gm == GameMode.SPECTATOR) return true;
            return p.hasPermission(Settings.IMMUNE_PERMISSION);
        }
        return false;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Per-tick driver
    // ─────────────────────────────────────────────────────────────────────────

    void tick() {
        if (!active) return;
        tickCount++;
        long now = System.currentTimeMillis();
        if (tickCount % 10 == 1) sweep();

        Iterator<Map.Entry<UUID, Subject>> it = subjects.entrySet().iterator();
        while (it.hasNext()) {
            Subject sub = it.next().getValue();
            Entity ent = Bukkit.getEntity(sub.id);
            if (!(ent instanceof LivingEntity le)
                    || exempt(le)
                    || !le.getWorld().equals(s.world())
                    || le.getLocation().distanceSquared(s.origin()) > Math.pow(s.getRadius() + 4.0, 2)) {
                release(sub, ent);
                it.remove();
                continue;
            }
            step(sub, le, now);
        }
    }

    /** Find newcomers inside the sphere and inject them with film. */
    private void sweep() {
        World world = s.world();
        if (world == null) return;
        double r = s.getRadius();
        double r2 = r * r;
        for (Entity raw : world.getNearbyEntities(s.origin(), r, r, r,
                e -> e instanceof LivingEntity && !(e instanceof ArmorStand))) {
            LivingEntity le = (LivingEntity) raw;
            if (exempt(le)) continue;
            if (le.getLocation().distanceSquared(s.origin()) > r2) continue;
            if (subjects.containsKey(le.getUniqueId())) continue;

            Subject sub = new Subject(le.getUniqueId());
            sub.last = le.getLocation();
            subjects.put(le.getUniqueId(), sub);

            // Before the scan line is done, the line injects them as it passes; afterwards, immediately
            if (scanDone) inject(sub, le);
        }
    }

    private void step(Subject sub, LivingEntity le, long now) {
        if (!sub.injected) {
            sub.last = le.getLocation();
            return;
        }
        Location cur = le.getLocation();
        if (sub.last == null || !cur.getWorld().equals(sub.last.getWorld())) {
            sub.last = cur;
            return;
        }
        double dx = cur.getX() - sub.last.getX();
        double dy = cur.getY() - sub.last.getY();
        double dz = cur.getZ() - sub.last.getZ();
        Location prev = sub.last;
        sub.last = cur;

        double moved = Math.hypot(dx, dz) + Math.max(0.0, dy);
        if (moved > TELEPORT) return; // teleport, not movement

        // ── Frozen: held in place, cuts keep opening ──
        if (sub.frozen(now)) {
            hold(sub, le);
            sub.bleed += cfg.frozenBleed * 0.05;
            flushBleed(sub, le);
            if (le instanceof Player p && tickCount % 5 == 0) hud(p, sub, now);
            return;
        }
        if (sub.frozenUntil != 0L) {
            thaw(sub, le, now);
        }

        // ── Moving: every block is a torn frame ──
        if (moved >= MIN_MOVE) {
            if (now >= sub.graceUntil) {
                sub.meter += moved * cfg.meterPerBlock;
                sub.bleed += moved * cfg.bleedPerBlock;
                if (tickCount % 3 == 0) Film.afterimage(le.getWorld(), prev, s.frame());
            }
        } else {
            sub.meter = Math.max(0.0, sub.meter - cfg.meterDecay * 0.05);
        }

        flushBleed(sub, le);
        if (sub.meter >= 100.0) freeze(sub, le, now);
        if (le instanceof Player p && tickCount % 5 == 0) hud(p, sub, now);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Bleed / freeze / thaw
    // ─────────────────────────────────────────────────────────────────────────

    /** Pooled cuts land 5 times a second, staggered per victim. */
    private void flushBleed(Subject sub, LivingEntity le) {
        if (((tickCount + (sub.id.hashCode() & 3)) & 3) != 0) return;
        if (sub.bleed < 0.4) return;
        double amount = Math.min(sub.bleed, MAX_BLEED_HIT);
        sub.bleed -= amount;
        hurt(le, amount);
        Film.cuts(le, 3 + (int) Math.ceil(amount * 2.0));
    }

    private void freeze(Subject sub, LivingEntity le, long now) {
        sub.frozenUntil = now + (long) (cfg.freezeSeconds * 1000.0);
        sub.meter = 100.0;
        if (!(le instanceof Player) && le.hasAI()) {
            le.setAI(false);
            sub.aiFrozen = true;
        }
        le.setVelocity(new Vector(0, 0, 0));
        Film.freezeBurst(s, le);
        sub.cell = Film.cell(s, le);
        s.stage.onFreeze();
        if (le instanceof Player p) {
            p.showTitle(Title.title(MM.deserialize("<#bfe9ff><bold>FROZEN"),
                    MM.deserialize("<gray>Every cell has stopped."),
                    Title.Times.times(Duration.ofMillis(100), Duration.ofMillis(1200), Duration.ofMillis(400))));
        }
    }

    /** Keep a frozen victim pinned: no horizontal drift, no rising. */
    private void hold(Subject sub, LivingEntity le) {
        if (tickCount % 2 == 0) Film.aura(le);
        if (tickCount % 6 == 0) Film.cuts(le, 2);
        Vector v = le.getVelocity();
        if (Math.abs(v.getX()) > 0.02 || Math.abs(v.getZ()) > 0.02 || v.getY() > 0.0) {
            le.setVelocity(new Vector(0, Math.min(v.getY(), 0.0), 0));
        }
    }

    private void thaw(Subject sub, LivingEntity le, long now) {
        sub.frozenUntil = 0L;
        sub.meter = 0.0;
        sub.graceUntil = now + (long) (cfg.graceSeconds * 1000.0);
        if (sub.aiFrozen) {
            le.setAI(true);
            sub.aiFrozen = false;
        }
        if (sub.cell != null) {
            Film.shatter(s, sub.cell);
            sub.cell = null;
        }
        Film.thaw(le);
    }

    /** Leaving the rule (domain end, left the sphere, died): never leave anything locked. */
    private void release(Subject sub, Entity ent) {
        if (ent instanceof LivingEntity le && sub.aiFrozen) {
            le.setAI(true);
        }
        sub.aiFrozen = false;
        sub.frozenUntil = 0L;
        if (sub.cell != null) {
            Film.shatter(s, sub.cell);
            sub.cell = null;
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Actions: the cost of moving a limb
    // ─────────────────────────────────────────────────────────────────────────

    /** An arm swing by a free victim is a movement too. */
    void swing(Player p) {
        if (!active) return;
        Subject sub = subjects.get(p.getUniqueId());
        if (sub == null || !sub.injected || sub.frozen(System.currentTimeMillis())) return;
        if (System.currentTimeMillis() < sub.graceUntil) return;
        sub.meter += cfg.swingMeter;
        sub.bleed += cfg.swingBleed;
        Film.cuts(p, 2);
    }

    /** A frozen victim tried to move a limb (attack, swing, use item): the hand comes off. */
    void attempt(Player p) {
        if (!active) return;
        Subject sub = subjects.get(p.getUniqueId());
        long now = System.currentTimeMillis();
        if (sub == null || !sub.frozen(now)) return;
        sever(sub, p, true, now);
    }

    /** A frozen victim was forced to move (hit): a limb comes off. */
    void forced(LivingEntity victim) {
        if (!active) return;
        Subject sub = subjects.get(victim.getUniqueId());
        long now = System.currentTimeMillis();
        if (sub == null || !sub.frozen(now)) return;
        sever(sub, victim, java.util.concurrent.ThreadLocalRandom.current().nextBoolean(), now);
    }

    private void sever(Subject sub, LivingEntity le, boolean arm, long now) {
        if (now - sub.lastSever < (long) (cfg.severCooldown * 1000.0)) {
            Film.cuts(le, 3);
            return;
        }
        sub.lastSever = now;

        double dmg = Math.max(2.0, le.getMaxHealth() * cfg.severPercent / 100.0);
        hurt(le, dmg);
        Film.severBurst(le, arm);
        Film.severLimb(s, le, arm);
        s.stage.onSever();

        if (le instanceof Player p) {
            p.sendActionBar(MM.deserialize("<dark_red><bold>✂ <red>Your " + (arm ? "arm" : "leg")
                    + " is cut off. <gray>You moved."));
            if (arm && cfg.dropOnSever) dropHeld(p);
        }
    }

    private void dropHeld(Player p) {
        ItemStack held = p.getInventory().getItemInMainHand();
        if (held == null || held.getType().isAir()) return;
        p.getInventory().setItemInMainHand(null);
        Item dropped = p.getWorld().dropItem(p.getEyeLocation().add(0, -0.3, 0), held);
        dropped.setPickupDelay(60);
        dropped.setVelocity(new Vector(0, 0.1, 0));
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Damage
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Cells do not care about armour: magic damage credited to the caster, no knockback
     * (velocity saved and restored), no i-frame throttling.
     */
    private void hurt(LivingEntity le, double amount) {
        if (amount <= 0.0 || le.isDead()) return;
        Vector vel = le.getVelocity();
        int noDamage = le.getNoDamageTicks();
        internalDamage = true;
        try {
            le.setNoDamageTicks(0);
            try {
                le.damage(amount, DamageSource.builder(DamageType.MAGIC)
                        .withCausingEntity(s.casterPlayer())
                        .build());
            } catch (Throwable t) {
                le.damage(amount, s.casterPlayer());
            }
        } finally {
            internalDamage = false;
        }
        le.setVelocity(vel);
        le.setNoDamageTicks(noDamage);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // HUD
    // ─────────────────────────────────────────────────────────────────────────

    private void hud(Player p, Subject sub, long now) {
        String frame = String.format("%05d", s.frame());
        if (sub.frozen(now)) {
            double left = Math.max(0.0, (sub.frozenUntil - now) / 1000.0);
            p.sendActionBar(MM.deserialize("<#bfe9ff>❄ CELLS FROZEN <white>" + String.format("%.1f", left)
                    + "s <dark_gray>| <gray>FRAME <white>" + frame + " <dark_gray>| <dark_red>do not move"));
            return;
        }
        int filled = (int) Math.round(Math.min(100.0, sub.meter) / 10.0);
        String color = sub.meter > 66 ? "<dark_red>" : sub.meter > 33 ? "<red>" : "<white>";
        p.sendActionBar(MM.deserialize("<gray>24 FPS <dark_gray>| <gray>FRAME <white>" + frame
                + " <dark_gray>| " + color + "▮".repeat(filled) + "<dark_gray>" + "▯".repeat(10 - filled)
                + " <gray>cells torn"));
    }
}
