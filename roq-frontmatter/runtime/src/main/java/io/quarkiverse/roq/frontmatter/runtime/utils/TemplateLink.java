package io.quarkiverse.roq.frontmatter.runtime.utils;

import static io.quarkiverse.roq.frontmatter.runtime.RoqFrontMatterKeys.SLUG;
import static io.quarkiverse.roq.frontmatter.runtime.RoqFrontMatterKeys.TITLE;
import static io.quarkiverse.roq.frontmatter.runtime.RoqTemplateExtension.slugify;
import static io.quarkiverse.tools.stringpaths.StringPaths.addTrailingSlashIfNoExt;
import static io.quarkiverse.tools.stringpaths.StringPaths.removeExtension;
import static io.quarkiverse.tools.stringpaths.StringPaths.removeLeadingSlash;

import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import io.quarkiverse.roq.exception.RoqException;
import io.quarkiverse.roq.frontmatter.runtime.exception.RoqTemplateLinkException;
import io.quarkiverse.roq.frontmatter.runtime.model.PageSource;
import io.quarkiverse.tools.stringpaths.StringPaths;
import io.vertx.core.json.JsonObject;

public class TemplateLink {
    public static final String DEFAULT_PAGE_LINK_TEMPLATE = "/:dir/:slug:ext";
    public static final String DEFAULT_DOC_LINK_TEMPLATE = "/:dir/:slug:ext";
    public static final String DEFAULT_PAGINATE_LINK_TEMPLATE = "/:collection/page:page/";
    private static final DateTimeFormatter YEAR_FORMAT = DateTimeFormatter.ofPattern("yyyy");
    private static final DateTimeFormatter MONTH_FORMAT = DateTimeFormatter.ofPattern("MM");
    private static final DateTimeFormatter DAY_FORMAT = DateTimeFormatter.ofPattern("dd");
    private static final Pattern PLACEHOLDER_PATTERN = Pattern.compile(":[a-zA-Z][a-zA-Z0-9-]*(~\\d+)?");
    private static final java.util.Set<String> TRUNCATABLE_PLACEHOLDERS = java.util.Set.of(":slug", ":Slug", ":name",
            ":Name");
    private static final Pattern FILE_NAME_DATE_PATTERN = Pattern.compile("(\\d{4}-\\d{1,2}-\\d{1,2})-");

    public interface LinkData {
        PageSource pageSource();

        String collection();

        JsonObject data();
    }

    public record PageLinkData(PageSource pageSource, String collection,
            JsonObject data) implements LinkData {
    }

    public record PaginateLinkData(PageSource pageSource, String collection, String page,
            JsonObject data) implements LinkData {
    }

    private static Map<String, Supplier<String>> withBasePlaceHolders(LinkData data, Map<String, Supplier<String>> other) {
        Map<String, Supplier<String>> result = new HashMap<>(Map.ofEntries(
                Map.entry(":collection", () -> {
                    if (data.collection() == null) {
                        throw new RoqTemplateLinkException(
                                RoqException.builder("Invalid link placeholder")
                                        .sourceInfo(data.pageSource().template().file().toSourceInfo())
                                        .detail("The ':collection' placeholder is used in the link template, but this page does not belong to a collection.")
                                        .hint("Remove ':collection' from the link template, or move this page into a collection."));
                    }
                    return data.collection();
                }),
                Map.entry(":year",
                        () -> Optional.ofNullable(data.pageSource().date()).orElse(ZonedDateTime.now()).format(YEAR_FORMAT)),
                Map.entry(":month",
                        () -> Optional.ofNullable(data.pageSource().date()).orElse(ZonedDateTime.now()).format(MONTH_FORMAT)),
                Map.entry(":day",
                        () -> Optional.ofNullable(data.pageSource().date()).orElse(ZonedDateTime.now()).format(DAY_FORMAT)),
                Map.entry(":dir", () -> {
                    String path = removeExtension(data.pageSource().path());
                    int lastSlash = path.lastIndexOf('/');
                    if (lastSlash < 0) {
                        return "";
                    }
                    String dir = path.substring(0, lastSlash);
                    if (data.pageSource().isIndex()) {
                        int parentSlash = dir.lastIndexOf('/');
                        dir = parentSlash >= 0 ? dir.substring(0, parentSlash) : "";
                    }
                    return dir.isEmpty() ? "" : StringPaths.slugify(removeDate(dir), true, true).toLowerCase();
                }),
                Map.entry(":raw-path", () -> removeExtension(data.pageSource().path())),
                Map.entry(":path",
                        () -> StringPaths.slugify(removeExtension(data.pageSource().path()), true, false).toLowerCase()),
                Map.entry(":ext",
                        () -> data.pageSource().isTargetHtml() ? ""
                                : "." + data.pageSource().extension()),
                Map.entry(":ext!",
                        () -> data.pageSource().isTargetHtml() ? ".html" : "." + data.pageSource().extension()),
                Map.entry(":slug", () -> resolveSlug(data).toLowerCase()),
                Map.entry(":Slug", () -> resolveSlug(data)),
                Map.entry(":name", () -> resolveName(data).toLowerCase()),
                Map.entry(":Name", () -> resolveName(data))));
        if (other != null) {
            result.putAll(other);
        }
        return result;
    }

