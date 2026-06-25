package io.quarkiverse.roq.plugin.ogimage.runtime.model;

/**
 * Metadata for a single generated OG image, registered at build time and rendered at runtime.
 */
public record OgImageTarget(
        String pngPath,
        String relativePath,
        String title,
        String description,
        String siteName,
        String kicker,
        String eyebrow,
        String imageAlt,
        int width,
        int height) {
}
