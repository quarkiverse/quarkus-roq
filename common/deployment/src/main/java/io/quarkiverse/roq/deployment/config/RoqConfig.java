package io.quarkiverse.roq.deployment.config;

import java.util.Objects;
import java.util.Optional;

import io.quarkus.runtime.annotations.ConfigPhase;
import io.quarkus.runtime.annotations.ConfigRoot;
import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithName;

@ConfigMapping(prefix = "quarkus.roq")
@ConfigRoot(phase = ConfigPhase.BUILD_TIME)
public interface RoqConfig {

    String DEFAULT_DIR = ""; // {project-dir}/
    String DEFAULT_RESOURCE_DIR = ""; // src/main/resources/

    /**
     * Path to the Roq directory (relative to the project root).
     */
    @WithName("dir")
    Optional<String> dirOptional();

    default String dir() {
        return dirOptional().orElse(DEFAULT_DIR);
    }

    /**
     * Path to the Roq directory in the resources.
     */
    @WithName("resource-dir")
    Optional<String> resourceDirOptional();

    default String resourceDir() {
        return resourceDirOptional().orElse(DEFAULT_RESOURCE_DIR);
    }

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
