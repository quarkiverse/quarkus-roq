package io.quarkiverse.roq.frontmatter.deployment.scan;

import static io.quarkiverse.roq.frontmatter.deployment.scan.RoqFrontMatterQuteMarkupBuildItem.findMarkupFilter;
import static io.quarkiverse.roq.frontmatter.deployment.scan.RoqFrontMatterScanUtils.*;
import static io.quarkiverse.tools.stringpaths.StringPaths.fileName;
import static io.quarkiverse.tools.stringpaths.StringPaths.removeExtension;
import static io.quarkiverse.tools.stringpaths.StringPaths.toUnixPath;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.jboss.logging.Logger;

import io.quarkiverse.roq.deployment.items.RoqProjectBuildItem;
import io.quarkiverse.roq.frontmatter.deployment.data.RoqFrontMatterDataModificationBuildItem;
import io.quarkiverse.roq.frontmatter.deployment.data.RoqFrontMatterDataModificationBuildItem.SourceData;
import io.quarkiverse.roq.frontmatter.deployment.exception.RoqSiteScanningException;
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
import io.quarkiverse.tools.projectscanner.ProjectScannerLocalDirBuildItem;
import io.quarkiverse.tools.projectscanner.ScanQueryBuilder;
import io.quarkus.deployment.annotations.BuildProducer;
import io.quarkus.deployment.annotations.BuildStep;
import io.quarkus.deployment.builditem.HotDeploymentWatchedFileBuildItem;
import io.quarkus.qute.runtime.QuteConfig;
import io.vertx.core.json.JsonObject;

public class RoqFrontMatterContentScanProcessor {

    private static final Logger LOGGER = Logger.getLogger(RoqFrontMatterContentScanProcessor.class);
    private static final String LAYOUT_KEY = "layout";

    @BuildStep
    void scanContentDir(BuildProducer<ProjectScannerLocalDirBuildItem> scanLocalDirProducer,
            RoqSiteConfig config,
            RoqProjectBuildItem roqProject) {
        roqProject.addScannerForLocalRoqDir(scanLocalDirProducer, config.contentDir());
    }

    @BuildStep
    void scanStaticDir(BuildProducer<ProjectScannerLocalDirBuildItem> scanLocalDirProducer,
            RoqSiteConfig config,
            RoqProjectBuildItem roqProject) {
        roqProject.addScannerForLocalRoqDir(scanLocalDirProducer, config.staticDir());
    }

    @BuildStep
    void scanPublicDir(BuildProducer<ProjectScannerLocalDirBuildItem> scanLocalDirProducer,
            RoqSiteConfig config,
            RoqProjectBuildItem roqProject) {
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

        // Query 1: Local project files under contentDir
        List<ProjectFile> localFiles = scanner.query()
                .scopeDir(config.contentDir())
                .origin(ProjectFile.Origin.LOCAL_PROJECT_FILE)
                .matching(buildTemplateGlob(quteConfig))
                .list();

        // Query 2: Classpath resources under roqResourceDir/contentDir
        List<ProjectFile> resourceFiles = scanner.query()
                .scopeDir(roqProject.resolveRoqResourceSubDir(config.contentDir()))
                .origin(ProjectFile.Origin.APPLICATION_RESOURCE, ProjectFile.Origin.DEPENDENCY_RESOURCE)
                .matching(buildTemplateGlob(quteConfig))
                .list();

        List<ProjectFile> files = ScanQueryBuilder.mergeByScopedPath(localFiles, resourceFiles);

        final Map<String, ConfiguredCollection> collections = config.collections().stream()
                .collect(Collectors.toMap(ConfiguredCollection::id, Function.identity()));

        final String siteDirPath = roqProject.local() != null
                ? toUnixPath(roqProject.local().roqDir().normalize().toAbsolutePath().toString())
                : null;

        for (ProjectFile file : files) {
            String fullRelativePath = toUnixPath(file.indexPath());
            String referencePath = toUnixPath(file.scopedPath());
            String fullContent = new String(file.content(), file.charset());

            // Determine collection and type
            Path refPath = Path.of(referencePath);
            final String topDirName = refPath.getName(0).toString();
            final boolean isCollectionDir = collections.containsKey(topDirName);
            final boolean isCollectionIndex = isCollectionDir
                    && INDEX_FILES.contains(refPath.getFileName().toString())
                    && refPath.getNameCount() == 2;
            ConfiguredCollection collection = null;
            TemplateType type = TemplateType.NORMAL_PAGE;
            if (isCollectionDir && !isCollectionIndex) {
                collection = collections.get(topDirName);
                type = TemplateType.DOCUMENT_PAGE;
            }

            // Scan attachments for index pages
            List<Attachment> attachments = null;
            if (INDEX_FILES.contains(refPath.getFileName().toString())) {
                attachments = new ArrayList<>();
                boolean isSiteIndex = referencePath.startsWith("index.");
                if (isSiteIndex) {
                    // Site index: scan static/ and public/ dirs via scanner (all origins)
                    scanSiteIndexAttachments(scanner, roqProject, config, watch, attachments);
                } else {
                    // Non-site index: scan sibling files via filesystem
                    Path filePath = file.path();
                    Path parentDir = filePath.getParent();
                    scanAttachments(true, false,
                            roqProject.local() != null ? roqProject.local().roqDir() : parentDir,
                            config, quteConfig, watch, attachments, parentDir, parentDir);
                }
            }

            RoqFrontMatterRawTemplateBuildItem item = createPageTemplate(
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
                    collection,
                    type,
                    attachments);

            LOGGER.debugf("Roq content scan producing raw template '%s'", item.id());
            rawTemplateProducer.produce(item);
        }
    }

