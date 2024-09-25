package io.quarkiverse.roq.frontmatter.runtime;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class RoqCollection extends ArrayList<DocumentPage> {

    public RoqCollection(List<DocumentPage> documents) {
        super(documents.stream()
                .sorted(Comparator.comparing(DocumentPage::date, Comparator.nullsLast(Comparator.naturalOrder())).reversed())
                .toList());
    }

    public DocumentPage resolveNextPage(DocumentPage page) {
        if (page.next() == null) {
            return null;
        }
        return this.get(page.next());
    }

    public DocumentPage resolvePreviousPage(DocumentPage page) {
        if (page.previous() == null) {
            return null;
        }
        return this.get(page.previous());
    }

    public DocumentPage resolvePrevPage(DocumentPage page) {
        return this.resolvePreviousPage(page);
    }

    public List<DocumentPage> paginated(Paginator paginator) {
        var zeroBasedCurrent = paginator.currentIndex - 1;
        return this.subList(zeroBasedCurrent * paginator.limit, Math.min(this.size(), zeroBasedCurrent + paginator.limit));
    }

    public record Paginator(
            String collection,
            int collectionSize,
            int limit,
            int total,
            int currentIndex,
            Integer previousIndex,
            PageUrl previous,
            Integer nextIndex,
            PageUrl next) {

        public Integer prevIndex() {
            return previousIndex();
        }

        public PageUrl prev() {
            return previous();
        }

        public boolean isFirst() {
            return currentIndex == 1;
        }

        public boolean isSecond() {
            return currentIndex == 2;
        }
    }

}