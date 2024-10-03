package io.quarkiverse.roq.frontmatter.deployment.publish;

import io.quarkiverse.roq.frontmatter.runtime.model.PageInfo;
import io.quarkus.builder.item.MultiBuildItem;
import io.vertx.core.json.JsonObject;

public final class RoqFrontMatterPublishDocumentPageBuildItem extends MultiBuildItem {
    private final String link;
    private final PageInfo info;
    private final String collection;
    private final JsonObject data;

    public RoqFrontMatterPublishDocumentPageBuildItem(String link, PageInfo info, String collection, JsonObject data) {
        this.link = link;
        this.info = info;
        this.collection = collection;
        this.data = data;
    }

    public String collection() {
        return collection;
    }

    public String link() {
        return link;
    }

    public PageInfo info() {
        return info;
    }

    public JsonObject data() {
        return data;
    }
}
