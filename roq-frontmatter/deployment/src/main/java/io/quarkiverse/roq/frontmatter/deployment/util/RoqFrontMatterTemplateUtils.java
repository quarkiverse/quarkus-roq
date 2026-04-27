package io.quarkiverse.roq.frontmatter.deployment.util;

import static io.quarkiverse.roq.frontmatter.deployment.util.RoqFrontMatterConstants.FRONTMATTER_PATTERN;
import static io.quarkiverse.roq.frontmatter.deployment.util.RoqFrontMatterLayoutUtils.getIncludeFilter;
import static io.quarkiverse.tools.stringpaths.StringPaths.removeExtension;
import static io.quarkiverse.tools.stringpaths.StringPaths.toUnixPath;

import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.dataformat.yaml.YAMLMapper;

import io.quarkiverse.roq.frontmatter.deployment.items.scan.RoqFrontMatterHeaderParserBuildItem;
import io.quarkiverse.roq.frontmatter.deployment.items.scan.RoqFrontMatterQuteMarkupBuildItem;
import io.quarkiverse.roq.frontmatter.deployment.items.scan.RoqFrontMatterQuteMarkupBuildItem.WrapperFilter;
import io.quarkiverse.roq.frontmatter.deployment.items.scan.TemplateContext;
import io.vertx.core.json.JsonObject;

public final class RoqFrontMatterTemplateUtils {

    private static final WrapperFilter ESCAPE_FILTER = new WrapperFilter("{|", "|}");
    private static final Pattern COMPLETE_HTML_PATTERN = Pattern.compile("^\\s*(?:<!--.*?-->\\s*)*(<!doctype|<html)",
            Pattern.CASE_INSENSITIVE | Pattern.DOTALL);

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

    private static final Pattern QUTE_SUFFIX_PATTERN = Pattern.compile("\\.qute\\.([^.]+)$");

    /**
     * Normalize a reference path: convert to unix path and strip the ".qute." marker.
     * Files named "foo.qute.txt" are Qute templates that output as "foo.txt".
     */
    public static String normalizeReferencePath(String referencePath) {
        String path = toUnixPath(referencePath);
        if (path.contains(".qute.")) {
            path = QUTE_SUFFIX_PATTERN.matcher(path).replaceFirst(".$1");
        }
        return path;
    }

    public static boolean isDotQuteTemplate(String path) {
        return path.contains(".qute.") && QUTE_SUFFIX_PATTERN.matcher(path).find();
    }

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
     * Check if content is a partial HTML document (not a complete HTML document with DOCTYPE/html tags).
     * Partial HTML needs a layout wrapper. Complete HTML docs start with DOCTYPE or html tags.
     * Uses regex to only match document markers at the start of content, preventing false positives
     * from HTML code examples in documentation.
     */
    public static boolean isPartialHtmlDocument(String content, boolean isHtml) {
        if (!isHtml) {
            return false;
        }
        // Fast path: if content doesn't contain <html or <!doctype, it's definitely partial
        String lower = content.toLowerCase();
        if (!lower.contains("<html") && !lower.contains("<!doctype")) {
            return true;
        }
        // Only treat as complete HTML if the markers appear at the start of the document
        return !COMPLETE_HTML_PATTERN.matcher(content).find();
    }

    // ── Content transforms ──────────────────────────────────────────────

    public record TransformedContent(String generatedTemplate) {
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
            RoqFrontMatterQuteMarkupBuildItem markup, String layoutId, boolean isPage) {
        WrapperFilter escapeFilter = getEscapeFilter(escaped);
        WrapperFilter includeFilter = getIncludeFilter(layoutId, isPage);
        String escapedContent = escapeFilter.apply(content);
        String contentWithMarkup = markup != null ? markup.toWrapperFilter().apply(escapedContent) : escapedContent;
        String generatedTemplate = includeFilter.apply(contentWithMarkup);
        return new TransformedContent(generatedTemplate);
    }
}