    @SuppressWarnings("unchecked")
    static RoqFrontMatterRawTemplateBuildItem createPageTemplate(
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
            ConfiguredCollection collection,
            TemplateType type,
            List<Attachment> attachments) {

        SourceFile sourceFile = new SourceFile(siteDirPath != null ? siteDirPath : "", relativePath);
        produceWatch(watchPath, watch);

        TemplateContext templateContext = new TemplateContext(sourcePath, referencePath, fullContent);
        RoqFrontMatterQuteMarkupBuildItem markup = findMarkupFilter(markupList, templateContext);
        List<RoqFrontMatterHeaderParserBuildItem> headerParsers = RoqFrontMatterHeaderParserBuildItem
                .resolveHeaderParsers(headerParserList, templateContext);

        String cleanPath = replaceWhitespaceChars(referencePath);
        final String templateOutputPath = removeExtension(cleanPath)
                + RoqFrontmatterTemplateUtils.resolveOutputExtension(markup != null, templateContext);
        String id = referencePath;

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
                ? (collection != null ? collection.layout() : config.pageLayout().orElse(null))
                : null;

        final String layoutId = RoqFrontmatterTemplateUtils.normalizedLayout(config.theme(),
                data.getString(LAYOUT_KEY),
                defaultLayout);

        for (RoqFrontMatterDataModificationBuildItem modification : dataModifications) {
            data = modification.modifier().modify(new SourceData(sourcePath, referencePath, collection, type, data));
        }

        final boolean escaped = Boolean.parseBoolean(data.getString(ESCAPE_KEY, "false"));
        final WrapperFilter escapeFilter = getEscapeFilter(escaped);
        final WrapperFilter includeFilter = RoqFrontmatterTemplateUtils.getIncludeFilter(layoutId);
        final String escapedContent = escapeFilter.apply(content);
        final String contentWithMarkup = markup != null ? markup.toWrapperFilter().apply(escapedContent) : escapedContent;
        final String generatedTemplate = includeFilter.apply(contentWithMarkup);

        final String fileNameStr = fileName(referencePath);
        var isIndex = INDEX_FILES.contains(fileNameStr);
        var isSiteIndex = isHtml && id.startsWith("index.");

        TemplateSource source = TemplateSource.create(
                id,
                getMarkup(isHtml, markup),
                sourceFile,
                referencePath,
                templateOutputPath,
                false,
                isHtml,
                isIndex,
                isSiteIndex);

        return new RoqFrontMatterRawTemplateBuildItem(source, layoutId, type, data, collection,
                generatedTemplate,
                contentWithMarkup, attachments);
    }

