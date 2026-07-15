package io.quarkiverse.roq.frontmatter.runtime.config;

import java.util.Optional;

import io.quarkiverse.roq.exception.RoqException;

public record ConfiguredCollection(
        String id,
        boolean derived,
        boolean hidden,
        boolean future,
        String layout,
        String link,
        Optional<CollectionFromData> fromData) {

    public ConfiguredCollection {
        requireNonEmpty(id, "id");

        layout = (layout == null || layout.isEmpty()) ? defaultLayoutFromCollectionId(id) : layout;

        fromData.ifPresent(item -> requireNonEmpty(item.idKey,
                "idKey (your configuration is missing a `site.collection.%s.from-data.id-key` property)".formatted(id)));
    }

    public String idKey() {
        return fromData.map(CollectionFromData::idKey).orElse(null);
    }

    public String dataName() {
        return fromData.map(CollectionFromData::name).orElse(id);
    }

    private void requireNonEmpty(String value, String name) {
        if (value == null || value.isEmpty())
            throw new RoqFrontMatterConfigException(
                    RoqException.builder("%s cannot be null or empty".formatted(name)));
    }

    private String defaultLayoutFromCollectionId(String collectionId) {
        if (collectionId.endsWith("s") && collectionId.length() > 1) {
            return collectionId.substring(0, collectionId.length() - 1);
        }
        return collectionId;
    }

    public record CollectionFromData(String idKey, String name) {
    }
}
