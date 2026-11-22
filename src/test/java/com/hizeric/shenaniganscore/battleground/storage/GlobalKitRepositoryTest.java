package com.hizeric.shenaniganscore.battleground.storage;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The global kit round-trip at the storage seam. Encoded strings stand in for ItemStack arrays
 * because a real Bukkit ItemStack cannot be serialised without a running Paper registry.
 */
class GlobalKitRepositoryTest {

    @Test
    void anEncodedKitRoundTripsOverwritesAndDeletes(@TempDir Path temp) throws Exception {
        Database db = Database.open(temp.resolve("database.db").toFile());
        try {
            GlobalKitRepository repo = new GlobalKitRepository(db);
            assertTrue(repo.load().isEmpty(), "a fresh database has no override");

            repo.save("encoded-kit-one");
            assertEquals(Optional.of("encoded-kit-one"), repo.load());

            repo.save("encoded-kit-two");
            assertEquals(Optional.of("encoded-kit-two"), repo.load());
            assertEquals(Optional.of("encoded-kit-two"), new GlobalKitRepository(db).load(),
                    "the row is shared, not cached per repository instance");

            repo.delete();
            assertTrue(repo.load().isEmpty());
        } finally {
            db.close();
        }
    }
}
