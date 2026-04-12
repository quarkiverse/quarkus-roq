package io.quarkiverse.roq.frontmatter.deployment.items.scan;

import java.util.List;

import io.quarkiverse.roq.frontmatter.deployment.items.assemble.RoqFrontMatterAttachment;
import io.quarkiverse.roq.frontmatter.runtime.config.ConfiguredCollection;
import io.quarkus.builder.item.MultiBuildItem;

/**
 * A scanned content file (page or document) with pre-collected metadata,
 * ready to be processed into a {@link RoqFrontMatterRawPageBuildItem}.
 */
public final class RoqFrontMatterScannedContentBuildItem extends MultiBuildItem {

    private final FrontMatterTemplateMetadata metadata;
    private final ConfiguredCollection collection;
    private final boolean isIndex;
    private final boolean isSiteIndex;
    private final List<RoqFrontMatterAttachment> attachments;

    public RoqFrontMatterScannedContentBuildItem(FrontMatterTemplateMetadata metadata,
            ConfiguredCollection collection, boolean isIndex, boolean isSiteIndex,
            List<RoqFrontMatterAttachment> attachments) {
        this.metadata = metadata;
        this.collection = collection;
        this.isIndex = isIndex;
        this.isSiteIndex = isSiteIndex;
        this.attachments = attachments;
    }

    public FrontMatterTemplateMetadata metadata() {
        return metadata;
    }

    public ConfiguredCollection collection() {
        return collection;
    }

    public boolean isIndex() {
        return isIndex;
    }

    public boolean isSiteIndex() {
        return isSiteIndex;
    }

    public List<RoqFrontMatterAttachment> attachments() {
        return attachments;
    }
}
