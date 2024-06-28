package io.quarkiverse.roq.data.deployment;

import java.util.Objects;

import io.quarkus.runtime.annotations.ConfigPhase;
import io.quarkus.runtime.annotations.ConfigRoot;
import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;

@ConfigMapping(prefix = "quarkus.roq.data")
@ConfigRoot(phase = ConfigPhase.BUILD_TIME)
public interface RoqDataConfig {

    String DEFAULT_LOCATION = "data";

    /**
     * The location of the Roq data files relative to the quarkus.roq.site-dir.
     */
    @WithDefault(DEFAULT_LOCATION)
    String dir();

    static boolean isEqual(RoqDataConfig q1, RoqDataConfig q2) {
        if (!Objects.equals(q1.dir(), q2.dir())) {
            return false;
        }
        return true;
    }
}
