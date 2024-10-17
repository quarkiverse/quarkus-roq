package io.quarkiverse.roq.frontmatter.deployment.scan;

import static io.quarkiverse.roq.frontmatter.runtime.model.PageInfo.HTML_OUTPUT_EXTENSIONS;
import static io.quarkiverse.roq.util.PathUtils.*;
import static java.util.function.Predicate.not;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileSystem;
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
import io.quarkiverse.roq.frontmatter.deployment.scan.RoqFrontMatterRawTemplateBuildItem.TemplateType;
import io.quarkiverse.roq.frontmatter.runtime.config.ConfiguredCollection;
import io.quarkiverse.roq.frontmatter.runtime.config.RoqSiteConfig;
import io.quarkiverse.roq.frontmatter.runtime.model.PageInfo;
import io.quarkiverse.roq.util.PathUtils;
import io.quarkus.deployment.annotations.BuildProducer;
import io.quarkus.deployment.annotations.BuildStep;
import io.quarkus.deployment.builditem.HotDeploymentWatchedFileBuildItem;
import io.quarkus.qute.deployment.TemplatePathBuildItem;
import io.quarkus.qute.runtime.QuteConfig;
import io.quarkus.runtime.util.ClassPathUtils;
import io.vertx.core.json.JsonObject;

public class RoqFrontMatterScanProcessor {
    private static final Logger LOGGER = org.jboss.logging.Logger.getLogger(RoqFrontMatterScanProcessor.class);
    public static final Pattern FRONTMATTER_PATTERN = Pattern.compile("^---\\v.*?---\\v", Pattern.DOTALL);
    private static final String DRAFT_KEY = "draft";
    private static final String DATE_KEY = "date";
    private static final String LAYOUT_KEY = "layout";
    private static final Pattern FILE_NAME_DATE_PATTERN = Pattern.compile("(\\d{4}-\\d{2}-\\d{2})");

    private record QuteMarkupSection(String open, String close) {
        public static final QuteMarkupSection MARKDOWN = new QuteMarkupSection("{#markdown}", "{/markdown}");
        public static final QuteMarkupSection ASCIIDOC = new QuteMarkupSection("{#asciidoc}", "{/asciidoc}");

        private static final Map<String, QuteMarkupSection> MARKUP_BY_EXT = Map.of(
                "md", QuteMarkupSection.MARKDOWN,
                "markdown", QuteMarkupSection.MARKDOWN,
                "adoc", QuteMarkupSection.ASCIIDOC,
                "asciidoc", QuteMarkupSection.ASCIIDOC);

        public String apply(String content) {
            return open + "\n" + content.strip() + "\n" + close;
        }

        public static Function<String, String> find(String fileName, Function<String, String> defaultFunction) {
            final String extension = PathUtils.getExtension(fileName);
            if (extension == null) {
                return defaultFunction;
            }
            if (!MARKUP_BY_EXT.containsKey(extension)) {
                return defaultFunction;
            }
            return MARKUP_BY_EXT.get(extension)::apply;
        }
    }

