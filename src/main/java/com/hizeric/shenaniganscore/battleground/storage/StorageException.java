package com.hizeric.shenaniganscore.battleground.storage;

/**
 * Unchecked failure for data operations. Repositories throw it so callers can decide whether to
 * retry (the player store re-queues a dirty record) or report a failed save to the caller.
 */
public final class StorageException extends RuntimeException {

    public StorageException(String message) {
        super(message);
    }

    public StorageException(String message, Throwable cause) {
        super(message, cause);
    }
}
