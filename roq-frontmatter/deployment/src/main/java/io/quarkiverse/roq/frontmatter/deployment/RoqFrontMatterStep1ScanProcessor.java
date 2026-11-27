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
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import org.jboss.logging.Logger;

import io.quarkiverse.roq.deployment.items.RoqProjectBuildItem;
import io.quarkiverse.roq.exception.RoqException;
import io.quarkiverse.roq.frontmatter.deployment.items.assemble.RoqFrontMatterAttachment;
import io.quarkiverse.roq.frontmatter.deployment.items.scan.FrontMatterTemplateMetadata;
import io.quarkiverse.roq.frontmatter.deployment.items.scan.RoqFrontMatterDependencyParserConfigsBuildItem;
import io.quarkiverse.roq.frontmatter.deployment.items.scan.RoqFrontMatterHeaderParserBuildItem;
import io.quarkiverse.roq.frontmatter.deployment.items.scan.RoqFrontMatterQuteMarkupBuildItem;
import io.quarkiverse.roq.frontmatter.deployment.items.scan.RoqFrontMatterScannedContentBuildItem;
import io.quarkiverse.roq.frontmatter.deployment.items.scan.RoqFrontMatterScannedLayoutBuildItem;
import io.quarkiverse.roq.frontmatter.deployment.util.RoqFrontMatterLanguageUtils;
import io.quarkiverse.roq.frontmatter.runtime.config.ConfiguredCollection;
import io.quarkiverse.roq.frontmatter.runtime.config.RoqFrontMatterConfigException;
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
import io.quarkus.deployment.builditem.LaunchModeBuildItem;
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
        producer.produce(new SuppressNonRuntimeConfigChangedWarningBuildItem("site.collections.*.from-data"));
        producer.produce(new SuppressNonRuntimeConfigChangedWarningBuildItem("site.collections.*.from-data.id-key"));
    }

    // ── Dir declarations ─────────────────────────────────────────────────

    @BuildStep
    @SuppressWarnings("deprecation")
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

        // Multilingual: index.<lang>.<ext> is a translation of the folder's index.<ext> when <lang> is in the
        // language catalogue and is not the default locale. Any other token is an ordinary file.
        final Set<String> languages = RoqFrontMatterLanguageUtils.translationLanguages(config.defaultLocale());

        // First pass: classify files as templates or attachments.
        // Index templates register as attachment owners for sibling non-template files.
        Map<Path, List<RoqFrontMatterAttachment>> pageAttachments = new HashMap<>();
        List<ContentEntry> entries = new ArrayList<>();

        for (ProjectFile f : allContentFiles) {
            String sp = toUnixPath(f.scopedPath());
            final Path spPath = Path.of(sp);
            boolean isTpl = templatePredicate.test(spPath);
            // A translation (index.<lang>.<ext>) is an index template of its folder, like the default variant.
            // It is never the site index: only the default variant can be.
            boolean isTranslation = isTpl && isTranslationIndex(fileName(sp), languages);
            // Same predicate as Step 2: when the feature is on, index.qute.<ext> is an index file too.
            boolean isIdx = isTpl && RoqFrontMatterLanguageUtils.isIndexFile(fileName(sp), languages);
            // Site index selection is unchanged by the feature: only a root index.<html-ext> can be the site index.
            boolean isSiteIdx = isIdx && !isTranslation && sp.startsWith("index.")
                    && INDEX_FILES.contains(fileName(sp));
            Path parent = spPath.getParent();

            if (isTpl) {
                // Determine if this page belongs to a collection (top-level dir matches a collection id).
                // The collection index itself (e.g. posts/index.html) is NOT a collection member —
                // it's a regular page that typically lists the collection via pagination.
                // Translations are not collection members either: only the default variant is listed.
                ConfiguredCollection collection = null;
                String topDirName = spPath.getName(0).toString();
                boolean isCollectionDir = collections.containsKey(topDirName);
                boolean isCollectionIndex = isCollectionDir && isIdx && spPath.getNameCount() == 2;
                if (isCollectionDir && !isCollectionIndex && !isTranslation) {
                    collection = collections.get(topDirName);
                }
                if (isTranslation) {
                    LOGGER.debugf("Roq content scan found translation '%s'", sp);
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
                    pageAttachments.get(ownerDir).add(toAttachment(name, f));
                }
            }
        }

        final Set<String> groupsWithTranslations = validateLanguageLayout(allContentFiles, entries, languages,
                config.defaultLocale());

        // Second pass: collect metadata (front matter, markup) and produce scanned build items
        for (ContentEntry entry : entries) {
            FrontMatterTemplateMetadata metadata = collectMetadata(entry.file(), false, markupList, headerParserList,
                    quteConfig, depParserConfigs.configs());
            if (entry.isIndex()) {
                validateFrontMatterLang(entry, metadata, languages, config.defaultLocale(), groupsWithTranslations);
            }

            LOGGER.debugf("Roq content scan producing scanned template '%s'", metadata.templateId());
            scannedContentProducer.produce(new RoqFrontMatterScannedContentBuildItem(
                    metadata, entry.collection(),
                    entry.isIndex(), entry.isSiteIndex(), entry.attachments()));
        }
    }

    private static String parentDir(String scopedPath) {
        int i = scopedPath.lastIndexOf('/');
        return i < 0 ? "" : scopedPath.substring(0, i);
    }

    private static RoqFrontMatterConfigException languageError(String title, String detail, String hint) {
        return new RoqFrontMatterConfigException(RoqException.builder(title).detail(detail).hint(hint));
    }

    /**
     * Validates the multilingual layout of the content dir.
     *
     * @return the folders (scoped paths) that hold at least one translation
     */
    private static Set<String> validateLanguageLayout(List<ProjectFile> allContentFiles, List<ContentEntry> entries,
            Set<String> languages, String defaultLocale) {
        Map<String, List<String>> defaultFiles = new HashMap<>(); // folder -> default variant files
        Map<String, String> translationFiles = new HashMap<>(); // folder + '#' + lang -> file
        Set<String> translationFolders = new LinkedHashSet<>();
        Set<String> usedLanguages = new HashSet<>();
        for (ContentEntry entry : entries) {
            String sp = toUnixPath(entry.file().scopedPath());
            String name = fileName(sp);
            String folder = parentDir(sp);
            if (!name.startsWith("index.")) {
                continue;
            }
            var plainIndex = RoqFrontMatterLanguageUtils.defaultLocaleSuffix(name, defaultLocale);
            if (plainIndex.isPresent()) {
                throw languageError(
                        "Language '%s' is the default language and cannot be used as a file suffix ('%s')"
                                .formatted(defaultLocale, sp),
                        "The default language uses the plain index file, without a language suffix.",
                        "Rename '%s' to the plain '%s' file.".formatted(name, plainIndex.get()));
            }
            var suffix = RoqFrontMatterLanguageUtils.parse(name, languages);
            if (suffix.isEmpty()) {
                continue;
            }
            if (suffix.get().lang() == null) {
                // Counted through the shared index predicate, like Step 2 does when grouping variants
                if (entry.isIndex()) {
                    defaultFiles.computeIfAbsent(folder, k -> new ArrayList<>()).add(sp);
                }
                continue;
            }
            String lang = suffix.get().lang();
            if (folder.isEmpty()) {
                throw languageError(
                        "Root-level translation '%s' is not supported (root translations are unsupported)".formatted(sp),
                        "Translations are only supported for index files inside a folder, not at the content root.",
                        "Move the translation into a folder next to a default-language index file.");
            }
            String previous = translationFiles.putIfAbsent(folder + "#" + lang, sp);
            if (previous != null) {
                throw languageError(
                        "Duplicate language '%s' in folder '%s' (duplicate language variant)".formatted(lang, folder),
                        "Both '%s' and '%s' are '%s' variants.".formatted(previous, sp, lang),
                        "Keep a single index file per language in each folder.");
            }
            translationFolders.add(folder);
            usedLanguages.add(lang);
        }
        // A language used by a translation must not be a top-level directory name: /<lang>/... would collide.
        Set<String> topDirs = new HashSet<>();
        for (ProjectFile f : allContentFiles) {
            Path sp = Path.of(toUnixPath(f.scopedPath()));
            if (sp.getNameCount() > 1) {
                topDirs.add(sp.getName(0).toString());
            }
        }
        for (String lang : usedLanguages) {
            if (topDirs.contains(lang)) {
                throw languageError(
                        "Language '%s' collides with the top-level content directory '%s' (language/directory collision)"
                                .formatted(lang, lang),
                        "Translations are published under '/%s/', which would collide with the content directory '%s/'."
                                .formatted(lang, lang),
                        "Rename the directory '%s/' or the '%s' translations.".formatted(lang, lang));
            }
        }

        for (String folder : translationFolders) {
            List<String> defaults = defaultFiles.getOrDefault(folder, List.of());
            if (defaults.size() > 1) {
                throw languageError(
                        "Duplicate language '%s' in folder '%s' (duplicate language variant)".formatted(defaultLocale, folder),
                        "%s are all default-language ('%s') variants.".formatted(defaults, defaultLocale),
                        "Keep a single index file per language in each folder.");
            }
            if (defaults.isEmpty()) {
                throw languageError(
                        "Folder '%s' has a translation but is missing its default-language variant".formatted(folder),
                        "A translation (index.<lang>.<ext>) needs a default-language index file (index.<ext>) in the same folder"
                                + " (a default-language variant is required).",
                        "Add 'index.<ext>' to '%s'.".formatted(folder));
            }
        }
        return translationFolders;
    }

    /**
     * Validates the front matter {@code lang} of an index variant that belongs to a group with translations.
     */
    private static void validateFrontMatterLang(ContentEntry entry, FrontMatterTemplateMetadata metadata,
            Set<String> languages, String defaultLocale, Set<String> groupsWithTranslations) {
        String sp = toUnixPath(entry.file().scopedPath());
        var suffix = RoqFrontMatterLanguageUtils.parse(fileName(sp), languages);
        if (suffix.isEmpty()) {
            return;
        }
        Object value = metadata.parsedHeaders().data().getValue("lang");
        if (value == null) {
            return;
        }
        String fmLang = String.valueOf(value);
        if (suffix.get().lang() != null) {
            if (!fmLang.equals(suffix.get().lang())) {
                throw languageError(
                        "Frontmatter lang '%s' of '%s' differs from its file suffix '%s' (frontmatter lang conflict)"
                                .formatted(fmLang, sp, suffix.get().lang()),
                        "A translation's language comes from its file name.",
                        "Remove 'lang' from the front matter or make it '%s'.".formatted(suffix.get().lang()));
            }
        } else if (groupsWithTranslations.contains(parentDir(sp)) && !fmLang.equals(defaultLocale)) {
            throw languageError(
                    "Frontmatter lang '%s' of '%s' differs from site.default-locale '%s' (default variant lang mismatch)"
                            .formatted(fmLang, sp, defaultLocale),
                    "The default variant of a translated article is always in the default locale.",
                    "Remove 'lang' from the front matter or set it to '%s'.".formatted(defaultLocale));
        }
    }

    /**
     * @return true when the file is a translation index ({@code index.<lang>.<ext>}) of a catalogue language.
     */
    private static boolean isTranslationIndex(String fileName, Set<String> languages) {
        return RoqFrontMatterLanguageUtils.parse(fileName, languages)
                .map(suffix -> suffix.lang() != null)
                .orElse(false);
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
            BuildProducer<TemplateRootBuildItem> templateRootProducer,
            LaunchModeBuildItem launchMode) throws IOException {
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

        if (launchMode.getLaunchMode().isDev()) {
            // Qute loads dependency templates through the classloader, which doesn't see files added to a local
            // dependency directory (e.g. a theme module in the same Maven reactor) after dev mode started.
            // Publishing them as generated resources makes new theme partials available after a reload.
            List<ProjectFile> dependencyFiles = scanner.query()
                    .scopeDir(TEMPLATES_DIR)
                    .origin(ProjectFile.Origin.DEPENDENCY_RESOURCE)
                    .matching(buildTemplateGlob(quteConfig))
                    .exclude("glob:" + LAYOUTS_DIR + "**")
                    .exclude("glob:" + THEME_LAYOUTS_DIR + "**")
                    .addExcluded(buildIgnoredPatterns(config))
                    .list();
            for (ProjectFile file : filterLocalDependencyTemplates(dependencyFiles, files)) {
                LOGGER.debugf("Roq template scan found in local dependency dir: scopedPath=%s, path=%s",
                        file.scopedPath(), file.file());
                generatedResourceProducer.produce(new GeneratedResourceBuildItem(
                        "templates/" + toUnixPath(file.scopedPath()), file.content()));
            }
        }
    }
}
