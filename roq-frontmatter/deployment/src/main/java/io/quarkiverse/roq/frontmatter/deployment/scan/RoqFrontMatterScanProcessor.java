package io.quarkiverse.roq.frontmatter.deployment.scan;

import static io.quarkiverse.roq.frontmatter.deployment.scan.RoqFrontMatterQuteMarkupBuildItem.markups;
import static io.quarkiverse.roq.frontmatter.deployment.scan.RoqFrontMatterQuteMarkupBuildItem.QuteMarkupSection.find;
import static io.quarkiverse.roq.frontmatter.runtime.model.PageInfo.HTML_OUTPUT_EXTENSIONS;
import static io.quarkiverse.roq.util.PathUtils.*;
import static java.util.function.Predicate.not;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
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

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.dataformat.yaml.YAMLMapper;

import io.quarkiverse.roq.deployment.items.RoqJacksonBuildItem;
import io.quarkiverse.roq.deployment.items.RoqProjectBuildItem;
import io.quarkiverse.roq.frontmatter.deployment.RoqFrontMatterProcessor;
import io.quarkiverse.roq.frontmatter.deployment.data.RoqFrontMatterDataModificationBuildItem;
import io.quarkiverse.roq.frontmatter.deployment.scan.RoqFrontMatterQuteMarkupBuildItem.QuteMarkupSection;
import io.quarkiverse.roq.frontmatter.deployment.scan.RoqFrontMatterRawTemplateBuildItem.TemplateType;
import io.quarkiverse.roq.frontmatter.runtime.config.ConfiguredCollection;
import io.quarkiverse.roq.frontmatter.runtime.config.RoqSiteConfig;
import io.quarkiverse.roq.frontmatter.runtime.model.PageInfo;
import io.quarkiverse.roq.util.PathUtils;
import io.quarkus.deployment.annotations.BuildProducer;
import io.quarkus.deployment.annotations.BuildStep;
import io.quarkus.deployment.builditem.HotDeploymentWatchedFileBuildItem;
import io.quarkus.paths.PathVisit;
import io.quarkus.qute.deployment.TemplatePathBuildItem;
import io.quarkus.qute.deployment.TemplateRootBuildItem;
import io.quarkus.runtime.configuration.ConfigurationException;
import io.vertx.core.http.impl.MimeMapping;
import io.vertx.core.json.JsonObject;

public class RoqFrontMatterScanProcessor {
    private static final Logger LOGGER = org.jboss.logging.Logger.getLogger(RoqFrontMatterScanProcessor.class);
    public static final Pattern FRONTMATTER_PATTERN = Pattern.compile("^---\\v.*?---\\v", Pattern.DOTALL);
    private static final String DRAFT_KEY = "draft";
    private static final String DATE_KEY = "date";
    private static final String LAYOUT_KEY = "layout";
    private static final Pattern FILE_NAME_DATE_PATTERN = Pattern.compile("(\\d{4}-\\d{2}-\\d{2})");
    public static final String ROQ_GENERATED_QUTE_PREFIX = "roq-gen/";
    public static final String LAYOUTS_DIR = "layouts";
    public static final String THEME_LAYOUTS_DIR_PREFIX = "theme-";
    public static final String TEMPLATES_DIR = "templates";

