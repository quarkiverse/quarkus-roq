package io.quarkiverse.roq.frontmatter.runtime.model;

import java.util.Objects;
import java.util.StringJoiner;

import jakarta.enterprise.inject.Vetoed;

import io.quarkus.arc.Arc;
import io.quarkus.qute.TemplateData;
import io.vertx.core.json.JsonObject;

/**
 * This represents a document page (in a collection)
 */
@TemplateData
@Vetoed
public final class DocumentPage extends Page {

    private final String collectionId;
    private final boolean hidden;

    /**
     * @param url the url to this page
     * @param collectionId the collection id
     * @param source the page source
     * @param data the FM data of this page
     * @param hidden if hidden, the page is not visible on the given url
     */
    public DocumentPage(
            String collectionId,
            RoqUrl url,
            PageSource source,
            JsonObject data,
            boolean hidden) {
        super(url, source, data);
        this.collectionId = collectionId;
        this.hidden = hidden;
    }

    /**
     * @return the collection associated with this page
     */
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
     * Resolve the next document page in the collection or null if none
     */
    public DocumentPage next() {
        return nextPage();
    }

    /**
     * Resolve the previous document page in the collection or null if none
     */
    public DocumentPage previous() {
        return previousPage();
    }

    /**
     * Resolve the previous document page in the collection or null if none
     */
    public DocumentPage prev() {
        return previousPage();
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

    public String collectionId() {
        return collectionId;
    }

    public boolean hidden() {
        return hidden;
    }

    @Override
    public boolean equals(Object o) {
        if (o == null || getClass() != o.getClass())
            return false;
        if (!super.equals(o))
            return false;
        DocumentPage that = (DocumentPage) o;
        return hidden == that.hidden && Objects.equals(collectionId, that.collectionId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), collectionId, hidden);
    }

    @Override
    public String toString() {
        return new StringJoiner(", ", DocumentPage.class.getSimpleName() + "[", "]")
                .add("collectionId='" + collectionId + "'")
                .add("hidden=" + hidden)
                .toString();
    }
}
