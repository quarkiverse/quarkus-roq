package io.quarkiverse.roq.frontmatter.deployment.scan;

import static io.quarkiverse.roq.frontmatter.deployment.data.RoqFrontMatterDataProcessor.DRAFT_KEY;
import static io.quarkiverse.roq.frontmatter.deployment.scan.RoqFrontMatterHeaderParserBuildItem.FRONTMATTER_HEADER_PARSER_PRIORITY;
import static io.quarkiverse.roq.frontmatter.deployment.scan.RoqFrontMatterHeaderParserBuildItem.resolveHeaderParsers;
import static io.quarkiverse.roq.frontmatter.deployment.scan.RoqFrontMatterQuteMarkupBuildItem.findMarkupFilter;
import static io.quarkiverse.roq.frontmatter.runtime.RoqTemplates.LAYOUTS_DIR;
import static io.quarkiverse.roq.frontmatter.runtime.RoqTemplates.THEME_LAYOUTS_DIR_PREFIX;
import static io.quarkiverse.roq.util.PathUtils.addTrailingSlash;
import static io.quarkiverse.roq.util.PathUtils.fileName;
import static io.quarkiverse.roq.util.PathUtils.getExtension;
import static io.quarkiverse.roq.util.PathUtils.removeExtension;
import static io.quarkiverse.roq.util.PathUtils.toUnixPath;
import static io.quarkus.qute.deployment.TemplatePathBuildItem.ROOT_ARCHIVE_PRIORITY;
import static java.util.function.Predicate.not;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.jboss.logging.Logger;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.dataformat.yaml.YAMLMapper;

import io.quarkiverse.roq.deployment.items.RoqJacksonBuildItem;
import io.quarkiverse.roq.deployment.items.RoqProjectBuildItem;
import io.quarkiverse.roq.frontmatter.deployment.RoqFrontMatterProcessor;
import io.quarkiverse.roq.frontmatter.deployment.data.RoqFrontMatterDataModificationBuildItem;
import io.quarkiverse.roq.frontmatter.deployment.data.RoqFrontMatterDataModificationBuildItem.SourceData;
import io.quarkiverse.roq.frontmatter.deployment.exception.RoqFrontMatterReadingException;
import io.quarkiverse.roq.frontmatter.deployment.exception.RoqSiteScanningException;
import io.quarkiverse.roq.frontmatter.deployment.scan.RoqFrontMatterQuteMarkupBuildItem.WrapperFilter;
import io.quarkiverse.roq.frontmatter.deployment.scan.RoqFrontMatterRawTemplateBuildItem.Attachment;
import io.quarkiverse.roq.frontmatter.deployment.scan.RoqFrontMatterRawTemplateBuildItem.TemplateType;
import io.quarkiverse.roq.frontmatter.runtime.config.ConfiguredCollection;
import io.quarkiverse.roq.frontmatter.runtime.config.RoqSiteConfig;
import io.quarkiverse.roq.frontmatter.runtime.model.PageFiles;
import io.quarkiverse.roq.frontmatter.runtime.model.SourceFile;
import io.quarkiverse.roq.frontmatter.runtime.model.TemplateSource;
import io.quarkiverse.roq.util.PathUtils;
import io.quarkiverse.web.bundler.spi.items.WebBundlerWatchedDirBuildItem;
import io.quarkus.deployment.IsDevelopment;
import io.quarkus.deployment.annotations.BuildProducer;
import io.quarkus.deployment.annotations.BuildStep;
import io.quarkus.deployment.builditem.GeneratedResourceBuildItem;
import io.quarkus.deployment.builditem.HotDeploymentWatchedFileBuildItem;
import io.quarkus.deployment.builditem.nativeimage.NativeImageResourceBuildItem;
import io.quarkus.paths.PathVisit;
import io.quarkus.qute.deployment.TemplatePathBuildItem;
import io.quarkus.qute.deployment.TemplateRootBuildItem;
import io.quarkus.qute.runtime.QuteConfig;
import io.vertx.core.json.JsonObject;

public class RoqFrontMatterScanProcessor {
    private static final Logger LOGGER = org.jboss.logging.Logger.getLogger(RoqFrontMatterScanProcessor.class);
    public static final Pattern FRONTMATTER_PATTERN = Pattern.compile("^---\\v.*?---(?:\\v|$)", Pattern.DOTALL);

    private static final String LAYOUT_KEY = "layout";
    public static final String ESCAPE_KEY = "escape";

