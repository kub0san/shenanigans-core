package com.hizeric.shenaniganscore.core.terrain;

import com.hizeric.shenaniganscore.ShenanigansCore;
import com.hizeric.shenaniganscore.core.domain.DomainRegistry;
import com.hizeric.shenaniganscore.core.util.Fractures;
import org.bukkit.Bukkit;
import org.bukkit.HeightMap;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Item;
import org.bukkit.entity.LivingEntity;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Level;
import com.hizeric.shenaniganscore.core.util.MathUtil;

/**
 * A <b>real</b>, flat floor for a closed domain.
 *
 * <p>Until now every domain painted its ground with fake blocks
 * ({@code sendBlockChange}). That is free for the server, but it has no
 * collision, it never hides the terrain that pokes through it, and the
 * "ground" of the domain is really just the terrain underneath with a
 * different texture. This class builds the ground instead:</p>
 *
 * <ol>
 *   <li>the terrain inside the domain's footprint is measured,</li>
 *   <li>everything that sticks up above the domain's floor level is cut away
 *       and everything below it is filled in,</li>
 *   <li>the result is a genuine, perfectly flat platform made of real server
 *       blocks — it has collision, it blocks the sky light, and everybody
 *       inside the domain stands on the same plane.</li>
 * </ol>
 *
 * <h2>Entities caught by the floor</h2>
 * <p>A flat floor cuts through whatever was standing on the uneven ground.
 * Every entity inside the footprint is therefore <b>repositioned</b>: as soon
 * as the floor reaches its column, anything standing below the new surface is
 * lifted onto it. When the domain ends the entities are put back where they
 * were found (when that spot is safe again), so nobody takes a fall they did
 * not earn.</p>
 *
 * <h2>Reverting</h2>
 * <p>Every block the floor touches is snapshotted <i>before</i> it is changed,
 * so the world can be handed back exactly as it was — real blocks included.
 * The snapshot is deliberately <b>not</b> re-read at restore time: the floor
 * owns those blocks, so re-reading would restore the floor to itself.</p>
 *
 * <p>All writes are budgeted per tick and never touch unloaded chunks, so a
 * 48-block domain cannot stall the server. {@link #close()} hands the world
 * back; {@link #recede(double)} does it gradually, from the rim inwards, which
 * is what a collapsing domain wants.</p>
 *
 * <p>Main-thread only, like every other world mutation in this plugin.</p>
 */
public final class DomainFloor implements AutoCloseable {

    /**
     * The domain-specific look of the floor's surface.
     *
     * <p>Implemented by the origin (the Void paints the Six Eyes sigil on it,
     * the Time Cell leaves it blank) so the shared core never grows any
     * media-specific knowledge.</p>
     */
    @FunctionalInterface
    public interface Pattern {
        /** The surface block for the column at the given offset from the centre. */
        BlockData blockAt(int dx, int dz);
    }

    /**
     * The order a domain wants its columns built in.
     *
     * <p>By default the platform grows outwards from its centre, like a
     * shockwave. A domain that wants a different choreography — the Shrine's
     * magma veins racing ahead of the tide, for instance — supplies its own
     * key: the lower the key, the earlier the column is built. Keys are
     * normalized to 0–1, and the build progress maps straight onto them.</p>
     */
    @FunctionalInterface
    public interface Order {
        /** The build key of the column at the given offset from the centre. */
        double key(int dx, int dz);
    }

    /** Smallest floor the class bothers to build. */
    private static final int MIN_RADIUS = 4;
    /** Hard ceiling; matches the widest closed domain the commands allow. */
    private static final int MAX_RADIUS = 200;
    /** How far below the floor a gap is filled before the platform is left hollow. */
    private static final int MAX_FILL_DEPTH = 8;
    /** Default number of real block writes per tick. */
    public static final int DEFAULT_BUDGET = 1200;
    /** Ticks between two refreshes of the entity list while the floor is growing. */
    private static final int ENTITY_SCAN_INTERVAL = 10;
    /** How far below the surface an entity may be and still be lifted onto the floor. */
    private static final double MAX_LIFT_DEPTH = 12.0;
    /** Ticks between two fracture fronts' staggered starts. */
    private static final long SHATTER_FRONT_DELAY = 2L;
    /** Total normalized shatter timeline; arrival {@code 1.0} lands here. */
    private static final int SHATTER_DURATION_TICKS =
            Fractures.FRONT_COUNT * (int) SHATTER_FRONT_DELAY;

    private final World world;
    private final int cx;
    private final int cz;
    /** Block Y of the topmost floor block. */
    private final int floorTopY;
    /** The standing height of the finished floor (the top face of {@link #floorTopY}). */
    private final int surfaceY;
    private final int radius;
    private final BlockData base;
    private final BlockData air;
    private final int budget;

    /** The owning session UUID — used to register/claim blocks in the DomainRegistry. */
    private UUID sessionId;

    private Pattern pattern;
    private Order order;

    /** Optional skirt: extends the outer rim down towards the terrain. 0 = off. */
    private int rimSkirt;
    /** Optional material for the skirt; null falls back to the base material. */
    private BlockData skirtData;

