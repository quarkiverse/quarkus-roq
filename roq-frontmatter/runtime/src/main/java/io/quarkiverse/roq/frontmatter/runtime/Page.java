package io.quarkiverse.roq.frontmatter.runtime;

import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;

import io.quarkiverse.roq.frontmatter.runtime.RoqCollection.Paginator;
import io.vertx.core.json.JsonObject;

public record Page(RootUrl rootUrl, String id, JsonObject data, Paginator paginator) {

    public static final String PAGINATE_KEY = "paginate";
    public static final String DRAFT_KEY = "draft";
    public static final String COLLECTION_KEY = "collection";
    public static final String DATE_KEY = "date";
    public static final String RAW_CONTENT_KEY = "rawContent";
    public static final String BASE_FILE_NAME_KEY = "baseFileName";
    public static final String FILE_NAME_KEY = "fileName";
    public static final String FILE_PATH_KEY = "filePath";
    public static final String LINK_KEY = "link";
    public static final String PREVIOUS_INDEX_KEY = "nextIndex";
    public static final String NEXT_INDEX_KEY = "previousIndex";

    public String rawContent() {
        return data.getString(RAW_CONTENT_KEY);
    }

    public String collection() {
        return data.getString(COLLECTION_KEY);
    }

    public Integer previous() {
        return data.getInteger(PREVIOUS_INDEX_KEY);
    }

    public Integer prev() {
        return previous();
    }

    public Integer next() {
        return data.getInteger(NEXT_INDEX_KEY);
    }

    public PageUrl url() {
        return new PageUrl(rootUrl, data.getString(LINK_KEY));
    }

    public ZonedDateTime date() {
        final String d = data.getString(DATE_KEY);
        if (d == null) {
            // must be NULL if no date found to distinguish website (null) from blog posts (date)
            return null;
        }
        return ZonedDateTime.parse(d, DateTimeFormatter.ISO_DATE_TIME);
    }

    public Object data(String name) {
        if (data().containsKey(name)) {
            return data.getValue(name);
        }
        return null;
    }

}