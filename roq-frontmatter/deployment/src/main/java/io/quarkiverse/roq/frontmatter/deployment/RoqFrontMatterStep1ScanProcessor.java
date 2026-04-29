package io.quarkiverse.roq.frontmatter.deployment;

import static io.quarkiverse.roq.frontmatter.deployment.util.RoqFrontMatterConstants.INDEX_FILES;
import static io.quarkiverse.roq.frontmatter.deployment.util.RoqFrontMatterConstants.TEMPLATES_DIR;
import static io.quarkiverse.roq.frontmatter.deployment.util.RoqFrontMatterScanUtils.*;
import static io.quarkiverse.roq.frontmatter.runtime.RoqTemplates.LAYOUTS_DIR;
import static io.quarkiverse.roq.frontmatter.runtime.RoqTemplates.THEME_LAYOUTS_DIR;
import static io.quarkiverse.tools.stringpaths.StringPaths.fileName;
import static io.quarkiverse.tools.stringpaths.StringPaths.toUnixPath;
import static io.quarkus.qute.deployment.TemplatePathBuildItem.ROOT_ARCHIVE_PRIORITY;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import org.jboss.logging.Logger;

import io.quarkiverse.roq.deployment.items.RoqProjectBuildItem;
import io.quarkiverse.roq.frontmatter.deployment.items.assemble.RoqFrontMatterAttachment;
import io.quarkiverse.roq.frontmatter.deployment.items.scan.FrontMatterTemplateMetadata;
import io.quarkiverse.roq.frontmatter.deployment.items.scan.RoqFrontMatterDependencyParserConfigsBuildItem;
import io.quarkiverse.roq.frontmatter.deployment.items.scan.RoqFrontMatterHeaderParserBuildItem;
import io.quarkiverse.roq.frontmatter.deployment.items.scan.RoqFrontMatterQuteMarkupBuildItem;
import io.quarkiverse.roq.frontmatter.deployment.items.scan.RoqFrontMatterScannedContentBuildItem;
import io.quarkiverse.roq.frontmatter.deployment.items.scan.RoqFrontMatterScannedLayoutBuildItem;
import io.quarkiverse.roq.frontmatter.runtime.config.ConfiguredCollection;
import io.quarkiverse.roq.frontmatter.runtime.config.RoqSiteConfig;
import io.quarkiverse.roq.frontmatter.runtime.model.PageFiles;
import io.quarkiverse.tools.projectscanner.ProjectFile;
import io.quarkiverse.tools.projectscanner.ProjectScannerBuildItem;
import io.quarkiverse.tools.projectscanner.ScanDeclarationBuildItem;
import io.quarkiverse.tools.projectscanner.ScanLocalDirBuildItem;
import io.quarkiverse.tools.projectscanner.ScanQueryBuilder;
import io.quarkiverse.tools.stringpaths.StringPaths;
import io.quarkus.deployment.annotations.BuildProducer;
import io.quarkus.deployment.annotations.BuildStep;
import io.quarkus.deployment.builditem.GeneratedResourceBuildItem;
import io.quarkus.deployment.builditem.SuppressNonRuntimeConfigChangedWarningBuildItem;
import io.quarkus.deployment.builditem.nativeimage.NativeImageResourceBuildItem;
import io.quarkus.qute.ParserConfig;
import io.quarkus.qute.deployment.TemplatePathBuildItem;
import io.quarkus.qute.deployment.TemplateRootBuildItem;
import io.quarkus.qute.runtime.QuteConfig;

public class RoqFrontMatterStep1ScanProcessor {

    private static final Logger LOGGER = Logger.getLogger(RoqFrontMatterStep1ScanProcessor.class);

