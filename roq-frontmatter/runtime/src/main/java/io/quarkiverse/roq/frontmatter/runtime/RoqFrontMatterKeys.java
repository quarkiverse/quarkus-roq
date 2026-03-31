package io.quarkiverse.roq.frontmatter.runtime;

/**
 * Standard frontmatter YAML keys recognized by Roq FrontMatter.
 * <p>
 * These are the keys that can appear between {@code ---} delimiters
 * in content files ({@code .md}, {@code .html}, {@code .adoc}) and layout templates.
 * <p>
 * Plugins can define their own key interfaces following the same pattern.
 */
public interface RoqFrontMatterKeys {

    // ── Page metadata ───────────────────────────────────────────────────

    /**
     * Page title — e.g. {@code title: "My First Post"}
     * <br>
     * ▸ Scope: site / page / document
     * <br>
     * ▸ Access: {@code site.title()} · {@code page.title()}
     */
    String TITLE = "title";

    /**
     * Page description for SEO — e.g. {@code description: "A guide to Roq"}
     * <br>
     * ▸ Scope: site / page / document
     * <br>
     * ▸ Access: {@code site.description()} · {@code page.description()}
     */
    String DESCRIPTION = "description";

    /**
     * Publish date — e.g. {@code date: 2024-08-29 13:32:20 +0200}
     * <br>
     * ▸ Scope: page / document
     * <br>
     * ▸ Access: {@code page.date()}
     */
    String DATE = "date";

    /**
     * Last modified date — e.g. {@code last-modified-at: 2024-09-15 10:00:00 +0200} (used by sitemap plugin)
     * <br>
     * ▸ Scope: page / document
     * <br>
     * ▸ Access: {@code page.data.getString("last-modified-at")}
     */
    String LAST_MODIFIED_AT = "last-modified-at";

    /**
     * Draft marker — e.g. {@code draft: true} (hidden unless site.draft=true)
     * <br>
     * ▸ Scope: page / document
     * <br>
     * ▸ Access: {@code page.draft()}
     */
    String DRAFT = "draft";

    /**
     * Tags list, can be comma separated string — e.g. {@code tags: [blogging, quarkus]}
     * <br>
     * ▸ Scope: document
     * <br>
     * ▸ Access: {@code page.data.get("tags")}
     */
    String TAGS = "tags";

    /**
     * Author identifier — e.g. {@code author: ia3andy}
     * <br>
     * ▸ Scope: page / document
     * <br>
     * ▸ Access: {@code page.data.getString("author")}
     */
    String AUTHOR = "author";

    /**
     * Custom URL slug — e.g. {@code slug: my-custom-slug} (overrides auto-generated slug from title)
     * <br>
     * ▸ Scope: page / document
     * <br>
     * ▸ Access: {@code page.data.getString("slug")}
     */
    String SLUG = "slug";

    // ── Layout ──────────────────────────────────────────────────────────

    /**
     * Layout template — e.g. {@code layout: main} (resolves local first, theme fallback)
     * <br>
     * ▸ Scope: page / document
     * <br>
     * ▸ Access: {@code page.data.getString("layout")}
     */
    String LAYOUT = "layout";

    /**
     * Explicit theme layout — e.g. {@code theme-layout: main} (targets theme layout directly)
     * <br>
     * ▸ Scope: page / document
     * <br>
     * ▸ Access: {@code page.data.getString("theme-layout")}
     */
    String THEME_LAYOUT = "theme-layout";

    // ── Images ──────────────────────────────────────────────────────────

    /**
     * Page image — e.g. {@code image: photo.jpg} (URL, public/images/ filename, or attached file)
     * <br>
     * ▸ Scope: site / page / document
     * <br>
     * ▸ Access: {@code site.image()} · {@code page.image()}
     */
    String IMAGE = "image";

    /**
     * Alternative image key — e.g. {@code img: photo.jpg}
     * <br>
     * ▸ Scope: site / page / document
     * <br>
     * ▸ Access: {@code site.image()} · {@code page.image()}
     */
    String IMG = "img";

    /**
     * Alternative image key — e.g. {@code picture: photo.jpg}
     * <br>
     * ▸ Scope: site / page / document
     * <br>
     * ▸ Access: {@code site.image()} · {@code page.image()}
     */
    String PICTURE = "picture";

    // ── Routing ─────────────────────────────────────────────────────────

    /**
     * Custom URL pattern — e.g. {@code link: /:collection/:slug/}
     * <br>
     * ▸ Scope: page / document
     * <br>
     * ▸ Access: {@code page.data.getString("link")}
     */
    String LINK = "link";

    /**
     * Pagination config — e.g. {@code paginate: posts} or {@code paginate: {collection: posts, size: 10}}
     * <br>
     * ▸ Scope: page (layout)
     * <br>
     * ▸ Access: {@code page.data.get("paginate")}
     */
    String PAGINATE = "paginate";

    // ── Content rendering ───────────────────────────────────────────────

    /**
     * Escape Qute expressions — e.g. {@code escape: true} (wraps content in escape delimiters)
     * <br>
     * ▸ Scope: page / document
     * <br>
     * ▸ Access: {@code page.data.getBoolean("escape")}
     */
    String ESCAPE = "escape";
}
