package io.quarkiverse.roq.frontmatter.deployment.publish;

import java.util.List;

import io.quarkus.builder.item.MultiBuildItem;
import io.vertx.core.json.JsonObject;

public final class RoqFrontMatterPublishDerivedCollectionBuildItem extends MultiBuildItem {
    private final String collection;
    private final List<String> documentIds;
    private final JsonObject data;

    public RoqFrontMatterPublishDerivedCollectionBuildItem(String collection, List<String> documentIds, JsonObject data) {
        this.collection = collection;
        this.documentIds = documentIds;
        this.data = data;
    }

    public String collection() {
        return collection;
    }

    public List<String> documentIds() {
        return documentIds;
    }

    public JsonObject data() {
        return data;
    }
}
