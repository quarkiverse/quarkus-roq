package io.quarkiverse.roq.generator.runtime;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import io.quarkus.runtime.annotations.ConfigPhase;
import io.quarkus.runtime.annotations.ConfigRoot;
import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;

@ConfigMapping(prefix = "quarkus.roq.generator")
@ConfigRoot(phase = ConfigPhase.BUILD_AND_RUN_TIME_FIXED)
public interface RoqGeneratorConfig {

    /**
     * The selected paths to include in the static website.
     * The output path is generated automatically: paths ending with a slash are completed with index.html, while other paths
     * remain unchanged.
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
     * Use ''path-replace'' to clean paths and only allow a given set of characters
     */
    PathReplaceConfig pathReplace();

    /**
     * With this config you can configure the path to get content from AND also the output path that will be generated for it.
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
     * Timeout for full generation in seconds
     */
    @WithDefault("60")
    long timeout();

    /**
     * How many times should a request be retried
     */
    @WithDefault("10")
    int requestRetry();
}