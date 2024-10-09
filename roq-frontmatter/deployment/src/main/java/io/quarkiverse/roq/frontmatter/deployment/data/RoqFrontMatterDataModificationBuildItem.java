package io.quarkiverse.roq.frontmatter.deployment.data;

import io.quarkus.builder.item.MultiBuildItem;
import io.vertx.core.json.JsonObject;

/**
 * Allow to modify the FrontMatter data just before it is produced (and before it is merged with parents).
 */
public final class RoqFrontMatterDataModificationBuildItem extends MultiBuildItem {
    private final DataModifier modifier;

    /**
     * Modifiers with the highest priority will run last.
     */
    private final int order;

    public RoqFrontMatterDataModificationBuildItem(DataModifier modifier, int order) {
        this.modifier = modifier;
        this.order = order;
    }

    public RoqFrontMatterDataModificationBuildItem(DataModifier modifier) {
        this.modifier = modifier;
        this.order = 0;
    }

    public DataModifier modifier() {
        return modifier;
    }

    public int order() {
        return order;
    }

    public interface DataModifier {

        JsonObject modify(String resolvedPath, String sourcePath, JsonObject fm);
    }
}
