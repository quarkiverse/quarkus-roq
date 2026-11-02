package io.quarkiverse.roq.migration.engine;

/**
 * Thrown by a migration stage to stop the migration with a message meant for the user.
 */
public class MigrationException extends Exception {

    public MigrationException(String message) {
        super(message);
    }

    public MigrationException(String message, Throwable cause) {
        super(message, cause);
    }
}
