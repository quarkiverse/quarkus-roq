package io.quarkiverse.roq.frontmatter.deployment.scan;

import static io.quarkiverse.roq.frontmatter.deployment.scan.RoqFrontMatterQuteMarkupBuildItem.findMarkupFilter;
import static io.quarkiverse.roq.frontmatter.deployment.scan.RoqFrontMatterScanUtils.*;
import static io.quarkiverse.tools.stringpaths.StringPaths.fileName;
import static io.quarkiverse.tools.stringpaths.StringPaths.removeExtension;
import static io.quarkiverse.tools.stringpaths.StringPaths.toUnixPath;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import org.jboss.logging.Logger;

import io.quarkiverse.roq.deployment.items.RoqProjectBuildItem;
import io.quarkiverse.roq.frontmatter.deployment.data.RoqFrontMatterDataModificationBuildItem;
import io.quarkiverse.roq.frontmatter.deployment.data.RoqFrontMatterDataModificationBuildItem.SourceData;
import io.quarkiverse.roq.frontmatter.deployment.scan.RoqFrontMatterQuteMarkupBuildItem.WrapperFilter;
import io.quarkiverse.roq.frontmatter.deployment.scan.RoqFrontMatterRawTemplateBuildItem.Attachment;
import io.quarkiverse.roq.frontmatter.deployment.scan.RoqFrontMatterRawTemplateBuildItem.TemplateType;
import io.quarkiverse.roq.frontmatter.runtime.config.ConfiguredCollection;
import io.quarkiverse.roq.frontmatter.runtime.config.RoqSiteConfig;
import io.quarkiverse.roq.frontmatter.runtime.model.PageFiles;
import io.quarkiverse.roq.frontmatter.runtime.model.SourceFile;
import io.quarkiverse.roq.frontmatter.runtime.model.TemplateSource;
import io.quarkiverse.tools.projectscanner.ProjectFile;
import io.quarkiverse.tools.projectscanner.ProjectScannerBuildItem;
import io.quarkiverse.tools.projectscanner.ScanDeclarationBuildItem;
import io.quarkiverse.tools.projectscanner.ScanLocalDirBuildItem;
import io.quarkiverse.tools.projectscanner.ScanQueryBuilder;
import io.quarkus.deployment.annotations.BuildProducer;
import io.quarkus.deployment.annotations.BuildStep;
import io.quarkus.deployment.builditem.HotDeploymentWatchedFileBuildItem;
import io.quarkus.qute.runtime.QuteConfig;
import io.vertx.core.json.JsonObject;

public class RoqFrontMatterContentScanProcessor {

    private static final Logger LOGGER = Logger.getLogger(RoqFrontMatterContentScanProcessor.class);
    private static final String LAYOUT_KEY = "layout";

    record ContentEntry(ProjectFile file, boolean isTemplate, boolean isIndex, boolean isSiteIndex,
            String parentDir, ConfiguredCollection collection, TemplateType type, List<Attachment> attachments) {
    }

    @BuildStep
    void declareAndScanContentDirs(RoqSiteConfig config, RoqProjectBuildItem roqProject,
            BuildProducer<ScanDeclarationBuildItem> declarations,
            BuildProducer<ScanLocalDirBuildItem> scanLocalDirProducer) {
        declarations.produce(ScanDeclarationBuildItem.of(config.contentDir()));
        declarations.produce(ScanDeclarationBuildItem.of(config.staticDir()));
        declarations.produce(ScanDeclarationBuildItem.of(config.publicDir()));
        if (!roqProject.isRoqResourcesInRoot()) {
            declarations.produce(ScanDeclarationBuildItem.of(roqProject.resolveRoqResourceSubDir(config.contentDir())));
            declarations.produce(ScanDeclarationBuildItem.of(roqProject.resolveRoqResourceSubDir(config.staticDir())));
            declarations.produce(ScanDeclarationBuildItem.of(roqProject.resolveRoqResourceSubDir(config.publicDir())));
        }
        roqProject.addScannerForLocalRoqDir(scanLocalDirProducer, config.contentDir());
        roqProject.addScannerForLocalRoqDir(scanLocalDirProducer, config.staticDir());
        roqProject.addScannerForLocalRoqDir(scanLocalDirProducer, config.publicDir());
    }

