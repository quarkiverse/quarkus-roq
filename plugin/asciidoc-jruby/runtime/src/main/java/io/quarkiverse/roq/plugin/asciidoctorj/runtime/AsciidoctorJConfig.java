package io.quarkiverse.roq.plugin.asciidoctorj.runtime;

import java.util.Optional;

import io.quarkus.runtime.annotations.ConfigPhase;
import io.quarkus.runtime.annotations.ConfigRoot;
import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;

@ConfigMapping(prefix = "quarkus.qute.asciidoctorj")
@ConfigRoot(phase = ConfigPhase.BUILD_AND_RUN_TIME_FIXED)
public interface AsciidoctorJConfig {

    /**
     * To enable image-based icons, you set this config to the value font.
     */
    Optional<String> icons();

    /**
     * Source highlighting is applied to text that’s assigned the source block style (either explicitly or implicitly) and a
     * source language.
     */
    Optional<String> sourceHighlighter();

    /**
     * Where images will be rendered
     */
    @WithDefault("src/main/asciidoc-templates")
    String templatesDir();

    /**
     * Where images will be rendered
     */
    @WithDefault("target/images/")
    Optional<String> outputImageDir();

    /**
     * Where images will linked to.oq
     *
     */
    @WithDefault("/public")
    Optional<String> imageDir();

}
