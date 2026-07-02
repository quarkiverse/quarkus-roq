package io.quarkiverse.roq.plugin.ogcard.runtime;

/**
 * Frontmatter and page data keys used by the OG Card plugin.
 */
public interface OgCardKeys {

    /**
     * Open Graph image width in pixels — e.g. {@code og-image-width: 1200}
     */
    String WIDTH = "og-image-width";

    /**
     * Open Graph image height in pixels — e.g. {@code og-image-height: 630}
     */
    String HEIGHT = "og-image-height";

    /**
     * Relative site path to the generated OG PNG (e.g. {@code /og/posts/welcome-to-roq.png}).
     * Used by {@code seoImage.html} instead of {@code image} to avoid static file resolution.
     */
    String IMAGE_PATH = "og-image";

    /**
     * Alt text for Twitter/social previews — e.g. {@code og-image-alt: "My Site — Page title"}
     */
    String ALT = "og-image-alt";
}
