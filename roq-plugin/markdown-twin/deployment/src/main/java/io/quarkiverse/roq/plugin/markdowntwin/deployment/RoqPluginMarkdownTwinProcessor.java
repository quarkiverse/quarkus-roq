package io.quarkiverse.roq.plugin.markdowntwin.deployment;

import static io.quarkiverse.roq.frontmatter.deployment.util.RoqFrontMatterConstants.TEMPLATES_DIR;
import static io.quarkiverse.tools.stringpaths.StringPaths.removeExtension;
import static io.quarkus.qute.deployment.TemplatePathBuildItem.ROOT_ARCHIVE_PRIORITY;

import java.io.IOException;
import java.io.InputStream;
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
import io.quarkiverse.roq.frontmatter.deployment.items.scan.RoqFrontMatterQuteMarkupBuildItem;
import io.quarkiverse.roq.frontmatter.deployment.items.scan.RoqFrontMatterQuteMarkupBuildItem.QuteMarkupSection;
import io.quarkiverse.roq.frontmatter.runtime.RoqFrontMatterKeys;
import io.quarkiverse.roq.frontmatter.runtime.config.RoqSiteConfig;
import io.quarkiverse.roq.frontmatter.runtime.model.PageSource;
import io.quarkiverse.roq.frontmatter.runtime.model.RootUrl;
import io.quarkiverse.roq.frontmatter.runtime.model.RoqUrl;
import io.quarkiverse.roq.frontmatter.runtime.utils.FuturePages;
import io.quarkiverse.roq.plugin.markdowntwin.runtime.MarkdownTwinRecorder;
import io.quarkiverse.roq.plugin.markdowntwin.runtime.MarkdownTwinTemplateExtension;
import io.quarkiverse.roq.plugin.markdowntwin.runtime.RoqMarkdownTwinKeys;
import io.quarkus.arc.deployment.AdditionalBeanBuildItem;
import io.quarkus.deployment.annotations.BuildProducer;
import io.quarkus.deployment.annotations.BuildStep;
import io.quarkus.deployment.annotations.ExecutionTime;
import io.quarkus.deployment.annotations.Record;
import io.quarkus.deployment.builditem.FeatureBuildItem;
import io.quarkus.deployment.builditem.GeneratedResourceBuildItem;
import io.quarkus.deployment.builditem.nativeimage.NativeImageResourceBuildItem;
import io.quarkus.qute.ParserConfig;
import io.quarkus.qute.deployment.TemplatePathBuildItem;
import io.vertx.core.json.JsonObject;

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
 * A page whose source cannot be converted gets no twin and a warning; the build never fails because of a twin. A
 * paginated listing page gets no twin, since its content is the pages it lists. A page scheduled for later, which the hybrid
 * plugin hides until its date, gets no twin either: twins are static files, so it gets one at the first build after its
 * date. Filtering is otherwise coarse: hidden and derived pages are not distinguished from content pages.
 */
public class RoqPluginMarkdownTwinProcessor {

    private static final Logger LOG = Logger.getLogger(RoqPluginMarkdownTwinProcessor.class);
    private static final String FEATURE = "roq-plugin-markdown-twin";

    private static final String MARKDOWN_MARKUP = "markdown";
    // the escape delimiters of RoqFrontMatterTemplateUtils, which wraps an escaped page body in them
    private static final String ESCAPE_OPEN = "{|";
    private static final String ESCAPE_CLOSE = "|}";
    private static final String ATTRIBUTES_PREFIX = "quarkus.asciidoc.attributes.";
    // RoqAsciidocKeys.ASCIIDOC_ATTRIBUTES of asciidoc-common, which this plugin does not depend on
    private static final String ASCIIDOC_ATTRIBUTES = "asciidoc-attributes";
    private static final String SAFE_MODE = "quarkus.asciidoc.safe-mode";
    private static final String TRUE = Boolean.TRUE.toString();
    // the seo tag's hook for alternate representations, overridden with the twin link (same mechanism as og-card)
    private static final String SEO_ALTERNATES_TAG = "tags/seoAlternates.html";
    private static final String SEO_ALTERNATES_OVERRIDE_RESOURCE = "templates/partials/markdown-twin/seoAlternates.html";