    // ── prepared layout ───────────────────────────────────────────────────
    private boolean prepared;
    private int columnCount;
    private int[] columnX;
    private int[] columnZ;
    private double[] columnDistance;
    /** The normalized build key of each column; lower is built first. */
    private double[] columnKey;
    /** The raw (un-normalized) key range the domain handed in. */
    private double rawMinKey;
    private double rawMaxKey;
    /** Per column: {@code [start, end)} into the change arrays. */
    private int[] columnChangeStart;
    private int[] changeY;
    private BlockData[] changeTarget;
    private BlockData[] changeOriginal;
    /** The change that carries each column's surface block. */
    private int[] columnSurfaceChange;
    /** True once a column has been built. */
    private boolean[] columnBuilt;
    /** Packed x/z to column index. */
    private Map<Long, Integer> columnIndex;
    /** Columns ordered from the rim inwards — the order the collapse uses. */
    private int[] restoreOrder;
    /** True once a column's blocks have been handed back to the world. */
    private boolean[] columnRestored;

    // ── runtime ───────────────────────────────────────────────────────────
    private int builtColumns;
    private int restoredColumns;
    private boolean closed;
    private boolean finished;
    /** True once the floor has been handed over to the fracture fronts. */
    private boolean shattered;
    private int entityScanCounter;
    /**
     * Number of async chunk loads dispatched to finish restoring columns that
     * lived in a chunk which unloaded mid-domain. The restore must not finish
     * (and clear the plan) while this is non-zero, or the async callback would
     * index an emptied plan — see {@link #readyToFinish(int, int, int)}.
     */
    private int pendingAsyncRestores;
    /**
     * Chunk keys whose async (re)load is currently in flight. Used to avoid
     * dispatching the same chunk more than once while a previous load is still
     * pending; each entry is removed when its load completes (or fails).
     */
    private final Set<Long> inFlightChunks = new HashSet<>();
    /** The radius the floor is asked to reach; the front follows it. */
    private double desiredProgress;
    private final Map<UUID, Lifted> lifted = new LinkedHashMap<>();

    /**
     * When true, end-of-domain entity restoration is owned externally (e.g. by a
     * domain return controller) to avoid double-teleports. Default false.
     */
    private boolean entityRestoreManaged;

    private record Lifted(Entity entity, Location original) {}

    // ── shared floor configuration ────────────────────────────────────────

    /**
     * Clamps a per-tick block budget into the range a {@link DomainFloor} can honour.
     * Centralised so every domain's {@code Floor} agrees on the bounds instead of
     * repeating {@code max(128, min(6000, …))} and drifting apart.
     */
    public static int clampBudget(int requested) {
        return Math.max(128, Math.min(6000, requested));
    }

    /**
     * Resolves a closed domain's floor radius: capped at {@code maxRadius} and never
     * larger than the domain's own radius, so the platform stays inside the shell.
     * The {@code [MIN_RADIUS, 64]} normalisation preserves the historical configured
     * radius computation shared by Unlimited Void and Time Cell Moon Palace.
     */
    public static int resolveFloorRadius(double domainRadius, int maxRadius) {
        int configured = Math.max(MIN_RADIUS, Math.min(64, maxRadius));
        return Math.min(configured, (int) Math.ceil(domainRadius));
    }

    /**
     * Creates a floor for a domain.
     *
     * @param center      the domain's centre; the floor's level is taken from its height
     * @param radius      the footprint's radius in blocks
     * @param baseMaterial the material of the platform (the surface may be painted by a pattern)
     */
    public DomainFloor(Location center, double radius, Material baseMaterial) {
        this(center, radius, baseMaterial, (int) Math.ceil(radius), DEFAULT_BUDGET);
    }

    /**
     * Creates a floor for a domain.
     *
     * @param center       the domain's centre; the floor's level is taken from its height
     * @param radius       the domain's radius
     * @param baseMaterial the material of the platform
     * @param maxRadius    the footprint's own cap (the floor never grows past it)
     * @param budget       real block writes allowed per tick
     */
    public DomainFloor(Location center, double radius, Material baseMaterial,
                       int maxRadius, int budget) {
        if (center == null || center.getWorld() == null) {
            throw new IllegalArgumentException("DomainFloor needs a centre with a world");
        }
        if (baseMaterial == null || !baseMaterial.isBlock() || baseMaterial.isAir()) {
            throw new IllegalArgumentException("baseMaterial must be a non-air block material");
        }
        if (!Double.isFinite(radius) || radius < 1.0) {
            throw new IllegalArgumentException("radius must be a finite number of at least one block");
        }

        this.world = center.getWorld();
        this.cx = center.getBlockX();
        this.cz = center.getBlockZ();
        this.base = baseMaterial.createBlockData();
        this.air = Material.AIR.createBlockData();
        this.budget = Math.max(64, budget);

        int cap = (int) Math.floor(Math.min(maxRadius, MAX_RADIUS));
        this.radius = Math.max(MIN_RADIUS, Math.min(cap, (int) Math.ceil(radius)));

        // The floor sits one block below the caster's feet, so the caster ends
        // up standing exactly on it.
        int minY = world.getMinHeight();
        int maxY = world.getMaxHeight() - 1;
        this.floorTopY = Math.max(minY, Math.min(maxY - 1, center.getBlockY() - 1));
        this.surfaceY = floorTopY + 1;
    }

    /** Installs the domain-specific surface pattern. Call before {@link #prepare()}. */
    public void setPattern(Pattern pattern) {
        this.pattern = pattern;
    }

