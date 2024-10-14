package io.quarkiverse.roq.frontmatter.runtime.model;

import jakarta.enterprise.inject.Vetoed;

import io.quarkus.qute.TemplateData;
import io.vertx.core.json.JsonObject;

/**
 * This represents a normal "standalone" page (not in a collection)
 *
 * @param url the url to this page
 * @param info the page info
 * @param data the FM data of this page
 * @param paginator the paginator if any
 */
@TemplateData
@Vetoed
public record NormalPage(RoqUrl url, PageInfo info, JsonObject data, Paginator paginator) implements Page {

}
