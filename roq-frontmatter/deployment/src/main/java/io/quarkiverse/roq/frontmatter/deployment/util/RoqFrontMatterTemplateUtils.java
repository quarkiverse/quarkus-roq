package io.quarkiverse.roq.frontmatter.deployment.util;

import static io.quarkiverse.roq.frontmatter.runtime.RoqTemplates.LAYOUTS_DIR;
import static io.quarkiverse.roq.frontmatter.runtime.RoqTemplates.ROQ_GENERATED_QUTE_PREFIX;
import static io.quarkiverse.roq.frontmatter.runtime.RoqTemplates.THEME_LAYOUTS_DIR_PREFIX;
import static io.quarkiverse.tools.stringpaths.StringPaths.addTrailingSlash;
import static io.quarkiverse.tools.stringpaths.StringPaths.removeExtension;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.dataformat.yaml.YAMLMapper;

import io.quarkiverse.roq.frontmatter.deployment.exception.RoqThemeConfigurationException;
import io.quarkiverse.roq.frontmatter.deployment.items.scan.RoqFrontMatterHeaderParserBuildItem;
import io.quarkiverse.roq.frontmatter.deployment.items.scan.RoqFrontMatterQuteMarkupBuildItem;
import io.quarkiverse.roq.frontmatter.deployment.items.scan.RoqFrontMatterQuteMarkupBuildItem.WrapperFilter;
import io.quarkiverse.roq.frontmatter.deployment.items.scan.TemplateContext;
import io.quarkiverse.roq.frontmatter.runtime.config.ConfiguredCollection;
import io.quarkiverse.roq.frontmatter.runtime.config.RoqSiteConfig;
import io.quarkiverse.tools.stringpaths.StringPaths;
import io.vertx.core.json.JsonObject;

public final class RoqFrontMatterTemplateUtils {

    public static final Pattern FRONTMATTER_PATTERN = Pattern.compile("^---\\v.*?---(?:\\v|$)", Pattern.DOTALL);
    public static final String ESCAPE_KEY = "escape";
    public static final String LAYOUTS_DIR_PREFIX = addTrailingSlash(LAYOUTS_DIR);

    private static final WrapperFilter ESCAPE_FILTER = new WrapperFilter("{|", "|}");

    private RoqFrontMatterTemplateUtils() {
    }

    // ── FrontMatter parsing ─────────────────────────────────────────────

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

    // ── Header parsing ──────────────────────────────────────────────────

    public record ParsedHeaders(JsonObject data, String content) {
    }

    public static ParsedHeaders parseHeaders(List<RoqFrontMatterHeaderParserBuildItem> headerParsers,
            TemplateContext templateContext, String fullContent) {
        JsonObject data = new JsonObject();
        String content = fullContent;
        for (RoqFrontMatterHeaderParserBuildItem headerParser : headerParsers) {
            data.mergeIn(headerParser.parse().apply(templateContext), true);
            content = headerParser.removeHeader().apply(content);
        }
        return new ParsedHeaders(data, content);
    }

    // ── Template ID and type resolution ─────────────────────────────────

    /**
     * Resolve the template id from the reference path.
     * Layouts strip the file extension; pages keep the full path.
     */
    public static String resolveTemplateId(String referencePath, boolean isLayout) {
        if (isLayout) {
            return removeExtension(referencePath);
        }
        return referencePath;
    }

    /**
     * Check if content is an HTML partial page (HTML target but no full document markers).
     */
    public static boolean isHtmlPartial(String content, boolean isHtml) {
        if (!isHtml) {
            return false;
        }
        String lower = content.toLowerCase(Locale.ROOT);
        return !(lower.contains("<html") || lower.contains("<!doctype"));
    }

    /**
     * Resolve the default layout for a page.
     * Returns null for layouts or when content is a full HTML document.
     */
    public static String resolveDefaultLayout(boolean isHtmlPartial, ConfiguredCollection collection,
            RoqSiteConfig config) {
        if (!isHtmlPartial) {
            return null;
        }
        return collection != null ? collection.layout() : config.pageLayout().orElse(null);
    }

    // ── Layout resolution ───────────────────────────────────────────────

    public static String getLayoutsDir(boolean isThemeLayout) {
        if (isThemeLayout) {
            return THEME_LAYOUTS_DIR_PREFIX + LAYOUTS_DIR;
        }
        return LAYOUTS_DIR;
    }

    public static String normalizedLayout(Optional<String> theme, String layout, String defaultLayout) {
        String normalized = layout;

        if (normalized == null) {
            normalized = defaultLayout;
            if (normalized == null || normalized.isBlank() || "none".equalsIgnoreCase(normalized)) {
                return null;
            }
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

        if (!normalized.contains(LAYOUTS_DIR_PREFIX)) {
            normalized = StringPaths.join(LAYOUTS_DIR_PREFIX, normalized);
        }
        return removeExtension(normalized);
    }

    public static String getLayoutKey(Optional<String> theme, String resolvedLayout) {
        String result = resolvedLayout;
        if (result.startsWith(LAYOUTS_DIR_PREFIX)) {
            result = result.substring(LAYOUTS_DIR_PREFIX.length());

            if (theme.isPresent() && result.contains(theme.get())) {
                result = result.replace(theme.get(), ":theme");
            }
        }
        return result;
    }

    public static String removeThemePrefix(String id) {
        return id.replace(getLayoutsDir(true),
                getLayoutsDir(false));
    }

    // ── Content transforms ──────────────────────────────────────────────

    public record TransformedContent(String generatedTemplate, String contentWithMarkup) {
    }

    public static WrapperFilter getIncludeFilter(String layout) {
        if (layout == null) {
            return WrapperFilter.EMPTY;
        }
        String prefix = "{#include %s%s}\n".formatted(ROQ_GENERATED_QUTE_PREFIX, layout);
        return new WrapperFilter(prefix, "\n{/include}");
    }

    public static WrapperFilter getMarkupFilter(
            Map<String, WrapperFilter> markups, String fileName) {
        return RoqFrontMatterQuteMarkupBuildItem.QuteMarkupSection.find(markups, fileName, WrapperFilter.EMPTY);
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

    /**
     * Apply escape filter, markup wrapper, and layout include to produce the final generated templates.
     */
    public static TransformedContent applyContentTransforms(String content, boolean escaped,
            RoqFrontMatterQuteMarkupBuildItem markup, String layoutId) {
        WrapperFilter escapeFilter = getEscapeFilter(escaped);
        WrapperFilter includeFilter = getIncludeFilter(layoutId);
        String escapedContent = escapeFilter.apply(content);
        String contentWithMarkup = markup != null ? markup.toWrapperFilter().apply(escapedContent) : escapedContent;
        String generatedTemplate = includeFilter.apply(contentWithMarkup);
        return new TransformedContent(generatedTemplate, contentWithMarkup);
    }
}
