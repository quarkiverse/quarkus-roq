package io.quarkiverse.roq.plugin.markdowntwin.runtime;

/**
 * Frontmatter keys for the Roq Markdown Twin plugin.
 */
public interface RoqMarkdownTwinKeys {
    /**
     * Publish/suppress the Markdown twin of a page — e.g. {@code mdtwin: false} (default: true, or false when the page
     * sets {@code no-index-all: true})
     * <br>
     * ▸ Scope: page / document
     * <br>
     * ▸ Access: {@code page.data.getBoolean("mdtwin")}
     */
    String MDTWIN = "mdtwin";

    /**
     * The URL of the page's Markdown twin, set by the plugin on each page that got a twin — e.g.
     * {@code https://example.com/docs/basics/index.md}
     * <br>
     * ▸ Scope: page / document
     * <br>
     * ▸ Access: {@code page.data.markdownTwin}
     */
    String MARKDOWN_TWIN = "markdownTwin";
}