    @BuildStep
    void scan(RoqProjectBuildItem roqProject,
            List<RoqFrontMatterQuteMarkupBuildItem> markupList,
            RoqJacksonBuildItem jackson,
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
                    jackson.getYamlMapper(),
                    siteConfig,
                    markups(markupList),
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
                                item.published()));
                    }
                }
                produceRawTemplate(dataProducer, item);
            }

        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private static void produceRawTemplate(BuildProducer<RoqFrontMatterRawTemplateBuildItem> dataProducer,
            RoqFrontMatterRawTemplateBuildItem item) {
        LOGGER.debugf("Roq is producing a raw template '%s'", item.id());
        dataProducer.produce(item);
    }

    private static String removeThemePrefix(String id) {
        return id.replace(getLayoutsDir(TemplateType.THEME_LAYOUT), getLayoutsDir(TemplateType.LAYOUT));
    }

    public List<RoqFrontMatterRawTemplateBuildItem> resolveItems(RoqProjectBuildItem roqProject,
            YAMLMapper mapper,
            RoqSiteConfig config,
            Map<String, QuteMarkupSection> markups,
            BuildProducer<HotDeploymentWatchedFileBuildItem> watch,
            List<RoqFrontMatterDataModificationBuildItem> dataModifications,
            BuildProducer<TemplatePathBuildItem> templatePathProducer,
            BuildProducer<TemplateRootBuildItem> templateRootProducer,
            BuildProducer<RoqFrontMatterStaticFileBuildItem> staticFilesProducer) throws IOException {
        List<RoqFrontMatterRawTemplateBuildItem> items = new ArrayList<>();
        roqProject.consumeRoqDir(createRoqDirConsumer(mapper, config, markups, watch, dataModifications,
                staticFilesProducer, templatePathProducer, items));
        // Scan for layouts in classpath root
        RoqProjectBuildItem.visitRuntimeResources(TEMPLATES_DIR,
                t -> scanLayouts(mapper, config, markups, watch, dataModifications, items, t.getPath(), TemplateType.LAYOUT));
        // Scan for layouts & theme-layouts in classpath root
        RoqProjectBuildItem.visitRuntimeResources(TEMPLATES_DIR,
                t -> {
                    scanLayouts(mapper, config, markups, watch, dataModifications, items, t.getPath(), TemplateType.LAYOUT);
                    scanLayouts(mapper, config, markups, watch, dataModifications, items, t.getPath(),
                            TemplateType.THEME_LAYOUT);
                });
        if (!roqProject.isRoqResourcesInRoot()) {
            // We need to add the template root
            templateRootProducer.produce(new TemplateRootBuildItem(PathUtils.join(roqProject.roqResourceDir(), TEMPLATES_DIR)));
            // Also scan for layouts in roq dir if not root
            roqProject.consumePathFromRoqResourceDir(TEMPLATES_DIR,
                    l -> scanLayouts(mapper, config, markups, watch, dataModifications, items, l.getPath(),
                            TemplateType.LAYOUT));
        }

        roqProject.consumePathFromRoqResourceDir(config.contentDir(),
                l -> {
                    watchResourceDir(watch, l);
                    scanContent(mapper, config, markups, watch, dataModifications, items, l.getPath());
                });
        roqProject.consumePathFromRoqResourceDir(config.staticDir(), l -> {
            watchResourceDir(watch, l);
            scanStatic(config, staticFilesProducer, l.getPath());
        });
        return items;
    }

    private static Consumer<Path> createRoqDirConsumer(YAMLMapper mapper, RoqSiteConfig config,
            Map<String, QuteMarkupSection> markups,
            BuildProducer<HotDeploymentWatchedFileBuildItem> watch,
            List<RoqFrontMatterDataModificationBuildItem> dataModifications,
            BuildProducer<RoqFrontMatterStaticFileBuildItem> staticFilesProducer,
            BuildProducer<TemplatePathBuildItem> templatePathProducer, List<RoqFrontMatterRawTemplateBuildItem> items) {
        return root -> {
            if (!Files.isDirectory(root)) {
                return;
            }

            // We scan Qute templates manually outside of resources for now
            final Path templatesDir = root.resolve(TEMPLATES_DIR);
            watchDirectory(templatesDir, watch);
            scanTemplates(config, watch, templatePathProducer, templatesDir);
            scanLayouts(mapper, config, markups, watch, dataModifications, items, templatesDir,
                    TemplateType.LAYOUT);
            final Path contentDir = root.resolve(config.contentDir());
            watchDirectory(contentDir, watch);
            scanContent(mapper, config, markups, watch, dataModifications, items, contentDir);
            final Path staticDir = root.resolve(config.staticDir());
            watchDirectory(staticDir, watch);
            scanStatic(config, staticFilesProducer, staticDir);
        };
    }

    private static void scanStatic(RoqSiteConfig config, BuildProducer<RoqFrontMatterStaticFileBuildItem> staticFilesProducer,
            Path staticDir) {
        // scan static
        if (Files.isDirectory(staticDir)) {
            try (Stream<Path> stream = Files.walk(staticDir)) {
                stream
                        .filter(Files::isRegularFile)
                        .filter(not(isFileExcluded(staticDir, config)))
                        .forEach(p -> {
                            final String link = toUnixPath(staticDir.getParent().relativize(p).toString());
                            staticFilesProducer.produce(new RoqFrontMatterStaticFileBuildItem(link, p));
                        });
            } catch (IOException e) {
                throw new RuntimeException("Was not possible to scan static files on location %s".formatted(staticDir),
                        e);
            }

        }
    }

    private static void scanContent(YAMLMapper mapper, RoqSiteConfig config,
            Map<String, QuteMarkupSection> markups,
            BuildProducer<HotDeploymentWatchedFileBuildItem> watch,
            List<RoqFrontMatterDataModificationBuildItem> dataModifications, List<RoqFrontMatterRawTemplateBuildItem> items,
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
                    .filter(not(isFileExcluded(contentDir, config)))
                    .forEach(p -> {
                        final String dirName = contentDir.relativize(p).getName(0).toString();
                        if (collections.containsKey(dirName)) {
                            addBuildItem(contentDir, items, mapper, config, markups, dataModifications,
                                    collections.get(dirName), TemplateType.DOCUMENT_PAGE).accept(p);
                        } else {
                            addBuildItem(contentDir, items, mapper, config, markups, dataModifications, null,
                                    TemplateType.NORMAL_PAGE).accept(p);
                        }
                    });
        } catch (IOException e) {
            throw new RuntimeException(
                    "Was not possible to scan content files on location %s".formatted(contentDir), e);
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
            throw new RuntimeException(e);
        }
    }

    private static void watchResourceDir(BuildProducer<HotDeploymentWatchedFileBuildItem> watch, PathVisit l) {
        final String dir = l.getRelativePath();
        watch.produce(HotDeploymentWatchedFileBuildItem.builder().setLocationPredicate(p -> p.startsWith(dir)).build());
        if (LOGGER.isDebugEnabled()) {
            LOGGER.debugf("Watching resources %s for changes", dir);
        }
    }

    private static void scanLayouts(YAMLMapper mapper,
            RoqSiteConfig config,
            Map<String, QuteMarkupSection> markups,
            BuildProducer<HotDeploymentWatchedFileBuildItem> watch,
            List<RoqFrontMatterDataModificationBuildItem> dataModifications,
            List<RoqFrontMatterRawTemplateBuildItem> items,
            Path templatesRoot,
            TemplateType type) {
        final String dir = getLayoutsDir(type);
        Path layoutsDir = templatesRoot.resolve(dir);

        if (!Files.isDirectory(layoutsDir)) {
            return;
        }

        // scan layouts and templates
        try (Stream<Path> stream = Files.walk(layoutsDir)) {
            final Consumer<Path> layoutsConsumer = addBuildItem(templatesRoot, items, mapper, config, markups,
                    dataModifications,
                    null,
                    type);
            stream
                    .filter(Files::isRegularFile)
                    .filter(not(isFileExcluded(templatesRoot, config)))
                    .filter(RoqFrontMatterScanProcessor::isExtensionSupportedForLayout)
                    .forEach(layoutsConsumer);
        } catch (IOException e) {
            throw new RuntimeException("Was not possible to scan templates dir %s".formatted(templatesRoot), e);
        }
    }

    private static String getLayoutsDir(TemplateType type) {
        if (type.isThemeLayout()) {
            return THEME_LAYOUTS_DIR_PREFIX + LAYOUTS_DIR;
        }
        return LAYOUTS_DIR;
    }

    private static void scanTemplates(RoqSiteConfig config,
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
                    .filter(not(isFileExcluded(templatesRoot, config)))
                    .forEach(p -> {
                        final String dirName = templatesRoot.relativize(p).getName(0).toString();
                        if (LAYOUTS_DIR.equals(dirName)) {
                            return;
                        }
                        // add Qute templates
                        try {
                            final String link = toUnixPath(templatesRoot.relativize(p).toString());
                            templatePathProducer.produce(TemplatePathBuildItem.builder()
                                    .path(link)
                                    .content(Files.readString(p, StandardCharsets.UTF_8))
                                    .extensionInfo(RoqFrontMatterProcessor.FEATURE)
                                    .build());
                        } catch (IOException e) {
                            throw new RuntimeException("Error while reading the FrontMatter file %s"
                                    .formatted(p), e);
                        }
                    });
        } catch (IOException e) {
            throw new RuntimeException("Was not possible to scan templates dir %s".formatted(templatesRoot), e);
        }
    }

    @SuppressWarnings("unchecked")
    private static Consumer<Path> addBuildItem(Path root,
            List<RoqFrontMatterRawTemplateBuildItem> items,
            YAMLMapper mapper,
            RoqSiteConfig config,
            Map<String, QuteMarkupSection> markups,
            List<RoqFrontMatterDataModificationBuildItem> dataModifications,
            ConfiguredCollection collection,
            TemplateType type) {
        return file -> {
            String sourcePath = toUnixPath(root.relativize(file).toString());
            String quteTemplatePath = ROQ_GENERATED_QUTE_PREFIX + removeExtension(sourcePath)
                    + resolveOutputExtension(markups, sourcePath);
            boolean published = type.isPage();
            String id = type.isPage() ? sourcePath : removeExtension(sourcePath);
            try {
                final String fullContent = Files.readString(file, StandardCharsets.UTF_8);
                if (hasFrontMatter(fullContent)) {
                    JsonNode rootNode = mapper.readTree(getFrontMatter(fullContent));
                    final Map<String, Object> map = mapper.convertValue(rootNode, Map.class);
                    JsonObject fm = new JsonObject(map);

                    for (RoqFrontMatterDataModificationBuildItem modification : dataModifications) {
                        fm = modification.modifier().modify(sourcePath, fm);
                    }
                    final boolean draft = fm.getBoolean(DRAFT_KEY, false);
                    if (!config.draft() && draft) {
                        return;
                    }

                    final String layoutId = normalizedLayout(config.theme(),
                            fm.getString(LAYOUT_KEY));
                    final String content = stripFrontMatter(fullContent);
                    ZonedDateTime date = parsePublishDate(file, fm, config.dateFormat(), config.timeZone());
                    final boolean noFuture = !config.future() && (collection == null || !collection.future());
                    if (date != null && noFuture && date.isAfter(ZonedDateTime.now())) {
                        return;
                    }
                    String dateString = date.format(DateTimeFormatter.ISO_ZONED_DATE_TIME);
                    PageInfo info = PageInfo.create(id, draft, config.imagesPath(), dateString, content,
                            sourcePath, quteTemplatePath);
                    final String layoutTemplate = ROQ_GENERATED_QUTE_PREFIX + layoutId;
                    final String generatedTemplate = generateTemplate(markups, sourcePath, layoutTemplate, content);
                    items.add(
                            new RoqFrontMatterRawTemplateBuildItem(info, layoutId, type, fm, collection, generatedTemplate,
                                    published));
                } else {
                    PageInfo info = PageInfo.create(id, false, config.imagesPath(), null, fullContent, sourcePath,
                            quteTemplatePath);
                    items.add(
                            new RoqFrontMatterRawTemplateBuildItem(info, null, type, new JsonObject(), collection,
                                    fullContent,
                                    published));
                }
            } catch (IOException e) {
                throw new RuntimeException("Error while reading the FrontMatter file %s"
                        .formatted(sourcePath), e);
            }
        };
    }

    protected static ZonedDateTime parsePublishDate(Path file, JsonObject frontMatter, String dateFormat,
            Optional<String> timeZone) {
        String dateString;
        if (frontMatter.containsKey(DATE_KEY)) {
            dateString = frontMatter.getString(DATE_KEY);
        } else {
            Matcher matcher = FILE_NAME_DATE_PATTERN.matcher(file.getFileName().toString());
            if (!matcher.find()) {
                // Lets fallback on using today's date if not specified
                return ZonedDateTime.now();
            }
            dateString = matcher.group(1);
        }

        return new DateTimeFormatterBuilder().appendPattern(dateFormat)
                .parseDefaulting(ChronoField.HOUR_OF_DAY, 0)
                .parseDefaulting(ChronoField.MINUTE_OF_HOUR, 0)
                .parseDefaulting(ChronoField.SECOND_OF_MINUTE, 0)
                .toFormatter()
                .withZone(timeZone.isPresent() ? ZoneId.of(timeZone.get()) : ZoneId.systemDefault())
                .parse(dateString, ZonedDateTime::from);

    }

    private static String generateTemplate(Map<String, QuteMarkupSection> markups, String fileName, String layout,
            String content) {
        StringBuilder template = new StringBuilder();
        if (layout != null) {
            template.append("{#include ").append(layout).append("}\n");
        }
        template.append(find(markups, fileName, Function.identity()).apply(content));
        template.append("\n{/include}");
        return template.toString();
    }

    private static String normalizedLayout(Optional<String> theme, String layout) {
        if (layout == null) {
            return null;
        }
        String normalized = layout;
        if (normalized.contains(":theme")) {
            if (theme.isPresent()) {
                normalized = normalized.replace(":theme", theme.get());
            } else {
                throw new ConfigurationException(
                        "No theme detected! Using :theme in 'layout: " + layout + " is only possible with a theme installed.");
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

    private static String resolveOutputExtension(Map<String, QuteMarkupSection> markups, String fileName) {
        if (find(markups, fileName, null) != null) {
            return ".html";
        }
        final String extension = getExtension(fileName);
        if (extension == null) {
            return "";
        }
        final String mimeTypeForExtension = MimeMapping.getMimeTypeForExtension(extension);
        if (mimeTypeForExtension != null) {
            return "." + extension;
        }
        return ".html";
    }

    private static Predicate<Path> isFileExcluded(Path root, RoqSiteConfig config) {
        return path -> config.ignoredFiles().stream()
                .anyMatch(s -> path.getFileSystem().getPathMatcher("glob:" + s).matches(root.relativize(path)));
    }

    private static boolean isExtensionSupportedForLayout(Path path) {
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

    private static String stripFrontMatter(String content) {
        return FRONTMATTER_PATTERN.matcher(content).replaceFirst("");
    }

    private static boolean hasFrontMatter(String content) {
        return FRONTMATTER_PATTERN.matcher(content).find();
    }
}
