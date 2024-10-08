package io.quarkiverse.roq.frontmatter.runtime.model;

import java.util.Map;

import jakarta.enterprise.inject.Vetoed;

import io.quarkus.qute.TemplateData;

@TemplateData
@Vetoed
public record RoqCollections(Map<String, RoqCollection> collections) {
    public RoqCollection get(String name) {
        return collections.get(name);
    }

    public DocumentPage resolveNextPage(DocumentPage page) {
        final RoqCollection collection = resolveCollection(page);
        if (collection == null)
            return null;
        return collection.resolveNextPage(page);
    }

    public DocumentPage resolvePreviousPage(DocumentPage page) {
        final RoqCollection collection = resolveCollection(page);
        if (collection == null)
            return null;
        return collection.resolvePreviousPage(page);
    }

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
