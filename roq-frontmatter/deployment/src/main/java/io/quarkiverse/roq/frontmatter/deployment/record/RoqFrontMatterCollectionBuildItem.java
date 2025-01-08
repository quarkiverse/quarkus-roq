package io.quarkiverse.roq.frontmatter.deployment.record;

import java.util.List;
import java.util.function.Supplier;

import io.quarkiverse.roq.frontmatter.runtime.config.ConfiguredCollection;
import io.quarkiverse.roq.frontmatter.runtime.model.DocumentPage;
import io.quarkus.builder.item.MultiBuildItem;

public final class RoqFrontMatterCollectionBuildItem extends MultiBuildItem {
    private final ConfiguredCollection collection;
    private final List<Supplier<DocumentPage>> documents;

    public RoqFrontMatterCollectionBuildItem(ConfiguredCollection collection, List<Supplier<DocumentPage>> documents) {
        this.collection = collection;
        this.documents = documents;
    }

    public ConfiguredCollection collection() {
        return collection;
    }

    public List<Supplier<DocumentPage>> documents() {
        return documents;
    }
}
