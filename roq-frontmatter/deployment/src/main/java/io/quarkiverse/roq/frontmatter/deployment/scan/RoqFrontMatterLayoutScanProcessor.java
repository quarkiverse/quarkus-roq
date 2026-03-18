package io.quarkiverse.roq.frontmatter.deployment.scan;

import static io.quarkiverse.roq.frontmatter.deployment.scan.RoqFrontMatterQuteMarkupBuildItem.findMarkupFilter;
import static io.quarkiverse.roq.frontmatter.deployment.scan.RoqFrontMatterScanUtils.*;
import static io.quarkiverse.roq.frontmatter.runtime.RoqTemplates.LAYOUTS_DIR;
import static io.quarkiverse.roq.frontmatter.runtime.RoqTemplates.THEME_LAYOUTS_DIR_PREFIX;
import static io.quarkiverse.tools.stringpaths.StringPaths.removeExtension;
import static io.quarkiverse.tools.stringpaths.StringPaths.toUnixPath;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.jboss.logging.Logger;

import io.quarkiverse.roq.deployment.items.RoqProjectBuildItem;
import io.quarkiverse.roq.frontmatter.deployment.data.RoqFrontMatterDataModificationBuildItem;
import io.quarkiverse.roq.frontmatter.deployment.data.RoqFrontMatterDataModificationBuildItem.SourceData;
import io.quarkiverse.roq.frontmatter.deployment.scan.RoqFrontMatterQuteMarkupBuildItem.WrapperFilter;
import io.quarkiverse.roq.frontmatter.deployment.scan.RoqFrontMatterRawTemplateBuildItem.TemplateType;
import io.quarkiverse.roq.frontmatter.runtime.config.RoqSiteConfig;
import io.quarkiverse.roq.frontmatter.runtime.model.SourceFile;
import io.quarkiverse.roq.frontmatter.runtime.model.TemplateSource;
import io.quarkiverse.tools.projectscanner.ProjectFile;
import io.quarkiverse.tools.projectscanner.ProjectScannerBuildItem;
import io.quarkiverse.tools.projectscanner.ProjectScannerLocalDirBuildItem;
import io.quarkiverse.tools.projectscanner.ScanQueryBuilder;
import io.quarkus.deployment.annotations.BuildProducer;
import io.quarkus.deployment.annotations.BuildStep;
import io.quarkus.deployment.builditem.HotDeploymentWatchedFileBuildItem;
import io.vertx.core.json.JsonObject;

public class RoqFrontMatterLayoutScanProcessor {

    private static final Logger LOGGER = Logger.getLogger(RoqFrontMatterLayoutScanProcessor.class);
    private static final String LAYOUT_KEY = "layout";

    @BuildStep
    void scanTemplatesDir(BuildProducer<ProjectScannerLocalDirBuildItem> scanLocalDirProducer,
            RoqProjectBuildItem roqProject) {
        roqProject.addScannerForLocalRoqDir(scanLocalDirProducer, TEMPLATES_DIR);
    }

    @BuildStep
    void scanLayouts(RoqProjectBuildItem roqProject,
            ProjectScannerBuildItem scanner,
            RoqSiteConfig config,
            List<RoqFrontMatterQuteMarkupBuildItem> markupList,
            List<RoqFrontMatterHeaderParserBuildItem> headerParserList,
            List<RoqFrontMatterDataModificationBuildItem> dataModifications,
            BuildProducer<RoqFrontMatterRawTemplateBuildItem> rawTemplateProducer,
            BuildProducer<HotDeploymentWatchedFileBuildItem> watch) throws IOException {
        if (!roqProject.isActive()) {
            return;
        }

        final List<RoqFrontMatterDataModificationBuildItem> sortedModifications = dataModifications.stream()
                .sorted(Comparator.comparing(RoqFrontMatterDataModificationBuildItem::order))
                .toList();

        final String siteDirPath = roqProject.local() != null
                ? toUnixPath(roqProject.local().roqDir().normalize().toAbsolutePath().toString())
                : null;

        // Scan regular layouts
        List<ProjectFile> layoutFiles = scanner.query()
                .scopeDir(TEMPLATES_DIR)
                .matchingGlob(LAYOUTS_DIR + "/**")
                .matching(buildHtmlTemplateGlob())
                .list();

        // Query roqResourceDir/templates for non-root resources
        if (!roqProject.isRoqResourcesInRoot()) {
            List<ProjectFile> roqResourceLayouts = scanner.query()
                    .scopeDir(roqProject.resolveRoqResourceSubDir(TEMPLATES_DIR))
                    .origin(ProjectFile.Origin.APPLICATION_RESOURCE, ProjectFile.Origin.DEPENDENCY_RESOURCE)
                    .matchingGlob(LAYOUTS_DIR + "/**")
                    .matching(buildHtmlTemplateGlob())
                    .list();
            layoutFiles = ScanQueryBuilder.mergeByScopedPath(layoutFiles, roqResourceLayouts);
        }

        Set<String> layoutIds = new HashSet<>();
        for (ProjectFile file : layoutFiles) {
            String referencePath = toUnixPath(file.scopedPath());
            if (!isTemplateTargetHtml(referencePath)) {
                continue;
            }
            String fullRelativePath = toUnixPath(file.indexPath());
            String fullContent = new String(file.content(), file.charset());

            RoqFrontMatterRawTemplateBuildItem item = createLayoutTemplate(
                    siteDirPath,
                    fullRelativePath,
                    referencePath,
                    fullContent,
                    file.watchPath(),
                    file.path(),
                    config,
                    markupList,
                    headerParserList,
                    watch,
                    sortedModifications,
                    TemplateType.LAYOUT);

            layoutIds.add(item.id());
            LOGGER.debugf("Roq layout scan producing raw template '%s'", item.id());
            rawTemplateProducer.produce(item);
        }

        // Scan theme layouts
        List<ProjectFile> themeLayoutFiles = scanner.query()
                .scopeDir(TEMPLATES_DIR)
                .matchingGlob(THEME_LAYOUTS_DIR_PREFIX + LAYOUTS_DIR + "/**")
                .matching(buildHtmlTemplateGlob())
                .list();

        for (ProjectFile file : themeLayoutFiles) {
            String referencePath = toUnixPath(file.scopedPath());
            if (!isTemplateTargetHtml(referencePath)) {
                continue;
            }
            String fullRelativePath = toUnixPath(file.indexPath());
            String fullContent = new String(file.content(), file.charset());

            RoqFrontMatterRawTemplateBuildItem item = createLayoutTemplate(
                    siteDirPath,
                    fullRelativePath,
                    referencePath,
                    fullContent,
                    file.watchPath(),
                    file.path(),
                    config,
                    markupList,
                    headerParserList,
                    watch,
                    sortedModifications,
                    TemplateType.THEME_LAYOUT);

            LOGGER.debugf("Roq theme-layout scan producing raw template '%s'", item.id());
            rawTemplateProducer.produce(item);

            // Theme-layout dedup: if no regular layout overrides this, produce a LAYOUT copy
            String overrideId = RoqFrontMatterScanUtils.removeThemePrefix(item.id());
            if (!layoutIds.contains(overrideId)) {
                RoqFrontMatterRawTemplateBuildItem overrideItem = new RoqFrontMatterRawTemplateBuildItem(
                        item.templateSource().changeIds(RoqFrontMatterScanUtils::removeThemePrefix),
                        item.layout(),
                        TemplateType.LAYOUT,
                        item.data(),
                        item.collection(),
                        item.generatedTemplate(),
                        item.generatedContentTemplate(),
                        item.attachments());
                LOGGER.debugf("Roq theme-layout producing override '%s'", overrideItem.id());
                rawTemplateProducer.produce(overrideItem);
            }
        }
    }

