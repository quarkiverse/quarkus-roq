package io.quarkiverse.roq.plugin.ogimage.runtime;

/**
 * Frontmatter and page data keys used by the OG Image plugin.
 */
public interface OgImageKeys {

    /**
     * Open Graph image width in pixels — e.g. {@code og-image-width: 1200}
     */
    String WIDTH = "og-image-width";

    /**
     * Open Graph image height in pixels — e.g. {@code og-image-height: 630}
     */
    String HEIGHT = "og-image-height";

    /**
     * Relative site path to the generated OG PNG (e.g. {@code /og/posters/start-here.png}).
     * Used by {@code seoImage.html} instead of {@code image} to avoid static file resolution.
     */
    String IMAGE_PATH = "og-image";

    /**
     * Alt text for Twitter/social previews — e.g. {@code og-image-alt: "My Site — Page title"}
     */
    String ALT = "og-image-alt";
}
