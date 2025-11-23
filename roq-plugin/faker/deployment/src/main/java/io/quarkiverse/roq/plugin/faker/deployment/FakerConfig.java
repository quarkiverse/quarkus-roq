package io.quarkiverse.roq.plugin.faker.deployment;

import java.util.Map;

import io.quarkus.runtime.annotations.ConfigPhase;
import io.quarkus.runtime.annotations.ConfigRoot;
import io.smallrye.config.ConfigMapping;

@ConfigMapping(prefix = "quarkus.roq.faker")
@ConfigRoot(phase = ConfigPhase.BUILD_TIME)
public interface FakerConfig {

    /**
     * Number of fake documents to generate per collection
     */
    Map<String, Integer> count();

}
