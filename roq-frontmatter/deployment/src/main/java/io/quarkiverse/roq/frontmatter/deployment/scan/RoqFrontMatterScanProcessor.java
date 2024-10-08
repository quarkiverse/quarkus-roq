package io.quarkiverse.roq.frontmatter.deployment.scan;

import static io.quarkiverse.roq.util.PathUtils.removeExtension;
import static io.quarkiverse.roq.util.PathUtils.toUnixPath;
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
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.jboss.logging.Logger;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.dataformat.yaml.YAMLMapper;

import io.quarkiverse.roq.deployment.items.RoqJacksonBuildItem;
import io.quarkiverse.roq.deployment.items.RoqProjectBuildItem;
import io.quarkiverse.roq.frontmatter.deployment.RoqFrontMatterConfig;
import io.quarkiverse.roq.frontmatter.runtime.model.PageInfo;
import io.quarkus.deployment.annotations.BuildProducer;
import io.quarkus.deployment.annotations.BuildStep;
import io.quarkus.deployment.builditem.HotDeploymentWatchedFileBuildItem;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

public class RoqFrontMatterScanProcessor {
    private static final Logger LOGGER = org.jboss.logging.Logger.getLogger(RoqFrontMatterScanProcessor.class);
    private static final Set<String> SUPPORTED_EXTENSIONS = Set.of(".md", "markdown", ".html", ".asciidoc", ".adoc");
    public static final Pattern FRONTMATTER_PATTERN = Pattern.compile("^---\\v.*\\v---\\v", Pattern.DOTALL);
    private static final String DRAFT_KEY = "draft";
    private static final String DATE_KEY = "date";
    private static final String LAYOUT_KEY = "layout";
    private static final String ALIASES_KEY = "aliases";
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

