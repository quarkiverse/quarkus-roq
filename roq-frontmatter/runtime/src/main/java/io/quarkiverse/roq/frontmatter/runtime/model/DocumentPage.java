package io.quarkiverse.roq.frontmatter.runtime.model;

import io.vertx.core.json.JsonObject;

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
