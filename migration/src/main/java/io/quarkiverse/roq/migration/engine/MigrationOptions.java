package io.quarkiverse.roq.migration.engine;

import java.net.URI;
import java.nio.file.Path;
import java.util.Objects;

/**
 * How to run a migration.
 *
 * @param outputDir where to write the converted site; the source directory itself to migrate in place
 * @param build whether to build the converted site with the {@link SiteBuilder}
 * @param output where progress and warnings go
 * @param referenceSite the site to compare the built site with, usually the live one; null for the one the migrator
 *        finds in the site's configuration, if any
 */
public record MigrationOptions(Path outputDir, boolean build, MigrationOutput output, URI referenceSite) {

    public MigrationOptions {
        Objects.requireNonNull(outputDir, "outputDir");
        Objects.requireNonNull(output, "output");
    }

    public MigrationOptions(Path outputDir, boolean build, MigrationOutput output) {
        this(outputDir, build, output, null);
    }
}
