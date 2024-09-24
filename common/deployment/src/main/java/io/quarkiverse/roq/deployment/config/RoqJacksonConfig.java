package io.quarkiverse.roq.deployment.config;

import java.util.Optional;

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
    @WithDefault("true")
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

    /**
     * Defines how names of JSON properties ("external names") are derived
     * from names of POJO methods and fields ("internal names").
     * The value can be one of the one of the constants in {@link com.fasterxml.jackson.databind.PropertyNamingStrategies},
     * so for example, {@code LOWER_CAMEL_CASE} or {@code UPPER_CAMEL_CASE}.
     * <p>
     * The value can also be a fully qualified class name of a {@link com.fasterxml.jackson.databind.PropertyNamingStrategy}
     * subclass.
     */
    Optional<String> propertyNamingStrategy();

}