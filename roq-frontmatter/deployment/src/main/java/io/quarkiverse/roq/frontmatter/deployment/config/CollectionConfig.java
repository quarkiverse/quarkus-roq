package io.quarkiverse.roq.frontmatter.deployment.config;

import io.smallrye.config.WithDefault;

public interface CollectionConfig {
    /**
     * The id for this collection (e.g. posts), it is also used as default path.
     */
    String id();

    /**
     * If true, the collection won't be available on path but consumable as data.
     */
    @WithDefault("false")
    boolean hidden();
}
