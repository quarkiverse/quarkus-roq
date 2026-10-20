package io.quarkiverse.roq.plugin.markdowntwin.deployment;

import java.util.List;
import java.util.Optional;

import io.quarkus.runtime.annotations.ConfigPhase;
import io.quarkus.runtime.annotations.ConfigRoot;
import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;

/**
 * Build-time options for Markdown twin generation.
 */
@ConfigMapping(prefix = "quarkus.roq.plugin.markdown-twin")
@ConfigRoot(phase = ConfigPhase.BUILD_TIME)
public interface MarkdownTwinConfig {

    /**
     * Whether Markdown twins are generated.
     * <p>
     * Twins are built during augmentation, so they are rebuilt on every dev-mode restart. On a large site, such as
     * quarkus.io with its thousands of pages, that is the slowest part of the plugin and multiplies the dev-mode
     * startup time. Set {@code %dev.quarkus.roq.plugin.markdown-twin.enabled=false} to skip it while working on the
     * site, keeping twins in the production build.
     * <p>
     * When disabled, no twin files are written and no page advertises one, because the
     * {@code link rel="alternate"} override is not registered either.
     */
    @WithDefault("true")
    boolean enabled();

    /**
     * Page paths to generate twins for. When unset, every eligible page gets a twin.
     * <p>
     * Patterns are matched against the page's path relative to the site root, with or without a leading slash
     * ({@code guides/http-reference} and {@code /guides/http-reference} are the same page). A pattern is a glob
     * unless it carries an explicit {@code glob:} or {@code regex:} prefix, the same convention as
     * {@code site.ignored-files}. In a glob, {@code *} stops at a slash and {@code **} crosses slashes, so
     * {@code /guides/**} covers the whole guides tree.
     * <p>
     * These are page paths rather than source paths so that the same patterns can be reused by a CDN rule that
     * serves twins on content negotiation. A path in that rule but not here answers 404 instead of the page. A
     * directory-style page such as {@code /guides/} also matches as {@code /guides/index}, so a tree pattern covers
     * the landing page of the tree.
     * <p>
     * The value is a comma-separated list, so a comma inside a glob alternation such as {@code /guides/{http,rest}-*}
     * must be escaped as {@code \\,} in the property value, or the patterns written as indexed properties
     * ({@code include[0]=...}).
     */
    Optional<List<String>> include();

    /**
     * Page paths to skip. Applied after {@link #include()}, so an excluded page never gets a twin.
     * <p>
     * Use this to keep archived or generated trees out of the build. Pattern syntax is the same as
     * {@link #include()}. The per-page {@code mdtwin: false} front matter key still opts a single page out.
     */
    Optional<List<String>> exclude();
}
