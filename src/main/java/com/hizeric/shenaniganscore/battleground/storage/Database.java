package com.hizeric.shenaniganscore.battleground.storage;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
import java.util.Objects;

/**
 * The single SQLite database behind the global kit, player records and shop catalogue.
 *
 * <p>Open/init failures are checked ({@link DatabaseException}) so plugin enable fails loudly —
 * there is deliberately no file-based fallback. Data operations are unchecked
 * ({@link StorageException}) so the player store can retry a dirty record.</p>
 */
public final class Database implements AutoCloseable {

    /** Bump only when a new {@link Migration} is appended. */
    public static final int SCHEMA_VERSION = 1;

    private final Path sourcePath;
    private final HikariDataSource dataSource;

    private Database(Path sourcePath, HikariDataSource dataSource) {
        this.sourcePath = sourcePath;
        this.dataSource = dataSource;
    }

    /** Opens (creating if needed) the database and brings its schema up to {@link #SCHEMA_VERSION}. */
    public static Database open(File file) throws DatabaseException {
        Objects.requireNonNull(file, "file");
        File parent = file.getAbsoluteFile().getParentFile();
        if (parent != null && !parent.exists() && !parent.mkdirs()) {
            throw new DatabaseException("Could not create database folder " + parent);
        }

        HikariConfig config = new HikariConfig();
        config.setPoolName("shenanigans-sqlite");
        config.setJdbcUrl("jdbc:sqlite:" + file.getAbsolutePath()
                + "?journal_mode=WAL&synchronous=NORMAL&busy_timeout=30000");
        // Set explicitly so a missing driver fails at open instead of at the first query.
        config.setDriverClassName("org.sqlite.JDBC");
        config.setMaximumPoolSize(1);
        config.setMinimumIdle(1);
        config.setLeakDetectionThreshold(60_000L);
        config.setKeepaliveTime(30_000L);
        config.setConnectionTimeout(10_000L);
        config.setValidationTimeout(5_000L);

        HikariDataSource opened;
        try {
            opened = new HikariDataSource(config);
        } catch (RuntimeException e) {
            throw new DatabaseException("Could not open database " + file, e);
        }

        if (MIGRATIONS.size() != SCHEMA_VERSION) {
            opened.close();
            throw new DatabaseException("MIGRATIONS (" + MIGRATIONS.size()
                    + ") does not match SCHEMA_VERSION (" + SCHEMA_VERSION + ")");
        }
        Database database = new Database(file.toPath().toAbsolutePath().normalize(), opened);
        try {
            database.initialiseSchema();
        } catch (SQLException e) {
            opened.close();
            throw new DatabaseException("Could not initialise database schema in " + file, e);
        }
        return database;
    }

    public Connection connection() throws SQLException {
        return dataSource.getConnection();
    }

    /** A consistent `VACUUM INTO` copy, safe to take while the pool is live. */
    public void backupTo(File target) throws DatabaseException {
        Objects.requireNonNull(target, "target");
        Path targetPath = target.toPath().toAbsolutePath().normalize();
        if (targetPath.equals(sourcePath)) {
            throw new DatabaseException("Cannot back up the database to its own file: " + target);
        }
        File parent = target.getAbsoluteFile().getParentFile();
        if (parent != null && !parent.exists() && !parent.mkdirs()) {
            throw new DatabaseException("Could not create backup folder " + parent);
        }
        try {
            Files.deleteIfExists(target.toPath());
        } catch (java.io.IOException e) {
            throw new DatabaseException("Could not replace backup " + target, e);
        }
        String escaped = target.getAbsolutePath().replace("'", "''");
        try (Connection connection = connection(); Statement statement = connection.createStatement()) {
            statement.executeUpdate("VACUUM INTO '" + escaped + "'");
        } catch (SQLException e) {
            throw new DatabaseException("Could not back up database to " + target, e);
        }
    }

    @Override
    public void close() throws DatabaseException {
        if (dataSource.isClosed()) return;
        try {
            dataSource.close();
        } catch (RuntimeException e) {
            throw new DatabaseException("Could not close the database", e);
        }
    }

    // ------------------------------------------------------------------ schema

    /** Ordered migrations; index {@code n} upgrades a version-{@code n} database to {@code n+1}. */
    @FunctionalInterface
    private interface Migration {
        void apply(Connection connection) throws SQLException;
    }

    private static final List<Migration> MIGRATIONS = List.of(Database::migration1);

    private void initialiseSchema() throws SQLException {
        try (Connection connection = connection()) {
            connection.setAutoCommit(false);
            try (Statement statement = connection.createStatement()) {
                statement.executeUpdate("CREATE TABLE IF NOT EXISTS schema_version (version INTEGER NOT NULL)");
                int version;
                try (ResultSet result = statement.executeQuery("SELECT version FROM schema_version LIMIT 1")) {
                    version = result.next() ? result.getInt(1) : 0;
                }
                if (version > SCHEMA_VERSION) {
                    throw new SQLException("Database schema version " + version
                            + " is newer than this build supports (" + SCHEMA_VERSION + ")");
                }
                if (version < SCHEMA_VERSION) {
                    for (int i = version; i < SCHEMA_VERSION; i++) {
                        MIGRATIONS.get(i).apply(connection);
                    }
                    try (PreparedStatement clear = connection.prepareStatement("DELETE FROM schema_version")) {
                        clear.executeUpdate();
                    }
                    try (PreparedStatement stamp = connection.prepareStatement(
                            "INSERT INTO schema_version(version) VALUES(?)")) {
                        stamp.setInt(1, SCHEMA_VERSION);
                        stamp.executeUpdate();
                    }
                }
                // Verify the version row is exactly once, even on an already-current database.
                try (ResultSet result = statement.executeQuery("SELECT version FROM schema_version")) {
                    if (!result.next() || result.getInt(1) != SCHEMA_VERSION || result.next()) {
                        throw new SQLException("schema_version is missing or duplicated");
                    }
                }
                connection.commit();
            } catch (SQLException e) {
                connection.rollback();
                throw e;
            } finally {
                connection.setAutoCommit(true);
            }
        }
    }

    private static void migration1(Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.executeUpdate("CREATE TABLE IF NOT EXISTS kits ("
                    + "kit_id TEXT PRIMARY KEY NOT NULL, kit_data TEXT NOT NULL)");
            statement.executeUpdate("CREATE TABLE IF NOT EXISTS players ("
                    + "uuid TEXT PRIMARY KEY NOT NULL, data TEXT NOT NULL)");
            statement.executeUpdate("CREATE TABLE IF NOT EXISTS shop_entries ("
                    + "ordinal INTEGER PRIMARY KEY NOT NULL, id TEXT NOT NULL, data TEXT NOT NULL)");
        }
    }
}