    /**
     * Extends the platform's outer rim down towards the terrain below.
     *
     * <p>On sloped ground the finished platform would otherwise read as a
     * hovering disk from outside the domain — the flat slab never reaches the
     * ground it hovers over. A skirt carries the rim's columns downwards
     * towards the measured terrain height (capped at {@code depthBlocks}) so
     * the disk reads as carved ground or a terrace instead. It uses the base
     * material unless {@link #setRimSkirt(int, Material)} provides an override,
     * and the blocks are snapshotted/restored like every other floor block.</p>
     *
     * @param depthBlocks how far below the floor the rim may reach; 0 disables it
     */
    public void setRimSkirt(int depthBlocks) {
        setRimSkirt(depthBlocks, null);
    }

    /** Same as {@link #setRimSkirt(int)} with a skirt material override. */
    public void setRimSkirt(int depthBlocks, Material skirtMaterial) {
        this.rimSkirt = Math.max(0, Math.min(32, depthBlocks));
        this.skirtData = (skirtMaterial != null && skirtMaterial.isBlock() && !skirtMaterial.isAir())
                ? skirtMaterial.createBlockData() : null;
    }

    /**
     * Installs a custom build order. Call before {@link #prepare()}.
     *
     * <p>Without one, the platform grows outwards from its centre, like a
     * shockwave.</p>
     */
    public void setOrder(Order order) {
        this.order = order;
    }

    // ------------------------------------------------------------------
    // Accessors
    // ------------------------------------------------------------------

    /** Block Y of the topmost floor block. */
    public int floorTopY() {
        return floorTopY;
    }

    /** The standing height of the finished floor. */
    public int surfaceY() {
        return surfaceY;
    }

    /** The footprint's radius in blocks. */
    public int radius() {
        return radius;
    }

    /** True when the given block column is part of the platform. */
    public boolean contains(int x, int z) {
        if (columnIndex == null) return false;
        return columnIndex.containsKey(columnKey(x, z));
    }

    /** The lowest build key the domain handed in. */
    public double minKey() {
        return rawMinKey;
    }

    /** The highest build key the domain handed in. */
    public double maxKey() {
        return rawMaxKey;
    }

    /** How far the floor has actually been built, in blocks from the centre. */
    public double builtRadius() {
        if (builtColumns <= 0) return 0.0;
        return columnDistance[builtColumns - 1];
    }

    /** True once the whole footprint has been built. */
    public boolean complete() {
        return prepared && builtColumns >= columnCount;
    }

    /** True once the world has been handed back. */
    public boolean isClosed() {
        return closed;
    }

    // ------------------------------------------------------------------
    // Preparation
    // ------------------------------------------------------------------

