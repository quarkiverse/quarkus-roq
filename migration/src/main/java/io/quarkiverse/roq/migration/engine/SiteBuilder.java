package io.quarkiverse.roq.migration.engine;

/**
 * Builds the converted site. Given to the {@link MigrationRunner} by the caller (the Roq CLI).
 */
@FunctionalInterface
public interface SiteBuilder {

    void build(TargetSite site) throws Exception;
}
