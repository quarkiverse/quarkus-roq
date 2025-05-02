package io.quarkiverse.roq.frontmatter.deployment.data;

import java.nio.file.Path;

import io.quarkiverse.roq.frontmatter.runtime.config.ConfiguredCollection;
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

        JsonObject modify(SourceData sourceData);
    }

    /**
     * Represent the parsed data from a Roq template
     *
     * @param path the source file Path
     * @param relativePath the source file relative path (from the content directory)
     * @param collection the source file collection if defined
     * @param type
     * @param fm the FM data
     */
    public record SourceData(Path path, String relativePath, ConfiguredCollection collection,
            io.quarkiverse.roq.frontmatter.deployment.scan.RoqFrontMatterRawTemplateBuildItem.TemplateType type,
            JsonObject fm) {
    }
}
