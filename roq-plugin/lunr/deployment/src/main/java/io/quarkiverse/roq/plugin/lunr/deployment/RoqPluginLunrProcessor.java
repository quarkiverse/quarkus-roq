package io.quarkiverse.roq.plugin.lunr.deployment;

import static io.quarkiverse.roq.frontmatter.runtime.RoqFrontMatterKeys.NO_INDEX_ALL;
import static io.quarkiverse.roq.plugin.lunr.runtime.RoqLunrKeys.SEARCH;
import static io.quarkiverse.roq.plugin.lunr.runtime.RoqLunrKeys.SEARCH_INDEXES;
import static io.quarkiverse.roq.plugin.lunr.runtime.RoqLunrKeys.SEARCH_INDEX_NAME;
import static io.quarkiverse.tools.stringpaths.StringPaths.removeLeadingSlash;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;

import org.jboss.logging.Logger;

import io.quarkiverse.roq.frontmatter.deployment.items.data.RoqFrontMatterPageTemplateBuildItem;
import io.quarkiverse.roq.frontmatter.deployment.items.data.RoqFrontMatterRootUrlBuildItem;
import io.quarkiverse.roq.frontmatter.deployment.items.publish.RoqFrontMatterPublishNormalPageBuildItem;
import io.quarkiverse.roq.frontmatter.runtime.config.ConfiguredCollection;
import io.quarkiverse.roq.frontmatter.runtime.config.RoqSiteConfig;
import io.quarkiverse.roq.frontmatter.runtime.model.PageFiles;
import io.quarkiverse.roq.frontmatter.runtime.model.PageSource;
import io.quarkiverse.roq.frontmatter.runtime.model.SourceFile;
import io.quarkiverse.roq.frontmatter.runtime.model.TemplateSource;
import io.quarkiverse.roq.plugin.lunr.runtime.RoqLunrIndexNames;
import io.quarkiverse.roq.plugin.lunr.runtime.RoqPluginLunrTemplateExtension;
import io.quarkiverse.tools.stringpaths.StringPaths;
import io.quarkus.arc.deployment.AdditionalBeanBuildItem;
import io.quarkus.deployment.annotations.BuildProducer;
import io.quarkus.deployment.annotations.BuildStep;
import io.quarkus.deployment.builditem.FeatureBuildItem;
import io.quarkus.deployment.builditem.GeneratedResourceBuildItem;
import io.quarkus.deployment.builditem.nativeimage.NativeImageResourceBuildItem;
import io.quarkus.qute.ParserConfig;
import io.quarkus.qute.deployment.TemplatePathBuildItem;
import io.vertx.core.json.JsonObject;

public class RoqPluginLunrProcessor {
    private static final Logger LOG = Logger.getLogger(RoqPluginLunrProcessor.class);
    private static final String FEATURE = "roq-plugin-lunr";

    /**
     * A generated index page renders the same include as a hand-written index file.
     */
    private static final String INDEX_TEMPLATE = "{#include fm/search-index.json /}";
    private static final String DEFAULT_INDEX_PATH = "search-index.json";
    private static final String NAMED_INDEX_DIR = "search-index/";
    /**
     * Generated pages get ids and templates in a namespace no content file can produce.
     */
    private static final String GENERATED_PREFIX = FEATURE + "/";

    @BuildStep
    FeatureBuildItem feature() {
        return new FeatureBuildItem(FEATURE);
    }

    @BuildStep
    AdditionalBeanBuildItem process() {
        return new AdditionalBeanBuildItem(
                RoqPluginLunrTemplateExtension.class);
    }