    @BuildStep
    void scanContent(RoqProjectBuildItem roqProject,
            ProjectScannerBuildItem scanner,
            RoqSiteConfig config,
            QuteConfig quteConfig,
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

        final List<String> ignoredPatterns = buildIgnoredPatterns(config);

        Predicate<Path> templatePredicate = isTemplate(quteConfig);
        // Sort so index templates come first, allowing their attachment lists to be registered
        // before non-template files arrive in the loop (which then get assigned to the deepest matching index).
        List<ProjectFile> allContentFiles = queryAllOrigins(scanner, roqProject, config.contentDir(), ignoredPatterns)
                .stream()
                .sorted(indexTemplatesFirst(templatePredicate))
                .toList();

        final Map<String, ConfiguredCollection> collections = config.collections().stream()
                .collect(Collectors.toMap(ConfiguredCollection::id, Function.identity()));

        // Build entries and resolve attachments in one pass
        // (indexes come first so their attachment lists are registered before non-templates arrive)
        Map<Path, List<Attachment>> pageAttachments = new HashMap<>();
        List<ContentEntry> entries = new ArrayList<>();

        for (ProjectFile f : allContentFiles) {
            String sp = toUnixPath(f.scopedPath());
            final Path spPath = Path.of(sp);
            boolean isTpl = templatePredicate.test(spPath);
            boolean isIdx = isTpl && INDEX_FILES.contains(fileName(sp));
            boolean isSiteIdx = isIdx && sp.startsWith("index.");
            Path parent = spPath.getParent();
            String parentDir = parent != null ? toUnixPath(parent.toString()) : "";

            if (isTpl) {
                // Determine collection and type
                ConfiguredCollection collection = null;
                TemplateType type = TemplateType.NORMAL_PAGE;
                String topDirName = spPath.getName(0).toString();
                boolean isCollectionDir = collections.containsKey(topDirName);
                boolean isCollectionIndex = isCollectionDir && isIdx && spPath.getNameCount() == 2;
                if (isCollectionDir && !isCollectionIndex) {
                    collection = collections.get(topDirName);
                    type = TemplateType.DOCUMENT_PAGE;
                }

                // Resolve attachments for index pages
                List<Attachment> attachments = null;
                if (isIdx) {
                    if (isSiteIdx) {
                        attachments = new ArrayList<>();
                        scanSiteIndexAttachments(scanner, roqProject, config, watch, attachments);
                    } else {
                        attachments = pageAttachments.computeIfAbsent(parent, k -> new ArrayList<>());
                    }
                }

                entries.add(new ContentEntry(f, true, isIdx, isSiteIdx, parentDir, collection, type, attachments));
            } else {
                // Non-template file: assign as attachment to the nearest owning index
                Path ownerDir = findNearestOwner(pageAttachments, spPath);
                if (ownerDir != null) {
                    String name = toUnixPath(ownerDir.relativize(spPath).toString());
                    if (config.slugifyFiles()) {
                        name = PageFiles.slugifyFile(name);
                    }
                    pageAttachments.get(ownerDir).add(new Attachment(name, f.path()));
                    produceWatch(f.watchPath(), watch);
                }
            }
        }

        // Process templates
        for (ContentEntry entry : entries) {
            RoqFrontMatterRawTemplateBuildItem item = createPageTemplate(
                    entry,
                    config,
                    markupList,
                    headerParserList,
                    watch,
                    sortedModifications);

            LOGGER.debugf("Roq content scan producing raw template '%s'", item.id());
            rawTemplateProducer.produce(item);
        }
    }

    static RoqFrontMatterRawTemplateBuildItem createPageTemplate(
            ContentEntry entry,
            RoqSiteConfig config,
            List<RoqFrontMatterQuteMarkupBuildItem> markupList,
            List<RoqFrontMatterHeaderParserBuildItem> headerParserList,
            BuildProducer<HotDeploymentWatchedFileBuildItem> watch,
            List<RoqFrontMatterDataModificationBuildItem> dataModifications) {

        ProjectFile file = entry.file();
        String referencePath = toUnixPath(file.scopedPath());
        String relativePath = toUnixPath(file.indexPath());
        String siteDirPath = deriveSiteDirPath(file.path(), relativePath);
        String fullContent = new String(file.content(), file.charset());

        SourceFile sourceFile = new SourceFile(siteDirPath != null ? siteDirPath : "", relativePath);
        produceWatch(file.watchPath(), watch);

        TemplateContext templateContext = new TemplateContext(file.path(), referencePath, fullContent);
        RoqFrontMatterQuteMarkupBuildItem markup = findMarkupFilter(markupList, templateContext);
        List<RoqFrontMatterHeaderParserBuildItem> headerParsers = RoqFrontMatterHeaderParserBuildItem
                .resolveHeaderParsers(headerParserList, templateContext);

        String cleanPath = replaceWhitespaceChars(referencePath);
        final String templateOutputPath = removeExtension(cleanPath)
                + RoqFrontmatterTemplateUtils.resolveOutputExtension(markup != null, templateContext);

        JsonObject data = new JsonObject();
        String content = fullContent;

        for (RoqFrontMatterHeaderParserBuildItem headerParser : headerParsers) {
            data.mergeIn(headerParser.parse().apply(templateContext), true);
            content = headerParser.removeHeader().apply(content);
        }

        final boolean isHtml = isTemplateTargetHtml(referencePath);

        final boolean isHtmlPartialPage = isHtml && !(content.toLowerCase(Locale.ROOT).contains("<html")
                || content.toLowerCase(Locale.ROOT).contains("<!doctype"));

        final String defaultLayout = isHtmlPartialPage
                ? (entry.collection() != null ? entry.collection().layout() : config.pageLayout().orElse(null))
                : null;

        final String layoutId = RoqFrontmatterTemplateUtils.normalizedLayout(config.theme(),
                data.getString(LAYOUT_KEY),
                defaultLayout);

        for (RoqFrontMatterDataModificationBuildItem modification : dataModifications) {
            data = modification.modifier()
                    .modify(new SourceData(file.path(), referencePath, entry.collection(), entry.type(), data));
        }

        final boolean escaped = Boolean.parseBoolean(data.getString(ESCAPE_KEY, "false"));
        final WrapperFilter escapeFilter = getEscapeFilter(escaped);
        final WrapperFilter includeFilter = RoqFrontmatterTemplateUtils.getIncludeFilter(layoutId);
        final String escapedContent = escapeFilter.apply(content);
        final String contentWithMarkup = markup != null ? markup.toWrapperFilter().apply(escapedContent) : escapedContent;
        final String generatedTemplate = includeFilter.apply(contentWithMarkup);

        TemplateSource source = TemplateSource.create(
                referencePath,
                getMarkup(isHtml, markup),
                sourceFile,
                referencePath,
                templateOutputPath,
                false,
                isHtml,
                entry.isIndex(),
                entry.isSiteIndex());

        return new RoqFrontMatterRawTemplateBuildItem(source, layoutId, entry.type(), data, entry.collection(),
                generatedTemplate,
                contentWithMarkup, entry.attachments());
    }

