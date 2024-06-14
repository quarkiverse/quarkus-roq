package io.quarkiverse.statiq.runtime;

import java.util.List;
import java.util.Optional;

import io.quarkus.runtime.annotations.ConfigPhase;
import io.quarkus.runtime.annotations.ConfigRoot;
import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;

@ConfigMapping(prefix = "quarkus.statiq")
@ConfigRoot(phase = ConfigPhase.RUN_TIME)
public interface StatiqConfig {

    /**
     * The fixed paths to include in the static website
     * Glob syntax is authorized to add static resources
     */
    Optional<List<String>> fixed();

    /**
     * Output directory for the static website
     * relative to the target directory
     */
    @WithDefault("statiq")
    String outputDir();

    /**
     * Build as a CLI to export the static website
     */
    @WithDefault("false")
    boolean batch();

    /**
     * Timeout for generation in seconds
     */
    @WithDefault("30")
    long timeout();
}
