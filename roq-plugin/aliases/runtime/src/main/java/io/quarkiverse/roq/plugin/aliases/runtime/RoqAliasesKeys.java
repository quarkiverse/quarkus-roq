package io.quarkiverse.roq.plugin.aliases.runtime;

/**
 * Frontmatter keys for the Roq Aliases plugin.
 */
public interface RoqAliasesKeys {
    /**
     * URL redirects — e.g. {@code redirect_from: [/old-url, /another-old-url]}
     * <br>
     * ▸ Scope: page / document
     * <br>
     * ▸ Access: {@code page.data.get("redirect_from")}
     */
    String REDIRECT_FROM = "redirect_from";

    /**
     * URL redirects (hyphenated) — e.g. {@code redirect-from: [/old-url]}
     * <br>
     * ▸ Scope: page / document
     * <br>
     * ▸ Access: {@code page.data.get("redirect-from")}
     */
    String REDIRECT_FROM_HYPHEN = "redirect-from";

    /**
     * URL aliases — e.g. {@code aliases: [/old-url]}
     * <br>
     * ▸ Scope: page / document
     * <br>
     * ▸ Access: {@code page.data.get("aliases")}
     */
    String ALIASES = "aliases";
}
