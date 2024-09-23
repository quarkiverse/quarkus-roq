package io.quarkiverse.roq.frontmatter.runtime;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class RoqCollection extends ArrayList<Page> {

    public RoqCollection(List<Page> pages) {
        super(pages.stream()
                .sorted(Comparator.comparing(Page::date, Comparator.nullsLast(Comparator.naturalOrder())).reversed())
                .toList());
    }

    public Page resolveNextPage(Page page) {
        if (page.next() == null) {
            return null;
        }
        return this.get(page.next());
    }

    public Page resolvePreviousPage(Page page) {
        if (page.previous() == null) {
            return null;
        }
        return this.get(page.previous());
    }

    public Page resolvePrevPage(Page page) {
        return this.resolvePreviousPage(page);
    }

    public List<Page> paginated(Paginator paginator) {
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