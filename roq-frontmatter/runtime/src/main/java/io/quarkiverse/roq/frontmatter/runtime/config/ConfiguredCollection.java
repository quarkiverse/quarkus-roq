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
        String effectiveLayout = (layout == null || layout.isEmpty())
                ? defaultLayoutFromCollectionId(id)
                : layout;

        fromData.ifPresent(item -> {
            if (item.idKey == null || item.idKey.isEmpty())
                throw new RoqFrontMatterConfigException(RoqException.builder("idKey cannot be null or empty")
                        .hint("Your configuration is missing a `site.collection.%s.from-data.id-key` property".formatted(id)));

            if (effectiveLayout == null || effectiveLayout.isEmpty())
                throw new RoqFrontMatterConfigException(RoqException.builder("layout cannot be null or empty")
                        .hint("Your configuration is missing a `site.collection.%s.layout` property".formatted(id)));
        });

        if ((layout == null && effectiveLayout != null) || (layout != null && !layout.equals(effectiveLayout))) {
            layout = effectiveLayout;
        }
    }

    public String idKey() {
        return fromData.map(CollectionFromData::idKey).orElse(null);
    }

    public String dataName() {
        return fromData.map(CollectionFromData::name).orElse(id);
    }

    private static String defaultLayoutFromCollectionId(String collectionId) {
        if (collectionId != null && collectionId.endsWith("s") && collectionId.length() > 1) {
            return collectionId.substring(0, collectionId.length() - 1);
        }
        return collectionId;
    }

    public record CollectionFromData(String idKey, String name) {
    }
}
