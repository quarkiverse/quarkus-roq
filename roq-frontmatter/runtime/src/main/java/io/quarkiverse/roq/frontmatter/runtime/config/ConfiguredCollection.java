package io.quarkiverse.roq.frontmatter.runtime.config;

public record ConfiguredCollection(
        String id,
        boolean derived,
        boolean hidden,
        boolean future,
        String layout) {
}
