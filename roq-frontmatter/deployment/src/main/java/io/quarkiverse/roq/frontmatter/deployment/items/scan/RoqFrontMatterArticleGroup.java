package io.quarkiverse.roq.frontmatter.deployment.items.scan;

import java.nio.file.Path;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * A group of article variants living in the same folder: one default variant and its translations.
 *
 * @param folder the folder containing the variants
 * @param defaultVariant the default language variant (never null)
 * @param translations the other variants keyed by language code, in the order the scan encountered them
 *        (not catalogue order)
 */
public record RoqFrontMatterArticleGroup(Path folder,
        RoqFrontMatterScannedContentBuildItem defaultVariant,
        Map<String, RoqFrontMatterScannedContentBuildItem> translations) {

    public RoqFrontMatterArticleGroup {
        Objects.requireNonNull(defaultVariant, "defaultVariant must not be null");
        translations = Collections.unmodifiableMap(new LinkedHashMap<>(translations));
    }
}
