package io.quarkiverse.roq.plugin.lunr.runtime;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;

import org.jboss.logging.Logger;

import io.quarkiverse.roq.frontmatter.runtime.model.NormalPage;
import io.quarkiverse.roq.frontmatter.runtime.model.Site;

/**
 * The search index files of a site, found by their {@link RoqLunrKeys#SEARCH_INDEX_NAME}.
 */
final class RoqLunrIndexFiles {

    private static final Logger LOG = Logger.getLogger(RoqLunrIndexFiles.class);
    private static final String DEFAULT_INDEX_PATH = "/search-index.json";

    // search triggers usually sit in a layout and render on every page: log each problem once
    private static final Set<String> WARNED = ConcurrentHashMap.newKeySet();

    private RoqLunrIndexFiles() {
    }

    /**
     * @return index name to URL path, {@code default} first, then the other names sorted
     */
    static Map<String, String> urls(Site site) {
        final Map<String, String> named = new TreeMap<>();
        final Map<String, String> sources = new HashMap<>();
        for (NormalPage page : site.pages()) {
            final Object value = page.data(RoqLunrKeys.SEARCH_INDEX_NAME);
            if (value == null) {
                continue;
            }
            final String name = value.toString();
            if (!RoqLunrIndexNames.isValidName(name)) {
                warnOnce("Invalid search index name '%s' in '%s': use letters, digits, '-' or '_' ('%s' is reserved)",
                        name, page.sourcePath(), RoqLunrIndexNames.ALL);
                continue;
            }
            final String existing = sources.putIfAbsent(name, page.sourcePath());
            if (existing != null) {
                warnOnce("Duplicate search index name '%s' in '%s' and '%s': using '%s'",
                        name, existing, page.sourcePath(), existing);
                continue;
            }
            named.put(name, page.url().path());
        }
        final Map<String, String> urls = new LinkedHashMap<>();
        urls.put(RoqLunrIndexNames.DEFAULT,
                named.getOrDefault(RoqLunrIndexNames.DEFAULT, site.url().fromRoot(DEFAULT_INDEX_PATH).path()));
        named.forEach(urls::putIfAbsent);
        return urls;
    }

    /**
     * Resolve the names requested by a search trigger, logging unknown names.
     */
    static List<String> resolve(Site site, Object requested) {
        final RoqLunrIndexNames.Resolution resolution = RoqLunrIndexNames.resolve(requested, urls(site).keySet());
        for (String name : resolution.unknown()) {
            warnOnce("Unknown search index '%s' requested by a search trigger: no index file declares it with '%s'",
                    name, RoqLunrKeys.SEARCH_INDEX_NAME);
        }
        return resolution.names();
    }

    private static void warnOnce(String format, Object... params) {
        if (WARNED.add(format.formatted(params))) {
            LOG.warnf(format, params);
        }
    }
}
