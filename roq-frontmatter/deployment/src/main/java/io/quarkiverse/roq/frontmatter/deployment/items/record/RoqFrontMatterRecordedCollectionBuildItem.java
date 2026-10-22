package io.quarkiverse.roq.frontmatter.deployment.items.record;

import java.util.List;
import java.util.function.Supplier;

import io.quarkiverse.roq.frontmatter.runtime.config.ConfiguredCollection;
import io.quarkiverse.roq.frontmatter.runtime.model.DocumentPage;
import io.quarkus.builder.item.MultiBuildItem;

public final class RoqFrontMatterRecordedCollectionBuildItem extends MultiBuildItem {
    private final ConfiguredCollection collection;
    private final Supplier<List<DocumentPage>> documents;

    public RoqFrontMatterRecordedCollectionBuildItem(ConfiguredCollection collection, Supplier<List<DocumentPage>> documents) {
        this.collection = collection;
        this.documents = documents;
    }

    public ConfiguredCollection collection() {
        return collection;
    }

    public Supplier<List<DocumentPage>> documents() {
        return documents;
    }
}
