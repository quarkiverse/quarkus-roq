package io.quarkiverse.roq.deployment.config;

import java.util.Objects;

import io.quarkus.runtime.annotations.ConfigPhase;
import io.quarkus.runtime.annotations.ConfigRoot;
import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;

@ConfigMapping(prefix = "quarkus.roq")
@ConfigRoot(phase = ConfigPhase.BUILD_TIME)
public interface RoqConfig {

    String DEFAULT_SITE_DIR = "site";

    /**
     * Path to the Roq site directory (relative to the project root).
     */
    @WithDefault(DEFAULT_SITE_DIR)
    String siteDir();

    static boolean isEqual(RoqConfig q1, RoqConfig q2) {
        if (!Objects.equals(q1.siteDir(), q2.siteDir())) {
            return false;
        }
        return true;
    }
}