    /**
     * Query scanner for files under a subDir from all origins, using origin-split + merge
     * to handle non-root roqResourceDir for classpath resources.
     */
    private static List<ProjectFile> queryAllOrigins(
            ProjectScannerBuildItem scanner,
            RoqProjectBuildItem roqProject,
            String subDir) throws IOException {
        List<ProjectFile> localFiles = scanner.query()
                .scopeDir(subDir)
                .origin(ProjectFile.Origin.LOCAL_PROJECT_FILE)
                .list();

        List<ProjectFile> resourceFiles = scanner.query()
                .scopeDir(roqProject.resolveRoqResourceSubDir(subDir))
                .origin(ProjectFile.Origin.APPLICATION_RESOURCE, ProjectFile.Origin.DEPENDENCY_RESOURCE)
                .list();

        return ScanQueryBuilder.mergeByScopedPath(localFiles, resourceFiles);
    }

    static void scanSiteIndexAttachments(
            ProjectScannerBuildItem scanner,
            RoqProjectBuildItem roqProject,
            RoqSiteConfig config,
            BuildProducer<HotDeploymentWatchedFileBuildItem> watch,
            List<Attachment> attachments) throws IOException {

        // Static dir files (served under /static/ path)
        for (ProjectFile f : queryAllOrigins(scanner, roqProject, config.staticDir())) {
            String attachmentName = config.staticDir() + "/" + toUnixPath(f.scopedPath());
            if (config.slugifyFiles()) {
                attachmentName = PageFiles.slugifyFile(attachmentName);
            }
            attachments.add(new Attachment(attachmentName, f.path()));
            produceWatch(f.watchPath(), watch);
        }

        // Public dir files (served at root path)
        for (ProjectFile f : queryAllOrigins(scanner, roqProject, config.publicDir())) {
            String attachmentName = toUnixPath(f.scopedPath());
            if (config.slugifyFiles()) {
                attachmentName = PageFiles.slugifyFile(attachmentName);
            }
            attachments.add(new Attachment(attachmentName, f.path()));
            produceWatch(f.watchPath(), watch);
        }
    }

    static void scanAttachments(boolean isAttachmentRoot,
            boolean isStaticDir,
            Path siteDir,
            RoqSiteConfig config,
            QuteConfig quteConfig,
            BuildProducer<HotDeploymentWatchedFileBuildItem> watch,
            List<Attachment> attachments,
            Path refDir,
            Path attachmentDir) {
        if (!Files.isDirectory(attachmentDir)) {
            return;
        }
        if (!isStaticDir && !isAttachmentRoot && hasIndexFile(attachmentDir)) {
            return;
        }
        watchDirectory(attachmentDir, watch);
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(attachmentDir)) {
            for (Path entry : stream) {
                if (Files.isDirectory(entry)) {
                    scanAttachments(false, isStaticDir, siteDir, config, quteConfig, watch, attachments, refDir, entry);
                } else if (Files.isRegularFile(entry)
                        && (isStaticDir || !isTemplate(quteConfig).test(entry))) {
                    attachments.add(new Attachment(
                            resolveAttachmentLink(config, entry, refDir), entry));
                }
            }
        } catch (IOException e) {
            throw new RoqSiteScanningException(
                    "Error scanning static attachment files in directory: %s".formatted(attachmentDir), e);
        }
    }

    private static boolean hasIndexFile(Path dir) {
        try {
            return Files
                    .find(dir, 1,
                            (path, attr) -> attr.isRegularFile()
                                    && INDEX_FILES.contains(path.getFileName().toString().toLowerCase()))
                    .findFirst().isPresent();
        } catch (IOException e) {
            throw new UncheckedIOException("Error checking index in " + dir, e);
        }
    }

    private static void watchDirectory(Path dir, BuildProducer<HotDeploymentWatchedFileBuildItem> watch) {
        if (!Files.isDirectory(dir)) {
            return;
        }
        try (var stream = Files.walk(dir, 1)) {
            stream.forEach(f -> watch.produce(
                    HotDeploymentWatchedFileBuildItem.builder().setLocation(f.toAbsolutePath().toString()).build()));
        } catch (IOException e) {
            throw new RoqSiteScanningException("Unable to read directory: %s".formatted(dir), e);
        }
    }

    private static String resolveAttachmentLink(RoqSiteConfig config, Path p, Path pageDir) {
        final String relative = toUnixPath(pageDir.relativize(p).toString());
        if (config.slugifyFiles()) {
            return PageFiles.slugifyFile(relative);
        }
        return relative;
    }
}
