package io.quarkiverse.roq.migration.engine;

import java.nio.file.Path;

/**
 * The site being converted, given to the {@link Scaffolder}, the writing stages of a {@link SiteMigrator} and the
 * {@link SiteBuilder}.
 */
public final class TargetSite {

    private final Path dir;
    private final boolean inPlace;
    private final SiteAnalysis analysis;
    private final MigrationReport report;

    TargetSite(Path dir, boolean inPlace, SiteAnalysis analysis, MigrationReport report) {
        this.dir = dir;
        this.inPlace = inPlace;
        this.analysis = analysis;
        this.report = report;
    }

    /**
     * @return the directory being converted: the output directory, or the original site when migrating in place
     */
    public Path dir() {
        return dir;
    }

    public boolean inPlace() {
        return inPlace;
    }

    public SiteAnalysis analysis() {
        return analysis;
    }

    public MigrationReport report() {
        return report;
    }
}
