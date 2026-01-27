package io.quarkiverse.roq.deployment.config;

import java.util.Optional;

import io.quarkus.runtime.annotations.ConfigDocDefault;
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
     * Path to the Roq site directory (relative to the project root).
     */
    @ConfigDocDefault("the project root")
    @WithName("dir")
    Optional<String> dirOptional();

    default String dir() {
        return dirOptional().orElse(DEFAULT_DIR);
    }

    /**
     * Path to the Roq directory in the resources.
     */
    @WithName("resource-dir")
    @ConfigDocDefault("the root of the resources")
    Optional<String> resourceDirOptional();

    default String resourceDir() {
        return resourceDirOptional().orElse(DEFAULT_RESOURCE_DIR);
    }

}
