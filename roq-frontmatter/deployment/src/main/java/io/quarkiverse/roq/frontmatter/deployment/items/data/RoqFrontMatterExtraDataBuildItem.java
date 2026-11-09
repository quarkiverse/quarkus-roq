package io.quarkiverse.roq.frontmatter.deployment.items.data;

import io.quarkus.builder.item.MultiBuildItem;
import io.vertx.core.json.JsonObject;

/**
 * Contribute extra data to a page during recording (after URLs are resolved). Cannot override user FM.
 * To modify data that affects layout, link, date or draft resolution, use {@link RoqFrontMatterDataModificationBuildItem}.
 */
public final class RoqFrontMatterExtraDataBuildItem extends MultiBuildItem {

    private final String pageId;
    private final JsonObject data;

    /**
     * @param pageId the page's source id ({@code PageSource#id()})
     * @param data the extra data to merge (under user FM)
     */
    public RoqFrontMatterExtraDataBuildItem(String pageId, JsonObject data) {
        this.pageId = pageId;
        this.data = data;
    }

    public String pageId() {
        return pageId;
    }

    public JsonObject data() {
        return data;
    }
}
