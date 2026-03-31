package io.quarkiverse.roq.plugin.sitemap.runtime;

/**
 * Frontmatter keys for the Roq Sitemap plugin.
 */
public interface RoqSitemapKeys {
    /**
     * Include/exclude from sitemap — e.g. {@code sitemap: false} (default: true)
     * <br>
     * ▸ Scope: page / document
     * <br>
     * ▸ Access: {@code page.data.getBoolean("sitemap")}
     */
    String SITEMAP = "sitemap";
}
