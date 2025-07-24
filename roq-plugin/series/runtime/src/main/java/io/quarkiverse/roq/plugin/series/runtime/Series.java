package io.quarkiverse.roq.plugin.series.runtime;

import static io.quarkiverse.roq.frontmatter.runtime.model.RoqCollection.BY_DATE;

import java.util.List;
import java.util.Map;
import java.util.Objects;

import io.quarkiverse.roq.frontmatter.runtime.model.DocumentPage;
import io.quarkiverse.roq.frontmatter.runtime.model.Page;
import io.quarkiverse.roq.frontmatter.runtime.model.Site;
import io.quarkiverse.roq.frontmatter.runtime.utils.Sites;
import io.quarkus.arc.impl.LazyValue;

public record Series(Map<String, SeriesEntry> series) {

    public static final String FM_SERIE = "series";

    public static final class SeriesEntry {
        private final String title;
        private final List<String> documentIds;
        private final LazyValue<List<DocumentPage>> documents;

        public SeriesEntry(String title, List<String> documentIds) {
            this.title = title;
            this.documentIds = documentIds;
            this.documents = new LazyValue<>(() -> {
                Site site = Sites.getSite();
                return documentIds.stream().map(site::document)
                        .sorted(BY_DATE.reversed())
                        .toList();
            });
        }

        public List<DocumentPage> documents() {
            return documents.get();
        }

        public String title() {
            return title;
        }

        public List<String> documentIds() {
            return documentIds;
        }

        @Override
        public boolean equals(Object obj) {
            if (obj == this)
                return true;
            if (obj == null || obj.getClass() != this.getClass())
                return false;
            var that = (SeriesEntry) obj;
            return Objects.equals(this.title, that.title) &&
                    Objects.equals(this.documentIds, that.documentIds);
        }

        @Override
        public int hashCode() {
            return Objects.hash(title, documentIds);
        }

        @Override
        public String toString() {
            return "series[" +
                    "title=" + title + ", " +
                    "documentIds=" + documentIds + ']';
        }

    }

    public SeriesEntry get(Page page) {
        if (!page.data().containsKey(FM_SERIE)) {
            return null;
        }
        return series.get(page.data().getString(FM_SERIE));
    }
}
