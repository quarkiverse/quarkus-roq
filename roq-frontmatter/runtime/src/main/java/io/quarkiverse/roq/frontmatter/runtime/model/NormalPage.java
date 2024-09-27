package io.quarkiverse.roq.frontmatter.runtime.model;

import jakarta.enterprise.inject.Vetoed;

import io.quarkus.qute.TemplateData;
import io.vertx.core.json.JsonObject;

@TemplateData
@Vetoed
public record NormalPage(RoqUrl url, PageInfo info, JsonObject data, Paginator paginator) implements Page {

}
