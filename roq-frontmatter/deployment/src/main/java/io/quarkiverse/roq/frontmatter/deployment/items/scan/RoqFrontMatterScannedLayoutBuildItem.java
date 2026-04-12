package io.quarkiverse.roq.frontmatter.deployment.items.scan;

import io.quarkus.builder.item.MultiBuildItem;

/**
 * A scanned layout file with pre-collected metadata,
 * ready to be processed into a {@link RoqFrontMatterRawLayoutBuildItem}.
 */
public final class RoqFrontMatterScannedLayoutBuildItem extends MultiBuildItem {

    private final FrontMatterTemplateMetadata metadata;
    private final boolean themeLayout;

    public RoqFrontMatterScannedLayoutBuildItem(FrontMatterTemplateMetadata metadata, boolean themeLayout) {
        this.metadata = metadata;
        this.themeLayout = themeLayout;
    }

    public FrontMatterTemplateMetadata metadata() {
        return metadata;
    }

    public boolean isThemeLayout() {
        return themeLayout;
    }
}
