package io.quarkiverse.roq.data.runtime;

import io.quarkus.runtime.annotations.ConfigPhase;
import io.quarkus.runtime.annotations.ConfigRoot;
import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;

@ConfigMapping(prefix = "quarkus.roq.data")
@ConfigRoot(phase = ConfigPhase.BUILD_AND_RUN_TIME_FIXED)
public interface RoqDataConfig {

    String DEFAULT_LOCATION = "data";

    /**
     * The location of the Roq data files.
     */
    @WithDefault(DEFAULT_LOCATION)
    String location();
}
