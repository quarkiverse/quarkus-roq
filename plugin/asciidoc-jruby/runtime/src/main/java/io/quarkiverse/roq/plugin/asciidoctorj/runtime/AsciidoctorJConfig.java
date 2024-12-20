package io.quarkiverse.roq.plugin.asciidoctorj.runtime;

import java.util.Map;

import io.quarkus.runtime.annotations.ConfigPhase;
import io.quarkus.runtime.annotations.ConfigRoot;
import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;

@ConfigMapping(prefix = "quarkus.asciidoctorj")
@ConfigRoot(phase = ConfigPhase.BUILD_AND_RUN_TIME_FIXED)
public interface AsciidoctorJConfig {

    /**
     * Set Asciidoctorj attributes
     */
    Map<String, String> attributes();

    /**
     * Templates directory for Asciidoctorj
     */
    @WithDefault("src/main/asciidoc-templates")
    String templatesDir();

}
