package io.quarkiverse.roq.frontmatter.deployment;

import static io.quarkiverse.roq.frontmatter.deployment.data.RoqFrontMatterDataProcessor.FILE_NAME_DATE_PATTERN;
import static io.quarkiverse.roq.frontmatter.runtime.RoqTemplateExtension.slugify;
import static io.quarkiverse.roq.util.PathUtils.*;
import static io.quarkiverse.roq.util.PathUtils.slugify;

import java.nio.file.Path;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Supplier;

import io.quarkiverse.roq.frontmatter.deployment.exception.RoqTemplateLinkException;
import io.quarkiverse.roq.frontmatter.runtime.model.PageSource;
import io.quarkiverse.roq.util.PathUtils;
import io.vertx.core.json.JsonObject;

public class TemplateLink {
    public static final String DEFAULT_PAGE_LINK_TEMPLATE = "/:path:ext";
    public static final String DEFAULT_DOC_LINK_TEMPLATE = "/:collection/:slug/";
    public static final String DEFAULT_PAGINATE_LINK_TEMPLATE = "/:collection/page:page/";
    private static final DateTimeFormatter YEAR_FORMAT = DateTimeFormatter.ofPattern("yyyy");
    private static final DateTimeFormatter MONTH_FORMAT = DateTimeFormatter.ofPattern("MM");
    private static final DateTimeFormatter DAY_FORMAT = DateTimeFormatter.ofPattern("dd");

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
                                "Page '%s' uses ':collection' placeholder in the link, it should not be used outside of a collection."
                                        .formatted(data.pageSource().path()));
                    }
                    return data.collection();
                }),
                Map.entry(":year",
                        () -> Optional.ofNullable(data.pageSource().date()).orElse(ZonedDateTime.now()).format(YEAR_FORMAT)),
                Map.entry(":month",
                        () -> Optional.ofNullable(data.pageSource().date()).orElse(ZonedDateTime.now()).format(MONTH_FORMAT)),
                Map.entry(":day",
                        () -> Optional.ofNullable(data.pageSource().date()).orElse(ZonedDateTime.now()).format(DAY_FORMAT)),
                Map.entry(":raw-path", () -> removeExtension(data.pageSource().path())),
                Map.entry(":path",
                        () -> slugify(removeExtension(data.pageSource().path()), true, false).toLowerCase()),
                Map.entry(":ext",
                        () -> data.pageSource().isTargetHtml() ? ""
                                : "." + data.pageSource().extension()),
                Map.entry(":ext!",
                        () -> data.pageSource().isTargetHtml() ? ".html" : "." + data.pageSource().extension()),
                Map.entry(":slug", () -> resolveSlug(data).toLowerCase()),
                Map.entry(":Slug", () -> resolveSlug(data)),
                Map.entry(":name", () -> slugify(resolveName(data), true, false)
                        .toLowerCase()),
                Map.entry(":Name", () -> slugify(resolveName(data), true, false)))); // Case-preserving slug
        if (other != null) {
            result.putAll(other);
        }
        return result;
    }

    private static String resolveName(LinkData data) {
        final String name;
        if (data.pageSource().isIndex()) {
            final Path parent = Path.of(data.pageSource().path()).getParent();
            name = parent != null ? parent.getFileName().toString() : "not-available";
        } else {
            name = data.pageSource().baseFileName();
        }

        return removeDate(name);
    }

    public static String resolveSlug(LinkData data) {
        String title = data.data().getString("slug",
                data.data().getString("title"));
        if (title == null || title.isBlank()) {
            title = resolveName(data);
        }
        return slugify(title);
    }

    public static String pageLink(String basePath, String template, PageLinkData data) {
        return linkInternal(basePath, template,
                data.collection == null ? DEFAULT_PAGE_LINK_TEMPLATE : DEFAULT_DOC_LINK_TEMPLATE, data,
                withBasePlaceHolders(data, null));
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
        // Replace each placeholder in the template if it exists
        for (Map.Entry<String, Supplier<String>> entry : mapping.entrySet()) {
            if (link.contains(entry.getKey())) {
                String replacement = entry.getValue().get();
                if (replacement == null) {
                    throw new RoqTemplateLinkException("Link placeholder value for '%s' not found for 'link: %s' in page '%s'."
                            .formatted(entry.getKey(), template, data.pageSource().file().relativePath()));
                }
                link = link.replace(entry.getKey(), replacement);
            }
        }

        if (link.endsWith("index") || link.endsWith("index.html")) {
            link = link.replaceAll("index(\\.html)?", "");
        }
        return addTrailingSlashIfNoExt(removeLeadingSlash(PathUtils.join(basePath, link)));
    }

    private static String removeDate(String path) {
        return FILE_NAME_DATE_PATTERN.matcher(path).replaceAll("");
    }
}