    private static final WrapperFilter ESCAPE_FILTER = new WrapperFilter("{|", "|}");
    public static final String TEMPLATES_DIR = "templates";

    // We might need to allow plugins to contribute to this at some point
    private static final Set<String> HTML_OUTPUT_EXTENSIONS = Set.of("md", "markdown", "html", "htm", "xhtml", "asciidoc",
            "adoc");
    private static final Set<String> INDEX_FILES = HTML_OUTPUT_EXTENSIONS.stream().map(e -> "index." + e)
            .collect(Collectors.toSet());
    public static final String LAYOUTS_DIR_PREFIX = addTrailingSlash(LAYOUTS_DIR);

    @BuildStep
    void registerEscapedTemplates(RoqSiteConfig config,
            BuildProducer<RoqFrontMatterDataModificationBuildItem> dataModificationProducer) {
        dataModificationProducer.produce(new RoqFrontMatterDataModificationBuildItem(sourceData -> {
            if (sourceData.type().isPage() && !sourceData.fm().containsKey(ESCAPE_KEY)
                    && isPageEscaped(config).test(sourceData.relativePath())) {
                sourceData.fm().put(ESCAPE_KEY, true);
            }
            return sourceData.fm();
        }));
    }

    @BuildStep
    void amendDraftContent(RoqSiteConfig config,
            BuildProducer<RoqFrontMatterDataModificationBuildItem> dataModificationProducer) {
        dataModificationProducer.produce(new RoqFrontMatterDataModificationBuildItem(sourceData -> {
            var isDraft = sourceData.type().isPage() && sourceData.collection() != null
                    && sourceData.relativePath().contains(config.draftDirectory() + "/");
            sourceData.fm().put(DRAFT_KEY, isDraft);
            return sourceData.fm();
        }));
    }

    @BuildStep
    RoqFrontMatterHeaderParserBuildItem registerFrontMatterParse(RoqJacksonBuildItem jackson) {
        return new RoqFrontMatterHeaderParserBuildItem(templateContext -> hasFrontMatter(templateContext.content()), c -> {
            try {
                return readFM(jackson.getYamlMapper(), c.content());
            } catch (JsonProcessingException | IllegalArgumentException e) {
                throw new RoqFrontMatterReadingException(
                        "Error reading YAML FrontMatter block (enclosed by '---') in file: %s".formatted(c.templatePath()));
            }
        }, RoqFrontmatterTemplateUtils::stripFrontMatter, FRONTMATTER_HEADER_PARSER_PRIORITY);
    }

    @BuildStep(onlyIf = IsDevelopment.class)
    void watch(RoqSiteConfig config, RoqProjectBuildItem roqProject,
            BuildProducer<WebBundlerWatchedDirBuildItem> webBundlerWatch) {
        webBundlerWatch
                .produce(new WebBundlerWatchedDirBuildItem(roqProject.project().roqDir().resolve(config.contentDir())));
        webBundlerWatch
                .produce(new WebBundlerWatchedDirBuildItem(roqProject.project().roqDir().resolve(config.publicDir())));
        webBundlerWatch.produce(new WebBundlerWatchedDirBuildItem(roqProject.project().roqDir().resolve(TEMPLATES_DIR)));
    }

