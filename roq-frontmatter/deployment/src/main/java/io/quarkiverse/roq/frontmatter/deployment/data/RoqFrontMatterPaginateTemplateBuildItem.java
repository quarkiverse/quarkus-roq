package io.quarkiverse.roq.frontmatter.deployment.data;

import io.quarkiverse.roq.frontmatter.deployment.scan.RoqFrontMatterRawTemplateBuildItem;
import io.quarkus.builder.item.MultiBuildItem;
import io.vertx.core.json.JsonObject;

public final class RoqFrontMatterPaginateTemplateBuildItem extends MultiBuildItem {

    private final RoqFrontMatterRawTemplateBuildItem item;
    private final String paginatedCollection;
    private final String link;
    private final JsonObject data;

    public RoqFrontMatterPaginateTemplateBuildItem(RoqFrontMatterRawTemplateBuildItem item, String paginatedCollection,
            String link, JsonObject data) {
        this.item = item;
        this.paginatedCollection = paginatedCollection;
        this.link = link;
        this.data = data;
    }

    public String defaultPaginatedCollection() {
        return paginatedCollection;
    }

    public RoqFrontMatterRawTemplateBuildItem item() {
        return item;
    }

    public String link() {
        return link;
    }

    public JsonObject data() {
        return data;
    }
}
