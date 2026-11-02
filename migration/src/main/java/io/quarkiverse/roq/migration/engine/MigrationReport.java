package io.quarkiverse.roq.migration.engine;

import java.util.ArrayList;
import java.util.List;

/**
 * Collects messages from the migration stages. Messages go to the {@link MigrationOutput} as they arrive;
 * warnings are also kept so they can be summarised once the migration finishes.
 */
public class MigrationReport {

    public record Warning(String stage, String message) {
    }

    private final MigrationOutput output;
    private final List<Warning> warnings = new ArrayList<>();
    private String currentStage;

    MigrationReport(MigrationOutput output) {
        this.output = output;
    }

    void currentStage(String stage) {
        this.currentStage = stage;
    }

    public void info(String message) {
        output.info("  " + message);
    }

    public void warn(String message) {
        warnings.add(new Warning(currentStage, message));
        output.warn("  " + message);
    }

    public List<Warning> warnings() {
        return List.copyOf(warnings);
    }
}
