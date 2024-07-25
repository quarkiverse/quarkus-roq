package io.quarkiverse.roq.deployment.config;

import io.quarkus.runtime.annotations.ConfigPhase;
import io.quarkus.runtime.annotations.ConfigRoot;
import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;

@ConfigMapping(prefix = "quarkus.roq.jackson")
@ConfigRoot(phase = ConfigPhase.BUILD_TIME)
public interface RoqJacksonConfig {

    /**
     * If enabled, Jackson will fail when encountering unknown properties.
     * <p>
     * You can still override it locally with {@code @JsonIgnoreProperties(ignoreUnknown = false)}.
     */
    @WithDefault("false")
    boolean failOnUnknownProperties();

    /**
     * If enabled, Jackson will fail when no accessors are found for a type.
     * This is enabled by default to match the default Jackson behavior.
     */
    @WithDefault("true")
    boolean failOnEmptyBeans();

    /**
     * If enabled, Jackson will ignore case during Enum deserialization.
     */
    @WithDefault("false")
    boolean acceptCaseInsensitiveEnums();

}
