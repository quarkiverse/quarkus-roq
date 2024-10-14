package io.quarkiverse.roq.frontmatter.runtime.model;

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

    /**
     * Resolve the next document in the given document collection
     */
    public DocumentPage resolveNextPage(DocumentPage page) {
        final RoqCollection collection = resolveCollection(page);
        if (collection == null)
            return null;
        return collection.resolveNextPage(page);
    }

    /**
     * Resolve the previous document in the given document collection
     */
    public DocumentPage resolvePreviousPage(DocumentPage page) {
        final RoqCollection collection = resolveCollection(page);
        if (collection == null)
            return null;
        return collection.resolvePreviousPage(page);
    }

    /**
     * Resolve the collection for this document page
     */
    public RoqCollection resolveCollection(DocumentPage page) {
        if (page.collection() == null) {
            return null;
        }
        return this.get(page.collection());
    }

    public DocumentPage resolvePrevPage(DocumentPage page) {
        return this.resolvePreviousPage(page);
    }

}
