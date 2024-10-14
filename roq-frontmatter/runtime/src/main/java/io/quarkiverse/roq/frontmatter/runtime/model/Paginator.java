package io.quarkiverse.roq.frontmatter.runtime.model;

import jakarta.enterprise.inject.Vetoed;

import io.quarkus.qute.TemplateData;

/**
 * A paginator allows to achieve collection pagination.
 * The same page will be called with different values in the paginator allowing to generate new pages.
 *
 * @param collection the paginated collection
 * @param collectionSize the collection size (count of documents)
 * @param limit the limit of document per page
 * @param total the total amount of pages
 * @param currentIndex the current document page index 1-based
 * @param previousIndex the previous page index 1-based
 * @param previous the url to the previous document page
 * @param nextIndex the next document page index 1-based
 * @param next the url to the next document page
 */
@TemplateData
@Vetoed
public record Paginator(
        String collection,
        int collectionSize,
        int limit,
        int total,
        int currentIndex,
        Integer previousIndex,
        RoqUrl previous,
        Integer nextIndex,
        RoqUrl next) {

    public Integer prevIndex() {
        return previousIndex();
    }

    public RoqUrl prev() {
        return previous();
    }

    public boolean isFirst() {
        return currentIndex == 1;
    }

    public boolean isSecond() {
        return currentIndex == 2;
    }
}
