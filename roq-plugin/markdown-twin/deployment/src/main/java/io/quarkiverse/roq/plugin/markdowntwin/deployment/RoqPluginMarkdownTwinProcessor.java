package io.quarkiverse.roq.plugin.markdowntwin.deployment;

import static io.quarkiverse.tools.stringpaths.StringPaths.removeExtension;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

import org.eclipse.microprofile.config.Config;
import org.eclipse.microprofile.config.ConfigProvider;
import org.jboss.logging.Logger;

import io.quarkiverse.roq.frontmatter.deployment.items.data.RoqFrontMatterPageTemplateBuildItem;
import io.quarkiverse.roq.frontmatter.deployment.items.data.RoqFrontMatterRootUrlBuildItem;
import io.quarkiverse.roq.frontmatter.deployment.items.data.RoqFrontMatterStaticFileBuildItem;
import io.quarkiverse.roq.frontmatter.runtime.RoqFrontMatterKeys;
import io.quarkiverse.roq.frontmatter.runtime.config.RoqSiteConfig;
import io.quarkiverse.roq.frontmatter.runtime.model.PageSource;
import io.quarkiverse.roq.frontmatter.runtime.model.RootUrl;
import io.quarkiverse.roq.frontmatter.runtime.model.RoqUrl;
import io.quarkiverse.roq.frontmatter.runtime.utils.FuturePages;
import io.quarkiverse.roq.plugin.markdowntwin.runtime.RoqMarkdownTwinKeys;
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
 * <li>Markdown pages: the Markdown source, with the front matter title as its heading when the source has none and
 * without the Qute {@code {|...|}} escape delimiters, which the rendered page does not show either.</li>
 * <li>AsciiDoc pages: parsed in-process with the pure-Java {@code asciidoc-java} parser, which resolves includes while
 * parsing, then rendered to Markdown by its {@code GithubFlavoredMarkdownRenderer}, see
 * {@link AsciidocMarkdownConverter}. Source-to-source, never through HTML.</li>
 * </ul>
 * A page whose source cannot be converted gets no twin and a warning; the build never fails because of a twin. A page
 * scheduled for later, which the hybrid plugin hides until its date, gets no twin either. Filtering is otherwise coarse
 * for now: hidden, derived, and pagination pages are not distinguished from content pages.
 */
public class RoqPluginMarkdownTwinProcessor {

    private static final Logger LOG = Logger.getLogger(RoqPluginMarkdownTwinProcessor.class);
    private static final String FEATURE = "roq-plugin-markdown-twin";

    private static final String MARKDOWN_OPEN = "{#markdown}";
    private static final String MARKDOWN_CLOSE = "{/markdown}";
    private static final String ESCAPE_OPEN = "{|";
    private static final String ESCAPE_CLOSE = "|}";
    private static final String ATTRIBUTES_PREFIX = "quarkus.asciidoc.attributes.";
    private static final String SAFE_MODE = "quarkus.asciidoc.safe-mode";
    private static final String TRUE = Boolean.TRUE.toString();

    @BuildStep
    FeatureBuildItem feature() {
        return new FeatureBuildItem(FEATURE);
    }

