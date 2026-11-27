package io.quarkiverse.roq.frontmatter.deployment.items.assemble;

import java.util.Objects;

/**
 * Membership of a raw page in a multilingual article group (see
 * {@link io.quarkiverse.roq.frontmatter.deployment.items.scan.RoqFrontMatterArticleGroup}).
 * <p>
 * Only set when the article folder has at least one translation.
 *
 * @param group the group key (the article folder), shared by all variants of the article
 * @param lang the language of this variant
 * @param isDefault true for the default-language variant, false for a translation
 */
public record RoqFrontMatterLanguageVariant(String group, String lang, boolean isDefault) {

    public RoqFrontMatterLanguageVariant {
        Objects.requireNonNull(group, "group must not be null");
        Objects.requireNonNull(lang, "lang must not be null");
    }
}
