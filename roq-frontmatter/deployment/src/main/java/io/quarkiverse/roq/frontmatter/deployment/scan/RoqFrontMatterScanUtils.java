package io.quarkiverse.roq.frontmatter.deployment.scan;

import static io.quarkiverse.roq.frontmatter.runtime.RoqTemplates.LAYOUTS_DIR;
import static io.quarkiverse.roq.frontmatter.runtime.RoqTemplates.THEME_LAYOUTS_DIR_PREFIX;
import static io.quarkiverse.tools.stringpaths.StringPaths.addTrailingSlash;
import static io.quarkiverse.tools.stringpaths.StringPaths.fileExtension;
import static io.quarkiverse.tools.stringpaths.StringPaths.toUnixPath;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.dataformat.yaml.YAMLMapper;

import io.quarkiverse.roq.frontmatter.deployment.scan.RoqFrontMatterQuteMarkupBuildItem.WrapperFilter;
import io.quarkiverse.roq.frontmatter.deployment.scan.RoqFrontMatterRawTemplateBuildItem.TemplateType;
import io.quarkiverse.roq.frontmatter.runtime.config.RoqSiteConfig;
import io.quarkus.deployment.annotations.BuildProducer;
import io.quarkus.deployment.builditem.HotDeploymentWatchedFileBuildItem;
import io.quarkus.qute.runtime.QuteConfig;
import io.vertx.core.json.JsonObject;

public final class RoqFrontMatterScanUtils {

    public static final Pattern FRONTMATTER_PATTERN = Pattern.compile("^---\\v.*?---(?:\\v|$)", Pattern.DOTALL);
    public static final String ESCAPE_KEY = "escape";
    public static final String TEMPLATES_DIR = "templates";

    static final Set<String> HTML_OUTPUT_EXTENSIONS = Set.of("md", "markdown", "html", "htm", "xhtml", "asciidoc",
            "adoc");
    static final Set<String> INDEX_FILES = HTML_OUTPUT_EXTENSIONS.stream().map(e -> "index." + e)
            .collect(Collectors.toSet());
    public static final String LAYOUTS_DIR_PREFIX = addTrailingSlash(LAYOUTS_DIR);

    private static final WrapperFilter ESCAPE_FILTER = new WrapperFilter("{|", "|}");

    private RoqFrontMatterScanUtils() {
    }

    /**
     * Build the combined list of ignored file patterns from {@link RoqSiteConfig#defaultIgnoredFiles()}
     * and {@link RoqSiteConfig#ignoredFiles()}, prefixing each with {@code glob:} if not already prefixed.
     */
    public static List<String> buildIgnoredPatterns(RoqSiteConfig config) {
        List<String> patterns = new ArrayList<>(config.defaultIgnoredFiles());
        config.ignoredFiles().ifPresent(patterns::addAll);
        return patterns.stream()
                .map(p -> p.startsWith("glob:") || p.startsWith("regex:") ? p : "glob:" + p)
                .toList();
    }

    /**
     * Build a glob pattern matching all template extensions (HTML output extensions + Qute suffixes).
     */
    public static String buildTemplateGlob(QuteConfig quteConfig) {
        Set<String> extensions = new HashSet<>(HTML_OUTPUT_EXTENSIONS);
        extensions.addAll(quteConfig.suffixes());
        return "glob:**.{" + String.join(",", extensions) + "}";
    }

    /**
     * Build a glob matching only HTML-output extensions.
     */
    public static String buildHtmlTemplateGlob() {
        return "glob:**.{" + String.join(",", HTML_OUTPUT_EXTENSIONS) + "}";
    }

    public static Predicate<Path> isTemplate(QuteConfig config) {
        HashSet<String> suffixes = new HashSet<>(config.suffixes());
        suffixes.addAll(HTML_OUTPUT_EXTENSIONS);
        return path -> suffixes.contains(fileExtension(path.toString()));
    }

    public static boolean isTemplateTargetHtml(String path) {
        final String extension = fileExtension(path);
        return HTML_OUTPUT_EXTENSIONS.contains(extension);
    }

    public static String getLayoutsDir(TemplateType type) {
        if (type.isThemeLayout()) {
            return THEME_LAYOUTS_DIR_PREFIX + LAYOUTS_DIR;
        }
        return LAYOUTS_DIR;
    }

    static void produceWatch(String watchPath, BuildProducer<HotDeploymentWatchedFileBuildItem> watch) {
        if (watchPath != null) {
            watch.produce(HotDeploymentWatchedFileBuildItem.builder().setLocation(watchPath).build());
        }
    }

    static String replaceWhitespaceChars(String sourcePath) {
        return sourcePath.replaceAll("\\s+", "-");
    }

    static WrapperFilter getEscapeFilter(boolean escaped) {
        if (!escaped) {
            return WrapperFilter.EMPTY;
        }
        return ESCAPE_FILTER;
    }

    static String getMarkup(boolean isHtml, RoqFrontMatterQuteMarkupBuildItem markup) {
        if (isHtml) {
            return markup != null ? markup.name() : "html";
        }
        return null;
    }

    public static String deriveSiteDirPath(Path filePath, String relativePath) {
        Path resolved = filePath.normalize().toAbsolutePath();
        int depth = Path.of(relativePath).getNameCount();
        for (int i = 0; i < depth; i++) {
            resolved = resolved.getParent();
        }
        return toUnixPath(resolved.toString());
    }

    public static String removeThemePrefix(String id) {
        return id.replace(getLayoutsDir(TemplateType.THEME_LAYOUT), getLayoutsDir(TemplateType.LAYOUT));
    }

    public static boolean hasFrontMatter(String content) {
        return FRONTMATTER_PATTERN.matcher(content).find();
    }

    public static String getFrontMatter(String content) {
        int endOfFrontMatter = content.indexOf("---", 3);
        if (endOfFrontMatter != -1) {
            return content.substring(3, endOfFrontMatter).trim();
        }
        return "";
    }

    @SuppressWarnings("unchecked")
    public static JsonObject readFM(YAMLMapper mapper, String fullContent)
            throws JsonProcessingException, IllegalArgumentException {
        final String frontMatter = getFrontMatter(fullContent);
        if (frontMatter.isBlank()) {
            return new JsonObject();
        }
        JsonNode rootNode = mapper.readTree(frontMatter);
        final Map<String, Object> map = mapper.convertValue(rootNode, Map.class);
        return new JsonObject(map);
    }

    public static String stripFrontMatter(String content) {
        return FRONTMATTER_PATTERN.matcher(content).replaceFirst("");
    }
}
