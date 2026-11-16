package io.quarkiverse.roq.plugin.image.runtime;

import io.quarkus.runtime.annotations.ConfigPhase;
import io.quarkus.runtime.annotations.ConfigRoot;
import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;

@ConfigMapping(prefix = "quarkus.roq.plugin.image")
@ConfigRoot(phase = ConfigPhase.BUILD_AND_RUN_TIME_FIXED)
public interface RoqImageConfig {

    /**
     * Whether the original files of the images converted at build time are published (served, and written to the
     * generated site) next to their generated variants. By default only the variants are published: a page
     * attachment or a public image that was converted stays private, pages still resolve it.
     */
    @WithDefault("false")
    boolean publishOriginals();
}
