package io.quarkiverse.roq.frontmatter.runtime;

/**
 * Standard frontmatter YAML keys recognized by Roq FrontMatter.
 * <p>
 * These are the keys that can appear between {@code ---} delimiters
 * in content files ({@code .md}, {@code .html}, {@code .adoc}) and layout templates.
 */
public final class RoqFrontMatterKeys {

    private RoqFrontMatterKeys() {
    }

    // ── Page metadata ───────────────────────────────────────────────────

    /** Page title — e.g. {@code title: "My First Post"} */
    public static final String TITLE = "title";

    /** Page description for SEO — e.g. {@code description: "A guide to Roq"} */
    public static final String DESCRIPTION = "description";

    /** Publish date — e.g. {@code date: 2024-08-29 13:32:20 +0200} */
    public static final String DATE = "date";

    /** Draft marker — e.g. {@code draft: true} (hidden unless site.draft=true) */
    public static final String DRAFT = "draft";

    /** Tags list, can be comma separated string — e.g. {@code tags: [blogging, quarkus]} */
    public static final String TAGS = "tags";

    /** Author identifier — e.g. {@code author: ia3andy} */
    public static final String AUTHOR = "author";

    // ── Layout ──────────────────────────────────────────────────────────

    /** Layout template — e.g. {@code layout: main} (resolves local first, theme fallback) */
    public static final String LAYOUT = "layout";

    /** Explicit theme layout — e.g. {@code theme-layout: main} (targets theme layout directly) */
    public static final String THEME_LAYOUT = "theme-layout";

    // ── Images ──────────────────────────────────────────────────────────

    /** Page image — e.g. {@code image: photo.jpg} (URL, public/images/ filename, or attached file) */
    public static final String IMAGE = "image";

    /** Alternative image key — e.g. {@code img: photo.jpg} */
    public static final String IMG = "img";

    /** Alternative image key — e.g. {@code picture: photo.jpg} */
    public static final String PICTURE = "picture";

    // ── Routing ─────────────────────────────────────────────────────────

    /** Custom URL pattern — e.g. {@code link: /:collection/:slug/} */
    public static final String LINK = "link";

    /** Pagination config — e.g. {@code paginate: posts} or {@code paginate: {collection: posts, size: 10}} */
    public static final String PAGINATE = "paginate";

    // ── Content rendering ───────────────────────────────────────────────

    /** Escape Qute expressions — e.g. {@code escape: true} (wraps content in escape delimiters) */
    public static final String ESCAPE = "escape";
}
