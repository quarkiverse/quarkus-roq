package io.quarkiverse.roq.frontmatter.runtime.model;

import java.util.*;
import java.util.stream.Collectors;

import jakarta.enterprise.inject.Vetoed;

import io.quarkus.qute.TemplateData;

@TemplateData
@Vetoed
public class RoqCollection extends ArrayList<DocumentPage> {

    public RoqCollection(List<DocumentPage> documents) {
        super(documents.stream()
                .sorted(Comparator.comparing(DocumentPage::date, Comparator.nullsLast(Comparator.naturalOrder())).reversed())
                .toList());
    }

    public DocumentPage resolveNextPage(DocumentPage page) {
        final int i = this.indexOf(page);
        if (i == -1 || i >= this.size() - 1) {
            return null;
        }
        return this.get(i + 1);
    }

    public DocumentPage resolvePreviousPage(DocumentPage page) {
        final int i = this.indexOf(page);
        if (i <= 0) {
            return null;
        }
        return this.get(i - 1);
    }

    public DocumentPage resolvePrevPage(DocumentPage page) {
        return this.resolvePreviousPage(page);
    }

    public List<DocumentPage> paginated(Paginator paginator) {
        var zeroBasedCurrent = paginator.currentIndex() - 1;
        return this.subList(zeroBasedCurrent * paginator.limit(),
                Math.min(this.size(), (zeroBasedCurrent * paginator.limit()) + paginator.limit()));
    }

    /**
     * Retrieves a list of non-null values from the pages for the specified keys.
     * This method searches through all the pages for each of the provided keys and
     * collects all non-null values associated with the keys.
     *
     * @param keys the keys to search for in the pages' data. Multiple keys can be passed.
     * @return a {@code List<Object>} containing all non-null values found in the pages for the specified keys.
     */
    public List<Object> by(String... keys) {
        return this.stream()
                .flatMap(page -> Arrays.stream(keys)
                        .map(page::data) // Get the data for each key
                        .filter(Objects::nonNull) // Filter out null results
                )
                .collect(Collectors.toList()); // Collect non-null values into a list
    }

    /**
     * Groups the pages by the values found for the specified keys.
     * For each key provided, this method searches through the pages and groups them
     * based on the values associated with that key. The resulting map will contain the
     * found values as keys and the corresponding list of pages that contain those values.
     *
     * @param keys the keys to group pages by. Multiple keys can be passed.
     * @return a {@code Map<Object, List<Page>>} where each key is a unique value found
     *         in the pages' data for the specified keys, and the corresponding value is
     *         a list of pages where the value was found.
     */
    public Map<Object, List<Page>> group(String... keys) {
        Map<Object, List<Page>> resultMap = new LinkedHashMap<>();

        for (Page page : this) {
            for (String key : keys) {
                Object value = page.data(key);
                if (value != null) {
                    resultMap.computeIfAbsent(value, k -> new ArrayList<>()).add(page);
                }
            }
        }

        return resultMap;
    }

}