    @BuildStep
    void scan(RoqProjectBuildItem roqProject,
            QuteConfig quteConfig,
            RoqJacksonBuildItem jackson,
            BuildProducer<RoqFrontMatterRawTemplateBuildItem> dataProducer,
            BuildProducer<RoqFrontMatterStaticFileBuildItem> staticFilesProducer,
            BuildProducer<TemplatePathBuildItem> templatePathProducer,
            List<RoqFrontMatterDataModificationBuildItem> dataModifications,
            RoqSiteConfig siteConfig,
            BuildProducer<HotDeploymentWatchedFileBuildItem> watch) {
        try {
            dataModifications.sort(Comparator.comparing(RoqFrontMatterDataModificationBuildItem::order));
            Set<RoqFrontMatterRawTemplateBuildItem> items = resolveItems(roqProject,
                    quteConfig,
                    jackson.getYamlMapper(),
                    siteConfig,
                    watch,
                    dataModifications,
                    templatePathProducer,
                    staticFilesProducer);

            for (RoqFrontMatterRawTemplateBuildItem item : items) {
                dataProducer.produce(item);
            }

        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public Set<RoqFrontMatterRawTemplateBuildItem> resolveItems(RoqProjectBuildItem roqProject,
            QuteConfig quteConfig,
            YAMLMapper mapper,
            RoqSiteConfig config,
            BuildProducer<HotDeploymentWatchedFileBuildItem> watch,
            List<RoqFrontMatterDataModificationBuildItem> dataModifications,
            BuildProducer<TemplatePathBuildItem> templatePathProducer,
            BuildProducer<RoqFrontMatterStaticFileBuildItem> staticFilesProducer) throws IOException {
        HashSet<RoqFrontMatterRawTemplateBuildItem> items = new HashSet<>();
        final Consumer<Path> roqDirConsumer = createRoqDirConsumer(mapper, config, watch, dataModifications,
                templatePathProducer, staticFilesProducer, items);
        roqProject.consumeRoqDir(roqDirConsumer);
        ClassPathUtils.consumeAsPaths("roq/theme", roqDirConsumer);
        return items;
    }

    private static Consumer<Path> createRoqDirConsumer(YAMLMapper mapper, RoqSiteConfig config,
            BuildProducer<HotDeploymentWatchedFileBuildItem> watch,
            List<RoqFrontMatterDataModificationBuildItem> dataModifications,
            BuildProducer<TemplatePathBuildItem> templatePathProducer,
            BuildProducer<RoqFrontMatterStaticFileBuildItem> staticFilesProducer,
            HashSet<RoqFrontMatterRawTemplateBuildItem> items) {
        return root -> {
            if (Files.isDirectory(root)) {
                // scan layouts and templates
                final Path templatesDir = root.resolve(config.templateDir());
                if (Files.isDirectory(templatesDir)) {
                    try (Stream<Path> stream = Files.walk(templatesDir)) {
                        final Consumer<Path> layoutsConsumer = addBuildItem(templatesDir, items, mapper, config,
                                dataModifications,
                                watch, null,
                                TemplateType.LAYOUT);
                        stream
                                .filter(Files::isRegularFile)
                                .filter(not(isFileExcluded(templatesDir, config)))
                                .forEach(p -> {
                                    final String dirName = templatesDir.relativize(p).getName(0).toString();
                                    if (config.layoutsDir().equals(dirName)) {
                                        // Add layouts
                                        if (isExtensionSupportedForLayout(p)) {
                                            layoutsConsumer.accept(p);
                                        }
                                    } else {
                                        // add Qute templates
                                        try {
                                            final String link = toUnixPath(templatesDir.relativize(p).toString());
                                            templatePathProducer.produce(TemplatePathBuildItem.builder()
                                                    .path(link)
                                                    .content(Files.readString(p, StandardCharsets.UTF_8))
                                                    .extensionInfo(RoqFrontMatterProcessor.FEATURE)
                                                    .build());
                                        } catch (IOException e) {
                                            throw new RuntimeException("Error while reading the FrontMatter file %s"
                                                    .formatted(p), e);
                                        }
                                    }
                                });
                    } catch (IOException e) {
                        throw new RuntimeException("Was not possible to scan templates dir %s".formatted(templatesDir), e);
                    }
                }

                // scan content
                final Path contentDir = root.resolve(config.contentDir());
                if (Files.isDirectory(contentDir)) {
                    final Map<String, ConfiguredCollection> collections = config.collections().stream()
                            .collect(Collectors.toMap(ConfiguredCollection::id, Function.identity()));
                    try (Stream<Path> stream = Files.walk(contentDir)) {
                        stream
                                .filter(Files::isRegularFile)
                                .filter(not(isFileExcluded(contentDir, config)))
                                .forEach(p -> {
                                    final String dirName = contentDir.relativize(p).getName(0).toString();
                                    if (collections.containsKey(dirName)) {
                                        addBuildItem(contentDir, items, mapper, config, dataModifications, watch,
                                                collections.get(dirName), TemplateType.DOCUMENT_PAGE).accept(p);
                                    } else {
                                        addBuildItem(contentDir, items, mapper, config, dataModifications, watch, null,
                                                TemplateType.NORMAL_PAGE).accept(p);
                                    }
                                });
                    } catch (IOException e) {
                        throw new RuntimeException(
                                "Was not possible to scan content files on location %s".formatted(contentDir), e);
                    }
                }

                // scan static
                final Path staticDir = root.resolve(config.staticDir());
                if (Files.isDirectory(staticDir)) {
                    try (Stream<Path> stream = Files.walk(staticDir)) {
                        stream
                                .filter(Files::isRegularFile)
                                .filter(not(isFileExcluded(staticDir, config)))
                                .forEach(p -> {
                                    final String link = toUnixPath(root.relativize(p).toString());
                                    staticFilesProducer.produce(new RoqFrontMatterStaticFileBuildItem(link, p));
                                });
                    } catch (IOException e) {
                        throw new RuntimeException("Was not possible to scan static files on location %s".formatted(staticDir),
                                e);
                    }

                }
            }
        };
    }

    @SuppressWarnings("unchecked")
    private static Consumer<Path> addBuildItem(Path root,
            HashSet<RoqFrontMatterRawTemplateBuildItem> items,
            YAMLMapper mapper,
            RoqSiteConfig config,
            List<RoqFrontMatterDataModificationBuildItem> dataModifications,
            BuildProducer<HotDeploymentWatchedFileBuildItem> watch,
            ConfiguredCollection collection,
            TemplateType type) {
        return file -> {
            watch.produce(HotDeploymentWatchedFileBuildItem.builder().setLocation(file.toAbsolutePath().toString()).build());
            String sourcePath = toUnixPath(root.relativize(file).toString());
            String quteTemplatePath = removeExtension(sourcePath) + resolveOutputExtension(sourcePath);
            boolean published = type.isPage();
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
                    final String layout = normalizedLayoutTemplateId(config.theme(), config.layoutsDir(),
                            fm.getString(LAYOUT_KEY));
                    final String content = stripFrontMatter(fullContent);
                    ZonedDateTime date = parsePublishDate(file, fm, config.dateFormat(), config.timeZone());
                    if (date != null && !config.future() && date.isAfter(ZonedDateTime.now())) {
                        return;
                    }
                    String dateString = date.format(DateTimeFormatter.ISO_ZONED_DATE_TIME);
                    PageInfo info = PageInfo.create(sourcePath, draft, config.imagesPath(), dateString, content,
                            sourcePath, quteTemplatePath);
                    LOGGER.debugf("Creating generated template for id %s" + sourcePath);
                    final String generatedTemplate = generateTemplate(sourcePath, layout, content);
                    items.add(
                            new RoqFrontMatterRawTemplateBuildItem(info, layout, type, fm, collection, generatedTemplate,
                                    published));
                } else {
                    PageInfo info = PageInfo.create(sourcePath, false, config.imagesPath(), null, fullContent, sourcePath,
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

    private static Predicate<Path> matchGlobs(Path root, List<String> globs) {
        return path -> {
            final FileSystem fs = root.getFileSystem();
            final Path relative = root.relativize(path);
            return globs.stream().anyMatch(glob -> fs.getPathMatcher("glob:" + glob).matches(relative));
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
                .parseDefaulting(ChronoField.HOUR_OF_DAY, 12)
                .parseDefaulting(ChronoField.MINUTE_OF_HOUR, 0)
                .parseDefaulting(ChronoField.SECOND_OF_MINUTE, 0)
                .toFormatter()
                .withZone(timeZone.isPresent() ? ZoneId.of(timeZone.get()) : ZoneId.systemDefault())
                .parse(dateString, ZonedDateTime::from);

    }

    private static String generateTemplate(String fileName, String layout, String content) {
        StringBuilder template = new StringBuilder();
        if (layout != null) {
            template.append("{#include ").append(layout).append("}\n");
        }
        template.append(QuteMarkupSection.find(fileName, Function.identity()).apply(content));
        template.append("\n{/include}");
        return template.toString();
    }

    private static String normalizedLayoutTemplateId(Optional<String> theme, String layoutDir, String layout) {
        if (layout == null) {
            return null;
        }
        String normalized = layout;
        if (theme.isPresent()) {
            normalized = normalized.replace(":theme", theme.get());
        }
        if (!normalized.contains(PathUtils.addTrailingSlash(layoutDir))) {
            normalized = PathUtils.join(layoutDir, normalized);
        }
        return removeExtension(normalized);
    }

    private static String resolveOutputExtension(String fileName) {
        if (QuteMarkupSection.find(fileName, null) == null) {
            final String extension = getExtension(fileName);
            if (extension == null) {
                return "";
            }
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

    private static Predicate<? super Path> isExtensionSupportedForTemplate(QuteConfig quteConfig) {
        return (p) -> {
            String fileName = p.getFileName().toString();
            return isExtensionSupportedForLayout(p) || quteConfig.suffixes.stream().anyMatch(fileName::endsWith);
        };
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
