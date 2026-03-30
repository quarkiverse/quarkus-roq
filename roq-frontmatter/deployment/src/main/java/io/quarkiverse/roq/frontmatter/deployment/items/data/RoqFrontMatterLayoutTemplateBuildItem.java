package io.quarkiverse.roq.frontmatter.deployment.items.data;

import io.quarkiverse.roq.frontmatter.deployment.items.assemble.RoqFrontMatterRawLayoutBuildItem;
import io.quarkus.builder.item.MultiBuildItem;
import io.vertx.core.json.JsonObject;

/**
 * A build item representing a Roq layout template.
 * The data has been inherited from parent layouts.
 * This is meant for reading purpose.
 */
public final class RoqFrontMatterLayoutTemplateBuildItem extends MultiBuildItem {
    private final RoqFrontMatterRawLayoutBuildItem raw;
    private final JsonObject data;

    public RoqFrontMatterLayoutTemplateBuildItem(RoqFrontMatterRawLayoutBuildItem raw, JsonObject data) {
        this.raw = raw;
        this.data = data;
    }

    public JsonObject data() {
        return data;
    }

    public RoqFrontMatterRawLayoutBuildItem raw() {
        return raw;
    }

}
