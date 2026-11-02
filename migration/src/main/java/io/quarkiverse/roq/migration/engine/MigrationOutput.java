package io.quarkiverse.roq.migration.engine;

/**
 * Where a migration reports progress. The Roq CLI maps it onto its own output, so warnings are styled and shown the
 * same way as for other commands.
 */
public interface MigrationOutput {

    void info(String message);

    void warn(String message);
}
