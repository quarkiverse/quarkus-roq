package io.quarkiverse.roq.data.deployment;

import io.quarkus.runtime.annotations.ConfigGroup;
import io.smallrye.config.ConfigMapping;

@ConfigMapping(prefix = "quarkus.roq.data")
@ConfigGroup
public interface RoqDataNamedConfig {

    /**
     * If value is `true` (default `false`), then a page will be generated for each item of this collection.
     *
     * All pages will be generated under `<root>/content/collection_name`
     *
     * @asciidoclet
     */
    Boolean generate();

    /**
     * The attribute to be used as title
     *
     * @asciidoclet
     */
    String titleAttributeName();

    /**
     * The layout for this collection
     *
     * @asciidoclet
     */
    String layout();

}
