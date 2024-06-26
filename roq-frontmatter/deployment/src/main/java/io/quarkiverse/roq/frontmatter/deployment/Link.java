package io.quarkiverse.roq.frontmatter.deployment;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

import io.quarkiverse.roq.frontmatter.runtime.Page;
import io.vertx.core.json.JsonObject;

public class Link {
    private static final DateTimeFormatter YEAR_FORMAT = DateTimeFormatter.ofPattern("yyyy");
    private static final DateTimeFormatter MONTH_FORMAT = DateTimeFormatter.ofPattern("MM");
    private static final DateTimeFormatter DAY_FORMAT = DateTimeFormatter.ofPattern("dd");

    // Static map of placeholder handlers
    private static final Map<String, Function<JsonObject, String>> PLACEHOLDER_HANDLERS = new HashMap<>();

    static {
        LocalDateTime now = LocalDateTime.now();
        PLACEHOLDER_HANDLERS.put(":year", data -> data.getString("year", YEAR_FORMAT.format(now)));
        PLACEHOLDER_HANDLERS.put(":month", data -> data.getString("month", MONTH_FORMAT.format(now)));
        PLACEHOLDER_HANDLERS.put(":day", data -> data.getString("day", DAY_FORMAT.format(now)));
        PLACEHOLDER_HANDLERS.put(":name", data -> slugify(data.getString(Page.BASE_FILE_NAME_KEY)));
        PLACEHOLDER_HANDLERS.put(":title", data -> data.getString("slug", slugify(data.getString(Page.BASE_FILE_NAME_KEY))));
    }

    public static String link(JsonObject data) {
        String template = data.getString("link", "/:name");

        // Replace each placeholder in the template if it exists
        for (Map.Entry<String, Function<JsonObject, String>> entry : PLACEHOLDER_HANDLERS.entrySet()) {
            if (template.contains(entry.getKey())) {
                String replacement = entry.getValue().apply(data);
                template = template.replace(entry.getKey(), replacement);
            }
        }

        if (template.endsWith("index") || template.endsWith("index.html")) {
            return template.replaceAll("index(\\.html)?", "");
        }

        return template;
    }

    // Slugify logic to make the title URL-friendly
    private static String slugify(String input) {
        return input.toLowerCase()
                .replaceAll("[^a-z0-9\\-]", "-") // Replace non-alphanumeric characters with hyphens
                .replaceAll("-+", "-") // Replace multiple hyphens with a single one
                .replaceAll("^-|-$", ""); // Remove leading/trailing hyphens
    }

}
