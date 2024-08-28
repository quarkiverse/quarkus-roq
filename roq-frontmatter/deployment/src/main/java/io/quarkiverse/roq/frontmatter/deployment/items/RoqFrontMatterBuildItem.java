package io.quarkiverse.roq.frontmatter.deployment.items;

import java.util.Objects;

import io.quarkiverse.roq.util.PathUtils;
import io.quarkus.builder.item.MultiBuildItem;
import io.vertx.core.json.JsonObject;

/**
 * A build item representing a Roq fm file.
 */
public final class RoqFrontMatterBuildItem extends MultiBuildItem {

    private final String collection;

    /**
     * The name of the Roq FrontMatter source file.
     */
    private final String sourcePath;

    private final String templatePath;
    private final String key;

    private final String layout;

    /**
     * The FrontMatter data
     */
    private final JsonObject fm;

    private final String generatedContent;
    private final boolean visible;

    public RoqFrontMatterBuildItem(String collection, String sourcePath, String templatePath, boolean visible, String layout,
            JsonObject fm,
            String generatedContent) {
        this.collection = collection;
        this.sourcePath = sourcePath;
        this.templatePath = templatePath;
        this.key = PathUtils.removeExtension(templatePath);
        this.visible = visible;
        this.layout = layout;
        this.fm = fm;
        this.generatedContent = generatedContent;
    }

    public String collection() {
        return collection;
    }

    public String sourcePath() {
        return sourcePath;
    }

    public String templatePath() {
        return templatePath;
    }

    public String key() {
        return key;
    }

    public boolean visible() {
        return visible;
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
    public boolean equals(Object o) {
        if (this == o)
            return true;
        if (o == null || getClass() != o.getClass())
            return false;
        RoqFrontMatterBuildItem that = (RoqFrontMatterBuildItem) o;
        return Objects.equals(collection, that.collection) && Objects.equals(sourcePath, that.sourcePath)
                && Objects.equals(templatePath, that.templatePath) && Objects.equals(key, that.key)
                && Objects.equals(layout, that.layout) && Objects.equals(fm, that.fm)
                && Objects.equals(generatedContent, that.generatedContent);
    }

    @Override
    public int hashCode() {
        return Objects.hash(collection, sourcePath, templatePath, key, layout, fm, generatedContent);
    }
}
