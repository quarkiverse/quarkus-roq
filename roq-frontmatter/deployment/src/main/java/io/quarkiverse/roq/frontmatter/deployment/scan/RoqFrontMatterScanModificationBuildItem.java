package io.quarkiverse.roq.frontmatter.deployment.scan;

import io.quarkiverse.roq.frontmatter.runtime.config.ConfiguredCollection;
import io.quarkus.builder.item.MultiBuildItem;
import io.vertx.core.json.JsonObject;

import java.nio.file.Path;
import java.util.function.UnaryOperator;

/**
 * Allow to modify the scan result just before template source are produced from it.
 */
public final class RoqFrontMatterScanModificationBuildItem extends MultiBuildItem {
    private final UnaryOperator<SourceScanData> modifier;

    /**
     * Modifiers with the highest priority will run last.
     */
    private final int order;

    public RoqFrontMatterScanModificationBuildItem(UnaryOperator<SourceScanData> modifier, int order) {
        this.modifier = modifier;
        this.order = order;
    }

    public RoqFrontMatterScanModificationBuildItem(UnaryOperator<SourceScanData> modifier) {
        this.modifier = modifier;
        this.order = 0;
    }

    public UnaryOperator<SourceScanData> modifier() {
        return modifier;
    }

    public int order() {
        return order;
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
    public record SourceScanData(Path path, String relativePath, ConfiguredCollection collection,
            RoqFrontMatterRawTemplateBuildItem.TemplateType type,
            JsonObject fm) {
    }
}
