package io.quarkiverse.roq.migration.engine;

import java.nio.file.Path;
import java.util.Objects;

/**
 * How to run a migration.
 *
 * @param outputDir where to write the converted site; the source directory itself to migrate in place
 * @param build whether to build the converted site with the {@link SiteBuilder}
 * @param output where progress and warnings go
 */
public record MigrationOptions(Path outputDir, boolean build, MigrationOutput output) {

    public MigrationOptions {
        Objects.requireNonNull(outputDir, "outputDir");
        Objects.requireNonNull(output, "output");
    }
}