    @BuildStep
    void generateTwins(RoqSiteConfig siteConfig, RoqFrontMatterRootUrlBuildItem rootUrl,
            List<RoqFrontMatterPageTemplateBuildItem> templates,
            BuildProducer<RoqFrontMatterStaticFileBuildItem> staticFiles) {
        final Config config = ConfigProvider.getConfig();
        final Map<String, String> siteAttributes = configuredAttributes(config);
        siteAttributes.putIfAbsent("sitegen", "roq");
        final AsciidocMarkdownConverter converter = new AsciidocMarkdownConverter(siteAttributes, includes(config));
        int twins = 0;
        for (RoqFrontMatterPageTemplateBuildItem item : templates) {
            final PageSource source = item.source();
            // Only real content pages that render to HTML; skip the site index.
            if (!source.isTargetHtml() || source.isSiteIndex()) {
                continue;
            }
            // `mdtwin: false` opts the page out, and so does `no-index-all: true` unless the page also sets `mdtwin: true`.
            final boolean noIndexAll = item.data().getBoolean(RoqFrontMatterKeys.NO_INDEX_ALL, false);
            if (!item.data().getBoolean(RoqMarkdownTwinKeys.MDTWIN, !noIndexAll)) {
                continue;
            }
            // A twin is a static file, served whatever the date, so a page the hybrid plugin keeps for its date gets none.
            if (FuturePages.isFutureDateEnforced(siteConfig, item.raw().collection(), source.date())) {
                continue;
            }
            final String twinPath = twinPath(item.url().resourcePath());
            if (twinPath == null) {
                continue;
            }

            final String markup = source.markup();
            final String title = item.data() == null ? null : item.data().getString("title");
            final String markdown;
            if ("markdown".equals(markup)) {
                markdown = withTitle(extractMarkdownSource(item.raw().generatedTemplate()), title);
            } else if ("asciidoc".equals(markup)) {
                markdown = convertAsciidoc(converter, rootUrl, item, source, title);
            } else {
                // html and anything else: no source to convert.
                continue;
            }

            if (markdown == null || markdown.isBlank()) {
                continue;
            }
            staticFiles.produce(new RoqFrontMatterStaticFileBuildItem(twinPath,
                    markdown.getBytes(StandardCharsets.UTF_8)));
            twins++;
            LOG.debugf("Markdown twin: /%s (from %s)", twinPath, markup);
        }
        LOG.infof("Generated %d Markdown twins", twins);
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
    String twinPath(String resourcePath) {
        if (resourcePath == null || resourcePath.isBlank() || "/".equals(resourcePath)) {
            return null;
        }
        return resourcePath.endsWith("/") ? resourcePath + "index.md" : resourcePath + ".md";
    }

    /**
     * Recover the Markdown body from the generated Qute template, which wraps it as {@code {#markdown}}, a line feed,
     * the body (inside {@code {| ... |}} escape delimiters unless the page is a Qute template), a line feed and
     * {@code {/markdown}}. Only those wrapper characters go, so the body keeps its own indentation; it ends with one line
     * feed, as an AsciiDoc twin does. The author's own {@code {| ... |}} delimiters are removed, as Qute removes them
     * from the page; other author-written Qute expressions are left as-is.
     */
    String extractMarkdownSource(String generatedTemplate) {
        if (generatedTemplate == null) {
            return null;
        }
        final int start = generatedTemplate.indexOf(MARKDOWN_OPEN);
        final int end = generatedTemplate.lastIndexOf(MARKDOWN_CLOSE);
        if (start < 0 || end < 0 || end <= start) {
            return null;
        }
        String body = unwrap(generatedTemplate.substring(start + MARKDOWN_OPEN.length(), end), "\n", "\n");
        body = unwrap(body, ESCAPE_OPEN, ESCAPE_CLOSE);
        body = unescape(body);
        return body.endsWith("\n") ? body : body + "\n";
    }

    private String unwrap(String text, String prefix, String suffix) {
        if (text.startsWith(prefix) && text.endsWith(suffix) && text.length() >= prefix.length() + suffix.length()) {
            return text.substring(prefix.length(), text.length() - suffix.length());
        }
        return text;
    }

    /** Removes each {@code {| ... |}} pair, keeping its content; an unclosed {@code {|} stays as written. */
    String unescape(String body) {
        if (!body.contains(ESCAPE_OPEN)) {
            return body;
        }
        final StringBuilder result = new StringBuilder(body.length());
        int from = 0;
        while (true) {
            final int open = body.indexOf(ESCAPE_OPEN, from);
            final int close = open < 0 ? -1 : body.indexOf(ESCAPE_CLOSE, open + ESCAPE_OPEN.length());
            if (close < 0) {
                result.append(body, from, body.length());
                return result.toString();
            }
            result.append(body, from, open).append(body, open + ESCAPE_OPEN.length(), close);
            from = close + ESCAPE_CLOSE.length();
        }
    }

    /**
     * A Markdown page keeps its title in the front matter, which the twin drops, so the twin opens with the title as a
     * level-one heading unless the source already has one, written as {@code # Title} or underlined with {@code =}; an
     * AsciiDoc twin gets the same fallback from the converter.
     */
    String withTitle(String markdown, String title) {
        if (markdown == null || title == null || title.isBlank() || hasLevelOneHeading(markdown)) {
            return markdown;
        }
        int body = 0;
        while (body < markdown.length() && markdown.charAt(body) == '\n') {
            body++;
        }
        return "# " + title.strip() + "\n\n" + markdown.substring(body);
    }

    private boolean hasLevelOneHeading(String markdown) {
        String previous = "";
        for (String line : markdown.split("\n")) {
            if (line.startsWith("# ") || (!previous.isBlank() && !line.isEmpty() && line.chars().allMatch(c -> c == '='))) {
                return true;
            }
            previous = line;
        }
        return false;
    }

    /**
     * Convert the AsciiDoc source on disk. The page attributes mirror the ones the AsciidoctorJ plugin injects when it
     * renders the page ({@code site-url}, {@code site-path}, {@code page-url}, {@code page-path}, {@code docname} and
     * {@code relfileprefix}), so references such as {@code {site-url}} and cross references resolve the same way in
     * the twin.
     */
    private String convertAsciidoc(AsciidocMarkdownConverter converter, RoqFrontMatterRootUrlBuildItem rootUrl,
            RoqFrontMatterPageTemplateBuildItem item, PageSource source, String fallbackTitle) {
        final Path path = Path.of(source.file().absolutePath());
        final Path siteDir = source.file().siteDirPath() == null ? null : Path.of(source.file().siteDirPath());
        final Map<String, String> attributes = new LinkedHashMap<>();
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
                // Cross references are site-absolute, as on the HTML page (quarkiverse/quarkus-roq#1158): a relative
                // link breaks when the twin is read at an address without a trailing slash.
                putIfPresent(attributes, "relfileprefix", RoqUrl.parentPath(url.absolute()));
            }
        } catch (RuntimeException e) {
            LOG.debugf(e, "Markdown twin: site attributes unavailable for %s", path);
        }
        return converter.convert(path, siteDir, attributes, fallbackTitle).orElse(null);
    }

    /**
     * The AsciiDoc attributes the site configures for its renderer ({@code quarkus.asciidoc.attributes.*}), so that
     * the twin resolves the same references as the rendered page. Unset markers ({@code "!name"}) are skipped, and
     * {@code true} becomes an empty value, as the AsciidoctorJ plugin does, because SmallRye Config cannot hold an
     * empty map value for an attribute that is only set.
     */
    Map<String, String> configuredAttributes(Config config) {
        final Map<String, String> attributes = new LinkedHashMap<>();
        try {
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
                config.getOptionalValue(name, String.class)
                        .ifPresent(value -> attributes.put(attribute, TRUE.equals(value) ? "" : value));
            }
        } catch (RuntimeException e) {
            LOG.debugf(e, "Markdown twin: could not read the configured AsciiDoc attributes");
        }
        return attributes;
    }

    /**
     * Which files an include may read, from the safe mode the site renders its AsciiDoc pages with
     * ({@code quarkus.asciidoc.safe-mode}, default {@code SAFE}), so that a twin never publishes what the page refuses
     * to show.
     */
    private AsciidocMarkdownConverter.Includes includes(Config config) {
        final Optional<String> mode = config.getOptionalValue(SAFE_MODE, String.class)
                .map(value -> value.strip().toLowerCase(Locale.ROOT));
        if (mode.isPresent() && "unsafe".equals(mode.get())) {
            return AsciidocMarkdownConverter.Includes.ANY;
        }
        if (mode.isPresent() && "secure".equals(mode.get())) {
            return AsciidocMarkdownConverter.Includes.NONE;
        }
        return AsciidocMarkdownConverter.Includes.SITE;
    }

    private void putIfPresent(Map<String, String> attributes, String key, String value) {
        if (value != null && !value.isBlank()) {
            attributes.put(key, value);
        }
    }
}
