package io.quarkiverse.roq.frontmatter.runtime.model;

import io.quarkiverse.roq.frontmatter.runtime.model.RoqCollection.Paginator;
import io.vertx.core.json.JsonObject;

public record NormalPage(RoqUrl url, PageInfo info, JsonObject data, Paginator paginator) implements Page {

}