    @BuildStep
    FeatureBuildItem feature() {
        return new FeatureBuildItem(FEATURE);
    }

    @BuildStep
    AdditionalBeanBuildItem templateExtension() {
        return new AdditionalBeanBuildItem(MarkdownTwinTemplateExtension.class);
    }

    /**
     * Replaces the seo tag's {@code seoAlternates} hook with the twin link, which renders only for pages recorded in
     * {@code MarkdownTwins}, so a page whose twin was not generated never advertises one.
     */
    @BuildStep
    void registerSeoAlternatesOverride(
            BuildProducer<TemplatePathBuildItem> templatePathProducer,
            BuildProducer<GeneratedResourceBuildItem> generatedResourceProducer,
            BuildProducer<NativeImageResourceBuildItem> nativeImageResourceProducer) throws IOException {
        try (InputStream in = Thread.currentThread().getContextClassLoader()
                .getResourceAsStream(SEO_ALTERNATES_OVERRIDE_RESOURCE)) {
            if (in == null) {
                throw new IllegalStateException("Missing seoAlternates override template: " + SEO_ALTERNATES_OVERRIDE_RESOURCE);
            }
            final String content = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            final String targetPath = TEMPLATES_DIR + "/" + SEO_ALTERNATES_TAG;
            generatedResourceProducer.produce(new GeneratedResourceBuildItem(targetPath,
                    content.getBytes(StandardCharsets.UTF_8)));
            nativeImageResourceProducer.produce(new NativeImageResourceBuildItem(targetPath));
            templatePathProducer.produce(TemplatePathBuildItem.builder()
                    .priority(ROOT_ARCHIVE_PRIORITY + 10)
                    .path(SEO_ALTERNATES_TAG)
                    .content(content)
                    .parserConfig(ParserConfig.DEFAULT)
                    .extensionInfo(FEATURE)
                    .build());
        }
    }

    @BuildStep
    @Record(ExecutionTime.STATIC_INIT)
    void generateTwins(MarkdownTwinRecorder recorder, RoqSiteConfig siteConfig, RoqFrontMatterRootUrlBuildItem rootUrl,
            List<RoqFrontMatterPageTemplateBuildItem> templates, List<RoqFrontMatterQuteMarkupBuildItem> markups,
            BuildProducer<RoqFrontMatterStaticFileBuildItem> staticFiles) {
        final QuteMarkupSection markdownSection = markupSection(markups, MARKDOWN_MARKUP);
        final Config config = ConfigProvider.getConfig();
        final Map<String, String> siteAttributes = configuredAttributes(config);
        siteAttributes.putIfAbsent("sitegen", "roq");
        final AsciidocMarkdownConverter converter = new AsciidocMarkdownConverter(siteAttributes, includes(config));
        // page resource path -> twin resource path, for the pages that really got a twin
        final Map<String, String> twins = new LinkedHashMap<>();
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
            // A paginated page lists other pages; its source is a Qute loop, not content.
            final Object paginate = item.data().getValue(RoqFrontMatterKeys.PAGINATE);
            if (paginate != null && !Boolean.FALSE.equals(paginate)) {
                LOG.debugf("Markdown twin: %s is a paginated listing, no twin", source.path());
                continue;
            }
            final String twinPath = twinPath(item.url().resourcePath());
            if (twinPath == null) {
                continue;
            }

            final String markup = source.markup();
            final String title = item.data().getString("title");
            final String markdown;
            if (MARKDOWN_MARKUP.equals(markup)) {
                if (markdownSection == null) {
                    LOG.warnf("Markdown twin: no %s markup is registered, skipping %s", MARKDOWN_MARKUP, source.path());
                    continue;
                }
                markdown = withTitle(extractMarkdownSource(item.raw().generatedTemplate(), markdownSection), title);
                if (markdown == null) {
                    LOG.warnf("Markdown twin: the generated template of %s has no %s section (a page made by a plugin),"
                            + " skipping", source.path(), markdownSection.open());
                    continue;
                }
            } else if ("asciidoc".equals(markup)) {
                markdown = convertAsciidoc(converter, rootUrl, item, source, title);
            } else {
                // html and anything else: no source to convert.
                LOG.debugf("Markdown twin: %s has markup %s, no twin", source.path(), markup);
                continue;
            }

            if (markdown == null || markdown.isBlank()) {
                if (markdown != null) {
                    LOG.warnf("Markdown twin: %s is empty, skipping", source.path());
                }
                continue;
            }
            staticFiles.produce(new RoqFrontMatterStaticFileBuildItem(twinPath,
                    markdown.getBytes(StandardCharsets.UTF_8)));
            twins.put(item.url().resourcePath(), twinPath);
            LOG.debugf("Markdown twin: /%s (from %s)", twinPath, markup);
        }
        LOG.infof("Generated %d Markdown twins", twins.size());
        recorder.init(twins);
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
     * The Qute section a markup wraps its pages in, from the plugin that registered the markup; null when no plugin did.
     */
    QuteMarkupSection markupSection(List<RoqFrontMatterQuteMarkupBuildItem> markups, String name) {
        for (RoqFrontMatterQuteMarkupBuildItem markup : markups) {
            if (name.equals(markup.name())) {
                return markup.markupSection();
            }
        }
        return null;
    }