    // Workaround for SmallRye Config greedy wildcard bug (smallrye/smallrye-config#1492).
    // PropertyName.equals() lets site.collections.* match site.collections.*.hidden,
    // causing the @WithParentName @WithDefault("true") value to bleed into sub-keys.
    // TODO: remove when Quarkus upgrades to SmallRye Config 3.17+
    @BuildStep
    void suppressWildcardMapMismatch(BuildProducer<SuppressNonRuntimeConfigChangedWarningBuildItem> producer) {
        producer.produce(new SuppressNonRuntimeConfigChangedWarningBuildItem("site.collections.*"));
        producer.produce(new SuppressNonRuntimeConfigChangedWarningBuildItem("site.collections.*.hidden"));
        producer.produce(new SuppressNonRuntimeConfigChangedWarningBuildItem("site.collections.*.future"));
        producer.produce(new SuppressNonRuntimeConfigChangedWarningBuildItem("site.collections.*.layout"));
        producer.produce(new SuppressNonRuntimeConfigChangedWarningBuildItem("site.collections.*.generate"));
        producer.produce(new SuppressNonRuntimeConfigChangedWarningBuildItem("site.collections.*.title-attribute-name"));
    }

    // ── Dir declarations ─────────────────────────────────────────────────

    @BuildStep
    void declareAndScanDirs(RoqSiteConfig config, RoqProjectBuildItem roqProject,
            BuildProducer<ScanDeclarationBuildItem> declarations,
            BuildProducer<ScanLocalDirBuildItem> scanLocalDirProducer) {
        // Declare site directories for scanning (content, static, public, templates)
        declarations.produce(ScanDeclarationBuildItem.of(config.contentDir()));
        declarations.produce(ScanDeclarationBuildItem.of(config.staticDir()));
        declarations.produce(ScanDeclarationBuildItem.of(config.publicDir()));
        declarations.produce(ScanDeclarationBuildItem.of(TEMPLATES_DIR));

        // Also declare under the roq resource sub-dir for classpath resources (e.g. from themes)
        if (!roqProject.isRoqResourcesInRoot()) {
            declarations.produce(ScanDeclarationBuildItem.of(roqProject.resolveRoqResourceSubDir(config.contentDir())));
            declarations.produce(ScanDeclarationBuildItem.of(roqProject.resolveRoqResourceSubDir(config.staticDir())));
            declarations.produce(ScanDeclarationBuildItem.of(roqProject.resolveRoqResourceSubDir(config.publicDir())));
            declarations.produce(ScanDeclarationBuildItem.of(roqProject.resolveRoqResourceSubDir(TEMPLATES_DIR)));
        }

        // Register local project directories so the ProjectScanner can find files on the filesystem
        // (not just on the classpath). This is what makes local site files visible during build.
        roqProject.addScannerForLocalRoqDir(scanLocalDirProducer, config.contentDir());
        roqProject.addScannerForLocalRoqDir(scanLocalDirProducer, config.staticDir());
        roqProject.addScannerForLocalRoqDir(scanLocalDirProducer, config.publicDir());
        roqProject.addScannerForLocalRoqDir(scanLocalDirProducer, TEMPLATES_DIR);
    }

    // ── Dependency parser config scanning ─────────────────────────────────

    private static final String QUTE_CONFIG_FILE = ".qute";
    private static final String ALT_EXPR_PROPERTY = "alt-expr-syntax";

    @BuildStep
    RoqFrontMatterDependencyParserConfigsBuildItem scanDependencyParserConfigs(
            RoqProjectBuildItem roqProject,
            ProjectScannerBuildItem scanner) throws IOException {
        if (!roqProject.isActive()) {
            return new RoqFrontMatterDependencyParserConfigsBuildItem(Map.of());
        }

        Map<Path, ParserConfig> result = new HashMap<>();

        // Query .qute files from the main templates dir
        List<ProjectFile> quteFiles = scanner.query()
                .scopeDir(TEMPLATES_DIR)
                .matchingGlob(QUTE_CONFIG_FILE)
                .origin(ProjectFile.Origin.DEPENDENCY_RESOURCE)
                .list();

        // Also check the roq resource sub-dir for themes
        if (!roqProject.isRoqResourcesInRoot()) {
            List<ProjectFile> roqResourceQuteFiles = scanner.query()
                    .scopeDir(roqProject.resolveRoqResourceSubDir(TEMPLATES_DIR))
                    .matchingGlob(QUTE_CONFIG_FILE)
                    .origin(ProjectFile.Origin.DEPENDENCY_RESOURCE)
                    .list();
            quteFiles = ScanQueryBuilder.mergeByScopedPath(quteFiles, roqResourceQuteFiles);
        }

        for (ProjectFile f : quteFiles) {
            Path templateRoot = resolveTemplateRoot(f);
            if (templateRoot == null) {
                continue;
            }
            Properties props = new Properties();
            try {
                props.load(new ByteArrayInputStream(f.content()));
            } catch (IOException e) {
                LOGGER.warnf("Unable to read %s file: %s", f.file(), e);
                continue;
            }
            String altExprValue = props.getProperty(ALT_EXPR_PROPERTY, "false");
            if (Boolean.parseBoolean(altExprValue)) {
                LOGGER.debugf("Dependency template root %s has alt-expr-syntax enabled", templateRoot);
                result.put(templateRoot, TemplatePathBuildItem.ALT_PARSER_CONFIG);
            }
        }

        return new RoqFrontMatterDependencyParserConfigsBuildItem(result);
    }

