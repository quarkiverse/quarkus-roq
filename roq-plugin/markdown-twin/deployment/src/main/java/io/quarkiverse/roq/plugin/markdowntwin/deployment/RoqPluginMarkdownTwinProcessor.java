package io.quarkiverse.roq.plugin.markdowntwin.deployment;

import static io.quarkiverse.roq.frontmatter.deployment.util.RoqFrontMatterConstants.TEMPLATES_DIR;
import static io.quarkus.qute.deployment.TemplatePathBuildItem.ROOT_ARCHIVE_PRIORITY;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.jboss.logging.Logger;

import io.quarkiverse.roq.LargeRecords;
import io.quarkiverse.roq.deployment.items.LargeRecordBuildItem;
import io.quarkiverse.roq.frontmatter.deployment.items.data.RoqFrontMatterPageTemplateBuildItem;
import io.quarkiverse.roq.frontmatter.deployment.items.data.RoqFrontMatterRootUrlBuildItem;
import io.quarkiverse.roq.frontmatter.deployment.items.data.RoqFrontMatterStaticFileBuildItem;
import io.quarkiverse.roq.frontmatter.deployment.items.data.RoqPageMarkdownBuildItem;
import io.quarkiverse.roq.frontmatter.deployment.items.scan.RoqMarkdownConverterBuildItem;
import io.quarkiverse.roq.frontmatter.deployment.items.scan.RoqMarkdownConverterBuildItem.MarkdownConversion;
import io.quarkiverse.roq.frontmatter.deployment.items.scan.RoqMarkdownConverterBuildItem.MarkdownConverter;
import io.quarkiverse.roq.frontmatter.runtime.RoqFrontMatterKeys;
import io.quarkiverse.roq.frontmatter.runtime.config.RoqSiteConfig;
import io.quarkiverse.roq.frontmatter.runtime.model.PageSource;
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

/**
 * Publishes a Markdown twin of every content page for AI agents (quarkiverse/quarkus-roq#1104), at
 * {@code <page-url>index.md} for a directory-style URL such as {@code /docs/basics/} and at {@code <page-url>.md} for a
 * URL with a file name; see {@link #twinPath(String)}.
 * <p>
 * The plugin decides which pages get a twin and where, and publishes it. The conversion itself belongs to the plugin
 * that owns the page's markup, which contributes it as a {@link RoqMarkdownConverterBuildItem}: the Markdown plugin
 * gives the Markdown source, the AsciiDoc plugins convert the AsciiDoc source with the pure-Java {@code asciidoc-java}
 * parser and its Markdown renderer. A markup without a converter, HTML for one, gets no twin.
 * <p>
 * Each page is converted once, into a {@link RoqPageMarkdownBuildItem} that any build step can consume; from it the
 * plugin produces the static file ({@link RoqFrontMatterStaticFileBuildItem}), which Roq both serves and writes to the
 * generated site. A page whose source cannot be converted gets no twin and a warning from its converter; the build never
 * fails because of a twin. A paginated listing page gets no twin, since its content is the pages it lists. A page
 * scheduled for later, which the hybrid plugin hides until its date, gets no twin either: twins are static files, so it
 * gets one at the first build after its date. Filtering is otherwise coarse: hidden and derived pages are not
 * distinguished from content pages.
 */
public class RoqPluginMarkdownTwinProcessor {

    private static final Logger LOG = Logger.getLogger(RoqPluginMarkdownTwinProcessor.class);
    private static final String FEATURE = "roq-plugin-markdown-twin";

    /**
     * What a cross reference to another page ends with in a twin: the twin of a directory-style URL, where the HTML
     * page ends with {@code /}.
     */
    static final String CROSS_REFERENCE_SUFFIX = "/index.md";