    @BuildStep
    void scan(RoqProjectBuildItem roqProject,
            List<RoqFrontMatterQuteMarkupBuildItem> markupList,
            List<RoqFrontMatterHeaderParserBuildItem> headerParserList,
            RoqJacksonBuildItem jackson,
            QuteConfig quteConfig,
            BuildProducer<RoqFrontMatterRawTemplateBuildItem> dataProducer,
            BuildProducer<TemplatePathBuildItem> templatePathProducer,
            BuildProducer<GeneratedResourceBuildItem> generatedResourceProducer,
            BuildProducer<NativeImageResourceBuildItem> nativeImageResourceProducer,
            BuildProducer<TemplateRootBuildItem> templateRootProducer,
            List<RoqFrontMatterDataModificationBuildItem> dataModifications,
            RoqSiteConfig siteConfig,
            BuildProducer<HotDeploymentWatchedFileBuildItem> watch) {
        try {
            dataModifications.sort(Comparator.comparing(RoqFrontMatterDataModificationBuildItem::order));
            List<RoqFrontMatterRawTemplateBuildItem> items = resolveItems(roqProject,
                    quteConfig,
                    siteConfig,
                    markupList,
                    headerParserList,
                    watch,
                    dataModifications,
                    templatePathProducer,
                    generatedResourceProducer,
                    nativeImageResourceProducer,
                    templateRootProducer);

            Set<String> ids = items.stream().map(RoqFrontMatterRawTemplateBuildItem::id).collect(Collectors.toSet());

            for (RoqFrontMatterRawTemplateBuildItem item : items) {
                if (item.type().isThemeLayout()) {
                    // Check and apply theme layout if no override exist for this layout
                    String layoutId = removeThemePrefix(item.id());
                    if (!ids.contains(layoutId)) {
                        produceRawTemplate(dataProducer, new RoqFrontMatterRawTemplateBuildItem(
                                item.templateSource().changeIds(RoqFrontMatterScanProcessor::removeThemePrefix),
                                item.layout(),
                                TemplateType.LAYOUT,
                                item.data(),
                                item.collection(),
                                item.generatedTemplate(),
                                item.generatedContentTemplate(),
                                item.attachments()));
                    }
                }
                produceRawTemplate(dataProducer, item);
            }

        } catch (IOException e) {
            throw new RoqSiteScanningException("Unable to scan the Roq project", e);
        }
    }

    private static Predicate<String> isPageEscaped(RoqSiteConfig config) {
        return path -> config.escapedPages().orElse(List.of()).stream()
                .anyMatch(s -> Path.of("").getFileSystem().getPathMatcher("glob:" + s)
                        .matches(Path.of(path)));
    }

    private static void produceRawTemplate(BuildProducer<RoqFrontMatterRawTemplateBuildItem> dataProducer,
            RoqFrontMatterRawTemplateBuildItem item) {
        LOGGER.debugf("Roq is producing a raw template '%s'", item.templateSource().generatedQuteId());
        dataProducer.produce(item);
    }

    public static String removeThemePrefix(String id) {
        return id.replace(getLayoutsDir(TemplateType.THEME_LAYOUT), getLayoutsDir(TemplateType.LAYOUT));
    }

    public List<RoqFrontMatterRawTemplateBuildItem> resolveItems(RoqProjectBuildItem roqProject,
            QuteConfig quteConfig,
            RoqSiteConfig config,
            List<RoqFrontMatterQuteMarkupBuildItem> markupList,
            List<RoqFrontMatterHeaderParserBuildItem> headerParserList,
            BuildProducer<HotDeploymentWatchedFileBuildItem> watch,
            List<RoqFrontMatterDataModificationBuildItem> dataModifications,
            BuildProducer<TemplatePathBuildItem> templatePathProducer,
            BuildProducer<GeneratedResourceBuildItem> generatedResourceProducer,
            BuildProducer<NativeImageResourceBuildItem> nativeImageResourceProducer,
            BuildProducer<TemplateRootBuildItem> templateRootProducer) throws IOException {
        List<RoqFrontMatterRawTemplateBuildItem> items = new ArrayList<>();
        roqProject.consumeRoqDir(
                createRoqDirConsumer(quteConfig, config, markupList, headerParserList, watch, dataModifications,
                        templatePathProducer, generatedResourceProducer, nativeImageResourceProducer, items));

        // Scan for layouts & theme-layouts in classpath root
        RoqProjectBuildItem.visitRuntimeResources(TEMPLATES_DIR,
                t -> {
                    scanLayouts(quteConfig, config, markupList, headerParserList, watch, dataModifications, items,
                            t.getPath().getParent(),
                            t.getPath(),
                            TemplateType.LAYOUT);
                    scanLayouts(quteConfig, config, markupList, headerParserList, watch, dataModifications, items,
                            t.getPath().getParent(),
                            t.getPath(),
                            TemplateType.THEME_LAYOUT);
                });

        // Scan for content in the classpath in the resource roq dir (could be classpath root or custom)
        roqProject.consumePathFromRoqResourceDir(config.contentDir(),
                l -> {
                    watchResourceDir(watch, l);
                    scanContent(quteConfig, config, markupList, headerParserList, watch, dataModifications, items,
                            l.getPath().getParent(),
                            l.getPath());
                });

        // When the resource roq dir is not the classpath root
        if (!roqProject.isRoqResourcesInRoot()) {
            // We need to produce the template root so Qute looks for templates
            templateRootProducer.produce(new TemplateRootBuildItem(PathUtils.join(roqProject.roqResourceDir(), TEMPLATES_DIR)));
            // and scan for layouts in this directory
            roqProject.consumePathFromRoqResourceDir(TEMPLATES_DIR,
                    l -> scanLayouts(quteConfig, config, markupList, headerParserList, watch, dataModifications, items,
                            l.getPath().getParent(), l.getPath(),
                            TemplateType.LAYOUT));
        }

        return items;
    }