    // ── Content scanning ─────────────────────────────────────────────────

    record ContentEntry(ProjectFile file, boolean isIndex, boolean isSiteIndex,
            ConfiguredCollection collection,
            List<RoqFrontMatterAttachment> attachments) {
    }

    @BuildStep
    void scanContent(RoqProjectBuildItem roqProject,
            ProjectScannerBuildItem scanner,
            RoqSiteConfig config,
            QuteConfig quteConfig,
            RoqFrontMatterDependencyParserConfigsBuildItem depParserConfigs,
            List<RoqFrontMatterQuteMarkupBuildItem> markupList,
            List<RoqFrontMatterHeaderParserBuildItem> headerParserList,
            BuildProducer<RoqFrontMatterScannedContentBuildItem> scannedContentProducer) throws IOException {
        if (!roqProject.isActive()) {
            return;
        }

        final List<String> ignoredPatterns = buildIgnoredPatterns(config);

        // Query all content files (local + classpath), sorted so index templates come first.
        // Sorting matters because index pages must register as attachment owners BEFORE
        // non-template sibling files are encountered in the first pass below.
        Predicate<Path> templatePredicate = isTemplate(quteConfig);
        List<ProjectFile> allContentFiles = queryAllOrigins(scanner, roqProject, config.contentDir(), ignoredPatterns)
                .stream()
                .sorted(indexTemplatesFirst(templatePredicate))
                .toList();

        final Map<String, ConfiguredCollection> collections = config.collections().stream()
                .collect(Collectors.toMap(ConfiguredCollection::id, Function.identity()));

        // First pass: classify files as templates or attachments.
        // Index templates register as attachment owners for sibling non-template files.
        Map<Path, List<RoqFrontMatterAttachment>> pageAttachments = new HashMap<>();
        List<ContentEntry> entries = new ArrayList<>();

        for (ProjectFile f : allContentFiles) {
            String sp = toUnixPath(f.scopedPath());
            final Path spPath = Path.of(sp);
            boolean isTpl = templatePredicate.test(spPath);
            boolean isIdx = isTpl && INDEX_FILES.contains(fileName(sp));
            boolean isSiteIdx = isIdx && sp.startsWith("index.");
            Path parent = spPath.getParent();

            if (isTpl) {
                // Determine if this page belongs to a collection (top-level dir matches a collection id).
                // The collection index itself (e.g. posts/index.html) is NOT a collection member —
                // it's a regular page that typically lists the collection via pagination.
                ConfiguredCollection collection = null;
                String topDirName = spPath.getName(0).toString();
                boolean isCollectionDir = collections.containsKey(topDirName);
                boolean isCollectionIndex = isCollectionDir && isIdx && spPath.getNameCount() == 2;
                if (isCollectionDir && !isCollectionIndex) {
                    collection = collections.get(topDirName);
                }

                // Index pages own attachments: site index gets static/public files, others get sibling files
                List<RoqFrontMatterAttachment> attachments = null;
                if (isIdx) {
                    if (isSiteIdx) {
                        attachments = new ArrayList<>();
                        scanSiteIndexAttachments(scanner, roqProject, config, attachments);
                    } else {
                        attachments = pageAttachments.computeIfAbsent(parent, k -> new ArrayList<>());
                    }
                }

                entries.add(new ContentEntry(f, isIdx, isSiteIdx, collection, attachments));
            } else {
                // Non-template file: attach to the nearest parent index page
                Path ownerDir = findNearestOwner(pageAttachments, spPath);
                if (ownerDir != null) {
                    String name = toUnixPath(ownerDir.relativize(spPath).toString());
                    if (config.slugifyFiles()) {
                        name = PageFiles.slugifyFile(name);
                    }
                    pageAttachments.get(ownerDir).add(new RoqFrontMatterAttachment(name, f.file()));
                }
            }
        }

        // Second pass: collect metadata (front matter, markup) and produce scanned build items
        for (ContentEntry entry : entries) {
            FrontMatterTemplateMetadata metadata = collectMetadata(entry.file(), false, markupList, headerParserList,
                    quteConfig, depParserConfigs.configs());

            LOGGER.debugf("Roq content scan producing scanned template '%s'", metadata.templateId());
            scannedContentProducer.produce(new RoqFrontMatterScannedContentBuildItem(
                    metadata, entry.collection(),
                    entry.isIndex(), entry.isSiteIndex(), entry.attachments()));
        }
    }

