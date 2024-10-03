package io.quarkiverse.roq.frontmatter.deployment;

import java.util.Map;
import java.util.function.Supplier;

import io.quarkiverse.roq.frontmatter.runtime.model.Page;
import io.quarkiverse.roq.frontmatter.runtime.model.RoqCollections;
import io.quarkus.builder.item.SimpleBuildItem;

public final class RoqFrontMatterOutputBuildItem extends SimpleBuildItem {

    private final Map<String, Supplier<? extends Page>> allPagesByPath;
    private final Supplier<RoqCollections> roqCollectionsSupplier;

    public RoqFrontMatterOutputBuildItem(Map<String, Supplier<? extends Page>> allPagesByPath,
            Supplier<RoqCollections> roqCollectionsSupplier) {
        this.allPagesByPath = allPagesByPath;
        this.roqCollectionsSupplier = roqCollectionsSupplier;
    }

    public Map<String, Supplier<? extends Page>> allPagesByPath() {
        return allPagesByPath;
    }

    public Supplier<RoqCollections> roqCollectionsSupplier() {
        return roqCollectionsSupplier;
    }
}
