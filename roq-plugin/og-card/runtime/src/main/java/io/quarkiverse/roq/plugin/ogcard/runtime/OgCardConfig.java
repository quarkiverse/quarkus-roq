package io.quarkiverse.roq.plugin.ogcard.runtime;

import java.util.List;
import java.util.Optional;

import io.quarkus.runtime.annotations.ConfigPhase;
import io.quarkus.runtime.annotations.ConfigRoot;
import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;

@ConfigMapping(prefix = "quarkus.roq.plugin.og-card")
@ConfigRoot(phase = ConfigPhase.BUILD_AND_RUN_TIME_FIXED)
public interface OgCardConfig {

    /**
     * Collection ids for which OG images are generated (e.g. {@code posts}).
     */
    Optional<List<String>> collections();

    /**
     * Explicit normal page paths to include (e.g. {@code /}, {@code /about/}).
     */
    Optional<List<String>> includePaths();

    /**
     * Page path prefixes to skip when generating cards from {@link #collections()} (e.g. {@code /posts/tag/}
     * to omit auto-generated tag archive pages). Not applied to {@link #includePaths()}.
     */
    Optional<List<String>> excludePaths();

    /**
     * Base URL path for generated PNG files.
     */
    @WithDefault("/og")
    String outputPrefix();

    /**
     * Output segment for collection documents. {@code :collection} is replaced with the collection id.
     * The special value {@code :collections} appends {@code s} to the collection id ({@code posts} → {@code posts},
     * {@code guide} → {@code guides}).
     */
    @WithDefault(":collections")
    String collectionOutputSegment();

    /**
     * Image width in pixels.
     */
    @WithDefault("1200")
    int width();

    /**
     * Image height in pixels.
     */
    @WithDefault("630")
    int height();

    /**
     * When true, pages that already declare {@code image}, {@code img}, or {@code picture} are skipped.
     */
    @WithDefault("true")
    boolean skipIfImageSet();

    /**
     * Site name used in OG image alt text and card branding.
     */
    @WithDefault("Site")
    String siteName();

    /**
     * Qute template id for the OG card (site templates override bundled defaults).
     */
    @WithDefault("og-card/default-card.svg")
    String template();
}
