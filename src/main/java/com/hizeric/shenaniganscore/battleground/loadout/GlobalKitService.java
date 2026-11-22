package com.hizeric.shenaniganscore.battleground.loadout;

import com.hizeric.shenaniganscore.battleground.loadout.GlobalKit;
import com.hizeric.shenaniganscore.battleground.storage.Database;
import com.hizeric.shenaniganscore.battleground.storage.Databases;
import com.hizeric.shenaniganscore.battleground.storage.GlobalKitRepository;
import com.hizeric.shenaniganscore.battleground.storage.StorageException;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Level;

public final class GlobalKitService {

    public record LoadResult(boolean seeded, String error) { }
    public record SaveResult(boolean ok, long version, String error) { }

    private final JavaPlugin plugin;
    private final GlobalKitRepository repository;
    private final KitStore store;
    private final ExecutorService ioExecutor;
    private final AtomicReference<CompletableFuture<Void>> writeTail =
            new AtomicReference<>(CompletableFuture.completedFuture(null));

    public GlobalKitService(JavaPlugin plugin) {
        this(plugin, Databases.require(), new KitStore(KitDefaults.BOARD_SIZE, KitDefaults.items()));
    }

    public GlobalKitService(JavaPlugin plugin, Database database) {
        this(plugin, database, new KitStore(KitDefaults.BOARD_SIZE, KitDefaults.items()));
    }

    public GlobalKitService(JavaPlugin plugin, Database database, KitStore store) {
        this.plugin = plugin;
        this.repository = new GlobalKitRepository(database);
        this.store = store;
        this.ioExecutor = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "kit-io");
            t.setDaemon(true);
            return t;
        });
    }

    public CompletableFuture<LoadResult> loadAsync() {
        return CompletableFuture.supplyAsync(() -> {
            try {
                Optional<String> encoded = repository.load();
                if (encoded.isPresent()) {
                    LoadoutCodec.DecodeResult decoded =
                            LoadoutCodec.decode(encoded.get(), KitDefaults.items());
                    if (decoded.error() != null) {
                        warn("Kit load reported: " + decoded.error(), null);
                    }
                    store.replace(decoded.items());
                    GlobalKit.setGlobalKit(store.kit());
                    return new LoadResult(false, null);
                }
                store.reset();
                GlobalKit.setGlobalKit(store.kit());
                enqueueWrite(() -> repository.save(
                        LoadoutCodec.encode(KitDefaults.items()).data()));
                return new LoadResult(true, null);
            } catch (StorageException e) {
                warn("Could not load the server kit; using defaults", e);
                store.reset();
                GlobalKit.setGlobalKit(store.kit());
                return new LoadResult(false, e.getMessage());
            }
        }, ioExecutor);
    }

    /** Synchronous load for callers that expect the old contract. */
    public void load() {
        try { loadAsync().get(); }
        catch (Exception e) { warn("Kit load failed", e); }
    }

    public void disable() {
        try {
            writeTail.get().get(5, TimeUnit.SECONDS);
        } catch (Exception e) {
            warn("Timed out draining kit write", e);
        }
        ioExecutor.shutdown();
    }

    public SaveResult save(ItemStack[] board) {
        return save(board, store.version());
    }

    public SaveResult save(ItemStack[] board, long expectedVersion) {
        if (board == null || board.length != store.boardSize()) {
            return new SaveResult(false, store.version(),
                    "board must be " + store.boardSize() + " slots");
        }
        LoadoutCodec.EncodeResult encoded = LoadoutCodec.encode(board);
        if (!encoded.success()) {
            return new SaveResult(false, store.version(), encoded.error());
        }
        if (store.version() != expectedVersion) {
            return new SaveResult(false, store.version(),
                    "stale version " + expectedVersion + " (current " + store.version() + ")");
        }
        long version = store.replace(board);
        GlobalKit.setGlobalKit(store.kit());
        enqueueWrite(() -> repository.save(encoded.data()));
        return new SaveResult(true, version, null);
    }

    public boolean saveLegacy(ItemStack[] board) {
        SaveResult r = save(board);
        return r.ok();
    }

    public SaveResult reset() {
        store.reset();
        GlobalKit.setGlobalKit(store.kit());
        enqueueWrite(() -> {
            try {
                repository.delete();
            } catch (StorageException e) {
                warn("Could not delete the saved kit; live kit is defaults", e);
            }
        });
        return new SaveResult(true, store.version(), null);
    }

    public void resetLegacy() {
        reset();
    }

    public CompletableFuture<LoadResult> reloadAsync() {
        return loadAsync();
    }

    public void reload() {
        try { reloadAsync().get(); }
        catch (Exception e) { warn("Kit reload failed", e); }
    }

    public ItemStack[] copy() { return store.kit(); }
    public long version() { return store.version(); }
    public KitStore store() { return store; }

    public static ItemStack[] importFrom(org.bukkit.entity.Player admin) {
        if (admin == null) return new ItemStack[KitDefaults.BOARD_SIZE];
        ItemStack[] board = new ItemStack[KitDefaults.BOARD_SIZE];
        org.bukkit.inventory.PlayerInventory inv = admin.getInventory();
        for (int i = 0; i < Math.min(36, inv.getSize()); i++) {
            board[i] = cloneOrNull(inv.getItem(i));
        }
        board[36] = cloneOrNull(inv.getBoots());
        board[37] = cloneOrNull(inv.getLeggings());
        board[38] = cloneOrNull(inv.getChestplate());
        board[39] = cloneOrNull(inv.getHelmet());
        board[40] = cloneOrNull(inv.getItemInOffHand());
        return board;
    }

    public static ItemStack[] empty() {
        return new ItemStack[KitDefaults.BOARD_SIZE];
    }

    private static ItemStack cloneOrNull(ItemStack item) {
        return item == null || item.getType().isAir() ? null : item.clone();
    }

    private void enqueueWrite(Runnable write) {
        writeTail.updateAndGet(prev -> prev.handle((ok, err) -> null)
                .thenRunAsync(() -> {
                    try { write.run(); }
                    catch (RuntimeException e) { warn("Kit write failed", e); }
                }, ioExecutor));
    }

    private void warn(String message, Throwable cause) {
        if (plugin != null) plugin.getLogger().log(Level.WARNING, message, cause);
        else { System.err.println(message); if (cause != null) cause.printStackTrace(System.err); }
    }
}