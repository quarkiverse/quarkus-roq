package io.quarkiverse.roq.frontmatter.runtime.config;

public record ConfiguredCollection(
        String id,
        boolean derived,
        boolean hidden,
        boolean future,
        String layout,
        boolean generate,
        String titleAttributeName) {

    /*
     * public ConfiguredCollection {
     * if (generate && (titleAttributeName == null || titleAttributeName.isEmpty())) {
     * throw new RoqFrontMatterConfigException(RoqException.builder("titleAttributeName cannot be null or empty")
     * .hint("Your configuration is missing a `site.collection.%s.title-attribute-name` property".formatted(id)));
     * }
     * }
     */
}
