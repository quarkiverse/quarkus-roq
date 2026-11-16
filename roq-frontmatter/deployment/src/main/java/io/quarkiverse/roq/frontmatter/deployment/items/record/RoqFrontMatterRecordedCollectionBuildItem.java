package io.quarkiverse.roq.frontmatter.deployment.items.record;

import java.util.List;
import java.util.function.Supplier;

import io.quarkiverse.roq.frontmatter.runtime.config.ConfiguredCollection;
import io.quarkiverse.roq.frontmatter.runtime.model.DocumentPage;
import io.quarkiverse.roq.frontmatter.runtime.model.SerializedPage;
import io.quarkus.builder.item.MultiBuildItem;

public final class RoqFrontMatterRecordedCollectionBuildItem extends MultiBuildItem {
    private final ConfiguredCollection collection;
    private final Supplier<List<DocumentPage>> documents;
    private final List<SerializedPage> serialized;

    /**
     * @param documents the documents as the recorder builds them at runtime
     * @param serialized the same documents in their recorded form, for build-time consumers (see
     *        {@link RoqFrontMatterSiteModelBuildItem})
     */
    public RoqFrontMatterRecordedCollectionBuildItem(ConfiguredCollection collection,
            Supplier<List<DocumentPage>> documents, List<SerializedPage> serialized) {
        this.collection = collection;
        this.documents = documents;
        this.serialized = List.copyOf(serialized);
    }

    public List<SerializedPage> serialized() {
        return serialized;
    }

    public ConfiguredCollection collection() {
        return collection;
    }

    public Supplier<List<DocumentPage>> documents() {
        return documents;
    }
}