    // ── Layout scanning ──────────────────────────────────────────────────

    @BuildStep
    void scanLayouts(RoqProjectBuildItem roqProject,
            ProjectScannerBuildItem scanner,
            RoqSiteConfig config,
            QuteConfig quteConfig,
            RoqFrontMatterDependencyParserConfigsBuildItem depParserConfigs,
            List<RoqFrontMatterQuteMarkupBuildItem> markupList,
            List<RoqFrontMatterHeaderParserBuildItem> headerParserList,
            BuildProducer<RoqFrontMatterScannedLayoutBuildItem> scannedLayoutProducer) throws IOException {
        if (!roqProject.isActive()) {
            return;
        }

        final List<String> ignoredPatterns = buildIgnoredPatterns(config);

        // Scan regular layouts from templates/layouts/ (local + classpath, merged)
        List<ProjectFile> layoutFiles = scanner.query()
                .scopeDir(TEMPLATES_DIR)
                .matchingGlob(LAYOUTS_DIR + "**")
                .matching(buildHtmlTemplateGlob())
                .addExcluded(ignoredPatterns)
                .list();

        // When roq resources live under a sub-directory (e.g. themes provide layouts via classpath),
        // we also query that sub-dir and merge results. mergeByScopedPath deduplicates by scoped path,
        // so local layouts override classpath ones with the same name.
        if (!roqProject.isRoqResourcesInRoot()) {
            List<ProjectFile> roqResourceLayouts = scanner.query()
                    .scopeDir(roqProject.resolveRoqResourceSubDir(TEMPLATES_DIR))
                    .origin(ProjectFile.Origin.ROOT_APPLICATION_RESOURCE, ProjectFile.Origin.DEPENDENCY_RESOURCE)
                    .matchingGlob(LAYOUTS_DIR + "**")
                    .matching(buildHtmlTemplateGlob())
                    .addExcluded(ignoredPatterns)
                    .list();
            layoutFiles = ScanQueryBuilder.mergeByScopedPath(layoutFiles, roqResourceLayouts);
        }

        for (ProjectFile file : layoutFiles) {
            if (!isTemplateTargetHtml(toUnixPath(file.scopedPath()))) {
                continue;
            }
            FrontMatterTemplateMetadata metadata = collectMetadata(file, true, markupList,
                    headerParserList, quteConfig, depParserConfigs.configs());

            LOGGER.debugf("Roq layout scan producing scanned layout '%s'", metadata.templateId());
            scannedLayoutProducer
                    .produce(new RoqFrontMatterScannedLayoutBuildItem(metadata, false));
        }

        // Scan theme layouts from templates/theme-layouts/ (provided by theme dependencies)
        List<ProjectFile> themeLayoutFiles = scanner.query()
                .scopeDir(TEMPLATES_DIR)
                .matchingGlob(THEME_LAYOUTS_DIR + "**")
                .matching(buildHtmlTemplateGlob())
                .addExcluded(ignoredPatterns)
                .list();

        // Same classpath sub-dir merge for theme layouts
        if (!roqProject.isRoqResourcesInRoot()) {
            List<ProjectFile> roqResourceThemeLayouts = scanner.query()
                    .scopeDir(roqProject.resolveRoqResourceSubDir(TEMPLATES_DIR))
                    .origin(ProjectFile.Origin.ROOT_APPLICATION_RESOURCE, ProjectFile.Origin.DEPENDENCY_RESOURCE)
                    .matchingGlob(THEME_LAYOUTS_DIR + "**")
                    .matching(buildHtmlTemplateGlob())
                    .addExcluded(ignoredPatterns)
                    .list();
            themeLayoutFiles = ScanQueryBuilder.mergeByScopedPath(themeLayoutFiles, roqResourceThemeLayouts);
        }

        for (ProjectFile file : themeLayoutFiles) {
            if (!isTemplateTargetHtml(toUnixPath(file.scopedPath()))) {
                continue;
            }
            FrontMatterTemplateMetadata metadata = collectMetadata(file, true, markupList,
                    headerParserList, quteConfig, depParserConfigs.configs());

            LOGGER.debugf("Roq theme-layout scan producing scanned layout '%s'", metadata.templateId());
            scannedLayoutProducer
                    .produce(new RoqFrontMatterScannedLayoutBuildItem(metadata, true));
        }
    }