    /**
     * Recover the Markdown body from the generated Qute template, which wraps it in the markup's section ({@code
     * {#markdown}}, a line feed, the body, a line feed, {@code {/markdown}}), the body itself inside {@code {| ... |}}
     * escape delimiters when the page sets {@code escape: true}. Only those wrapper characters go, so the body keeps
     * its own indentation; it ends with one line feed, as an AsciiDoc twin does. The author's own {@code {| ... |}}
     * delimiters are removed, as Qute removes them from the page; other Qute expressions the author wrote stay as
     * written, since a Markdown page is a Qute template by default. A source saved with CRLF line endings gets a twin
     * with line feeds, as an AsciiDoc twin has. Null when the template has no such section.
     */
    String extractMarkdownSource(String generatedTemplate, QuteMarkupSection section) {
        if (generatedTemplate == null || section == null) {
            return null;
        }
        final String template = generatedTemplate.replace("\r\n", "\n");
        final int start = template.indexOf(section.open());
        final int end = template.lastIndexOf(section.close());
        if (start < 0 || end < 0 || end <= start) {
            return null;
        }
        final String inner = template.substring(start + section.open().length(), end);
        final String wrapped = unwrap(inner, "\n", "\n");
        final String body = unwrap(wrapped, ESCAPE_OPEN, ESCAPE_CLOSE);
        final String unescaped = unescape(body);
        return unescaped.endsWith("\n") ? unescaped : unescaped + "\n";
    }

    /** The text between the prefix and the suffix, or the text itself when it does not carry both. */
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

    /**
     * Whether the Markdown has a level-one heading, as CommonMark reads one: {@code # Title} or a paragraph line
     * underlined with {@code =}, indented by at most three spaces. A line inside a fenced code block is code, not a
     * heading, so a {@code # comment} in a shell or YAML block does not give the page its title; nor is a line indented
     * by four spaces or a tab, which is an indented code block, and a {@code =} line after a list item or a code line
     * underlines nothing.
     */
    private boolean hasLevelOneHeading(String markdown) {
        String previous = "";
        String fence = null;
        for (String line : markdown.split("\n")) {
            final String marker = fenceMarker(line);
            if (fence != null) {
                if (marker != null && marker.charAt(0) == fence.charAt(0) && marker.length() >= fence.length()
                        && line.strip().equals(marker)) {
                    fence = null;
                }
                previous = "";
                continue;
            }
            if (marker != null) {
                fence = marker;
                previous = "";
                continue;
            }
            if (indentation(line) < 4) {
                final String text = line.strip();
                if (text.equals("#") || text.startsWith("# ") || text.startsWith("#\t")
                        || (isParagraphLine(previous) && !text.isEmpty() && text.chars().allMatch(c -> c == '='))) {
                    return true;
                }
            }
            previous = line;
        }
        return false;
    }

