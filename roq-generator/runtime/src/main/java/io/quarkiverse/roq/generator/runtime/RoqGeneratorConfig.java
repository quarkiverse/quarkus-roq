package io.quarkiverse.roq.generator.runtime;

import java.util.List;
import java.util.Map;
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
     * Glob syntax is authorized for non-dynamic resources (without query or path params)
     * <p>
     * For dynamic paths selection, produce a {@link RoqSelection} in you app.
     *
     * <pre>
     * {@code
     * &#64;Produces
     * &#64;Singleton
     * &#64;Transactional
     * RoqSelection produce() {
     *     return new RoqSelection(List.of(
     *             SelectedPath.builder().html("/roq?name=foo").build(),
     *             SelectedPath.builder().html("/blog/hello/").build(),
     *             SelectedPath.builder().path("/api/hello?name=foo").outputPath("/hello-foo.json").build()));
     * }
     * }
     * </pre>
     */
    @WithDefault("/,/static/**")
    Optional<List<String>> paths();

    /**
     * You can configure the path to get content from and the output path that will be generated.
     */
    @WithDefault("")
    Map<String, String> customPaths();

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