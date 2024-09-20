package io.quarkiverse.roq.frontmatter.deployment.items;

import java.util.Map;
import java.util.function.Supplier;

import io.quarkiverse.roq.frontmatter.runtime.Page;
import io.quarkiverse.roq.frontmatter.runtime.RoqCollections;
import io.quarkus.builder.item.SimpleBuildItem;

public final class RoqFrontMatterOutputBuildItem extends SimpleBuildItem {

    private final Map<String, Supplier<Page>> pages;
    private final Supplier<RoqCollections> roqCollectionsSupplier;

    public RoqFrontMatterOutputBuildItem(Map<String, Supplier<Page>> pages, Supplier<RoqCollections> roqCollectionsSupplier) {
        this.pages = pages;
        this.roqCollectionsSupplier = roqCollectionsSupplier;
    }

    public Map<String, Supplier<Page>> pages() {
        return pages;
    }

    public Supplier<RoqCollections> roqCollectionsSupplier() {
        return roqCollectionsSupplier;
    }
}
