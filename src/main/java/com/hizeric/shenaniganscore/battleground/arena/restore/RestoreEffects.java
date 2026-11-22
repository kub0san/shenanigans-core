package com.hizeric.shenaniganscore.battleground.arena.restore;

import com.hizeric.shenaniganscore.battleground.arena.ArenaRegion;
import com.hizeric.shenaniganscore.battleground.arena.snapshot.ArenaSnapshot.ChunkPos;
import org.bukkit.World;

import java.util.List;

/**
 * Cosmetic hooks fired by {@link ArenaRestoreEngine}. Every method is a no-op by default, runs on
 * the main thread, and is wrapped by the engine so an exception here can never fail a restore.
 */
public interface RestoreEffects {

    /** Does nothing. Used by tests and when animations are disabled. */
    RestoreEffects NONE = new RestoreEffects() { };

    /** A restore just began for this region. */
    default void onStart(World world, ArenaRegion region) { }

    /** Blocks were just written in one chunk (already restored, safe to decorate). */
    default void onChunkRestored(World world, ChunkPos chunk, List<Long> written, boolean tierOne) { }

    /** The deployment gate opened: every tier-1 essential block is back. */
    default void onTierOneComplete(World world) { }

    /** Throttled by the engine (about twice a second at 20 TPS). */
    default void onProgress(World world, int percent) { }

    /** The restore ended, successfully or not. */
    default void onFinished(World world, boolean failed) { }
}