    public static String resolveName(PageSource pageSource) {
        final String name;
        if (pageSource.isIndex()) {
            String path = pageSource.path();
            int lastSlash = path.lastIndexOf('/');
            name = lastSlash >= 0 ? StringPaths.fileName(path.substring(0, lastSlash)) : "not-available";
        } else {
            name = pageSource.baseFileName();
        }

        return StringPaths.slugify(removeDate(name), true, false);
    }

    private static String resolveName(LinkData data) {
        return resolveName(data.pageSource());
    }

    public static String resolveSlug(PageSource pageSource, JsonObject data) {
        String title = data.getString(SLUG,
                data.getString(TITLE));
        if (title == null || title.isBlank()) {
            return resolveName(pageSource);
        }
        return slugify(title);
    }

    public static String resolveSlug(LinkData data) {
        return resolveSlug(data.pageSource(), data.data());
    }

    public static String pageLink(String basePath, String template, PageLinkData data) {
        if (template == null && data.pageSource().isSiteIndex()) {
            return addTrailingSlashIfNoExt(removeLeadingSlash(StringPaths.join(basePath, "")));
        }
        return linkInternal(basePath, template,
                data.collection == null ? DEFAULT_PAGE_LINK_TEMPLATE : DEFAULT_DOC_LINK_TEMPLATE, data,
                withBasePlaceHolders(data, null));
    }

    public static String nameBasedPageLink(String basePath, PageLinkData data) {
        if (data.pageSource().isSiteIndex()) {
            return null;
        }
        String slug = resolveSlug(data).toLowerCase();
        String name = resolveName(data).toLowerCase();
        if (slug.equals(name)) {
            return null;
        }
        String defaultTemplate = data.collection == null ? DEFAULT_PAGE_LINK_TEMPLATE : DEFAULT_DOC_LINK_TEMPLATE;
        Map<String, Supplier<String>> mapping = withBasePlaceHolders(data, null);
        mapping.put(":slug", () -> resolveName(data).toLowerCase());
        mapping.put(":Slug", () -> resolveName(data));
        return linkInternal(basePath, null, defaultTemplate, data, mapping);
    }

    public static String paginateLink(String basePath, String template, PaginateLinkData data) {
        return linkInternal(basePath, template, DEFAULT_PAGINATE_LINK_TEMPLATE, data, withBasePlaceHolders(data, Map.of(
                ":page", () -> Objects.requireNonNull(data.page(), "page index is required to build the link"))));
    }

    public static String link(String basePath, String template, String defaultTemplate, LinkData data,
            Map<String, Supplier<String>> placeHolders) {
        return linkInternal(basePath, template, defaultTemplate, data, withBasePlaceHolders(data, placeHolders));
    }

