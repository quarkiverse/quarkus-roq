package io.quarkiverse.roq.frontmatter.runtime.model;

import jakarta.enterprise.inject.Vetoed;

import io.quarkus.qute.TemplateData;

/**
 * A wrapper for Page lookup results that provides null-safe access.
 * This allows templates to safely chain method calls even when a page is not found.
 */
@TemplateData
@Vetoed
public final class PageResult {

    private final Page page;

    private PageResult(Page page) {
        this.page = page;
    }

    /**
     * Create a PageResult from a nullable Page.
     *
     * @param page the page, may be null
     * @return a PageResult wrapping the page
     */
    public static PageResult of(Page page) {
        return new PageResult(page);
    }

    /**
     * Returns the page if present, otherwise returns an empty page that safely handles method calls.
     * This allows templates to use expressions like {@code site.page('invalid').orEmpty().url}
     * without throwing exceptions.
     *
     * @return the page if present, otherwise an empty page
     */
    public Page orEmpty() {
        return page != null ? page : EmptyPage.INSTANCE;
    }

    /**
     * Returns the wrapped page, which may be null.
     *
     * @return the page or null
     */
    public Page get() {
        return page;
    }

    /**
     * Checks if a page is present.
     *
     * @return true if a page is present, false otherwise
     */
    public boolean isPresent() {
        return page != null;
    }

    /**
     * Checks if no page is present.
     *
     * @return true if no page is present, false otherwise
     */
    public boolean isEmpty() {
        return page == null;
    }
}