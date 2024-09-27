package io.quarkiverse.roq.frontmatter.runtime.model;

import jakarta.enterprise.inject.Vetoed;

import io.quarkus.qute.TemplateData;
import io.vertx.core.json.JsonObject;

@TemplateData
@Vetoed
public record DocumentPage(
        RoqUrl url,
        PageInfo info,
        DocumentInfo doc,
        JsonObject data) implements Page {

    public String collection() {
        return doc.collection();
    }

    public Integer nextIndex() {
        return doc.nextIndex();
    }

    public Integer previousIndex() {
        return doc.previousIndex();
    }
}