        public static Function<String, String> find(String fileName) {
            if (!fileName.contains(".")) {
                return Function.identity();
            }
            final String extension = fileName.substring(fileName.lastIndexOf(".") + 1);
            if (!MARKUP_BY_EXT.containsKey(extension)) {
                return Function.identity();
            }
            return MARKUP_BY_EXT.get(extension)::apply;
        }
    }

    @BuildStep
    void scan(RoqProjectBuildItem roqProject,
            RoqJacksonBuildItem jackson,
            BuildProducer<RoqFrontMatterRawTemplateBuildItem> dataProducer,
            RoqFrontMatterConfig roqDataConfig,
            BuildProducer<HotDeploymentWatchedFileBuildItem> watch) {
        try {
            Set<RoqFrontMatterRawTemplateBuildItem> items = resolveItems(roqProject, jackson.getYamlMapper(), roqDataConfig,
                    watch);

            for (RoqFrontMatterRawTemplateBuildItem item : items) {
                dataProducer.produce(item);
            }

        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public Set<RoqFrontMatterRawTemplateBuildItem> resolveItems(RoqProjectBuildItem roqProject, YAMLMapper mapper,
            RoqFrontMatterConfig roqDataConfig, BuildProducer<HotDeploymentWatchedFileBuildItem> watch) throws IOException {

        HashSet<RoqFrontMatterRawTemplateBuildItem> items = new HashSet<>();
        roqProject.consumeRoqDir(site -> {
            if (Files.isDirectory(site)) {
                for (String includesDir : roqDataConfig.includesDirs()) {
                    // scan layouts
                    final Path dir = site.resolve(includesDir);
                    if (Files.isDirectory(dir)) {
                        try (Stream<Path> stream = Files.walk(dir)) {
                            stream
                                    .filter(Files::isRegularFile)
                                    .filter(RoqFrontMatterScanProcessor::isExtensionSupported)
                                    .forEach(addBuildItem(dir, items, mapper, roqDataConfig, watch, null, false));
                        } catch (IOException e) {
                            throw new RuntimeException("Was not possible to scan includes dir %s".formatted(dir), e);
                        }
                    }
                }

                final Map<String, String> collections = roqDataConfig.collectionsOrDefaults();
                for (Map.Entry<String, String> collectionsDir : collections.entrySet()) {
                    // scan collections
                    final Path dir = site.resolve(collectionsDir.getKey());
                    if (Files.isDirectory(dir)) {
                        try (Stream<Path> stream = Files.walk(dir)) {
                            stream
                                    .filter(Files::isRegularFile)
                                    .filter(RoqFrontMatterScanProcessor::isExtensionSupported)
                                    .forEach(addBuildItem(dir, items, mapper, roqDataConfig, watch, collectionsDir.getValue(),
                                            true));
                        } catch (IOException e) {
                            throw new RuntimeException("Was not possible to scan includes dir %s".formatted(dir), e);
                        }
                    }
                }

                // scan pages
                try (Stream<Path> stream = Files.walk(site)) {
                    stream
                            .filter(Files::isRegularFile)
                            .filter(RoqFrontMatterScanProcessor::isExtensionSupported)
                            .filter(not(RoqFrontMatterScanProcessor::isFileExcluded))
                            .forEach(addBuildItem(site, items, mapper, roqDataConfig, watch, null, true));
                } catch (IOException e) {
                    throw new RuntimeException("Was not possible to scan data files on location %s".formatted(site), e);
                }

            }
        });
        return items;
    }

    @SuppressWarnings("unchecked")
    private static Consumer<Path> addBuildItem(Path root, HashSet<RoqFrontMatterRawTemplateBuildItem> items, YAMLMapper mapper,
            RoqFrontMatterConfig config, BuildProducer<HotDeploymentWatchedFileBuildItem> watch, String collection,
            boolean isPage) {
        return file -> {
            watch.produce(HotDeploymentWatchedFileBuildItem.builder().setLocation(file.toAbsolutePath().toString()).build());
            String sourcePath = toUnixPath(
                    collection != null ? collection + "/" + root.relativize(file) : root.relativize(file).toString());
            String templatePath = removeExtension(sourcePath) + ".html";
            final String id = removeExtension(templatePath);
            try {
                final String fullContent = Files.readString(file, StandardCharsets.UTF_8);
                if (hasFrontMatter(fullContent)) {
                    JsonNode rootNode = mapper.readTree(getFrontMatter(fullContent));
                    final Map<String, Object> map = mapper.convertValue(rootNode, Map.class);
                    final JsonObject fm = new JsonObject(map);
                    final boolean draft = fm.getBoolean(DRAFT_KEY, false);
                    if (!config.draft() && draft) {
                        return;
                    }
                    final String layout = normalizedLayout(fm.getString(LAYOUT_KEY));
                    final String content = stripFrontMatter(fullContent);
                    String dateString = parsePublishDate(file, fm, config);

                    JsonArray aliasesArr = fm.getJsonArray(ALIASES_KEY);
                    List<String> aliases = getAliases(aliasesArr);

                    PageInfo info = new PageInfo(id, draft, config.imagesPath(), dateString, content,
                            sourcePath, templatePath);
                    LOGGER.debugf("Creating generated template for %s" + templatePath);
                    final String generatedTemplate = generateTemplate(sourcePath, layout, content);
                    items.add(
                            new RoqFrontMatterRawTemplateBuildItem(info, layout, isPage, fm, collection, generatedTemplate,
                                    isPage, aliases));
                } else {
                    PageInfo info = new PageInfo(id, false, config.imagesPath(), null, fullContent, sourcePath, templatePath);
                    items.add(
                            new RoqFrontMatterRawTemplateBuildItem(info, null, isPage, new JsonObject(), collection,
                                    fullContent,
                                    isPage, List.of()));
                }
            } catch (IOException e) {
                throw new RuntimeException("Error while reading the FrontMatter file %s"
                        .formatted(sourcePath), e);
            }
        };
    }

    private static List<String> getAliases(JsonArray aliasesArr) {
        if (aliasesArr == null) {
            return List.of();
        }
        ArrayList<String> aliases = new ArrayList<>();
        for (int i = 0; i < aliasesArr.size(); i++) {
            String alias = aliasesArr.getString(i);
            if (alias != null && !alias.isBlank()) {
                aliases.add(alias);
            }
        }
        return aliases;
    }

    protected static String parsePublishDate(Path file, JsonObject frontMatter, RoqFrontMatterConfig config) {
        String dateString;
        if (frontMatter.containsKey(DATE_KEY)) {
            dateString = frontMatter.getString(DATE_KEY);
        } else {
            Matcher matcher = FILE_NAME_DATE_PATTERN.matcher(file.getFileName().toString());
            if (!matcher.find())
                return null;
            dateString = matcher.group(1);
        }

        ZonedDateTime date = new DateTimeFormatterBuilder().appendPattern(config.dateFormat())
                .parseDefaulting(ChronoField.HOUR_OF_DAY, 12)
                .parseDefaulting(ChronoField.MINUTE_OF_HOUR, 0)
                .parseDefaulting(ChronoField.SECOND_OF_MINUTE, 0)
                .toFormatter()
                .withZone(config.timeZone().isPresent() ? ZoneId.of(config.timeZone().get()) : ZoneId.systemDefault())
                .parse(dateString, ZonedDateTime::from);
        if (!config.future() && date.isAfter(ZonedDateTime.now())) {
            return null;
        }

        return date.format(DateTimeFormatter.ISO_ZONED_DATE_TIME);
    }

    private static String generateTemplate(String fileName, String layout, String content) {
        StringBuilder template = new StringBuilder();
        if (layout != null) {
            template.append("{#include ").append(layout).append("}\n");
        }
        template.append(QuteMarkupSection.find(fileName).apply(content));
        template.append("\n{/include}");
        return template.toString();
    }

    private static String normalizedLayout(String layout) {
        if (layout == null) {
            return null;
        }
        return removeExtension(layout);
    }

    private static boolean isFileExcluded(Path path) {
        String p = toUnixPath(path.toString());
        return p.startsWith("_") || p.contains("/_");
    }

    private static boolean isExtensionSupported(Path path) {
        String fileName = path.getFileName().toString();
        return SUPPORTED_EXTENSIONS.stream().anyMatch(fileName::endsWith);
    }

    private static String getFrontMatter(String content) {
        int endOfFrontMatter = content.indexOf("---", 3);
        if (endOfFrontMatter != -1) {
            return content.substring(3, endOfFrontMatter).trim();
        }
        return "";
    }

    private static String stripFrontMatter(String content) {
        return FRONTMATTER_PATTERN.matcher(content).replaceAll("");
    }

    private static boolean hasFrontMatter(String content) {
        return FRONTMATTER_PATTERN.matcher(content).find();
    }
}
