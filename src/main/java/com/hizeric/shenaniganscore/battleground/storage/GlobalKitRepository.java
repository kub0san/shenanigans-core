package com.hizeric.shenaniganscore.battleground.storage;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Objects;
import java.util.Optional;

/** One row holds the single server kit's encoded 41-slot board. */
public final class GlobalKitRepository {

    private static final String GLOBAL_KEY = "global";

    private final Database database;

    public GlobalKitRepository(Database database) {
        this.database = Objects.requireNonNull(database, "database");
    }

    /** Replaces the override (REPLACE INTO keeps exactly one row). */
    public void save(String encoded) {
        try (Connection connection = database.connection();
             PreparedStatement statement = connection.prepareStatement(
                     "REPLACE INTO kits(kit_id, kit_data) VALUES(?, ?)")) {
            statement.setString(1, GLOBAL_KEY);
            statement.setString(2, encoded);
            statement.executeUpdate();
        } catch (SQLException e) {
            throw new StorageException("Could not save the global kit", e);
        }
    }

    /** The encoded board, or empty when the server has no saved override. */
    public Optional<String> load() {
        try (Connection connection = database.connection();
             PreparedStatement statement = connection.prepareStatement(
                     "SELECT kit_data FROM kits WHERE kit_id = ?")) {
            statement.setString(1, GLOBAL_KEY);
            try (ResultSet result = statement.executeQuery()) {
                return result.next() ? Optional.of(result.getString(1)) : Optional.empty();
            }
        } catch (SQLException e) {
            throw new StorageException("Could not load the global kit", e);
        }
    }

    /** Removes the override so the built-in defaults become authoritative again. */
    public void delete() {
        try (Connection connection = database.connection();
             PreparedStatement statement = connection.prepareStatement(
                     "DELETE FROM kits WHERE kit_id = ?")) {
            statement.setString(1, GLOBAL_KEY);
            statement.executeUpdate();
        } catch (SQLException e) {
            throw new StorageException("Could not delete the global kit override", e);
        }
    }
}
