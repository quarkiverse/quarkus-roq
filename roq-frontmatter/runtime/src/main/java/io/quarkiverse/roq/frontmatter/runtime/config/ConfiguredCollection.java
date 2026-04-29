package io.quarkiverse.roq.frontmatter.runtime.config;

import java.util.Optional;

import io.quarkiverse.roq.frontmatter.runtime.exception.RoqException;

public record ConfiguredCollection(
        String id,
        boolean derived,
        boolean hidden,
        boolean future,
        String layout,
        Optional<CollectionFromData> fromData) {

    public ConfiguredCollection {
        fromData.ifPresent(item -> {
            if (item.idKey == null || item.idKey.isEmpty())
                throw new RoqFrontMatterConfigException(RoqException.builder("idKey cannot be null or empty")
                        .hint("Your configuration is missing a `site.collection.%s.from-data.id-key` property".formatted(id)));

            if (layout == null || layout.isEmpty())
                throw new RoqFrontMatterConfigException(RoqException.builder("layout cannot be null or empty")
                        .hint("Your configuration is missing a `site.collection.%s.layout` property".formatted(id)));
        });
    }

    public String idKey() {
        return fromData.map(CollectionFromData::idKey).orElse(null);
    }

    public record CollectionFromData(String idKey) {
    }
}
