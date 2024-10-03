package io.quarkiverse.roq.frontmatter.runtime.model;

import jakarta.enterprise.inject.Vetoed;

import io.quarkus.qute.TemplateData;
import io.vertx.core.json.JsonObject;

@TemplateData
@Vetoed
public record DocumentPage(
        String collection,
        RoqUrl url,
        PageInfo info,
        JsonObject data) implements Page {

}
