package io.quarkiverse.roq.frontmatter.runtime;

import static io.quarkiverse.roq.frontmatter.runtime.NormalPage.*;

import io.vertx.core.json.JsonObject;

public record DocumentPage(RootUrl rootUrl, String id, JsonObject data) implements Page {

    public static final String COLLECTION_KEY = "collection";
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

}