    private static Consumer<Path> createRoqDirConsumer(
            QuteConfig quteConfig,
            RoqSiteConfig config,
            List<RoqFrontMatterQuteMarkupBuildItem> markupList,
            List<RoqFrontMatterHeaderParserBuildItem> headerParserList, BuildProducer<HotDeploymentWatchedFileBuildItem> watch,
            List<RoqFrontMatterDataModificationBuildItem> dataModifications,
            BuildProducer<TemplatePathBuildItem> templatePathProducer,
            BuildProducer<GeneratedResourceBuildItem> generatedResourceProducer,
            BuildProducer<NativeImageResourceBuildItem> nativeImageResourceProducer,
            List<RoqFrontMatterRawTemplateBuildItem> items) {
        return siteDir -> {
            if (!Files.isDirectory(siteDir)) {
                return;
            }

            // We scan Qute templates manually outside of resources for now
            final Path templatesDir = siteDir.resolve(TEMPLATES_DIR);
            watchDirectory(templatesDir, watch);
            scanTemplates(quteConfig, config, watch, templatePathProducer, generatedResourceProducer,
                    nativeImageResourceProducer, templatesDir);
            // No need to ignore the template as it's not a template root
            scanLayouts(quteConfig, config, markupList, headerParserList, watch, dataModifications, items, siteDir,
                    templatesDir,
                    TemplateType.LAYOUT);
            final Path contentDir = siteDir.resolve(config.contentDir());
            watchDirectory(contentDir, watch);
            scanContent(quteConfig, config, markupList, headerParserList, watch, dataModifications, items, siteDir,
                    contentDir);
        };
    }

    private static void scanContent(QuteConfig quteConfig, RoqSiteConfig config,
            List<RoqFrontMatterQuteMarkupBuildItem> markupList,
            List<RoqFrontMatterHeaderParserBuildItem> headerParserList, BuildProducer<HotDeploymentWatchedFileBuildItem> watch,
            List<RoqFrontMatterDataModificationBuildItem> dataModifications, List<RoqFrontMatterRawTemplateBuildItem> items,
            Path siteDir,
            Path contentDir) {
        if (!Files.isDirectory(contentDir)) {
            return;
        }
        // scan content
        final Map<String, ConfiguredCollection> collections = config.collections().stream()
                .collect(Collectors.toMap(ConfiguredCollection::id, Function.identity()));
        try (Stream<Path> stream = Files.walk(contentDir)) {
            stream
                    .filter(Files::isRegularFile)
                    .filter(not(isFileExcluded(contentDir.getParent(), config)))
                    .filter(isTemplate(quteConfig))
                    .forEach(p -> {
                        final Path relativize = contentDir.relativize(p);
                        final String topDirName = relativize.getName(0).toString();
                        final boolean isCollectionDir = collections.containsKey(topDirName);
                        final boolean isCollectionIndex = isCollectionDir
                                && INDEX_FILES.contains(p.getFileName().toString())
                                && relativize.getNameCount() == 2;
                        ConfiguredCollection collection = null;
                        TemplateType type = TemplateType.NORMAL_PAGE;
                        if (isCollectionDir && !isCollectionIndex) {
                            collection = collections.get(topDirName);
                            type = TemplateType.DOCUMENT_PAGE;
                        }
                        addBuildItem(siteDir, contentDir, items, quteConfig, config, watch,
                                markupList,
                                headerParserList,
                                dataModifications,
                                collection,
                                type).accept(p);
                    });
        } catch (IOException e) {
            throw new RoqSiteScanningException(
                    "Unable to scan content files at location: %s".formatted(contentDir), e);
        }

    }

    private static void watchDirectory(Path dir, BuildProducer<HotDeploymentWatchedFileBuildItem> watch) {
        watchDirectory(dir, watch, true);
    }

