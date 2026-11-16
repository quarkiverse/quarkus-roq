package io.quarkiverse.roq.frontmatter.deployment.items.record;

import java.util.List;
import java.util.function.Supplier;

import io.quarkiverse.roq.frontmatter.runtime.model.NormalPage;
import io.quarkiverse.roq.frontmatter.runtime.model.SerializedPage;
import io.quarkus.builder.item.SimpleBuildItem;

/**
 * All normal pages (non-collection pages), recorded at once.
 */
public final class RoqFrontMatterRecordedNormalPagesBuildItem extends SimpleBuildItem {
    private final Supplier<List<NormalPage>> pages;
    private final List<SerializedPage> serialized;
    private final boolean hasSiteIndex;

    /**
     * @param pages the pages as the recorder builds them at runtime
     * @param serialized the same pages in their recorded form, for build-time consumers (see
     *        {@link RoqFrontMatterSiteModelBuildItem})
     */
    public RoqFrontMatterRecordedNormalPagesBuildItem(Supplier<List<NormalPage>> pages, List<SerializedPage> serialized,
            boolean hasSiteIndex) {
        this.pages = pages;
        this.serialized = List.copyOf(serialized);
        this.hasSiteIndex = hasSiteIndex;
    }

    public List<SerializedPage> serialized() {
        return serialized;
    }

    public Supplier<List<NormalPage>> pages() {
        return pages;
    }

    public boolean isEmpty() {
        return serialized.isEmpty();
    }

    public boolean hasSiteIndex() {
        return hasSiteIndex;
    }
}