    @SuppressWarnings("unchecked")
    static RoqFrontMatterRawTemplateBuildItem createLayoutTemplate(
            String siteDirPath,
            String relativePath,
            String referencePath,
            String fullContent,
            String watchPath,
            Path sourcePath,
            RoqSiteConfig config,
            List<RoqFrontMatterQuteMarkupBuildItem> markupList,
            List<RoqFrontMatterHeaderParserBuildItem> headerParserList,
            BuildProducer<HotDeploymentWatchedFileBuildItem> watch,
            List<RoqFrontMatterDataModificationBuildItem> dataModifications,
            TemplateType type) {

        SourceFile sourceFile = new SourceFile(siteDirPath != null ? siteDirPath : "", relativePath);
        produceWatch(watchPath, watch);

        TemplateContext templateContext = new TemplateContext(sourcePath, referencePath, fullContent);
        RoqFrontMatterQuteMarkupBuildItem markup = findMarkupFilter(markupList, templateContext);
        List<RoqFrontMatterHeaderParserBuildItem> headerParsers = RoqFrontMatterHeaderParserBuildItem
                .resolveHeaderParsers(headerParserList, templateContext);

        String cleanPath = replaceWhitespaceChars(referencePath);
        final String templateOutputPath = removeExtension(cleanPath)
                + RoqFrontmatterTemplateUtils.resolveOutputExtension(markup != null, templateContext);
        // Layouts strip extension from id
        String id = removeExtension(referencePath);

        JsonObject data = new JsonObject();
        String content = fullContent;

        for (RoqFrontMatterHeaderParserBuildItem headerParser : headerParsers) {
            data.mergeIn(headerParser.parse().apply(templateContext), true);
            content = headerParser.removeHeader().apply(content);
        }

        final boolean isHtml = isTemplateTargetHtml(referencePath);

        // Layouts don't resolve a default layout
        final String layoutId = RoqFrontmatterTemplateUtils.normalizedLayout(config.theme(),
                data.getString(LAYOUT_KEY),
                null);

        for (RoqFrontMatterDataModificationBuildItem modification : dataModifications) {
            data = modification.modifier().modify(new SourceData(sourcePath, referencePath, null, type, data));
        }

        final boolean escaped = Boolean.parseBoolean(data.getString(ESCAPE_KEY, "false"));
        final WrapperFilter escapeFilter = getEscapeFilter(escaped);
        final WrapperFilter includeFilter = RoqFrontmatterTemplateUtils.getIncludeFilter(layoutId);
        final String escapedContent = escapeFilter.apply(content);
        final String contentWithMarkup = markup != null ? markup.toWrapperFilter().apply(escapedContent) : escapedContent;
        final String generatedTemplate = includeFilter.apply(contentWithMarkup);

        TemplateSource source = TemplateSource.create(
                id,
                getMarkup(isHtml, markup),
                sourceFile,
                referencePath,
                templateOutputPath,
                true,
                isHtml,
                false,
                false);

        return new RoqFrontMatterRawTemplateBuildItem(source, layoutId, type, data, null,
                generatedTemplate,
                contentWithMarkup, null);
    }
}
