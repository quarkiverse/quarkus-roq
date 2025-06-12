package io.quarkiverse.roq.plugin.diagram.deployment.devservices;

import io.quarkus.runtime.annotations.ConfigPhase;
import io.quarkus.runtime.annotations.ConfigRoot;
import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;

@ConfigMapping
@ConfigRoot(phase = ConfigPhase.BUILD_TIME)
public interface DevServiceConfig {

    /**
     * Enable or disable Dev Services explicitly.
     * [NOTE]
     * ====
     * Dev Services are automatically enabled unless {@code quarkus.rest-client.kroki-api.url} is set.
     * ====
     *
     * @asciiidoclet
     */
    @WithDefault("true")
    Boolean enabled();

    /**
     * <p>
     * The default kroki container image
     * [WARNING]
     * ====
     * The default
     * ====
     *
     * @asciidoclet
     */
    @WithDefault("yuzutech/kroki:0.28.0")
    String imageName();
}
