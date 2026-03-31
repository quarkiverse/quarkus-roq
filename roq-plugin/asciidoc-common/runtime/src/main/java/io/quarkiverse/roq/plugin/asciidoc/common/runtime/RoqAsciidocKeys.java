package io.quarkiverse.roq.plugin.asciidoc.common.runtime;

/**
 * Frontmatter keys for the Roq AsciiDoc plugin.
 */
public interface RoqAsciidocKeys {
    /**
     * AsciiDoc attributes — stores parsed AsciiDoc document attributes
     * <br>
     * ▸ Scope: page / document (asciidoc content)
     * <br>
     * ▸ Access: {@code page.data.get("asciidoc-attributes")}
     */
    String ASCIIDOC_ATTRIBUTES = "asciidoc-attributes";
}
