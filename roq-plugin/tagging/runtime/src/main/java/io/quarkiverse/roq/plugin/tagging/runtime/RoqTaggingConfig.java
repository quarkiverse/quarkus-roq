package io.quarkiverse.roq.plugin.tagging.runtime;

import io.quarkus.runtime.annotations.ConfigPhase;
import io.quarkus.runtime.annotations.ConfigRoot;
import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;

@ConfigMapping(prefix = "quarkus.roq.tagging")
@ConfigRoot(phase = ConfigPhase.BUILD_AND_RUN_TIME_FIXED)
public interface RoqTaggingConfig {
    /**
     * When true, all selected tags are transformed to lowercase.
     */
    @WithDefault("false")
    boolean lowercase();
}
