package io.quarkiverse.roq.frontmatter.runtime.model;

import io.quarkus.qute.TemplateExtension;

/**
 * Qute template extensions for PageResult to enable null-safe page access in templates.
 */
@TemplateExtension
public class PageResultTemplateExtensions {

    /**
     * Returns the page if present, otherwise returns an empty page that safely handles method calls.
     * This allows templates to use expressions like {@code site.page('invalid').orEmpty().url}
     * without throwing exceptions.
     *
     * @param result the PageResult
     * @return the page if present, otherwise an empty page
     */
    public static Page orEmpty(PageResult result) {
        return result.orEmpty();
    }

    /**
     * Returns the wrapped page, which may be null.
     *
     * @param result the PageResult
     * @return the page or null
     */
    public static Page get(PageResult result) {
        return result.get();
    }

    /**
     * Checks if a page is present.
     *
     * @param result the PageResult
     * @return true if a page is present, false otherwise
     */
    public static boolean isPresent(PageResult result) {
        return result.isPresent();
    }

    /**
     * Checks if no page is present.
     *
     * @param result the PageResult
     * @return true if no page is present, false otherwise
     */
    public static boolean isEmpty(PageResult result) {
        return result.isEmpty();
    }
}