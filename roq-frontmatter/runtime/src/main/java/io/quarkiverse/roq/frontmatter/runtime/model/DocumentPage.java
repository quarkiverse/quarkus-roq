package io.quarkiverse.roq.frontmatter.runtime.model;

import jakarta.enterprise.inject.Vetoed;

import io.quarkus.arc.Arc;
import io.quarkus.qute.TemplateData;
import io.vertx.core.json.JsonObject;

/**
 * This represents a document page (in a collection)
 *
 * @param url the url to this page
 * @param collectionId the collection id
 * @param info the page info
 * @param data the FM data of this page
 * @param hidden if hidden, the page is not visible on the given url
 */
@TemplateData
@Vetoed
public record DocumentPage(
        String collectionId,
        RoqUrl url,
        PageInfo info,
        JsonObject data,
        boolean hidden) implements Page {

    public RoqCollection collection() {
        return Arc.container().beanInstanceSupplier(Site.class).get().get().collections().resolveCollection(this);
    }

    /**
     * Resolve the next document page in the collection or null if none
     */
    public DocumentPage nextPage() {
        return collection().nextPage(this);
    }

    /**
     * Resolve the previous document page in the collection or null if none
     */
    public DocumentPage previousPage() {
        return collection().previousPage(this);
    }

    /**
     * Resolve the previous document page in the collection or null if none
     */
    public DocumentPage prevPage() {
        return collection().previousPage(this);
    }

}