    /**
     * The run of three or more backticks or tildes, indented by at most three spaces, that opens or closes a fence. A
     * backtick run followed by text holding a backtick is inline code, not a fence.
     */
    private String fenceMarker(String line) {
        final int start = indentation(line);
        if (start > 3 || start >= line.length() || (line.charAt(start) != '`' && line.charAt(start) != '~')) {
            return null;
        }
        int end = start;
        while (end < line.length() && line.charAt(end) == line.charAt(start)) {
            end++;
        }
        if (end - start < 3 || (line.charAt(start) == '`' && line.indexOf('`', end) >= 0)) {
            return null;
        }
        return line.substring(start, end);
    }

    /**
     * The indentation in columns, a tab reaching the next multiple of four as CommonMark counts it, so that a
     * tab-indented line is an indented code block.
     */
    private int indentation(String line) {
        int columns = 0;
        for (int i = 0; i < line.length(); i++) {
            final char c = line.charAt(i);
            if (c == ' ') {
                columns++;
            } else if (c == '\t') {
                columns += 4 - columns % 4;
            } else {
                break;
            }
        }
        return columns;
    }

    /**
     * Whether a line can be the paragraph a {@code =} line underlines: text, not blank, not indented code, not a
     * heading, not a list item ({@code -}, {@code *}, {@code +} or a number followed by {@code .} or {@code )}).
     */
    private boolean isParagraphLine(String line) {
        if (line.isBlank() || indentation(line) >= 4) {
            return false;
        }
        final String text = line.strip();
        if (text.startsWith("#") || text.startsWith(">")) {
            return false;
        }
        if ((text.startsWith("-") || text.startsWith("*") || text.startsWith("+"))
                && (text.length() == 1 || text.charAt(1) == ' ' || text.charAt(1) == '\t')) {
            return false;
        }
        int digits = 0;
        while (digits < text.length() && Character.isDigit(text.charAt(digits))) {
            digits++;
        }
        if (digits > 0 && digits < text.length() && (text.charAt(digits) == '.' || text.charAt(digits) == ')')
                && (digits + 1 == text.length() || text.charAt(digits + 1) == ' ')) {
            return false;
        }
        return true;
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
        final String siteDirPath = source.file().siteDirPath();
        final Path siteDir = siteDirPath == null || siteDirPath.isBlank() ? null : Path.of(siteDirPath);
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
            // Without them the cross references fall back to relative links, which break at an address without a
            // trailing slash (quarkiverse/quarkus-roq#1158), so the author should know.
            LOG.warnf(e, "Markdown twin: site attributes unavailable for %s, cross references stay relative", path);
        }
        return converter.convert(path, siteDir, attributes, frontMatterAttributes(item.data()), fallbackTitle).orElse(null);
    }

    /**
     * The page's {@code asciidoc-attributes} front matter map, which the AsciiDoc plugins hand to the renderer after the
     * site attributes, so that a page attribute wins over a site attribute of the same name. Values are written as text
     * from the raw map, as {@code AsciidocTemplateExtension} does for the page.
     */
    Map<String, String> frontMatterAttributes(JsonObject data) {
        final Map<String, String> attributes = new LinkedHashMap<>();
        if (data.getValue(ASCIIDOC_ATTRIBUTES) instanceof JsonObject map) {
            for (Map.Entry<String, Object> entry : map.getMap().entrySet()) {
                if (entry.getValue() != null) {
                    attributes.put(entry.getKey(), entry.getValue().toString());
                }
            }
        }
        return attributes;
    }

    /**
     * The AsciiDoc attributes the site configures for its renderer ({@code quarkus.asciidoc.attributes.*}), so that
     * the twin resolves the same references as the rendered page. {@code true} becomes an empty value, as the
     * AsciidoctorJ plugin does, because SmallRye Config cannot hold an empty map value for an attribute that is only
     * set. An unset marker ({@code "!name"}) is kept as written: the converter applies it as Asciidoctor does, and
     * unsets the attribute.
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
                if (key.isBlank()) {
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
