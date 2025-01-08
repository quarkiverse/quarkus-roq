package io.quarkiverse.roq.frontmatter.runtime.model;

import java.util.List;
import java.util.Map;

import jakarta.enterprise.inject.Vetoed;

import io.quarkus.qute.TemplateData;

/**
 * This represents all collections by id
 *
 * @param collections the map of collections by id
 */
@TemplateData
@Vetoed
public record RoqCollections(Map<String, RoqCollection> collections) {
    public RoqCollection get(String name) {
        return collections.get(name);
    }

    public List<RoqCollection> list() {
        return List.copyOf(collections.values());
    }

    /**
     * Resolve the collection for this document page
     */
    public RoqCollection resolveCollection(DocumentPage page) {
        if (page.collectionId() == null) {
            return null;
        }
        return this.get(page.collectionId());
    }

}
