package io.quarkiverse.roq.generator.runtime;

import java.util.List;
import java.util.Optional;

import io.quarkus.runtime.annotations.ConfigPhase;
import io.quarkus.runtime.annotations.ConfigRoot;
import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;

@ConfigMapping(prefix = "quarkus.roq.generator")
@ConfigRoot(phase = ConfigPhase.RUN_TIME)
public interface RoqGeneratorConfig {

    /**
     * The selected paths to include in the static website
     * Glob syntax is authorized to add resources
     *
     * For dynamic paths selection, produce a {@link RoqSelection} in you app.
     *
     * <code>
     *     &#64;Produces
     *     &#64;Singleton
     *     @Transactional
     *     RoqSelection produce() {
     *         return new RoqSelection(List.of(
     *                 SelectedPath.builder().html("/roq?name=foo-html").build(),
     *                 SelectedPath.builder().path("/roq?name=foo").build(),
     *                 SelectedPath.builder().path("/roq?name=bar").build()));
     *     }
     * </code>
     */
    @WithDefault("/,/static/**")
    Optional<List<String>> paths();

    /**
     * Output directory for the static website
     * relative to the target directory
     */
    @WithDefault("roq")
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
