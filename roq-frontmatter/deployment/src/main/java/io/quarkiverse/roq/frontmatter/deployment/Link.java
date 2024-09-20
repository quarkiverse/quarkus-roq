package io.quarkiverse.roq.frontmatter.deployment;

import static io.quarkiverse.roq.util.PathUtils.removeLeadingSlash;
import static io.quarkiverse.roq.util.PathUtils.removeTrailingSlash;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

import io.quarkiverse.roq.frontmatter.runtime.Page;
import io.quarkiverse.roq.util.PathUtils;
import io.vertx.core.json.JsonObject;

public class Link {
    public static final String DEFAULT_PAGE_LINK_TEMPLATE = "/:name";
    public static final String DEFAULT_PAGINATE_LINK_TEMPLATE = "/:collection/page:page";
    private static final DateTimeFormatter YEAR_FORMAT = DateTimeFormatter.ofPattern("yyyy");
    private static final DateTimeFormatter MONTH_FORMAT = DateTimeFormatter.ofPattern("MM");
    private static final DateTimeFormatter DAY_FORMAT = DateTimeFormatter.ofPattern("dd");

    // Static map of placeholder handlers
    private static final Map<String, Function<JsonObject, String>> PLACEHOLDER_HANDLERS = new HashMap<>();

    static {
        LocalDateTime now = LocalDateTime.now();
        PLACEHOLDER_HANDLERS.put(":page", (data) -> data.getString("page"));
        PLACEHOLDER_HANDLERS.put(":collection",
                (data) -> data.getString(Page.COLLECTION_KEY));
        PLACEHOLDER_HANDLERS.put(":year", (data) -> data.getString("year", YEAR_FORMAT.format(now)));
        PLACEHOLDER_HANDLERS.put(":month", (data) -> data.getString("month", MONTH_FORMAT.format(now)));
        PLACEHOLDER_HANDLERS.put(":day", (data) -> data.getString("day", DAY_FORMAT.format(now)));
        PLACEHOLDER_HANDLERS.put(":name", (data) -> slugify(data.getString(Page.BASE_FILE_NAME_KEY)));
        PLACEHOLDER_HANDLERS.put(":title",
                (data) -> data.getString("slug", slugify(data.getString(Page.BASE_FILE_NAME_KEY))));
    }

    public static String link(String rootPath, String template, JsonObject data) {
        String link = template;
        // Replace each placeholder in the template if it exists
        for (Map.Entry<String, Function<JsonObject, String>> entry : PLACEHOLDER_HANDLERS.entrySet()) {
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
        return input.toLowerCase()
                .replaceAll("[^a-z0-9\\-]", "-") // Replace non-alphanumeric characters with hyphens
                .replaceAll("-+", "-") // Replace multiple hyphens with a single one
                .replaceAll("^-|-$", ""); // Remove leading/trailing hyphens
    }

}
