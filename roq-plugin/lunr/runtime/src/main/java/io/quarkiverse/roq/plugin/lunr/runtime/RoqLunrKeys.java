package io.quarkiverse.roq.plugin.lunr.runtime;

/**
 * Frontmatter keys for the Roq Lunr search plugin.
 */
public interface RoqLunrKeys {
    /**
     * Include/exclude from search index — e.g. {@code search: false} (default: true)
     * <br>
     * ▸ Scope: page / document
     * <br>
     * ▸ Access: {@code page.data.getBoolean("search")}
     */
    String SEARCH = "search";

    /**
     * Search result boost — e.g. {@code search-boost: 1.5} (default: 1, recommended range: 0-2)
     * <br>
     * ▸ Scope: page / document
     * <br>
     * ▸ Access: {@code page.data.getDouble("search-boost")}
     */
    String SEARCH_BOOST = "search-boost";

    /**
     * Search indexes the page appears in — e.g. {@code search-indexes: [posts, default]} (default: {@code default})
     * <br>
     * ▸ Scope: page / document (usually set on a layout)
     * <br>
     * ▸ Access: {@code page.data("search-indexes")}
     */
    String SEARCH_INDEXES = "search-indexes";

    /**
     * Name of the search index rendered by an index file — e.g. {@code search-index-name: recipes} (default:
     * {@code default})
     * <br>
     * ▸ Scope: page (index file such as {@code content/recipes-search-index.json})
     * <br>
     * ▸ Access: {@code page.data("search-index-name")}
     */
    String SEARCH_INDEX_NAME = "search-index-name";
}
