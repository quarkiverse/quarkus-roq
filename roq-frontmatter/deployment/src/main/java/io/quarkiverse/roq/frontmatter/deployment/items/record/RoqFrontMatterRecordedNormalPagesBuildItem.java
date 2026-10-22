package io.quarkiverse.roq.frontmatter.deployment.items.record;

import java.util.List;
import java.util.function.Supplier;

import io.quarkiverse.roq.frontmatter.runtime.model.NormalPage;
import io.quarkus.builder.item.SimpleBuildItem;

/**
 * All normal pages (non-collection pages), recorded at once.
 */
public final class RoqFrontMatterRecordedNormalPagesBuildItem extends SimpleBuildItem {
    private final Supplier<List<NormalPage>> pages;
    private final int size;
    private final boolean hasSiteIndex;

    public RoqFrontMatterRecordedNormalPagesBuildItem(Supplier<List<NormalPage>> pages, int size, boolean hasSiteIndex) {
        this.pages = pages;
        this.size = size;
        this.hasSiteIndex = hasSiteIndex;
    }

    public Supplier<List<NormalPage>> pages() {
        return pages;
    }

    public boolean isEmpty() {
        return size == 0;
    }

    public boolean hasSiteIndex() {
        return hasSiteIndex;
    }
}