    // ── Template scanning (partials & non-layout templates) ──────────────

    // Scan for Qute partials and non-layout templates under templates/.
    // Unlike content pages and layouts, these are registered directly as Qute template paths
    // (they skip the assemble/data pipeline — no front matter processing or layout wrapping).
    @BuildStep
    void scanTemplates(RoqProjectBuildItem roqProject,
            ProjectScannerBuildItem scanner,
            RoqSiteConfig config,
            QuteConfig quteConfig,
            BuildProducer<TemplatePathBuildItem> templatePathProducer,
            BuildProducer<GeneratedResourceBuildItem> generatedResourceProducer,
            BuildProducer<NativeImageResourceBuildItem> nativeImageResourceProducer,
            BuildProducer<TemplateRootBuildItem> templateRootProducer) throws IOException {
        if (!roqProject.isActive()) {
            return;
        }

        // When roq resources are not at classpath root, register template root
        if (!roqProject.isRoqResourcesInRoot()) {
            templateRootProducer.produce(new TemplateRootBuildItem(
                    StringPaths.join(roqProject.roqResourceDir(), TEMPLATES_DIR)));
        }

        ParserConfig appParserConfig = quteConfig.altExprSyntax()
                ? TemplatePathBuildItem.ALT_PARSER_CONFIG
                : ParserConfig.DEFAULT;

        List<ProjectFile> files = scanner.query()
                .scopeDir(TEMPLATES_DIR)
                .origin(ProjectFile.Origin.LOCAL_PROJECT_FILE)
                .matching(buildTemplateGlob(quteConfig))
                .exclude("glob:" + LAYOUTS_DIR + "**")
                .exclude("glob:" + THEME_LAYOUTS_DIR + "**")
                .addExcluded(buildIgnoredPatterns(config))
                .list();

        for (ProjectFile file : files) {
            LOGGER.debugf("Roq template scan found in local dir: scopedPath=%s, origin=%s, path=%s",
                    file.scopedPath(), file.origin(), file.file());
            String link = toUnixPath(file.scopedPath());
            String content = new String(file.content(), file.charset());

            if (content.length() > 65535) {
                LOGGER.warnf(
                        "Template '%s' is too large for recording and will be ignored. Consider splitting it into smaller parts.",
                        link);
                continue;
            }

            generatedResourceProducer
                    .produce(new GeneratedResourceBuildItem(
                            "templates/" + link,
                            content.getBytes(StandardCharsets.UTF_8)));
            nativeImageResourceProducer.produce(new NativeImageResourceBuildItem("templates/" + link));
            templatePathProducer.produce(TemplatePathBuildItem.builder()
                    .priority(ROOT_ARCHIVE_PRIORITY)
                    .path(link)
                    .fullPath(file.file())
                    .content(content)
                    .parserConfig(appParserConfig)
                    .extensionInfo(RoqFrontMatterStep6BindProcessor.FEATURE)
                    .build());

        }
    }
}
