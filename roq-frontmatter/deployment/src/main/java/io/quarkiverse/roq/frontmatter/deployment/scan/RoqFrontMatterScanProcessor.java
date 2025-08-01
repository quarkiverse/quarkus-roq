package io.quarkiverse.roq.frontmatter.deployment.scan;

import static io.quarkiverse.roq.frontmatter.deployment.scan.RoqFrontMatterHeaderParserBuildItem.FRONTMATTER_HEADER_PARSER_PRIORITY;
import static io.quarkiverse.roq.frontmatter.deployment.scan.RoqFrontMatterHeaderParserBuildItem.resolveHeaderParsers;
import static io.quarkiverse.roq.frontmatter.deployment.scan.RoqFrontMatterQuteMarkupBuildItem.findMarkupFilter;
import static io.quarkiverse.roq.frontmatter.deployment.scan.RoqFrontMatterQuteMarkupBuildItem.QuteMarkupSection.find;
import static io.quarkiverse.roq.frontmatter.runtime.RoqTemplates.*;
import static io.quarkiverse.roq.util.PathUtils.*;
import static io.quarkus.qute.deployment.TemplatePathBuildItem.ROOT_ARCHIVE_PRIORITY;
import static java.util.function.Predicate.not;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoField;
import java.util.*;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.regex.Matcher;
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
import io.quarkiverse.roq.frontmatter.deployment.exception.RoqThemeConfigurationException;
import io.quarkiverse.roq.frontmatter.deployment.scan.RoqFrontMatterQuteMarkupBuildItem.WrapperFilter;
import io.quarkiverse.roq.frontmatter.deployment.scan.RoqFrontMatterRawTemplateBuildItem.Attachment;
import io.quarkiverse.roq.frontmatter.deployment.scan.RoqFrontMatterRawTemplateBuildItem.TemplateType;
import io.quarkiverse.roq.frontmatter.runtime.config.ConfiguredCollection;
import io.quarkiverse.roq.frontmatter.runtime.config.RoqSiteConfig;
import io.quarkiverse.roq.frontmatter.runtime.model.PageFiles;
import io.quarkiverse.roq.frontmatter.runtime.model.PageInfo;
import io.quarkiverse.roq.util.PathUtils;
import io.quarkus.deployment.annotations.BuildProducer;
import io.quarkus.deployment.annotations.BuildStep;
import io.quarkus.deployment.builditem.HotDeploymentWatchedFileBuildItem;
import io.quarkus.paths.PathVisit;
import io.quarkus.qute.deployment.TemplatePathBuildItem;
import io.quarkus.qute.deployment.TemplateRootBuildItem;
import io.quarkus.qute.runtime.QuteConfig;
import io.vertx.core.http.impl.MimeMapping;
import io.vertx.core.json.JsonObject;

public class RoqFrontMatterScanProcessor {
    private static final Logger LOGGER = org.jboss.logging.Logger.getLogger(RoqFrontMatterScanProcessor.class);
    public static final Pattern FRONTMATTER_PATTERN = Pattern.compile("^---\\v.*?---\\v", Pattern.DOTALL);
    private static final String DRAFT_KEY = "draft";
    private static final String DATE_KEY = "date";
    private static final String LAYOUT_KEY = "layout";
    public static final String ESCAPE_KEY = "escape";
    private static final Pattern FILE_NAME_DATE_PATTERN = Pattern.compile("(\\d{4}-\\d{2}-\\d{2})");
    private static final WrapperFilter ESCAPE_FILTER = new WrapperFilter("{|", "|}");
    public static final String TEMPLATES_DIR = "templates";

    // We might need to allow plugins to contribute to this at some point
    private static final Set<String> HTML_OUTPUT_EXTENSIONS = Set.of("md", "markdown", "html", "htm", "xhtml", "asciidoc",
            "adoc");

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
    RoqFrontMatterHeaderParserBuildItem registerFrontMatterParse(RoqJacksonBuildItem jackson) {
        return new RoqFrontMatterHeaderParserBuildItem(templateContext -> hasFrontMatter(templateContext.content()), c -> {
            try {
                return readFM(jackson.getYamlMapper(), c.content());
            } catch (JsonProcessingException | IllegalArgumentException e) {
                throw new RoqFrontMatterReadingException(
                        "Error reading YAML FrontMatter block (enclosed by '---') in file: %s".formatted(c.templatePath()));
            }
        }, RoqFrontMatterScanProcessor::stripFrontMatter, FRONTMATTER_HEADER_PARSER_PRIORITY);
    }

