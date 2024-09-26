package io.quarkiverse.roq.frontmatter.deployment;

import static io.quarkiverse.roq.util.PathUtils.removeLeadingSlash;
import static io.quarkiverse.roq.util.PathUtils.removeTrailingSlash;

import java.time.LocalDateTime;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;

import io.quarkiverse.roq.util.PathUtils;
import io.vertx.core.json.JsonObject;

public class Link {
    public static final String DEFAULT_PAGE_LINK_TEMPLATE = "/:name";
    public static final String DEFAULT_PAGINATE_LINK_TEMPLATE = "/:collection/page:page";
    private static final DateTimeFormatter YEAR_FORMAT = DateTimeFormatter.ofPattern("yyyy");
    private static final DateTimeFormatter MONTH_FORMAT = DateTimeFormatter.ofPattern("MM");
    private static final DateTimeFormatter DAY_FORMAT = DateTimeFormatter.ofPattern("dd");

    // Static map of placeholder handlers
    private static final Map<String, Function<LinkData, String>> PLACEHOLDER_HANDLERS = new HashMap<>();

    static {
        LocalDateTime now = LocalDateTime.now();
        PLACEHOLDER_HANDLERS.put(":page",
                (data) -> Objects.requireNonNull(data.page(), "page index is required to build the link"));
        PLACEHOLDER_HANDLERS.put(":collection",
                (data) -> data.collection());
        PLACEHOLDER_HANDLERS.put(":year",
                (data) -> Optional.ofNullable(data.date()).orElse(ZonedDateTime.now()).format(YEAR_FORMAT));
        PLACEHOLDER_HANDLERS.put(":month",
                (data) -> Optional.ofNullable(data.date()).orElse(ZonedDateTime.now()).format(MONTH_FORMAT));
        PLACEHOLDER_HANDLERS.put(":day",
                (data) -> Optional.ofNullable(data.date()).orElse(ZonedDateTime.now()).format(DAY_FORMAT));
        PLACEHOLDER_HANDLERS.put(":name", (data) -> slugify(data.baseFileName()));
        PLACEHOLDER_HANDLERS.put(":title",
                (data) -> data.data.getString("slug", slugify(data.baseFileName())));
    }

    public static String link(String rootPath, String template, LinkData data) {
        String link = template;
        // Replace each placeholder in the template if it exists
        for (Map.Entry<String, Function<LinkData, String>> entry : PLACEHOLDER_HANDLERS.entrySet()) {
            if (link.contains(entry.getKey())) {
                String replacement = entry.getValue().apply(data);
                link = link.replace(entry.getKey(), replacement);
            }
        }

        if (link.endsWith("index") || link.endsWith("index.html")) {
            link = link.replaceAll("index(\\.html)?", "");
        }
        return removeTrailingSlash(removeLeadingSlash(PathUtils.join(rootPath, link)));
    }

    // Slugify logic to make the title URL-friendly
    private static String slugify(String input) {
        if (input == null) {
            throw new IllegalArgumentException("Link input cannot be null");
        }
        return input.toLowerCase()
                .replaceAll("[^a-z0-9\\-]", "-") // Replace non-alphanumeric characters with hyphens
                .replaceAll("-+", "-") // Replace multiple hyphens with a single one
                .replaceAll("^-|-$", ""); // Remove leading/trailing hyphens
    }

    public record LinkData(String baseFileName, ZonedDateTime date, String collection, String page, JsonObject data) {
    }

}
