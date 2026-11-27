package io.quarkiverse.roq.frontmatter.runtime.model;

import jakarta.enterprise.inject.Vetoed;

import io.quarkus.qute.TemplateData;

/**
 * Another language variant of the same article.
 *
 * @param lang the language code of the variant (e.g. {@code en})
 * @param flag the flag emoji of the language (e.g. 🇬🇧), empty when the language is not in the catalogue
 * @param url the url of the variant
 */
@TemplateData
@Vetoed
public record Translation(String lang, String flag, RoqUrl url) {
}
