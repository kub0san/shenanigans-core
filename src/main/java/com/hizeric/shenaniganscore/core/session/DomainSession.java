package com.hizeric.shenaniganscore.core.session;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Base class for every Domain Expansion session.
 * Holds common state: caster, center, radius, lifecycle, captive snapshot and
 * per-domain policy hooks for vertical handling and return.
 */
public abstract class DomainSession {

    /** Vertical relation of a captive to the domain floor plane at capture time. */
    public enum VerticalClass {
        /** Clearly above the floor plane. */
        ABOVE,
        /** Within the on-band tolerance around the floor plane. */
        ON_BAND,
        /** Below the plane with a clear vertical rise corridor. */
        BELOW_CLEAR,
        /** Below the plane but the straight rise corridor is obstructed. */
        BELOW_OBSTRUCTED
    }

    /** Immutable capture snapshot for one player. */
    public record CaptiveRecord(UUID uuid, Location captureLocation, VerticalClass verticalClass) {
        public CaptiveRecord {
            captureLocation = captureLocation == null ? null : captureLocation.clone();
        }
    }

    protected final Player caster;
    protected final Location center;
    protected final double radius;
    /** Domain floor plane Y — fixed to caster Y at capture (D6). */
    protected final double planeY;

    private final Map<UUID, CaptiveRecord> captives = new HashMap<>();
    private final Set<UUID> captiveIds = new HashSet<>();
    private boolean captured = false;
    private DomainVerticalHandler verticalHandler;

    private boolean active = false;

    protected DomainSession(Player caster, Location center, double radius) {
        this.caster = caster;
        this.center = center == null ? null : center.clone();
        this.radius = radius;
        // Floor plane is the caster's Y at domain creation time.
        if (caster != null) {
            this.planeY = caster.getLocation().getY();
        } else if (this.center != null) {
            this.planeY = this.center.getY();
        } else {
            this.planeY = 0.0;
        }
    }

    /** Marks the session as running. Called by {@link SessionManager} on start. */
    protected final void activate() {
        this.active = true;
    }

    /** Marks the session as stopped. Called after {@link #close()} finishes cleanup. */
    protected final void deactivate() {
        this.active = false;
    }

    public final Player getCaster() {
        return caster;
    }

    /** The owning session key — the caster's UUID. Used by the domain block registry. */
    public final UUID getUniqueId() {
        return caster == null ? null : caster.getUniqueId();
    }

    public final Location getCenter() {
        return center;
    }

    public final double getRadius() {
        return radius;
    }

    /** The authoritative floor plane Y for this domain. */
    public final double getPlaneY() {
        return planeY;
    }

    public final boolean isActive() {
        return active;
    }

    /**
     * Origin-agnostic hook: does this domain currently hard-lock {@code player}'s movement?
     * Domains that impose such a lock override this; the default is no lock. {@link Immobilize}
     * aggregates the answer across every active session with a fail-open guarantee.
     */
    public boolean isMovementLocked(Player player) {
        return false;
    }

    /**
     * Snapshots captives at activation time. Players inside the activation sphere
     * (same world) are recorded with their capture location and vertical class.
     * Idempotent — later calls are no-ops once captured.
     */
    public final void capture() {
        if (captured) return;
        captured = true;
        World world = center.getWorld();
        if (world == null) return;
        double r2 = radius * radius;
        for (Player player : world.getPlayers()) {
            if (!shouldCapture(player)) continue;
            Location loc = player.getLocation();
            if (loc.getWorld() != world) continue;
            if (loc.distanceSquared(center) > r2) continue;
            VerticalClass cls = classify(player, loc);
            CaptiveRecord record = new CaptiveRecord(player.getUniqueId(), loc, cls);
            captives.put(record.uuid(), record);
            captiveIds.add(record.uuid());
        }
        onCaptured();
    }

    /**
     * Hook for domains to react after the base capture snapshot is taken.
     * Called once from {@link #capture()}.
     */
    protected void onCaptured() {
        // default: no-op
    }

    /**
     * Whether this player should be considered for capture. Default excludes the
     * caster and only considers players in the same world; domains can override
     * for custom radius/policy rules.
     */
    protected boolean shouldCapture(Player player) {
        if (player == null) return false;
        // capture.include-caster was false for every domain; the caster is never a captive.
        return !player.equals(caster);
    }

