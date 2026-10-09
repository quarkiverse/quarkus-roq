package io.quarkiverse.roq.plugin.markdowntwin.runtime;

/**
 * Frontmatter keys for the Roq Markdown Twin plugin.
 */
public interface RoqMarkdownTwinKeys {
    /**
     * Publish/suppress the Markdown twin of a page — e.g. {@code mdtwin: false} (default: true)
     * <br>
     * ▸ Scope: page / document
     * <br>
     * ▸ Access: {@code page.data.getBoolean("mdtwin")}
     */
    String MDTWIN = "mdtwin";
}
