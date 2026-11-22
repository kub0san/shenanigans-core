package com.hizeric.shenaniganscore.battleground.storage;

/** Checked failure for database lifecycle operations (open, schema init, backup, close). */
public final class DatabaseException extends Exception {

    public DatabaseException(String message) {
        super(message);
    }

    public DatabaseException(String message, Throwable cause) {
        super(message, cause);
    }
}
