package io.quarkiverse.roq.migration.engine;

/**
 * Creates the Roq project build file, using {@link SiteAnalysis#projectName()}, {@link SiteAnalysis#extensions()} and
 * {@link SiteAnalysis#dependencies()}.
 * Given to the {@link MigrationRunner} by the caller (the Roq CLI) because project creation is not specific to any
 * site type.
 */
@FunctionalInterface
public interface Scaffolder {

    void scaffold(TargetSite site) throws Exception;
}
