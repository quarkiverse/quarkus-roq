package io.quarkiverse.roq.frontmatter.runtime;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

import io.vertx.core.json.JsonObject;

public record Page(String id, JsonObject data) {
    public static final String DATE_KEY = "date";
    public static final String RAW_CONTENT_KEY = "rawContent";
    public static final String BASE_FILE_NAME_KEY = "baseFileName";
    public static final String FILE_NAME_KEY = "fileName";
    public static final String FILE_PATH_KEY = "filePath";
    public static final String LINK_KEY = "link";

    public String rawContent() {
        return data.getString(RAW_CONTENT_KEY);
    }

    public String link() {
        return data.getString(LINK_KEY);
    }

    public LocalDateTime date() {
        final String d = data.getString(DATE_KEY);
        if (d == null) {
            return LocalDateTime.now();
        }
        return LocalDateTime.parse(d, DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss Z"));
    }

    public Object data(String name) {
        if (data().containsKey(name)) {
            return data.getValue(name);
        }
        return null;
    }
}
