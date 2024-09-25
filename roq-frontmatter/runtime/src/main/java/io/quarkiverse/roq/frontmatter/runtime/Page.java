package io.quarkiverse.roq.frontmatter.runtime;

import static io.quarkiverse.roq.frontmatter.runtime.NormalPage.*;

import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;

import io.vertx.core.json.JsonObject;

public interface Page {

    String id();

    JsonObject data();

    RootUrl rootUrl();

    default PageUrl url() {
        return new PageUrl(rootUrl(), data().getString(LINK_KEY));
    }

    default String rawContent() {
        return data().getString(RAW_CONTENT_KEY);
    }

    default ZonedDateTime date() {
        final String d = data().getString(DATE_KEY);
        if (d == null) {
            // must be NULL if no date found to distinguish website (null) from blog posts (date)
            return null;
        }
        return ZonedDateTime.parse(d, DateTimeFormatter.ISO_DATE_TIME);
    }

    default Object data(String name) {
        if (data().containsKey(name)) {
            return data().getValue(name);
        }
        return null;
    }
}
