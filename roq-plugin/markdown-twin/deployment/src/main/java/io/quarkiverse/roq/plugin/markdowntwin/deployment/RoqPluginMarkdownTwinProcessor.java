package io.quarkiverse.roq.plugin.markdowntwin.deployment;

import static io.quarkiverse.tools.stringpaths.StringPaths.removeExtension;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.eclipse.microprofile.config.Config;
import org.eclipse.microprofile.config.ConfigProvider;
import org.jboss.logging.Logger;

import io.quarkiverse.roq.frontmatter.deployment.items.data.RoqFrontMatterPageTemplateBuildItem;
import io.quarkiverse.roq.frontmatter.deployment.items.data.RoqFrontMatterRootUrlBuildItem;
import io.quarkiverse.roq.frontmatter.deployment.items.data.RoqFrontMatterStaticFileBuildItem;
import io.quarkiverse.roq.frontmatter.runtime.model.PageSource;
import io.quarkiverse.roq.frontmatter.runtime.model.RootUrl;
import io.quarkiverse.roq.frontmatter.runtime.model.RoqUrl;
import io.quarkus.deployment.annotations.BuildProducer;
import io.quarkus.deployment.annotations.BuildStep;
import io.quarkus.deployment.builditem.FeatureBuildItem;

/**
 * Publishes a Markdown twin of every content page for AI agents (quarkiverse/quarkus-roq#1104), at
 * {@code <page-url>index.md} for a directory-style URL such as {@code /docs/basics/} and at {@code <page-url>.md} for a
 * URL with a file name; see {@link #twinPath(String)}.
 * <p>
 * The twin is produced from the <em>source</em>, at build time, and handed to Roq as a static file
 * ({@link RoqFrontMatterStaticFileBuildItem}), which Roq both serves and writes to the generated site.
 * <ul>
 * <li>Markdown pages: the verbatim Markdown source.</li>
 * <li>AsciiDoc pages: parsed in-process with the pure-Java {@code asciidoc-java} parser, which resolves includes while
 * parsing, then rendered to Markdown by {@link MarkdownRenderer}. Source-to-source, never through HTML.</li>
 * </ul>
 * A page whose source cannot be converted gets no twin and a warning; the build never fails because of a twin.
 * Filtering is coarse for now: hidden, derived, and pagination pages are not distinguished from content pages.
 */
public class RoqPluginMarkdownTwinProcessor {

    private static final Logger LOG = Logger.getLogger(RoqPluginMarkdownTwinProcessor.class);
    private static final String FEATURE = "roq-plugin-markdown-twin";

    private static final String MARKDOWN_OPEN = "{#markdown}";
    private static final String MARKDOWN_CLOSE = "{/markdown}";
    private static final String ATTRIBUTES_PREFIX = "quarkus.asciidoc.attributes.";

    @BuildStep
    FeatureBuildItem feature() {
        return new FeatureBuildItem(FEATURE);
    }

    @BuildStep
    void generateTwins(RoqFrontMatterRootUrlBuildItem rootUrl,
            List<RoqFrontMatterPageTemplateBuildItem> templates,
            BuildProducer<RoqFrontMatterStaticFileBuildItem> staticFiles) {
        final Map<String, String> configuredAttributes = configuredAttributes();
        for (RoqFrontMatterPageTemplateBuildItem item : templates) {
            final PageSource source = item.source();
            // Only real content pages that render to HTML; skip the site index.
            if (!source.isTargetHtml() || source.isSiteIndex()) {
                continue;
            }
            // Reuse the existing llms.txt opt-out: `llmstxt: false` suppresses the twin too.
            if (!item.data().getBoolean("llmstxt", true)) {
                continue;
            }
            final String twinPath = twinPath(item.url().resourcePath());
            if (twinPath == null) {
                continue;
            }

            final String markup = source.markup();
            final String markdown;
            if ("markdown".equals(markup)) {
                markdown = extractMarkdownSource(item.raw().generatedTemplate());
            } else if ("asciidoc".equals(markup)) {
                markdown = convertAsciidoc(rootUrl, configuredAttributes, item, source);
            } else {
                // html and anything else: no source to convert.
                continue;
            }

            if (markdown == null || markdown.isBlank()) {
                continue;
            }
            staticFiles.produce(new RoqFrontMatterStaticFileBuildItem(twinPath,
                    markdown.getBytes(StandardCharsets.UTF_8)));
            LOG.infof("Markdown twin: /%s (from %s)", twinPath, markup);
        }
    }

