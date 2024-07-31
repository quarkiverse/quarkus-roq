package io.quarkiverse.roq.frontmatter.deployment;

import java.util.List;

import io.quarkus.runtime.annotations.ConfigPhase;
import io.quarkus.runtime.annotations.ConfigRoot;
import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;

@ConfigRoot(phase = ConfigPhase.BUILD_TIME)
@ConfigMapping(prefix = "quarkus.roq.frontmatter")
public interface RoqFrontMatterConfig {

    String DEFAULT_LOCATIONS = "layout,pages,posts";

    /**
     * The location of the Roq data files.
     */
    @WithDefault(DEFAULT_LOCATIONS)
    List<String> locations();
}