    // the seo tag's hook for alternate representations, overridden with the twin link (same mechanism as og-card)
    private static final String SEO_ALTERNATES_TAG = "tags/seoAlternates.html";
    private static final String SEO_ALTERNATES_OVERRIDE_RESOURCE = "templates/partials/markdown-twin/seoAlternates.html";
    // keeps the twin map out of the recorded class, whose constant pool a large site would overflow
    private static final String TWINS_RESOURCE = "roq-recorded/markdown-twins.ser";

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
            MarkdownTwinConfig config,
            BuildProducer<TemplatePathBuildItem> templatePathProducer,
            BuildProducer<GeneratedResourceBuildItem> generatedResourceProducer,
            BuildProducer<NativeImageResourceBuildItem> nativeImageResourceProducer) throws IOException {
        if (!config.enabled()) {
            // No twins, so nothing to advertise: leave the site's own seoAlternates tag in place.
            return;
        }
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

    /**
     * Converts each page that gets a twin, once, with the converter of its markup. With {@code enabled=false}, it
     * converts nothing, and the {@code include} and {@code exclude} patterns keep pages out before their conversion.
     */
    @BuildStep
    void convertPages(MarkdownTwinConfig twinConfig, RoqSiteConfig siteConfig, RoqFrontMatterRootUrlBuildItem rootUrl,
            List<RoqFrontMatterPageTemplateBuildItem> templates, List<RoqMarkdownConverterBuildItem> converters,
            BuildProducer<RoqPageMarkdownBuildItem> pages) {
        if (!twinConfig.enabled()) {
            return;
        }
        if (converters.isEmpty()) {
            LOG.warn("No Markdown converter is available: add the Markdown or an AsciiDoc plugin to publish twins");
            return;
        }
        final TwinScope scope = TwinScope.of(twinConfig);
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
            // Configured include/exclude patterns, so a large archived tree can be kept out of the build.
            if (!scope.includes(item.url().resourcePath())) {
                continue;
            }
            final String twinPath = twinPath(item.url().resourcePath());
            if (twinPath == null) {
                continue;
            }
            final Optional<MarkdownConverter> converter = RoqMarkdownConverterBuildItem.find(converters, source.markup());
            if (converter.isEmpty()) {
                // html and anything else: no source to convert.
                LOG.debugf("Markdown twin: %s has markup %s, no twin", source.path(), source.markup());
                continue;
            }
            final String siteDirPath = source.file().siteDirPath();
            final Path siteDir = siteDirPath == null || siteDirPath.isBlank() ? null : Path.of(siteDirPath);
            final Optional<String> markdown = converter.get().convert(new MarkdownConversion(
                    Path.of(source.file().absolutePath()), item.raw().generatedTemplate(), siteDir,
                    rootUrl == null ? null : rootUrl.rootUrl(), item.url(), item.data(), CROSS_REFERENCE_SUFFIX));
            if (markdown.isEmpty()) {
                continue;
            }
            if (markdown.get().isBlank()) {
                LOG.warnf("Markdown twin: %s is empty, skipping", source.path());
                continue;
            }
            pages.produce(new RoqPageMarkdownBuildItem(item, twinPath, markdown.get()));
            LOG.debugf("Markdown twin: /%s (from %s)", twinPath, source.markup());
        }
    }

    /**
     * Publishes each converted page as a static file, which Roq serves and writes to the generated site.
     */
    @BuildStep
    void publishTwins(List<RoqPageMarkdownBuildItem> pages, BuildProducer<RoqFrontMatterStaticFileBuildItem> staticFiles) {
        for (RoqPageMarkdownBuildItem page : pages) {
            staticFiles.produce(new RoqFrontMatterStaticFileBuildItem(page.path(),
                    page.markdown().getBytes(StandardCharsets.UTF_8)));
        }
        LOG.infof("Generated %d Markdown twins", pages.size());
    }

    /**
     * Records which pages got a twin, so that only those advertise one. With {@code enabled=false} the map is empty.
     */
    @BuildStep
    @Record(ExecutionTime.STATIC_INIT)
    void recordTwins(MarkdownTwinRecorder recorder, List<RoqPageMarkdownBuildItem> pages,
            BuildProducer<LargeRecordBuildItem> largeRecords) {
        // page resource path -> twin resource path, for the pages that really got a twin
        final Map<String, String> twins = new HashMap<>();
        for (RoqPageMarkdownBuildItem page : pages) {
            twins.put(page.page().url().resourcePath(), page.path());
        }
        largeRecords.produce(new LargeRecordBuildItem(TWINS_RESOURCE, LargeRecords.serialize(twins)));
        recorder.init(TWINS_RESOURCE);
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
}
