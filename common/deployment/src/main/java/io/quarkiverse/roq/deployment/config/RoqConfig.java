package io.quarkiverse.roq.deployment.config;

import java.util.Objects;

import io.quarkus.runtime.annotations.ConfigPhase;
import io.quarkus.runtime.annotations.ConfigRoot;
import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;

@ConfigMapping(prefix = "quarkus.roq")
@ConfigRoot(phase = ConfigPhase.BUILD_TIME)
public interface RoqConfig {

    String DEFAULT_DIR = "site"; // {project-dir}/site
    String DEFAULT_RESOURCE_DIR = "site"; // src/main/resources/site

    /**
     * Path to the Roq directory (relative to the project root).
     */
    @WithDefault(DEFAULT_DIR)
    String dir();

    /**
     * Path to the Roq directory in the resources.
     */
    @WithDefault(DEFAULT_RESOURCE_DIR)
    String resourceDir();

    static boolean isEqual(RoqConfig q1, RoqConfig q2) {
        if (!Objects.equals(q1.dir(), q2.dir())) {
            return false;
        }
        if (!Objects.equals(q1.resourceDir(), q2.resourceDir())) {
            return false;
        }
        return true;
    }
}