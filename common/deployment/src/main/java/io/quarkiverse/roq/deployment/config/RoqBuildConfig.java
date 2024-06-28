package io.quarkiverse.roq.deployment.config;

import java.util.Objects;

import io.quarkus.runtime.annotations.ConfigPhase;
import io.quarkus.runtime.annotations.ConfigRoot;
import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;

@ConfigMapping(prefix = "quarkus.roq")
@ConfigRoot(phase = ConfigPhase.BUILD_TIME)
public interface RoqBuildConfig {

    String DEFAULT_SITE_DIR = "src/main/site";
    String DEFAULT_DATA_DIR = "data/";

    /**
     * Path to the static root directory (relative to the project root).
     */
    @WithDefault(DEFAULT_SITE_DIR)
    String siteDir();

    /**
     * Path to the static data directory (relative to the site directory `quarkus.roq.site-dir`).
     */
    @WithDefault(DEFAULT_DATA_DIR)
    String dataDir();

    static boolean isEqual(RoqBuildConfig q1, RoqBuildConfig q2) {
        if (!Objects.equals(q1.siteDir(), q2.siteDir())) {
            return false;
        }
        if (!Objects.equals(q1.dataDir(), q2.dataDir())) {
            return false;
        }
        return true;
    }
}