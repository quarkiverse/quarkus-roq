package io.quarkiverse.roq.frontmatter.deployment;

import static io.quarkiverse.roq.frontmatter.runtime.RoqTemplateExtension.slugify;
import static io.quarkiverse.roq.util.PathUtils.*;
import static io.quarkiverse.roq.util.PathUtils.slugify;

import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Supplier;

import io.quarkiverse.roq.frontmatter.runtime.model.PageInfo;
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
        PageInfo pageInfo();

        String collection();

        JsonObject data();
    }

    public record PageLinkData(PageInfo pageInfo, String collection,
            JsonObject data) implements LinkData {
    }

    public record PaginateLinkData(PageInfo pageInfo, String collection, String page,
            JsonObject data) implements LinkData {
    }

    private static Map<String, Supplier<String>> withBasePlaceHolders(LinkData data, Map<String, Supplier<String>> other) {
        Map<String, Supplier<String>> result = new HashMap<>(Map.ofEntries(
                Map.entry(":collection", data::collection),
                Map.entry(":year",
                        () -> Optional.ofNullable(data.pageInfo().date()).orElse(ZonedDateTime.now()).format(YEAR_FORMAT)),
                Map.entry(":month",
                        () -> Optional.ofNullable(data.pageInfo().date()).orElse(ZonedDateTime.now()).format(MONTH_FORMAT)),
                Map.entry(":day",
                        () -> Optional.ofNullable(data.pageInfo().date()).orElse(ZonedDateTime.now()).format(DAY_FORMAT)),
                Map.entry(":path", () -> slugify(removeExtension(data.pageInfo().sourceFilePath()), true, false).toLowerCase()),
                Map.entry(":ext",
                        () -> data.pageInfo().isHtml() ? ""
                                : "." + data.pageInfo().sourceFileExtension()),
                Map.entry(":ext!", () -> data.pageInfo().isHtml() ? ".html" : "." + data.pageInfo().sourceFileExtension()),
                Map.entry(":slug", () -> resolveSlug(data))));
        if (other != null) {
            result.putAll(other);
        }
        return result;
    }

    public static String resolveSlug(LinkData data) {
        String title = data.data().getString("slug",
                data.data().getString("title"));
        if (title == null || title.isBlank()) {
            final String baseFileName = data.pageInfo().sourceBaseFileName();
            if ("index".equalsIgnoreCase(baseFileName)) {
                // in this case we take the parent dir name
                title = PathUtils.fileName(data.pageInfo().sourceFilePath().replaceAll("/index\\..+", ""));
            } else {
                title = baseFileName;
            }
        }
        return slugify(title).toLowerCase();
    }

    public static String pageLink(String rootPath, String template, PageLinkData data) {
        return link(rootPath, template, data.collection == null ? DEFAULT_PAGE_LINK_TEMPLATE : DEFAULT_DOC_LINK_TEMPLATE,
                withBasePlaceHolders(data, null));
    }

    public static String paginateLink(String rootPath, String template, PaginateLinkData data) {
        return link(rootPath, template, DEFAULT_PAGINATE_LINK_TEMPLATE, withBasePlaceHolders(data, Map.of(
                ":page", () -> Objects.requireNonNull(data.page(), "page index is required to build the link"))));
    }

    public static String link(String rootPath, String template, String defaultTemplate, LinkData data,
            Map<String, Supplier<String>> placeHolders) {
        return link(rootPath, template, defaultTemplate, withBasePlaceHolders(data, placeHolders));
    }

    private static String link(String rootPath, String template, String defaultTemplate,
            Map<String, Supplier<String>> mapping) {
        String link = template != null ? template : defaultTemplate;
        // Replace each placeholder in the template if it exists
        for (Map.Entry<String, Supplier<String>> entry : mapping.entrySet()) {
            if (link.contains(entry.getKey())) {
                String replacement = entry.getValue().get();
                link = link.replace(entry.getKey(), replacement);
            }
        }

        if (link.endsWith("index") || link.endsWith("index.html")) {
            link = link.replaceAll("index(\\.html)?", "");
        }
        return addTrailingSlashIfNoExt(removeLeadingSlash(PathUtils.join(rootPath, link)));
    }

}