    private static void watchDirectory(Path dir, BuildProducer<HotDeploymentWatchedFileBuildItem> watch, boolean recursive) {
        if (!Files.isDirectory(dir)) {
            return;
        }
        try (var stream = Files.walk(dir, recursive ? Integer.MAX_VALUE : 1)) {
            stream.forEach(f -> {
                watch.produce(HotDeploymentWatchedFileBuildItem.builder().setLocation(f.toAbsolutePath().toString()).build());
                if (LOGGER.isDebugEnabled()) {
                    LOGGER.debugf("Watching %s for changes", f);
                }
            });
        } catch (IOException e) {
            throw new RoqSiteScanningException("Unable to read directory: %s".formatted(dir), e);
        }
    }

    private static void watchResourceDir(BuildProducer<HotDeploymentWatchedFileBuildItem> watch, PathVisit l) {
        final String dir = l.getRelativePath();
        watch.produce(HotDeploymentWatchedFileBuildItem.builder().setLocationPredicate(p -> p.startsWith(dir)).build());
        if (LOGGER.isDebugEnabled()) {
            LOGGER.debugf("Watching resources %s for changes", dir);
        }
    }

    private static void scanLayouts(QuteConfig quteConfig,
            RoqSiteConfig config,
            List<RoqFrontMatterQuteMarkupBuildItem> markupList,
            List<RoqFrontMatterHeaderParserBuildItem> headerParserList,
            BuildProducer<HotDeploymentWatchedFileBuildItem> watch,
            List<RoqFrontMatterDataModificationBuildItem> dataModifications,
            List<RoqFrontMatterRawTemplateBuildItem> items,
            Path siteDir,
            Path templatesRoot,
            TemplateType type) {
        final String dir = getLayoutsDir(type);
        Path layoutsDir = templatesRoot.resolve(dir);

        if (!Files.isDirectory(layoutsDir)) {
            return;
        }

        // scan layouts and templates
        try (Stream<Path> stream = Files.walk(layoutsDir)) {
            final Consumer<Path> layoutsConsumer = addBuildItem(siteDir, templatesRoot, items, quteConfig, config,
                    watch,
                    markupList,
                    headerParserList,
                    dataModifications,
                    null,
                    type);
            stream
                    .filter(Files::isRegularFile)
                    .filter(not(isFileExcluded(templatesRoot.getParent(), config)))
                    .filter(isTemplate(quteConfig))
                    .filter(RoqFrontMatterScanProcessor::isTemplateTargetHtml)
                    .forEach(layoutsConsumer);

        } catch (IOException e) {
            throw new RoqSiteScanningException(
                    "Error while scanning layouts directory: %s".formatted(templatesRoot), e);
        }
    }

    private static String getLayoutsDir(TemplateType type) {
        if (type.isThemeLayout()) {
            return THEME_LAYOUTS_DIR_PREFIX + LAYOUTS_DIR;
        }
        return LAYOUTS_DIR;
    }

    private static void scanTemplates(QuteConfig quteConfig,
            RoqSiteConfig config,
            BuildProducer<HotDeploymentWatchedFileBuildItem> watch,
            BuildProducer<TemplatePathBuildItem> templatePathProducer,
            BuildProducer<GeneratedResourceBuildItem> generatedResourceProducer,
            BuildProducer<NativeImageResourceBuildItem> nativeImageResourceProducer,
            Path templatesRoot) {
        if (!Files.isDirectory(templatesRoot)) {
            return;
        }
        // scan templates
        try (Stream<Path> stream = Files.walk(templatesRoot)) {
            stream
                    .filter(Files::isRegularFile)
                    .filter(isTemplate(quteConfig))
                    .filter(not(isFileExcluded(templatesRoot.getParent(), config)))
                    .forEach(p -> {
                        final String dirName = templatesRoot.relativize(p).getName(0).toString();
                        if (LAYOUTS_DIR.equals(dirName)) {
                            return;
                        }
                        // add Qute templates
                        try {
                            final String link = toUnixPath(templatesRoot.relativize(p).toString());
                            final String content = Files.readString(p, StandardCharsets.UTF_8);
                            if (content.length() > 65535) {
                                LOGGER.warnf(
                                        "Template '%s' is too large for recording and will be ignored. Consider splitting it into smaller parts.",
                                        link);
                                return;
                            }
                            generatedResourceProducer
                                    .produce(new GeneratedResourceBuildItem(
                                            "templates/" + link,
                                            content.getBytes(StandardCharsets.UTF_8)));
                            nativeImageResourceProducer.produce(new NativeImageResourceBuildItem("templates/" + link));
                            templatePathProducer.produce(TemplatePathBuildItem.builder()
                                    .priority(ROOT_ARCHIVE_PRIORITY)
                                    .path(link)
                                    .fullPath(p)
                                    .content(content)
                                    .extensionInfo(RoqFrontMatterProcessor.FEATURE)
                                    .build());
                        } catch (IOException e) {
                            throw new RoqSiteScanningException(
                                    "Error while reading template file: %s".formatted(p), e);
                        }
                    });
        } catch (IOException e) {
            throw new RoqSiteScanningException(
                    "Error while reading templates dir: %s".formatted(templatesRoot), e);
        }
    }

