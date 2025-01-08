package io.quarkiverse.roq.frontmatter.deployment.publish;

import java.util.List;

import io.quarkiverse.roq.frontmatter.runtime.config.ConfiguredCollection;
import io.quarkus.builder.item.MultiBuildItem;
import io.vertx.core.json.JsonObject;

public final class RoqFrontMatterPublishDerivedCollectionBuildItem extends MultiBuildItem {
    private final ConfiguredCollection collection;
    private final List<String> documentIds;
    private final JsonObject data;

    public RoqFrontMatterPublishDerivedCollectionBuildItem(ConfiguredCollection collection, List<String> documentIds,
            JsonObject data) {
        this.collection = collection;
        this.documentIds = documentIds;
        this.data = data;
    }

    public ConfiguredCollection collection() {
        return collection;
    }

    public List<String> documentIds() {
        return documentIds;
    }

    public JsonObject data() {
        return data;
    }
}
