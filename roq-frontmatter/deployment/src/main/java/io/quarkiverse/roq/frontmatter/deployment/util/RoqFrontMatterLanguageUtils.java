package io.quarkiverse.roq.frontmatter.deployment.util;

import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import io.quarkiverse.roq.frontmatter.runtime.model.Language;

/**
 * Parsing and validation of language suffixes on index files, e.g. {@code index.fr.adoc}.
 */
public final class RoqFrontMatterLanguageUtils {

    private static final String INDEX_PREFIX = "index.";

    private RoqFrontMatterLanguageUtils() {
    }

    /**
     * @param baseName the file name without the language suffix (e.g. {@code index.md})
     * @param lang the language, or {@code null} for the default variant
     */
    public record LanguageSuffix(String baseName, String lang) {
    }

    /**
     * Parses a file name. Language codes are matched exactly (no trimming, no case folding).
     *
     * @param languages the translation languages (see {@link #translationLanguages(String)})
     * @return empty when the file is not an index file or the suffix is not a translation language
     */
    public static Optional<LanguageSuffix> parse(String fileName, Set<String> languages) {
        if (languages == null || fileName == null || !fileName.startsWith(INDEX_PREFIX)) {
            return Optional.empty();
        }
        String rest = fileName.substring(INDEX_PREFIX.length());
        int dot = rest.indexOf('.');
        if (dot > 0 && dot < rest.length() - 1) {
            String candidate = rest.substring(0, dot);
            String afterLang = rest.substring(dot + 1);
            // A translation must produce HTML, like the default variant (index.en.json is not a translation)
            if (languages.contains(candidate) && isHtmlExtension(afterLang)) {
                return Optional.of(new LanguageSuffix(INDEX_PREFIX + afterLang, candidate));
            }
        }
        if (isHtmlExtension(rest)) {
            return Optional.of(new LanguageSuffix(fileName, null));
        }
        return Optional.empty();
    }

    /**
     * The {@code .qute.} marker is templating only and is never part of the language or index detection.
     *
     * @param ext the extension, e.g. {@code md} or {@code adoc}
     */
    private static boolean isHtmlExtension(String ext) {
        return RoqFrontMatterConstants.HTML_OUTPUT_EXTENSIONS.contains(ext);
    }

    /**
     * The single index-file predicate shared by the scan (Step 1) and the grouping of variants (Step 2).
     * <p>
     * {@code index.<html-ext>} and translations {@code index.<lang>.<html-ext>}. The {@code .qute.} marker is not an
     * index marker.
     */
    public static boolean isIndexFile(String fileName, Set<String> languages) {
        return RoqFrontMatterConstants.INDEX_FILES.contains(fileName) || parse(fileName, languages).isPresent();
    }

    /**
     * The languages a translation can use: the catalogue ({@link Language#codes()}) without the default locale.
     */
    public static Set<String> translationLanguages(String defaultLocale) {
        return Language.codes().stream().filter(code -> !code.equals(defaultLocale))
                .collect(Collectors.toUnmodifiableSet());
    }

    /**
     * @return the plain index name ({@code index.<html-ext>}) when the file is {@code index.<defaultLocale>.<html-ext>},
     *         else empty
     */
    public static Optional<String> defaultLocaleSuffix(String fileName, String defaultLocale) {
        if (fileName == null || defaultLocale == null) {
            return Optional.empty();
        }
        String prefix = INDEX_PREFIX + defaultLocale + ".";
        if (!fileName.startsWith(prefix) || !isHtmlExtension(fileName.substring(prefix.length()))) {
            return Optional.empty();
        }
        return Optional.of(INDEX_PREFIX + fileName.substring(prefix.length()));
    }
}