    /**
     * Publish an index page for every search index in use that no content file provides: {@code default} at
     * {@code /search-index.json} and any other name at {@code /search-index/<name>.json}. An index is provided when
     * a normal page declares its name with {@code search-index-name} (collection documents are not index files, the
     * runtime ignores them), or when a page already serves its path (the unnamed {@code content/search-index.json} is
     * the {@code default} index, any other page there is reported).
     * <p>
     * The names come from the pages' {@code search-indexes}, merged with their layouts (frontmatter step 3), so the
     * index pages are published directly (step 4 input), backed by a template registered by this plugin.
     */
    @BuildStep
    void publishMissingIndexes(
            RoqSiteConfig config,
            RoqFrontMatterRootUrlBuildItem rootUrl,
            List<RoqFrontMatterPageTemplateBuildItem> pages,
            BuildProducer<RoqFrontMatterPublishNormalPageBuildItem> pagesProducer,
            BuildProducer<TemplatePathBuildItem> templatePathProducer,
            BuildProducer<GeneratedResourceBuildItem> generatedResourceProducer,
            BuildProducer<NativeImageResourceBuildItem> nativeImageResourceProducer) {
        if (rootUrl == null || pages.isEmpty()) {
            return;
        }
        final Set<String> declared = new HashSet<>();
        final Map<String, RoqFrontMatterPageTemplateBuildItem> pageByPath = new HashMap<>();
        final SortedSet<String> used = new TreeSet<>();
        used.add(RoqLunrIndexNames.DEFAULT);
        for (RoqFrontMatterPageTemplateBuildItem page : pages) {
            pageByPath.putIfAbsent(page.url().resourcePath(), page);
            final Object name = page.data().getValue(SEARCH_INDEX_NAME);
            if (name != null && page.raw().collection() == null) {
                declared.add(name.toString());
            }
            if (!searchable(page)) {
                continue;
            }
            for (String index : RoqLunrIndexNames.membership(page.data().getValue(SEARCH_INDEXES))) {
                if (RoqLunrIndexNames.isValidName(index)) {
                    used.add(index);
                } else {
                    LOG.warnf(
                            "Invalid search index name '%s' in '%s' of '%s': use letters, digits, '-' or '_' ('%s' is reserved), no index is generated for it",
                            index, SEARCH_INDEXES, page.source().path(), RoqLunrIndexNames.ALL);
                }
            }
        }
        // the generated file does not exist, its source file only locates it in the site for messages
        final String siteDirPath = pages.get(0).source().file().siteDirPath();
        for (String name : used) {
            if (declared.contains(name)) {
                continue;
            }
            final String relativePath = RoqLunrIndexNames.DEFAULT.equals(name) ? DEFAULT_INDEX_PATH
                    : NAMED_INDEX_DIR + name + ".json";
            final String path = removeLeadingSlash(StringPaths.join(config.pathPrefixOrEmpty(), relativePath));
            final RoqFrontMatterPageTemplateBuildItem existing = pageByPath.get(path);
            if (existing != null) {
                // the unnamed content/search-index.json is the default index, anything else at an index path hides it
                final boolean unnamedDefault = RoqLunrIndexNames.DEFAULT.equals(name)
                        && existing.data().getValue(SEARCH_INDEX_NAME) == null;
                if (!unnamedDefault) {
                    LOG.warnf(
                            "Search index '%s' is not generated: '%s' is already at '%s' without declaring '%s: %s'",
                            name, existing.source().path(), path, SEARCH_INDEX_NAME, name);
                }
                continue;
            }
            final String generatedId = GENERATED_PREFIX + relativePath;
            final TemplateSource templateSource = TemplateSource.create(generatedId, null,
                    new SourceFile(siteDirPath, generatedId), generatedId, generatedId,
                    false, false, false, false);
            final String templateId = templateSource.generatedQuteTemplateId();
            final String resourceName = "templates/" + templateId;
            generatedResourceProducer
                    .produce(new GeneratedResourceBuildItem(resourceName, INDEX_TEMPLATE.getBytes(StandardCharsets.UTF_8)));
            nativeImageResourceProducer.produce(new NativeImageResourceBuildItem(resourceName));
            templatePathProducer.produce(TemplatePathBuildItem.builder()
                    .path(templateId)
                    .content(INDEX_TEMPLATE)
                    .parserConfig(ParserConfig.DEFAULT)
                    .extensionInfo(FEATURE)
                    .build());
            final PageSource pageSource = new PageSource(templateSource, false, null, PageFiles.empty(), true);
            final JsonObject data = new JsonObject().put(SEARCH_INDEX_NAME, name);
            pagesProducer.produce(
                    new RoqFrontMatterPublishNormalPageBuildItem(rootUrl.rootUrl().resolve(path), pageSource, data, null));
            LOG.debugf("Generated search index '%s' at '%s'", name, path);
        }
    }

    /**
     * Same rule as {@code RoqPluginLunrTemplateExtension#search(Page)}, plus the hidden collections skipped by the
     * index rendering.
     */
    private static boolean searchable(RoqFrontMatterPageTemplateBuildItem page) {
        final ConfiguredCollection collection = page.raw().collection();
        if (collection != null && collection.hidden()) {
            return false;
        }
        return page.source().isTargetHtml()
                && page.data().getBoolean(SEARCH, !page.data().getBoolean(NO_INDEX_ALL, false));
    }

}
