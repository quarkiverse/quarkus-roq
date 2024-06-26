package io.quarkiverse.roq.frontmatter.deployment.items;

import java.util.Objects;

import io.quarkus.builder.item.MultiBuildItem;
import io.vertx.core.json.JsonObject;

/**
 * A build item representing a Roq fm file.
 */
public final class RoqFrontMatterBuildItem extends MultiBuildItem {

    /**
     * The name of the Roq fm file.
     */
    private final String path;

    private final String layout;

    /**
     * The FrontMatter data
     */
    private final JsonObject fm;

    private final String generatedContent;

    public RoqFrontMatterBuildItem(String path, String layout, JsonObject fm, String generatedContent) {
        this.path = path;
        this.layout = layout;
        this.fm = fm;
        this.generatedContent = generatedContent;
    }

    public String path() {
        return path;
    }

    public JsonObject fm() {
        return fm;
    }

    public String layout() {
        return layout;
    }

    public String generatedContent() {
        return generatedContent;
    }

    @Override
    public boolean equals(Object object) {
        if (this == object)
            return true;
        if (object == null || getClass() != object.getClass())
            return false;
        RoqFrontMatterBuildItem that = (RoqFrontMatterBuildItem) object;
        return Objects.equals(path, that.path) && Objects.equals(layout, that.layout) && Objects.equals(fm, that.fm)
                && Objects.equals(generatedContent, that.generatedContent);
    }

    @Override
    public int hashCode() {
        return Objects.hash(path, layout, fm, generatedContent);
    }
}
