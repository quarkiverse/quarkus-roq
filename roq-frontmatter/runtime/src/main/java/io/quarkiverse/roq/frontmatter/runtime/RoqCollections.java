package io.quarkiverse.roq.frontmatter.runtime;

import java.util.Map;

public record RoqCollections(Map<String, RoqCollection> collections) {
    public RoqCollection get(String name) {
        return collections.get(name);
    }

    public Page resolveNextPage(Page page) {
        final RoqCollection collection = resolveCollection(page);
        if (collection == null)
            return null;
        return collection.resolveNextPage(page);
    }

    public Page resolvePreviousPage(Page page) {
        final RoqCollection collection = resolveCollection(page);
        if (collection == null)
            return null;
        return collection.resolvePreviousPage(page);
    }

    public RoqCollection resolveCollection(Page page) {
        if (page.collection() == null) {
            return null;
        }
        final RoqCollection collection = this.get(page.collection());
        if (collection == null) {
            return null;
        }
        return collection;
    }

    public Page resolvePrevPage(Page page) {
        return this.resolvePreviousPage(page);
    }

}