    private static String linkInternal(String basePath, String template, String defaultTemplate,
            LinkData data, Map<String, Supplier<String>> mapping) {
        String link = template != null ? template : defaultTemplate;
        link = resolvePlaceholders(link, mapping, template, data);
        link = link.replaceAll("//+", "/");

        if (link.endsWith("/index") || link.endsWith("/index.html")
                || link.equals("index") || link.equals("index.html")) {
            link = link.replaceFirst("index(\\.html)?$", "");
        }
        return addTrailingSlashIfNoExt(removeLeadingSlash(StringPaths.join(basePath, link)));
    }

    /**
     * Resolve placeholders in a pattern string using the provided values map.
     * Supports ~W truncation on slug-like placeholders (e.g., :slug~3).
     *
     * @param pattern the pattern with :placeholder tokens (e.g., ":date-:slug~3")
     * @param values map of placeholder names (with colon prefix) to their values
     * @return the resolved string
     */
    public static String resolvePattern(String pattern, Map<String, String> values) {
        Map<String, Supplier<String>> suppliers = new HashMap<>();
        values.forEach((k, v) -> suppliers.put(k, () -> v));
        return resolvePlaceholders(pattern, suppliers, pattern, null);
    }

    private static String resolvePlaceholders(String template, Map<String, Supplier<String>> mapping,
            String templateForErrors, LinkData dataForErrors) {
        String result = template;
        for (Map.Entry<String, Supplier<String>> entry : mapping.entrySet()) {
            String key = entry.getKey();
            Matcher truncMatcher = Pattern.compile(Pattern.quote(key) + "~(\\d+)").matcher(result);
            if (truncMatcher.find()) {
                int wordLimit = Integer.parseInt(truncMatcher.group(1));
                if (wordLimit < 1) {
                    throwError("Invalid truncation value",
                            "Truncation value must be > 0 in '%s'.".formatted(truncMatcher.group()),
                            "Use a positive number, e.g., ':slug~3'.",
                            templateForErrors, dataForErrors);
                }
                if (!TRUNCATABLE_PLACEHOLDERS.contains(key)) {
                    throwError("Truncation not supported",
                            "The ~W truncation syntax is not supported on '%s'.".formatted(key),
                            "Truncation is only supported on: %s.".formatted(
                                    String.join(", ", TRUNCATABLE_PLACEHOLDERS)),
                            templateForErrors, dataForErrors);
                }
                String replacement = entry.getValue().get();
                if (replacement == null) {
                    throwError("Placeholder not resolved",
                            "Placeholder '%s' resolved to null.".formatted(key),
                            "Provide a value for '%s' or remove it from the template.".formatted(key),
                            templateForErrors, dataForErrors);
                }
                result = result.replace(truncMatcher.group(), truncateWords(replacement, wordLimit));
            } else if (result.contains(key)) {
                String replacement = entry.getValue().get();
                if (replacement == null) {
                    throwError("Placeholder not resolved",
                            "Placeholder '%s' resolved to null.".formatted(key),
                            "Provide a value for '%s' or remove it from the template.".formatted(key),
                            templateForErrors, dataForErrors);
                }
                result = result.replace(key, replacement);
            }
        }

        if (result.contains(":")) {
            Matcher matcher = PLACEHOLDER_PATTERN.matcher(result);
            if (matcher.find()) {
                throwError("Invalid placeholder",
                        "Unknown placeholder '%s' in template '%s'.".formatted(matcher.group(), templateForErrors),
                        "Valid placeholders: %s".formatted(String.join(", ", mapping.keySet())),
                        templateForErrors, dataForErrors);
            }
        }
        return result;
    }

    private static void throwError(String title, String detail, String hint, String template, LinkData data) {
        RoqException.Builder builder = RoqException.builder(title);
        if (data != null) {
            builder.sourceInfo(data.pageSource().template().file().toSourceInfo());
        }
        throw new RoqTemplateLinkException(builder.detail(detail).hint(hint));
    }

    static String truncateWords(String value, int wordLimit) {
        String[] segments = value.split("-");
        if (wordLimit >= segments.length) {
            return value;
        }
        return String.join("-", java.util.Arrays.copyOf(segments, wordLimit));
    }

    private static String removeDate(String path) {
        return FILE_NAME_DATE_PATTERN.matcher(path).replaceAll("");
    }
}
