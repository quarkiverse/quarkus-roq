package io.quarkiverse.roq.migration.engine;

/**
 * Builds the converted site. Given to the {@link MigrationRunner} by the caller (the Roq CLI).
 */
@FunctionalInterface
public interface SiteBuilder {

    /**
     * Where the built site is, in the converted site.
     */
    String GENERATED_SITE = "target/roq";

    void build(TargetSite site) throws Exception;
}
