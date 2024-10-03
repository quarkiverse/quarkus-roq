package io.quarkiverse.roq.frontmatter.deployment.data;

import io.quarkiverse.roq.frontmatter.deployment.publish.RoqFrontMatterPublishDocumentPageBuildItem;
import io.quarkiverse.roq.frontmatter.deployment.publish.RoqFrontMatterPublishPageBuildItem;
import io.quarkiverse.roq.frontmatter.deployment.scan.RoqFrontMatterRawTemplateBuildItem;
import io.quarkus.builder.item.MultiBuildItem;
import io.vertx.core.json.JsonObject;

/**
 * This is NOT meant to be produced by Roq extensions.
 * This is meant to process the data and then publish
 * {@link RoqFrontMatterPublishDocumentPageBuildItem} and {@link RoqFrontMatterPublishPageBuildItem}
 */
public final class RoqFrontMatterDocumentTemplateBuildItem extends MultiBuildItem {
    private final RoqFrontMatterRawTemplateBuildItem item;
    private final String link;
    private final String collection;
    private final JsonObject data;

    RoqFrontMatterDocumentTemplateBuildItem(RoqFrontMatterRawTemplateBuildItem item, String link, String collection,
            JsonObject data) {
        this.item = item;
        this.link = link;
        this.collection = collection;
        this.data = data;
    }

    public RoqFrontMatterRawTemplateBuildItem item() {
        return item;
    }

    public String collection() {
        return collection;
    }

    public String link() {
        return link;
    }

    public JsonObject data() {
        return data;
    }
}