    public static Predicate<Path> isTemplate(QuteConfig config) {
        HashSet<String> suffixes = new HashSet<>(config.suffixes());
        suffixes.addAll(HTML_OUTPUT_EXTENSIONS);
        return path -> suffixes.contains(getExtension(path.toString()));
    }

    @SuppressWarnings("unchecked")
    private static Consumer<Path> addBuildItem(
            Path siteDir,
            Path referenceDir,
            List<RoqFrontMatterRawTemplateBuildItem> items,
            QuteConfig quteConfig,
            RoqSiteConfig config,
            BuildProducer<HotDeploymentWatchedFileBuildItem> watch,
            List<RoqFrontMatterQuteMarkupBuildItem> markupList,
            List<RoqFrontMatterHeaderParserBuildItem> headerParserList,
            List<RoqFrontMatterDataModificationBuildItem> dataModifications,
            ConfiguredCollection collection,
            TemplateType type) {
        return file -> {
            final String relativePath = toUnixPath(siteDir.relativize(file).normalize().toString());
            String referencePath = toUnixPath(referenceDir.relativize(file).normalize().toString());
            SourceFile sourceFile = new SourceFile(toUnixPath(siteDir.normalize().toAbsolutePath().toString()), relativePath);
            final String fullContent;
            try {
                fullContent = Files.readString(file, StandardCharsets.UTF_8);
            } catch (IOException e) {
                throw new RoqSiteScanningException(
                        "Error while reading template file: %s".formatted(sourceFile.absolutePath()), e);
            }
            TemplateContext templateContext = new TemplateContext(file, referencePath, fullContent);
            RoqFrontMatterQuteMarkupBuildItem markup = findMarkupFilter(markupList, templateContext);
            List<RoqFrontMatterHeaderParserBuildItem> headerParsers = resolveHeaderParsers(headerParserList,
                    templateContext);
            String cleanPath = replaceWhitespaceChars(referencePath);
            final String templateOutputPath = removeExtension(cleanPath)
                    + RoqFrontmatterTemplateUtils.resolveOutputExtension(markup != null, templateContext);
            String id = type.isPage() ? referencePath : removeExtension(referencePath);

            JsonObject data = new JsonObject();
            String content = fullContent;

            for (RoqFrontMatterHeaderParserBuildItem headerParser : headerParsers) {
                data.mergeIn(headerParser.parse().apply(templateContext), true);
                content = headerParser.removeHeader().apply(content);
            }

            final boolean isHtml = isTemplateTargetHtml(file);

            final boolean isHtmlPartialPage = type.isPage() && isHtml && !(content.toLowerCase(Locale.ROOT).contains("<html")
                    || content.toLowerCase(Locale.ROOT).contains("<!doctype"));

            final String defaultLayout = isHtmlPartialPage
                    ? (collection != null ? collection.layout() : config.pageLayout().orElse(null))
                    : null;

            final String layoutId = RoqFrontmatterTemplateUtils.normalizedLayout(config.theme(),
                    data.getString(LAYOUT_KEY),
                    defaultLayout);

            for (RoqFrontMatterDataModificationBuildItem modification : dataModifications) {
                data = modification.modifier().modify(new SourceData(file, referencePath, collection, type, data));
            }

            final boolean escaped = Boolean.parseBoolean(data.getString(ESCAPE_KEY, "false"));
            final WrapperFilter escapeFilter = getEscapeFilter(escaped);
            final WrapperFilter includeFilter = RoqFrontmatterTemplateUtils.getIncludeFilter(layoutId);
            final String escapedContent = escapeFilter.apply(content);
            final String contentWithMarkup = markup != null ? markup.toWrapperFilter().apply(escapedContent) : escapedContent;
            final String generatedTemplate = includeFilter.apply(contentWithMarkup);

            final String fileName = fileName(referencePath);
            var isIndex = type.isPage() && INDEX_FILES.contains(fileName);
            var isSiteIndex = isHtml && id.startsWith("index."); // the site index is at the root of the site

            TemplateSource source = TemplateSource.create(
                    id,
                    getMarkup(isHtml, markup),
                    sourceFile,
                    referencePath,
                    templateOutputPath,
                    type.isLayout() || type.isThemeLayout(),
                    isHtml,
                    isIndex,
                    isSiteIndex);

            List<Attachment> attachments = null;
            // Scan for files
            if (isIndex) {
                attachments = new ArrayList<>();
                if (isSiteIndex) {
                    // Support legacy static dir
                    scanAttachments(true, true, siteDir, config, quteConfig, watch, attachments, siteDir,
                            siteDir.resolve(config.staticDir()));
                    // Public dir
                    scanAttachments(true, true, siteDir, config, quteConfig, watch, attachments,
                            siteDir.resolve(config.publicDir()),
                            siteDir.resolve(config.publicDir()));
                } else {
                    // Attachments are in the index parent dir
                    scanAttachments(true, false, siteDir, config, quteConfig, watch, attachments, file.getParent(),
                            file.getParent());
                }

            }

            items.add(new RoqFrontMatterRawTemplateBuildItem(source, layoutId, type, data, collection,
                    generatedTemplate,
                    contentWithMarkup, attachments));

        };
    }

