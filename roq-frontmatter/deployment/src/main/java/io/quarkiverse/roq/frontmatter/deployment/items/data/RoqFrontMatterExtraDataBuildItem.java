package io.quarkiverse.roq.frontmatter.deployment.items.data;

import io.quarkus.builder.item.MultiBuildItem;
import io.vertx.core.json.JsonObject;

/**
 * Allows plugins to contribute additional data to a page's front matter at build time.
 * <p>
 * The extra data is merged into the page's {@code data()} in the recording step, with lower priority than
 * the user's own front matter (user FM keys always win). This lets plugins provide defaults that users can override.
 * <p>
 * Plugins produce this build item after Step 3 (when page URLs are resolved), and the merge happens in Step 5
 * (before pages are serialized), so it has no effect on layout resolution, date parsing, or draft filtering.
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
