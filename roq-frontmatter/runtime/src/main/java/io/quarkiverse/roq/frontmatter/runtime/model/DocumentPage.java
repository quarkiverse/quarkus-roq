package io.quarkiverse.roq.frontmatter.runtime.model;

import jakarta.enterprise.inject.Vetoed;

import io.quarkus.qute.TemplateData;
import io.vertx.core.json.JsonObject;

/**
 * This represents a document page (in a collection)
 *
 * @param url the url to this page
 * @param collection the collection id
 * @param info the page info
 * @param data the FM data of this page
 * @param hidden if hidden, the page is not visible on the given url
 */
@TemplateData
@Vetoed
public record DocumentPage(
        String collection,
        RoqUrl url,
        PageInfo info,
        JsonObject data,
        boolean hidden) implements Page {

}
