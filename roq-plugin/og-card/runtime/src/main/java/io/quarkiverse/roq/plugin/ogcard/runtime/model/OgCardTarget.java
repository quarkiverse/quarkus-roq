package io.quarkiverse.roq.plugin.ogcard.runtime.model;

/**
 * Metadata for a single generated OG card, resolved at build time.
 */
public record OgCardTarget(
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