    private static String getMarkup(boolean isHtml, RoqFrontMatterQuteMarkupBuildItem markup) {
        if (isHtml) {
            return markup != null ? markup.name() : "html";
        }
        return null;
    }

    private static void scanAttachments(boolean isAttachmentRoot,
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
            return; // this subdir is a bundle → skip
        }
        watchDirectory(attachmentDir, watch, false);
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(attachmentDir)) {
            for (Path entry : stream) {
                if (Files.isDirectory(entry)) {
                    scanAttachments(false, isStaticDir, siteDir, config, quteConfig, watch, attachments, refDir, entry);
                } else if (Files.isRegularFile(entry)
                        && !isFileExcluded(siteDir, config).test(entry)
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

    private static String resolveAttachmentLink(RoqSiteConfig config, Path p, Path pageDir) {
        final String relative = toUnixPath(pageDir.relativize(p).toString());
        if (config.slugifyFiles()) {
            return PageFiles.slugifyFile(relative);
        }
        return relative;
    }

    @SuppressWarnings("unchecked")
    private static JsonObject readFM(YAMLMapper mapper, String fullContent)
            throws JsonProcessingException, IllegalArgumentException {
        final String frontMatter = getFrontMatter(fullContent);
        if (frontMatter.isBlank()) {
            return new JsonObject();
        }
        JsonNode rootNode = mapper.readTree(frontMatter);
        final Map<String, Object> map = mapper.convertValue(rootNode, Map.class);
        return new JsonObject(map);
    }

    private static String replaceWhitespaceChars(String sourcePath) {
        return sourcePath.replaceAll("\\s+", "-");
    }

    private static WrapperFilter getEscapeFilter(boolean escaped) {
        if (!escaped) {
            return WrapperFilter.EMPTY;
        }
        return ESCAPE_FILTER;
    }

    private static Predicate<Path> isFileExcluded(Path siteDir, RoqSiteConfig config) {
        List<String> ignored = new ArrayList<>(config.defaultIgnoredFiles());
        config.ignoredFiles().ifPresent(ignored::addAll);
        return isFileExcluded(siteDir, ignored);
    }

    static Predicate<Path> isFileExcluded(Path siteDir, List<String> ignoredFiles) {
        return path -> ignoredFiles.stream()
                .anyMatch(s -> path.getFileSystem().getPathMatcher("glob:" + s).matches(siteDir.relativize(path)));
    }

    private static boolean isTemplateTargetHtml(Path path) {
        final String extension = getExtension(path.toString());
        return HTML_OUTPUT_EXTENSIONS.contains(extension);
    }

    private static String getFrontMatter(String content) {
        int endOfFrontMatter = content.indexOf("---", 3);
        if (endOfFrontMatter != -1) {
            return content.substring(3, endOfFrontMatter).trim();
        }
        return "";
    }

    private static boolean hasFrontMatter(String content) {
        return FRONTMATTER_PATTERN.matcher(content).find();
    }
}