    /** Classifies a player relative to the domain plane at the given location. */
    protected VerticalClass classify(Player player, Location loc) {
        if (loc == null) return VerticalClass.ON_BAND;
        double feetY = loc.getY();
        double onBand = 1.25; // tolerance around plane
        if (feetY > planeY + onBand) {
            return VerticalClass.ABOVE;
        }
        if (feetY >= planeY - onBand) {
            return VerticalClass.ON_BAND;
        }
        return isRiseCorridorClear(loc, planeY) ? VerticalClass.BELOW_CLEAR : VerticalClass.BELOW_OBSTRUCTED;
    }

    /**
     * Returns true if the vertical column above the player's feet up to the
     * target plane is passable enough for an elevator rise.
     */
    protected boolean isRiseCorridorClear(Location from, double targetY) {
        World world = from.getWorld();
        if (world == null) return true;
        int x = from.getBlockX();
        int z = from.getBlockZ();
        int startY = from.getBlockY();
        int endY = (int) Math.ceil(targetY);
        int minY = world.getMinHeight();
        int maxY = world.getMaxHeight() - 2;
        startY = Math.max(startY, minY);
        endY = Math.min(endY, maxY);
        for (int y = startY; y <= endY; y++) {
            var block = world.getBlockAt(x, y, z);
            if (!block.isPassable() && block.getType().isSolid()) {
                return false;
            }
        }
        return true;
    }

    /** All captives snapshot at activation, keyed by UUID. */
    public final Map<UUID, CaptiveRecord> getCaptives() {
        return Map.copyOf(captives);
    }

    /** Quick membership check for captured players. */
    public final boolean isCaptured(UUID uuid) {
        return uuid != null && captiveIds.contains(uuid);
    }

    /** The capture record for a player, or null if not captured. */
    public final CaptiveRecord getCaptive(UUID uuid) {
        return uuid == null ? null : captives.get(uuid);
    }

    /** Releases/removes a captive from the logical set (e.g. after return). */
    public final void releaseCaptive(UUID uuid) {
        if (uuid == null) return;
        captives.remove(uuid);
        captiveIds.remove(uuid);
    }

    /** Clears all captive records. */
    public final void releaseAllCaptives() {
        captives.clear();
        captiveIds.clear();
    }

    /** Under-level handling: the shipped value for all three domains. */
    public UnderLevelMode underLevelMode() {
        return UnderLevelMode.RISE;
    }

    /** Ticks used for animated rise/glide handling. */
    public int underlevelRiseTicks() {
        return 30;
    }

    public int underlevelGlideTicks() {
        return 24;
    }

    /** Post-domain return: the shipped mode, duration and easing. */
    public ReturnMode returnMode() {
        return ReturnMode.R2;
    }

    public int returnDurationTicks() {
        return 20;
    }

    public String returnEasing() {
        return "smoothstep";
    }

    /** Domain-owned blocks stay indestructible so a closing domain restores cleanly. */
    public boolean blocksIndestructible() {
        return true;
    }

    /** Ticks animated under-plane handling for captured players. Call from domain tick loops. */
    public final void tickVerticalHandling() {
        if (verticalHandler == null) {
            verticalHandler = new DomainVerticalHandler(this);
        }
        verticalHandler.tick();
    }

    /** Cleans up any active under-plane rise/glide state/platforms. */
    public final void cleanupVerticalHandling() {
        if (verticalHandler != null) {
            verticalHandler.cleanup();
        }
    }

    /**
     * Terminates the session. Implementations must cancel their task,
     * remove all entities they spawned and restore all altered state.
     */
    public abstract void close();

    /**
     * Shutdown-safe close, called by {@link SessionManager#stopAll()} before the
     * plugin scheduler is cancelled. Implementations with an animated outro must
     * override this to run teardown synchronously, instead of relying on a
     * scheduled finish callback that disable() is about to discard. The default
     * is a plain close.
     */
    public void closeForShutdown() {
        close();
    }

    /**
     * Ends the session and lets the domain pick its outro for the given reason.
     *
     * <p>The default ignores the reason and simply closes, which is what an
     * origin-agnostic domain wants. Domains with a barrier override this to
     * shatter the shell when the caster dies and to collapse it otherwise.</p>
     *
     * @param reason why the session is ending; never {@code null}
     */
    public void end(SessionEndReason reason) {
        close();
    }
}