    /**
     * Measures the terrain and plans every block change.
     *
     * <p>Only loaded chunks are measured — the floor never sync-loads a chunk.
     * The plan itself is cheap: the terrain's height comes from the chunk
     * heightmap, and the block states that have to be remembered are read
     * later, one column at a time, just before that column is changed. A
     * 48-block domain therefore costs a few milliseconds here instead of a
     * visible stall.</p>
     */
    public void prepare() {
        requireMainThread();
        if (prepared || closed) return;

        int minY = world.getMinHeight();
        int maxY = world.getMaxHeight() - 1;

        List<int[]> columns = new ArrayList<>();
        List<Double> distances = new ArrayList<>();
        List<Double> keys = new ArrayList<>();
        List<Integer> changeYs = new ArrayList<>();
        List<BlockData> changeTargets = new ArrayList<>();
        List<Integer> changeStarts = new ArrayList<>();
        // Per column: the index (into changeYs) of the surface block itself. The
        // rim skirt is appended *after* the surface, so the surface is not simply
        // the last change of the column once a skirt is present.
        List<Integer> changeSurfaces = new ArrayList<>();

        double maxDistance = 0.0;

        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                int d2 = dx * dx + dz * dz;
                if (d2 > radius * radius) continue;

                int x = cx + dx;
                int z = cz + dz;
                if (!world.isChunkLoaded(x >> 4, z >> 4)) continue;

                int top = world.getHighestBlockYAt(x, z, HeightMap.WORLD_SURFACE);
                if (top > maxY) top = maxY;
                if (top < minY - 1) top = minY - 1;

                final double distance = Math.sqrt(d2);
                changeStarts.add(changeYs.size());

                // 1. Cut away everything that stands above the floor.
                if (top > floorTopY) {
                    for (int y = floorTopY + 1; y <= top; y++) {
                        changeYs.add(y);
                        changeTargets.add(air);
                    }
                }

                // 2. Fill the hollows so the platform is solid where it matters.
                int fillBottom = Math.max(top + 1, floorTopY - MAX_FILL_DEPTH);
                for (int y = fillBottom; y <= floorTopY - 1; y++) {
                    changeYs.add(y);
                    changeTargets.add(base);
                }

                // 3. The surface block itself — this is where the pattern lives.
                BlockData target = pattern != null ? pattern.blockAt(dx, dz) : base;
                if (target == null) target = base;
                changeSurfaces.add(changeYs.size());
                changeYs.add(floorTopY);
                changeTargets.add(target);

                // 4. Rim skirt — carries the edge towards the terrain so the
                //    platform never reads as a floating plate from outside.
                if (rimSkirt > 0 && distance > radius - 1.5) {
                    BlockData skirt = skirtData != null ? skirtData : base;
                    int bottom = Math.max(top + 1, floorTopY - rimSkirt);
                    for (int y = floorTopY - 1; y >= bottom; y--) {
                        changeYs.add(y);
                        changeTargets.add(skirt);
                    }
                }

                columns.add(new int[]{x, z});
                distances.add(distance);
                if (distance > maxDistance) maxDistance = distance;
                keys.add(order != null ? order.key(dx, dz) : distance);
            }
        }

        columnCount = columns.size();
        columnX = new int[columnCount];
        columnZ = new int[columnCount];
        columnDistance = new double[columnCount];
        for (int i = 0; i < columnCount; i++) {
            columnX[i] = columns.get(i)[0];
            columnZ[i] = columns.get(i)[1];
            columnDistance[i] = distances.get(i);
        }

        // The keys are normalized to 0–1 so the build progress maps straight
        // onto them, whatever order the domain asked for.
        double minKey = Double.MAX_VALUE;
        double maxKey = -Double.MAX_VALUE;
        for (double key : keys) {
            if (key < minKey) minKey = key;
            if (key > maxKey) maxKey = key;
        }
        double keySpan = Math.max(1.0e-6, maxKey - minKey);
        for (int i = 0; i < columnCount; i++) {
            keys.set(i, (keys.get(i) - minKey) / keySpan);
        }
        this.rawMinKey = minKey;
        this.rawMaxKey = maxKey;

        // Order the columns: the lowest key is built first.
        Integer[] order = new Integer[columnCount];
        for (int i = 0; i < columnCount; i++) order[i] = i;
        Arrays.sort(order, Comparator.comparingDouble(i -> keys.get(i)));

        int[] sortedX = new int[columnCount];
        int[] sortedZ = new int[columnCount];
        double[] sortedDistance = new double[columnCount];
        double[] sortedKey = new double[columnCount];
        int[] sortedStart = new int[columnCount + 1];
        int[] sortedSurface = new int[columnCount];
        int[] sortedY = new int[changeYs.size()];
        BlockData[] sortedTarget = new BlockData[changeYs.size()];

        // The change lists are per column and contiguous, so the sorted layout
        // only has to remap the ranges, not copy every block.
        int[] columnBegin = new int[columnCount];
        int[] columnEnd = new int[columnCount];
        int[] columnSurface = new int[columnCount];
        for (int i = 0; i < columnCount; i++) {
            columnBegin[i] = changeStarts.get(i);
            columnEnd[i] = i + 1 < columnCount ? changeStarts.get(i + 1) : changeYs.size();
            columnSurface[i] = changeSurfaces.get(i);
        }

        // Rebuild the change arrays in the sorted column order.
        int write = 0;
        for (int slot = 0; slot < columnCount; slot++) {
            int source = order[slot];
            int begin = columnBegin[source];
            int end = columnEnd[source];
            sortedStart[slot] = write;
            for (int i = begin; i < end; i++) {
                sortedY[write] = changeYs.get(i);
                sortedTarget[write] = changeTargets.get(i);
                write++;
            }
            // The surface block is recorded per column; the rim skirt is
            // appended after it, so it is not simply the last change once a
            // skirt is present.
            sortedSurface[slot] = sortedStart[slot] + (columnSurface[source] - begin);
            sortedX[slot] = columnX[source];
            sortedZ[slot] = columnZ[source];
            sortedDistance[slot] = columnDistance[source];
            sortedKey[slot] = keys.get(source);
        }
        sortedStart[columnCount] = write;

        this.columnX = sortedX;
        this.columnZ = sortedZ;
        this.columnDistance = sortedDistance;
        this.columnKey = sortedKey;
        this.columnChangeStart = sortedStart;
        this.columnSurfaceChange = sortedSurface;
        this.changeY = sortedY;
        this.changeTarget = sortedTarget;
        this.changeOriginal = new BlockData[changeY.length];
        this.columnBuilt = new boolean[columnCount];
        this.columnRestored = new boolean[columnCount];

        // A direct lookup, so a domain can edit the surface of a single
        // column (a cut carved into the ground, for instance).
        this.columnIndex = new HashMap<>(columnCount * 2);
        for (int i = 0; i < columnCount; i++) {
            columnIndex.put(columnKey(columnX[i], columnZ[i]), i);
        }

        // The collapse hands the world back from the rim inwards.
        Integer[] restore = new Integer[columnCount];
        for (int i = 0; i < columnCount; i++) restore[i] = i;
        Arrays.sort(restore, Comparator.comparingDouble((Integer i) -> -columnDistance[i]));
        this.restoreOrder = new int[columnCount];
        for (int i = 0; i < columnCount; i++) this.restoreOrder[i] = restore[i];

        prepared = true;
    }

    // ------------------------------------------------------------------
    // Building
    // ------------------------------------------------------------------

    /**
     * Asks the floor to grow towards the given progress.
     *
     * <p>The request is only a wish: the actual front follows the tick budget,
     * so the floor is always perfectly flat up to the radius that has really
     * been built. A lag spike delays the front, it never deforms the ground.</p>
     *
     * @param progress 0–1; 1 asks for the whole footprint
     */
    public void build(double progress) {
        if (!prepared || closed) return;
        this.desiredProgress = MathUtil.clamp(progress, 0.0, 1.0);
    }

    /** The fraction of the platform the domain has asked for. */
    public double desiredProgress() {
        return desiredProgress;
    }

    /**
     * Identifies the session that owns this floor so its blocks can be claimed
     * in the {@link DomainRegistry}. Call before {@link #prepare()} or before
     * the first {@link #build(double)}.
     */
    public void setSessionId(UUID sessionId) {
        this.sessionId = sessionId;
    }

    /**
     * Delegates end-of-domain entity restoration to an external controller.
     * When enabled, {@link #restoreEntities()} will not teleport lifted entities
     * back (preventing double-teleport with the session return flow).
     */
    public void setEntityRestoreManaged(boolean entityRestoreManaged) {
        this.entityRestoreManaged = entityRestoreManaged;
    }

    public boolean isEntityRestoreManaged() {
        return entityRestoreManaged;
    }

    private UUID getSessionId() {
        return sessionId;
    }

    /**
     * Applies pending work: builds the next slice of the floor and hands back
     * the rim of the world when the domain is collapsing.
     *
     * <p>Call once per tick.</p>
     */
    public void tick() {
        requireMainThread();
        if (!prepared || closed) return;

        int writes = budget;
        writes = buildSlice(writes);
        writes = restoreSlice(writes);

        if (entityScanCounter++ % ENTITY_SCAN_INTERVAL == 0) {
            scanEntities();
        }
        liftEntities();
    }

    private int buildSlice(int budgetLeft) {
        while (builtColumns < columnCount) {
            // The front never runs ahead of the animation: the domain asks for
            // a fraction and the floor grows exactly that far (or less, when
            // the tick budget is the limiting factor).
            if (columnKey[builtColumns] > desiredProgress) break;

            int column = builtColumns;
            int start = columnChangeStart[column];
            int end = columnChangeStart[column + 1];
            int cost = end - start;

            // A column is applied in one go: a half-built column would show a
            // step in the floor, which is exactly what this class must not do.
            if (cost > budgetLeft && budgetLeft < budget) return budgetLeft;

            for (int i = start; i < end; i++) {
                applyChange(column, i);
            }
            columnBuilt[column] = true;
            budgetLeft -= cost;
            builtColumns++;
            if (builtColumns >= columnCount) break;
            if (budgetLeft <= 0) break;
        }
        return budgetLeft;
    }

    private int restoreSlice(int budgetLeft) {
        if (restoreOrder == null) return budgetLeft;
        while (restoredColumns < restoreOrder.length) {
            int column = restoreOrder[restoredColumns];
            if (!columnRestored[column] && columnDistance[column] <= collapseFront) break;

            int start = columnChangeStart[column];
            int end = columnChangeStart[column + 1];
            int cost = end - start;
            if (cost > budgetLeft && budgetLeft < budget) return budgetLeft;

            restoreColumn(column);
            budgetLeft -= cost;
            restoredColumns++;
            if (budgetLeft <= 0) break;
        }
        return budgetLeft;
    }

    private void applyChange(int column, int change) {
        int x = columnX[column];
        int z = columnZ[column];
        int y = changeY[change];
        if (!world.isChunkLoaded(x >> 4, z >> 4)) return;
        try {
            Block block = world.getBlockAt(x, y, z);
            // Remember what was there before we touched it. This is the only
            // snapshot the floor keeps: the world it hands back is the world it
            // found, not the world it made.
            BlockData original = block.getBlockData();
            if (changeOriginal[change] == null) {
                changeOriginal[change] = original;
            }
            BlockData target = changeTarget[change];
            if (original.getMaterial() == target.getMaterial()) {
                // Already what it should be — nothing to write, nothing to undo.
                // Still claim it in case it wasn't claimed before (first pass).
                DomainRegistry.get().claim(getSessionId(), block);
                return;
            }
            DomainRegistry.get().claim(getSessionId(), block);
            block.setBlockData(target, false);
        } catch (Throwable ignored) {
            // A chunk that unloaded mid-write is not worth crashing a domain over.
        }
    }

    private boolean restoreChange(int column, int change) {
        int x = columnX[column];
        int z = columnZ[column];
        int y = changeY[change];
        BlockData original = changeOriginal[change];
        if (original == null) {
            // The block was never written — nothing to release.
            return true;
        }
        if (!world.isChunkLoaded(x >> 4, z >> 4)) return false;
        try {
            world.getBlockAt(x, y, z).setBlockData(original, false);
            DomainRegistry.get().unclaim(getSessionId(), world.getBlockAt(x, y, z));
            return true;
        } catch (Throwable ignored) {
            return false;
        }
    }

    // ------------------------------------------------------------------
    // Collapse
    // ------------------------------------------------------------------

    /** How far out the collapse has eaten the floor, in blocks from the centre. */
    private double collapseFront = Double.MAX_VALUE;

    /**
     * Hands the world back from the rim inwards.
     *
     * @param progress 0 = the floor is still whole, 1 = it is gone
     */
    public void recede(double progress) {
        if (!prepared || closed) return;
        double p = MathUtil.clamp(progress, 0.0, 1.0);
        collapseFront = (1.0 - p) * radius;
    }

    // ------------------------------------------------------------------
    // Shatter
    // ------------------------------------------------------------------

    /**
     * Breaks the floor apart instead of handing it back neatly.
     *
     * <p>Used when the caster dies inside the domain: the platform shatters
     * along twelve planar fronts that share the shell's shard IDs and stagger.
     * Each front starts at its perimeter anchor (staggered across the first
     * {@code DELAY_SPREAD} of the timeline) and travels inward, so all twelve
     * meet at the centre at the end.</p>
     *
     * @return true when a fracture restore was started
     */
    public boolean shatter() {
        requireMainThread();
        if (!prepared || closed || columnCount == 0) return false;

        ShenanigansCore plugin = ShenanigansCore.getInstance();
        if (plugin == null || !plugin.isEnabled()) {
            restoreNow();
            return false;
        }

        // Order the columns by the front that will claim them.
        double[] arrival = new double[columnCount];
        for (int i = 0; i < columnCount; i++) {
            arrival[i] = fractureProgressOf(i);
        }
        Integer[] order = new Integer[columnCount];
        for (int i = 0; i < columnCount; i++) order[i] = i;
        Arrays.sort(order, Comparator.comparingDouble(index -> arrival[index]));

        // The restore order becomes the fracture order, so a shutdown in the
        // middle of a shatter still hands the whole world back.
        for (int i = 0; i < columnCount; i++) {
            restoreOrder[i] = order[i];
        }
        shattered = true;

        // Bucket every column by its true normalized arrival instead of cutting
        // the sorted list into twelve equal slices. The floor layout's stagger
        // lives in the arrival value, so this keeps each shard's front starting
        // at its own delay while every front still meets at the centre at 1.0.
        List<List<Integer>> buckets = new ArrayList<>(SHATTER_DURATION_TICKS + 1);
        for (int tick = 0; tick <= SHATTER_DURATION_TICKS; tick++) {
            buckets.add(new ArrayList<>());
        }
        for (int i = 0; i < columnCount; i++) {
            int column = order[i];
            int tick = (int) Math.round(arrival[column] * SHATTER_DURATION_TICKS);
            tick = Math.max(0, Math.min(SHATTER_DURATION_TICKS, tick));
            buckets.get(tick).add(column);
        }

        for (int tick = 0; tick <= SHATTER_DURATION_TICKS; tick++) {
            List<Integer> slice = buckets.get(tick);
            if (slice.isEmpty()) continue;

            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                for (int column : slice) {
                    restoreColumn(column);
                }
            }, tick);
        }
        return true;
    }

    /** How far a column is from its fracture front, in 0–1. */
    private double fractureProgressOf(int column) {
        double px = (columnX[column] - (double) cx) / radius;
        double pz = (columnZ[column] - (double) cz) / radius;
        // The floor has its own companion layout, so use its planar fronts
        // rather than projecting the spherical geometry down onto the disk.
        return Fractures.floorArrivalFraction(px, pz);
    }

    /** Restores one column's blocks immediately. */
    private boolean restoreColumn(int column) {
        // The plan may already be cleared (a late async callback firing after a
        // shutdown restore). Nothing to hand back in that case — treat it as done
        // rather than indexing an emptied array.
        if (column < 0 || column >= columnRestored.length) return true;
        if (columnRestored[column]) return true;
        int start = columnChangeStart[column];
        int end = columnChangeStart[column + 1];
        boolean complete = true;
        for (int i = start; i < end; i++) {
            if (!restoreChange(column, i)) complete = false;
        }
        // A column whose chunk was unloaded is not "restored" yet — it has to
        // stay on the list, or the floor would be left behind in the world.
        if (complete) columnRestored[column] = true;
        return complete;
    }

    // ------------------------------------------------------------------
    // Entities
    // ------------------------------------------------------------------

    private void scanEntities() {
        if (finished || builtColumns >= columnCount) return;
        for (Entity entity : world.getEntities()) {
            if (entity == null || !entity.isValid()) continue;
            if (!(entity instanceof LivingEntity) && !(entity instanceof Item)) continue;
            if (lifted.containsKey(entity.getUniqueId())) continue;

            Location location = entity.getLocation();
            if (location.getWorld() == null || !location.getWorld().equals(world)) continue;
            int dx = location.getBlockX() - cx;
            int dz = location.getBlockZ() - cz;
            if (dx * dx + dz * dz > radius * radius) continue;

            lifted.put(entity.getUniqueId(), new Lifted(entity, location.clone()));
        }
    }

    /** Lifts everything that the new floor swallowed onto its surface. */
    private void liftEntities() {
        if (lifted.isEmpty()) return;
        for (Lifted entry : lifted.values()) {
            Entity entity = entry.entity();
            if (entity == null || !entity.isValid()) continue;

            Location location = entity.getLocation();
            if (location.getWorld() == null || !location.getWorld().equals(world)) continue;
            if (location.getY() >= surfaceY) continue;
            if (location.getY() < surfaceY - MAX_LIFT_DEPTH) continue;
            if (!columnBuilt(location.getBlockX(), location.getBlockZ())) continue;

            Location target = new Location(world, location.getX(), surfaceY, location.getZ());
            try {
                entity.teleport(target);
            } catch (Throwable ignored) {
                // Never let a stuck entity break the floor.
            }
        }
    }

    /** True once the column under this position has been built. */
    private boolean columnBuilt(int x, int z) {
        if (columnIndex == null) return false;
        Integer column = columnIndex.get(columnKey(x, z));
        return column != null && columnBuilt[column];
    }

    /**
     * Replaces the surface block of a single column.
     *
     * <p>This is how a domain cuts into its own ground: the Shrine's
     * Dismantle carves white-hot gouges into the platform, and the platform
     * hands the block back to the world when it is restored.</p>
     *
     * @return true when the column exists, has been built and was changed
     */
    public boolean setSurface(int x, int z, BlockData material) {
        requireMainThread();
        if (!prepared || closed || material == null) return false;
        if (columnIndex == null) return false;

        Integer column = columnIndex.get(columnKey(x, z));
        if (column == null) return false;
        if (!columnBuilt[column]) return false;
        if (!world.isChunkLoaded(x >> 4, z >> 4)) return false;

        try {
            Block block = world.getBlockAt(x, floorTopY, z);
            if (block.getType() == material.getMaterial()) return false;
            block.setBlockData(material, false);
            return true;
        } catch (Throwable ignored) {
            return false;
        }
    }

    /** Puts every lifted entity back on the real ground it was found on. */
    private void restoreEntities() {
        if (entityRestoreManaged) {
            // End-position ownership is handled externally (domain return controller).
            lifted.clear();
            return;
        }
        for (Lifted entry : lifted.values()) {
            Entity entity = entry.entity();
            if (entity == null || !entity.isValid()) continue;
            Location original = entry.original();
            if (original == null || original.getWorld() == null) continue;
            if (!original.getWorld().equals(world)) continue;

            // The spot the entity was found on may have been cut away, so it is
            // put back on whatever the real ground is there now.
            double y = groundUnder(original);

            try {
                entity.teleport(new Location(world, original.getX(), y, original.getZ()));
                entity.setFallDistance(0.0f);
            } catch (Throwable ignored) {
                // The entity stays where it is; it will fall onto the real ground.
            }
        }
        lifted.clear();
    }

    /**
     * The real ground under a position.
     *
     * <p>The height map is not used here on purpose: the platform's own writes
     * go in without physics, and a chunk's height map does not always follow
     * them. A short scan down from where the entity was found always does.</p>
     */
    private double groundUnder(Location original) {
        int bx = original.getBlockX();
        int bz = original.getBlockZ();
        if (!world.isChunkLoaded(bx >> 4, bz >> 4)) {
            return original.getY();
        }

        int minY = world.getMinHeight();
        int start = Math.min(world.getMaxHeight() - 1, (int) Math.ceil(original.getY()));
        for (int y = start; y > start - 10 && y > minY; y--) {
            Block block = world.getBlockAt(bx, y, bz);
            Material material = block.getType();
            if (material.isSolid() && material.isOccluding()) {
                return y + 1.0;
            }
        }
        return original.getY();
    }

    // ------------------------------------------------------------------
    // Cleanup
    // ------------------------------------------------------------------

    /**
     * Hands the whole world back, ignoring the tick budget.
     *
     * <p>Used when the server is going down and there is no next tick to
     * spread the work over.</p>
     */
    public void restoreNow() {
        requireMainThread();
        closed = true;
        if (finished) return;

        if (prepared && restoreOrder != null) {
            for (int column = 0; column < columnCount; column++) {
                restoreColumn(column);
            }
        }
        finished = true;
        restoreEntities();
        clearPlan();
    }

    /**
     * Hands the world back over the coming ticks.
     *
     * <p>The domain's collapse usually calls {@link #recede(double)} first, so
     * only the last slice of the floor is left for this.</p>
     */
    @Override
    public void close() {
        requireMainThread();

        ShenanigansCore plugin = ShenanigansCore.getInstance();
        if (!prepared || plugin == null || !plugin.isEnabled()) {
            // The server is going down: there is no next tick to spread the
            // work over, so the world is handed back right now.
            restoreNow();
            return;
        }
        if (closed) return;

        closed = true;

        if (shattered) {
            // The fracture fronts own the restore — they are the show. A
            // watchdog makes sure the world is handed back even if the domain
            // died in the middle of the shatter.
            scheduleShatterWatchdog(plugin);
            return;
        }

        beginFinalRestore(plugin);
    }

    /**
     * Hands the whole world back over the coming ticks, then finishes.
     *
     * <p>Each tick restores the columns whose chunks are loaded (budgeted), asks
     * for the chunks that are not, and — crucially — only reports the floor as
     * finished once <strong>every</strong> column is back <em>and</em> no async
     * chunk load is still in flight. Finishing early would clear the plan out
     * from under a pending async restore, stranding that chunk's blocks.</p>
     */
    private void beginFinalRestore(ShenanigansCore plugin) {
        // From here on every remaining column is handed back; the collapse front
        // no longer gates the restore (that is the animation's job, not cleanup).
        collapseFront = -1.0;
        try {
            new BukkitRunnable() {
                @Override
                public void run() {
                    if (finished) {
                        cancel();
                        return;
                    }
                    restoreLoadedSlice(budget);
                    dispatchUnloadedChunks(plugin);
                    if (readyToFinish(restoredColumnCount(), columnCount, pendingAsyncRestores)) {
                        finishRestore();
                        cancel();
                    }
                }
            }.runTaskTimer(plugin, 1L, 1L);
        } catch (RuntimeException exception) {
            plugin.getLogger().log(Level.WARNING,
                    "Could not schedule the domain floor restore; restoring it now.", exception);
            restoreNow();
        }
    }

    /**
     * Restores the next slice of columns whose chunks are currently loaded.
     * Columns in unloaded chunks are left for {@link #dispatchUnloadedChunks}.
     */
    private void restoreLoadedSlice(int budgetLeft) {
        for (int i = 0; i < columnCount && budgetLeft > 0; i++) {
            int column = restoreOrder[i];
            if (columnRestored[column]) continue;
            int x = columnX[column];
            int z = columnZ[column];
            if (!world.isChunkLoaded(x >> 4, z >> 4)) continue;
            int start = columnChangeStart[column];
            int end = columnChangeStart[column + 1];
            int cost = end - start;
            // A half-column would leave a step, so only skip on the budget when
            // something else already went this tick; a lone oversized column is
            // always applied so the restore is guaranteed to make progress.
            if (cost > budgetLeft && budgetLeft < budget) continue;
            restoreColumn(column);
            budgetLeft -= cost;
        }
    }

    /**
     * Asynchronously (re)loads every chunk that still holds an unrestored
     * column and restores those columns once the chunk arrives. Safe to call
     * repeatedly: a column that is already restored is skipped, and each async
     * load decrements {@link #pendingAsyncRestores} when it lands.
     */
    private void dispatchUnloadedChunks(ShenanigansCore plugin) {
        if (!prepared || columnCount == 0) return;

        Map<Long, List<Integer>> byChunk = new HashMap<>();
        for (int column = 0; column < columnCount; column++) {
            if (columnRestored[column]) continue;
            int x = columnX[column] >> 4;
            int z = columnZ[column] >> 4;
            byChunk.computeIfAbsent(chunkKey(x, z), ignored -> new ArrayList<>()).add(column);
        }
        if (byChunk.isEmpty()) return;

        for (Map.Entry<Long, List<Integer>> entry : byChunk.entrySet()) {
            long key = entry.getKey();
            int chunkX = (int) (key >> 32);
            int chunkZ = (int) key;
            List<Integer> columns = entry.getValue();

            if (world.isChunkLoaded(chunkX, chunkZ)) {
                // It loaded since the last pass — restore it inline.
                restoreColumns(columns);
                continue;
            }
            // Skip chunks whose (re)load is already in flight, so a slow restore
            // does not queue the same chunk (and the same pending counter) twice.
            if (inFlightChunks.contains(key)) continue;
            inFlightChunks.add(key);
            pendingAsyncRestores++;
            try {
                world.getChunkAtAsync(chunkX, chunkZ).thenAccept(chunk ->
                        Bukkit.getScheduler().runTask(plugin, () -> {
                            try {
                                // Shutdown/reload empties the plan via clearPlan(); never
                                // index the zeroed column arrays from a late callback (F-54).
                                if (!finished && !closed && prepared && restoreOrder != null) {
                                    restoreColumns(columns);
                                }
                            } finally {
                                pendingAsyncRestores = Math.max(0, pendingAsyncRestores - 1);
                                inFlightChunks.remove(key);
                            }
                        }));
            } catch (RuntimeException exception) {
                pendingAsyncRestores--;
                inFlightChunks.remove(key);
                plugin.getLogger().warning("Could not reload a chunk to restore the domain floor: "
                        + exception.getMessage());
            }
        }
    }

    private void restoreColumns(List<Integer> columns) {
        for (int column : columns) {
            restoreColumn(column);
        }
    }

    /** Finishes a shattered floor once its last fracture front has passed. */
    private void scheduleShatterWatchdog(ShenanigansCore plugin) {
        long delay = SHATTER_DURATION_TICKS + 20L;
        try {
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (finished) return;
                // The fracture fronts restored the columns in loaded chunks;
                // mop up anything left in unloaded chunks and then finish, but
                // only once no async load is still in flight.
                beginFinalRestore(plugin);
            }, delay);
        } catch (RuntimeException exception) {
            plugin.getLogger().log(Level.WARNING,
                    "Could not schedule the shattered floor watchdog; restoring it now.", exception);
            restoreNow();
        }
    }

    private void finishRestore() {
        if (finished) return;
        finished = true;
        restoreEntities();
        clearPlan();
    }

    /** How many columns have had their blocks handed back. */
    private int restoredColumnCount() {
        int count = 0;
        for (int column = 0; column < columnCount; column++) {
            if (columnRestored[column]) count++;
        }
        return count;
    }

    /**
     * The restore-completion invariant.
     *
     * <p>The end-of-domain restore may finish (and clear the plan) only when
     * every column is handed back and no async chunk load is still pending.
     * Finishing while {@code pendingAsyncLoads > 0} is the historical bug: the
     * async callback then indexes the emptied plan arrays and the blocks in
     * those chunks are stranded in the world forever.</p>
     */
    static boolean readyToFinish(int restoredColumns, int totalColumns, int pendingAsyncLoads) {
        return pendingAsyncLoads == 0 && restoredColumns >= totalColumns;
    }

    private static long chunkKey(int x, int z) {
        return ((long) x << 32) | (z & 0xFFFF_FFFFL);
    }

    /** Packs a column position for the lookup tables. */
    private static long columnKey(int x, int z) {
        return ((long) x << 32) | (z & 0xFFFF_FFFFL);
    }

    private void clearPlan() {
        // Release any remaining claims for this session.
        if (sessionId != null) {
            DomainRegistry.get().release(sessionId);
        }
        columnX = new int[0];
        columnZ = new int[0];
        columnDistance = new double[0];
        columnKey = new double[0];
        columnChangeStart = new int[1];
        columnSurfaceChange = new int[0];
        columnBuilt = new boolean[0];
        changeY = new int[0];
        changeTarget = new BlockData[0];
        changeOriginal = new BlockData[0];
        restoreOrder = new int[0];
        columnRestored = new boolean[0];
        inFlightChunks.clear();
        // A shutdown clear must not leave late async callbacks holding the floor
        // in a permanently "unfinished" state (F-54).
        pendingAsyncRestores = 0;
        columnIndex = null;
        columnCount = 0;
        builtColumns = 0;
        restoredColumns = 0;
    }

    private static void requireMainThread() {
        if (!Bukkit.isPrimaryThread()) {
            throw new IllegalStateException("DomainFloor must be used on the server thread");
        }
    }
}