    @BuildStep
    void scan(RoqProjectBuildItem roqProject,
            List<RoqFrontMatterQuteMarkupBuildItem> markupList,
            List<RoqFrontMatterHeaderParserBuildItem> headerParserList,
            RoqJacksonBuildItem jackson,
            QuteConfig quteConfig,
            BuildProducer<RoqFrontMatterRawTemplateBuildItem> dataProducer,
            BuildProducer<RoqFrontMatterStaticFileBuildItem> staticFilesProducer,
            BuildProducer<TemplatePathBuildItem> templatePathProducer,
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
                    templateRootProducer,
                    staticFilesProducer);

            Set<String> ids = items.stream().map(RoqFrontMatterRawTemplateBuildItem::id).collect(Collectors.toSet());

            for (RoqFrontMatterRawTemplateBuildItem item : items) {
                if (item.type().isThemeLayout()) {
                    // Check and apply theme layout overrides if none exist for this item to specify that this check only occurs if there’s no pre-existing override.
                    String layoutId = removeThemePrefix(item.id());
                    if (!ids.contains(layoutId)) {
                        produceRawTemplate(dataProducer, new RoqFrontMatterRawTemplateBuildItem(
                                item.info().changeIds(RoqFrontMatterScanProcessor::removeThemePrefix),
                                item.layout(),
                                TemplateType.LAYOUT,
                                item.data(),
                                item.collection(),
                                item.generatedTemplate(),
                                item.generatedContentTemplate(),
                                item.published(),
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
        LOGGER.debugf("Roq is producing a raw template '%s'", item.info().generatedTemplateId());
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
            BuildProducer<TemplateRootBuildItem> templateRootProducer,
            BuildProducer<RoqFrontMatterStaticFileBuildItem> staticFilesProducer) throws IOException {
        List<RoqFrontMatterRawTemplateBuildItem> items = new ArrayList<>();
        roqProject.consumeRoqDir(
                createRoqDirConsumer(quteConfig, config, markupList, headerParserList, watch, dataModifications,
                        staticFilesProducer, templatePathProducer, items));

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

        // Scan for content in the classpath in the resource roq dir (could be classpath root)
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
            BuildProducer<RoqFrontMatterStaticFileBuildItem> staticFilesProducer,
            BuildProducer<TemplatePathBuildItem> templatePathProducer,
            List<RoqFrontMatterRawTemplateBuildItem> items) {
        return siteDir -> {
            if (!Files.isDirectory(siteDir)) {
                return;
            }

            // We scan Qute templates manually outside of resources for now
            final Path templatesDir = siteDir.resolve(TEMPLATES_DIR);
            watchDirectory(templatesDir, watch);
            scanTemplates(quteConfig, config, watch, templatePathProducer, templatesDir);
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
                        final String dirName = contentDir.relativize(p).getName(0).toString();
                        if (collections.containsKey(dirName)) {
                            addBuildItem(siteDir, contentDir, items, quteConfig, config, watch, markupList,
                                    headerParserList, dataModifications,
                                    collections.get(dirName), TemplateType.DOCUMENT_PAGE).accept(p);
                        } else {
                            addBuildItem(siteDir, contentDir, items, quteConfig, config, watch, markupList,
                                    headerParserList, dataModifications, null,
                                    TemplateType.NORMAL_PAGE).accept(p);
                        }
                    });
        } catch (IOException e) {
            throw new RoqSiteScanningException(
                    "Unable to scan content files at location: %s".formatted(contentDir), e);
        }

    }

    private static void watchDirectory(Path dir, BuildProducer<HotDeploymentWatchedFileBuildItem> watch) {
        if (!Files.isDirectory(dir)) {
            return;
        }
        try (var stream = Files.walk(dir)) {
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
                    .filter(RoqFrontMatterScanProcessor::isPageTargetHtml)
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
                            templatePathProducer.produce(TemplatePathBuildItem.builder()
                                    .priority(ROOT_ARCHIVE_PRIORITY)
                                    .path(link)
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
            Path contentDir,
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
            String sourcePath = toUnixPath(contentDir.relativize(file).toString());
            final String fullContent;
            try {
                fullContent = Files.readString(file, StandardCharsets.UTF_8);
            } catch (IOException e) {
                throw new RoqSiteScanningException(
                        "Error while reading template file: %s".formatted(sourcePath), e);
            }
            TemplateContext templateContext = new TemplateContext(file, sourcePath, fullContent);
            RoqFrontMatterQuteMarkupBuildItem markup = findMarkupFilter(markupList, templateContext);
            List<RoqFrontMatterHeaderParserBuildItem> headerParsers = resolveHeaderParsers(headerParserList,
                    templateContext);
            String cleanPath = replaceWhitespaceChars(sourcePath);
            final String templatePath = removeExtension(cleanPath)
                    + resolveOutputExtension(markup != null, templateContext);
            String quteTemplatePath = ROQ_GENERATED_QUTE_PREFIX + templatePath;
            String quteContentTemplatePath = ROQ_GENERATED_CONTENT_QUTE_PREFIX + templatePath;
            boolean published = type.isPage();
            String id = type.isPage() ? sourcePath : removeExtension(sourcePath);
            final boolean isHtml = isPageTargetHtml(file);
            var isIndex = isHtml && "index".equals(PathUtils.removeExtension(PathUtils.fileName(sourcePath)));
            var isSiteIndex = isHtml && id.startsWith("index.");

            JsonObject data = new JsonObject();
            String content = fullContent;

            for (RoqFrontMatterHeaderParserBuildItem headerParser : headerParsers) {
                data.mergeIn(headerParser.parse().apply(templateContext), true);
                content = headerParser.removeHeader().apply(content);
            }

            ZonedDateTime date = parsePublishDate(file, data, config.dateFormat(), config.timeZone());
            final boolean noFuture = !config.future() && (collection == null || !collection.future());
            ZonedDateTime now = ZonedDateTime.now();
            if (date != null && noFuture && date.isAfter(now)) {
                LOGGER.warnf("Ignoring page '%s' because it's scheduled for later (%s > %s)." +
                        " To display future articles, use -Dsite.future=true%s.", sourcePath, date, now,
                        collection == null ? "" : " or -Dsite.collections.%s.future=true".formatted(collection.id()));
                return;
            }
            String dateString = date.format(DateTimeFormatter.ISO_ZONED_DATE_TIME);
            final String defaultLayout = type.isPage() && isHtml
                    ? collection != null ? collection.layout() : config.pageLayout().orElse(null)
                    : null;
            final String layoutId = normalizedLayout(config.theme(),
                    data.getString(LAYOUT_KEY),
                    defaultLayout);

            for (RoqFrontMatterDataModificationBuildItem modification : dataModifications) {
                data = modification.modifier().modify(new SourceData(file, sourcePath, collection, type, data));
            }
            final boolean draft = data.getBoolean(DRAFT_KEY, false);
            if (!config.draft() && draft) {
                return;
            }
            final boolean escaped = Boolean.parseBoolean(data.getString(ESCAPE_KEY, "false"));
            final WrapperFilter escapeFilter = getEscapeFilter(escaped);
            final WrapperFilter includeFilter = getIncludeFilter(layoutId);
            final String escapedContent = escapeFilter.apply(content);
            final String contentWithMarkup = markup != null ? markup.toWrapperFilter().apply(escapedContent) : escapedContent;
            final String generatedTemplate = includeFilter.apply(contentWithMarkup);

            List<Attachment> attachments = null;
            // Scan for files
            if (isIndex) {
                attachments = new ArrayList<>();
                if (isSiteIndex) {
                    // Support legacy static dir
                    scanAttachments(siteDir, config, quteConfig, watch, attachments, siteDir,
                            siteDir.resolve(config.staticDir()), false);
                    // Public dir
                    scanAttachments(siteDir, config, quteConfig, watch, attachments, siteDir.resolve(config.publicDir()),
                            siteDir.resolve(config.publicDir()), false);
                } else {
                    // Attachments are in the index parent dir
                    scanAttachments(siteDir, config, quteConfig, watch, attachments, file.getParent(), file.getParent(), true);
                }

            }
            PageInfo info = PageInfo.create(id,
                    draft,
                    dateString,
                    markup != null ? markup.name() : null,
                    contentWithMarkup,
                    file.toAbsolutePath().toString(),
                    sourcePath,
                    quteTemplatePath,
                    attachments != null
                            ? new PageFiles(attachments.stream().map(Attachment::name).toList(), config.slugifyFiles())
                            : null,
                    isHtml,
                    isSiteIndex);

            items.add(new RoqFrontMatterRawTemplateBuildItem(info, layoutId, type, data, collection, generatedTemplate,
                    contentWithMarkup,
                    published, attachments));

        };
    }

    private static void scanAttachments(Path siteDir, RoqSiteConfig config, QuteConfig quteConfig,
            BuildProducer<HotDeploymentWatchedFileBuildItem> watch, List<Attachment> attachments, Path refDir,
            Path attachmentDir,
            boolean ignoreTemplates) {
        if (Files.isDirectory(attachmentDir)) {
            watchDirectory(attachmentDir, watch);
            try (Stream<Path> stream = Files.walk(attachmentDir)) {
                stream.filter(Files::isRegularFile)
                        .filter(not(isFileExcluded(siteDir, config)))
                        .filter(p -> !ignoreTemplates || !isTemplate(quteConfig).test(p))
                        .forEach(p -> attachments
                                .add(new Attachment(resolveAttachmentLink(config, p, refDir), p)));
            } catch (IOException e) {
                throw new RoqSiteScanningException(
                        "Error scanning static attachment files in directory: %s".formatted(attachmentDir), e);
            }
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

    protected static ZonedDateTime parsePublishDate(Path file, JsonObject frontMatter, String dateFormat,
            Optional<String> timeZone) {
        String dateString;
        final boolean fromFileName;
        if (frontMatter.containsKey(DATE_KEY)) {
            dateString = frontMatter.getString(DATE_KEY);
            fromFileName = false;
        } else {
            Matcher matcher = FILE_NAME_DATE_PATTERN.matcher(file.toString());
            if (!matcher.find()) {
                // Lets fallback on using today's date if not specified
                return ZonedDateTime.now();
            }
            dateString = matcher.group(1);
            fromFileName = true;
        }
        try {
            return new DateTimeFormatterBuilder().appendPattern(dateFormat)
                    .parseDefaulting(ChronoField.HOUR_OF_DAY, 0)
                    .parseDefaulting(ChronoField.MINUTE_OF_HOUR, 0)
                    .parseDefaulting(ChronoField.SECOND_OF_MINUTE, 0)
                    .toFormatter()
                    .withZone(timeZone.isPresent() ? ZoneId.of(timeZone.get()) : ZoneId.systemDefault())
                    .parse(dateString, ZonedDateTime::from);
        } catch (DateTimeParseException e) {
            if (fromFileName) {
                throw new RoqSiteScanningException(
                        "Error while reading date '%s' in file name: '%s'\nreason: %s".formatted(dateString, file,
                                e.getLocalizedMessage()));
            } else {
                throw new RoqFrontMatterReadingException(
                        "Error while reading FrontMatter 'date' ('%s') in file: '%s'\nreason: %s".formatted(dateString, file,
                                e.getLocalizedMessage()));
            }
        }

    }

    private static WrapperFilter getEscapeFilter(boolean escaped) {
        if (!escaped) {
            return WrapperFilter.EMPTY;
        }
        return ESCAPE_FILTER;
    }

    private static WrapperFilter getIncludeFilter(String layout) {
        if (layout == null) {
            return WrapperFilter.EMPTY;
        }
        String prefix = "{#include %s%s}\n".formatted(ROQ_GENERATED_QUTE_PREFIX, layout);
        return new WrapperFilter(prefix, "\n{/include}");
    }

    private static WrapperFilter getMarkupFilter(
            Map<String, WrapperFilter> markups, String fileName) {
        return find(markups, fileName,
                WrapperFilter.EMPTY);
    }

    private static String normalizedLayout(Optional<String> theme, String layout, String defaultLayout) {
        String normalized = layout;
        if (normalized == null) {
            normalized = defaultLayout;
            if (normalized == null || normalized.isBlank() || "none".equalsIgnoreCase(normalized)) {
                return null;
            }
            normalized = defaultLayout;
            if (normalized.contains(":theme/") && theme.isEmpty()) {
                normalized = normalized.replace(":theme/", "");
            }
        }

        if (normalized.contains(":theme")) {
            if (theme.isPresent()) {
                normalized = normalized.replace(":theme", theme.get());
            } else {
                throw new RoqThemeConfigurationException(
                        "No theme detected! Using ':theme' in 'layout: %s' is only possible with a theme installed as a dependency."
                                .formatted(layout));
            }
        }

        if (!normalized.contains(PathUtils.addTrailingSlash(LAYOUTS_DIR))) {
            normalized = PathUtils.join(LAYOUTS_DIR, normalized);
        }
        return removeExtension(normalized);
    }

    public static String getLayoutKey(Optional<String> theme, String resolvedLayout) {
        String result = resolvedLayout;
        if (result.startsWith(addTrailingSlash(LAYOUTS_DIR))) {
            result = result.substring(PathUtils.addTrailingSlash(LAYOUTS_DIR).length());

            if (theme.isPresent() && result.contains(theme.get())) {
                result = result.replace(theme.get(), ":theme");
            }
        }
        return result;
    }

    private static String resolveOutputExtension(boolean markupFound,
            TemplateContext templateContext) {
        if (markupFound) {
            return ".html";
        }
        final String extension = templateContext.getExtension();
        if (extension == null) {
            return "";
        }
        final String mimeTypeForExtension = MimeMapping.getMimeTypeForExtension(extension);
        if (mimeTypeForExtension != null) {
            return "." + extension;
        }
        return ".html";
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

    private static boolean isPageTargetHtml(Path path) {
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

    public static String stripFrontMatter(String content) {
        return FRONTMATTER_PATTERN.matcher(content).replaceFirst("");
    }

    private static boolean hasFrontMatter(String content) {
        return FRONTMATTER_PATTERN.matcher(content).find();
    }
}