    /**
     * Where a page's twin lives, relative to the site root, or null for the site root itself.
     * <p>
     * A directory-style URL such as {@code docs/basics/} gets {@code docs/basics/index.md}: the llms.txt convention for a
     * URL without a file name, which Cloudflare's docs, with the same URL shape, follow too. A URL with a file name such
     * as {@code 404.html} gets {@code 404.html.md}. Keeping the twin inside the page's directory also stops hosts that
     * resolve an extensionless request to a sibling file, surge.sh for one, from answering {@code /docs/basics} with the
     * twin instead of the page.
     */
    static String twinPath(String resourcePath) {
        if (resourcePath == null || resourcePath.isBlank() || "/".equals(resourcePath)) {
            return null;
        }
        return resourcePath.endsWith("/") ? resourcePath + "index.md" : resourcePath + ".md";
    }

    /**
     * Recover the verbatim Markdown body from the generated Qute template, which wraps it as
     * {@code {#markdown} ... {/markdown}} (optionally inside {@code {| ... |}} escape delimiters). Author-written Qute
     * expressions are left as-is.
     */
    private static String extractMarkdownSource(String generatedTemplate) {
        if (generatedTemplate == null) {
            return null;
        }
        final int start = generatedTemplate.indexOf(MARKDOWN_OPEN);
        final int end = generatedTemplate.lastIndexOf(MARKDOWN_CLOSE);
        if (start < 0 || end < 0 || end <= start) {
            return null;
        }
        String body = generatedTemplate.substring(start + MARKDOWN_OPEN.length(), end).strip();
        if (body.startsWith("{|") && body.endsWith("|}")) {
            body = body.substring(2, body.length() - 2).strip();
        }
        return body;
    }

    /**
     * Convert the AsciiDoc source on disk. The site attributes mirror the ones the AsciidoctorJ plugin injects when it
     * renders the page ({@code site-url}, {@code site-path}, {@code page-url}, {@code page-path}, {@code docname},
     * {@code sitegen}), so references such as {@code {site-url}} resolve the same way in the twin.
     */
    private static String convertAsciidoc(RoqFrontMatterRootUrlBuildItem rootUrl, Map<String, String> configuredAttributes,
            RoqFrontMatterPageTemplateBuildItem item, PageSource source) {
        final Path path = Path.of(source.file().absolutePath());
        final Map<String, String> attributes = new LinkedHashMap<>(configuredAttributes);
        attributes.put("sitegen", "roq");
        attributes.put("docname", removeExtension(path.getFileName().toString()));
        try {
            final RootUrl root = rootUrl == null ? null : rootUrl.rootUrl();
            if (root != null) {
                putIfPresent(attributes, "site-url", root.absolute());
                putIfPresent(attributes, "site-path", root.path());
            }
            final RoqUrl url = item.url();
            if (url != null) {
                putIfPresent(attributes, "page-url", url.absolute());
                putIfPresent(attributes, "page-path", url.path());
            }
        } catch (RuntimeException e) {
            LOG.debugf(e, "Markdown twin: site attributes unavailable for %s", path);
        }
        final String fallbackTitle = item.data() == null ? null : item.data().getString("title");
        return AsciidocMarkdownConverter.convert(path, attributes, fallbackTitle).orElse(null);
    }

    /**
     * The AsciiDoc attributes the site configures for its renderer ({@code quarkus.asciidoc.attributes.*}), so that
     * the twin resolves the same references as the rendered page. Unset markers ({@code "!name"}) are skipped.
     */
    private static Map<String, String> configuredAttributes() {
        final Map<String, String> attributes = new LinkedHashMap<>();
        try {
            final Config config = ConfigProvider.getConfig();
            for (String name : config.getPropertyNames()) {
                if (!name.startsWith(ATTRIBUTES_PREFIX)) {
                    continue;
                }
                String key = name.substring(ATTRIBUTES_PREFIX.length());
                if (key.length() > 1 && key.startsWith("\"") && key.endsWith("\"")) {
                    key = key.substring(1, key.length() - 1);
                }
                if (key.isBlank() || key.startsWith("!")) {
                    continue;
                }
                final String attribute = key;
                config.getOptionalValue(name, String.class).ifPresent(value -> attributes.put(attribute, value));
            }
        } catch (RuntimeException e) {
            LOG.debugf(e, "Markdown twin: could not read the configured AsciiDoc attributes");
        }
        return attributes;
    }

    private static void putIfPresent(Map<String, String> attributes, String key, String value) {
        if (value != null && !value.isBlank()) {
            attributes.put(key, value);
        }
    }
}
