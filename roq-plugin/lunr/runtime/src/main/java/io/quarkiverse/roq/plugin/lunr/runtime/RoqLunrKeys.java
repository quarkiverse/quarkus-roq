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
     * Search result boost — e.g. {@code search-boost: 2} (higher = more prominent)
     * <br>
     * ▸ Scope: page / document
     * <br>
     * ▸ Access: {@code page.data.getInteger("search-boost")}
     */
    String SEARCH_BOOST = "search-boost";
}