    /**
     * Query scanner for files under a subDir from all origins, using origin-split + merge
     * to handle non-root roqResourceDir for classpath resources.
     */
    private static List<ProjectFile> queryAllOrigins(
            ProjectScannerBuildItem scanner,
            RoqProjectBuildItem roqProject,
            String subDir,
            List<String> ignoredPatterns) throws IOException {
        List<ProjectFile> localFiles = scanner.query()
                .scopeDir(subDir)
                .origin(ProjectFile.Origin.LOCAL_PROJECT_FILE)
                .addExcluded(ignoredPatterns)
                .list();

        List<ProjectFile> resourceFiles = scanner.query()
                .scopeDir(roqProject.resolveRoqResourceSubDir(subDir))
                .origin(ProjectFile.Origin.APPLICATION_RESOURCE, ProjectFile.Origin.DEPENDENCY_RESOURCE)
                .addExcluded(ignoredPatterns)
                .list();

        return ScanQueryBuilder.mergeByScopedPath(localFiles, resourceFiles);
    }

    static void scanSiteIndexAttachments(
            ProjectScannerBuildItem scanner,
            RoqProjectBuildItem roqProject,
            RoqSiteConfig config,
            BuildProducer<HotDeploymentWatchedFileBuildItem> watch,
            List<Attachment> attachments) throws IOException {

        final List<String> ignoredPatterns = buildIgnoredPatterns(config);

        // Static dir files (served under /static/ path)
        for (ProjectFile f : queryAllOrigins(scanner, roqProject, config.staticDir(), ignoredPatterns)) {
            String attachmentName = config.staticDir() + "/" + toUnixPath(f.scopedPath());
            if (config.slugifyFiles()) {
                attachmentName = PageFiles.slugifyFile(attachmentName);
            }
            attachments.add(new Attachment(attachmentName, f.path()));
            produceWatch(f.watchPath(), watch);
        }

        // Public dir files (served at root path)
        for (ProjectFile f : queryAllOrigins(scanner, roqProject, config.publicDir(), ignoredPatterns)) {
            String attachmentName = toUnixPath(f.scopedPath());
            if (config.slugifyFiles()) {
                attachmentName = PageFiles.slugifyFile(attachmentName);
            }
            attachments.add(new Attachment(attachmentName, f.path()));
            produceWatch(f.watchPath(), watch);
        }
    }

    /**
     * Order content files so that index templates (e.g. index.html) come first,
     * then other templates, then non-template files.
     * This ensures index pages register their attachment lists before non-template
     * files are encountered and assigned to their deepest matching index.
     */
    private static Comparator<ProjectFile> indexTemplatesFirst(Predicate<Path> templatePredicate) {
        return Comparator.comparingInt(f -> {
            String sp = toUnixPath(f.scopedPath());
            if (!templatePredicate.test(Path.of(sp)))
                return 2; // non-template (attachment candidate)
            return INDEX_FILES.contains(fileName(sp)) ? 0 : 1; // index template or other template
        });
    }

    /**
     * Walk up parent directories to find the nearest registered index that owns the given path.
     * Returns the owning directory path, or null if no index owns this file.
     */
    private static Path findNearestOwner(Map<Path, List<Attachment>> pageAttachments, Path path) {
        Path dir = path.getParent();
        while (dir != null) {
            if (pageAttachments.containsKey(dir)) {
                return dir;
            }
            dir = dir.getParent();
        }
        return null;
    }
}
