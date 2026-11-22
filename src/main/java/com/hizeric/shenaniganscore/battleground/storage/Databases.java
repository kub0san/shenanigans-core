package com.hizeric.shenaniganscore.battleground.storage;

/**
 * Plugin-scoped handle to the one {@link Database}. The plugin's lifecycle installs it once on
 * enable (fail loud if {@link Database#open} throws) and clears it on disable; services keep their
 * original constructors by resolving it here.
 */
public final class Databases {

    private static volatile Database current;

    private Databases() { }

    public static synchronized void install(Database database) {
        if (database == null) throw new IllegalArgumentException("database");
        Database previous = current;
        current = database;
        if (previous != null && previous != database) {
            try { previous.close(); }
            catch (DatabaseException ignored) { /* best effort: never fail install on the old pool */ }
        }
    }

    /** Fails loudly when the lifecycle has not installed a database. */
    public static synchronized Database require() {
        Database database = current;
        if (database == null) {
            throw new IllegalStateException(
                    "The SQL database is not initialised — open it during plugin enable before " +
                    "constructing storage-backed services.");
        }
        return database;
    }

    public static synchronized void closeAndClear() throws DatabaseException {
        Database database = current;
        current = null;
        if (database != null) database.close();
    }
}