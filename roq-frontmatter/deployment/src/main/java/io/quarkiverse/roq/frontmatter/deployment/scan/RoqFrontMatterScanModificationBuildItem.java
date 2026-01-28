package io.quarkiverse.roq.frontmatter.deployment.scan;

import java.util.List;
import java.util.function.Consumer;

import io.quarkus.builder.item.MultiBuildItem;

/**
 * Allow to modify the scan result just before template source are produced from it.
 */
public final class RoqFrontMatterScanModificationBuildItem extends MultiBuildItem {
    private final Consumer<List<RoqFrontMatterRawTemplateBuildItem>> modifier;

    /**
     * Modifiers with the highest priority will run last.
     */
    private final int order;

    public RoqFrontMatterScanModificationBuildItem(Consumer<List<RoqFrontMatterRawTemplateBuildItem>> modifier, int order) {
        this.modifier = modifier;
        this.order = order;
    }

    public RoqFrontMatterScanModificationBuildItem(Consumer<List<RoqFrontMatterRawTemplateBuildItem>> modifier) {
        this.modifier = modifier;
        this.order = 0;
    }

    public Consumer<List<RoqFrontMatterRawTemplateBuildItem>> modifier() {
        return modifier;
    }

    public int order() {
        return order;
    }
}
