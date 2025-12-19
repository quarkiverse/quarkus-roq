package io.quarkiverse.roq.frontmatter.runtime;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Stream;

import io.quarkiverse.roq.frontmatter.runtime.model.Page;
import io.quarkiverse.roq.frontmatter.runtime.model.RoqUrl;
import io.quarkus.qute.TemplateExtension;

/**
 * Template extension for the multilingual plugin.
 * Provides methods to access multilingual information from Qute templates.
 */
@TemplateExtension
public class RoqI18NTemplateExtension {

    // Frontmatter property names
    private static final String LANG_PROPERTY = "lang";
    private static final String TRANSLATIONS_PROPERTY = "translations";

    /**
     * Language flag mapping for runtime access.
     * This is a subset of the mapping from the deployment module for common languages.
     */
    private static final Map<String, String> LANGUAGE_FLAG_MAP = Map.ofEntries(
            // European languages
            Map.entry("fr", "🇫🇷"), // French -> France
            Map.entry("en", "🇺🇸"), // English -> United States
            Map.entry("es", "🇪🇸"), // Spanish -> Spain
            Map.entry("de", "🇩🇪"), // German -> Germany
            Map.entry("it", "🇮🇹"), // Italian -> Italy
            Map.entry("pt", "🇵🇹"), // Portuguese -> Portugal
            Map.entry("nl", "🇳🇱"), // Dutch -> Netherlands
            Map.entry("sv", "🇸🇪"), // Swedish -> Sweden
            Map.entry("no", "🇳🇴"), // Norwegian -> Norway
            Map.entry("da", "🇩🇰"), // Danish -> Denmark
            Map.entry("fi", "🇫🇮"), // Finnish -> Finland
            Map.entry("cs", "🇨🇿"), // Czech -> Czech Republic
            Map.entry("sk", "🇸🇰"), // Slovak -> Slovakia
            Map.entry("hu", "🇭🇺"), // Hungarian -> Hungary
            Map.entry("ro", "🇷🇴"), // Romanian -> Romania
            Map.entry("bg", "🇧🇬"), // Bulgarian -> Bulgaria
            Map.entry("hr", "🇭🇷"), // Croatian -> Croatia
            Map.entry("sl", "🇸🇮"), // Slovenian -> Slovenia
            Map.entry("et", "🇪🇪"), // Estonian -> Estonia
            Map.entry("lv", "🇱🇻"), // Latvian -> Latvia
            Map.entry("lt", "🇱🇹"), // Lithuanian -> Lithuania
            Map.entry("el", "🇬🇷"), // Greek -> Greece

            // Asian languages
            Map.entry("ja", "🇯🇵"), // Japanese -> Japan
            Map.entry("ko", "🇰🇷"), // Korean -> South Korea
            Map.entry("zh", "🇨🇳"), // Chinese -> China
            Map.entry("hi", "🇮🇳"), // Hindi -> India
            Map.entry("th", "🇹🇭"), // Thai -> Thailand
            Map.entry("vi", "🇻🇳"), // Vietnamese -> Vietnam
            Map.entry("id", "🇮🇩"), // Indonesian -> Indonesia
            Map.entry("ms", "🇲🇾"), // Malay -> Malaysia

            // Middle Eastern and African languages
            Map.entry("ar", "🇸🇦"), // Arabic -> Saudi Arabia
            Map.entry("he", "🇮🇱"), // Hebrew -> Israel
            Map.entry("tr", "🇹🇷"), // Turkish -> Turkey
            Map.entry("fa", "🇮🇷"), // Persian -> Iran

            // Other languages
            Map.entry("ru", "🇷🇺"), // Russian -> Russia
            Map.entry("uk", "🇺🇦"), // Ukrainian -> Ukraine
            Map.entry("be", "🇧🇾"), // Belarusian -> Belarus

            // Alternative English variants
            Map.entry("en-gb", "🇬🇧"), // British English -> United Kingdom
            Map.entry("en-au", "🇦🇺"), // Australian English -> Australia
            Map.entry("en-ca", "🇨🇦"), // Canadian English -> Canada

            // Alternative Spanish variants
            Map.entry("es-mx", "🇲🇽"), // Mexican Spanish -> Mexico
            Map.entry("es-ar", "🇦🇷"), // Argentinian Spanish -> Argentina

            // Alternative Portuguese variants
            Map.entry("pt-br", "🇧🇷"), // Brazilian Portuguese -> Brazil

            // Alternative French variants
            Map.entry("fr-ca", "🇨🇦"), // Canadian French -> Canada

            // Alternative Chinese variants
            Map.entry("zh-cn", "🇨🇳"), // Simplified Chinese -> China
            Map.entry("zh-tw", "🇹🇼"), // Traditional Chinese -> Taiwan
            Map.entry("zh-hk", "🇭🇰") // Hong Kong Chinese -> Hong Kong
    );

    /**
     * Default flag emoji used when no mapping is found for a language code.
     */
    private static final String DEFAULT_FLAG = "🌐";

    /**
     * Checks if the given page has multiple language translations available.
     *
     * @param page the page to check
     * @return true if the page has translations, false otherwise
     */

    public static boolean hasTranslations(Page page) {
        return getMultilingualData(page).findAny().isPresent();
    }

    /**
     * Returns the list of available languages for the given page.
     * Each language object contains the language code, flag emoji, and URL.
     * This method dynamically searches for related translations at runtime.
     *
     * @param page the page to get languages for
     * @return a list of language objects, or an empty list if no multilingual data is available
     */
    public static List<Language> languages(Page page) {
        // Dynamically find all pages with the same translation ID
        var pages = getMultilingualData(page);
        return pages
                .map(doc -> {
                    String languageCode = getLanguageCode(doc);
                    String flag = languageFlag(languageCode);
                    return new Language(languageCode, flag, doc.url());
                })
                .toList();
    }

    /**
     * Returns the current language code for the given page.
     *
     * @param page the page to get the current language for
     * @return the current language code, or null if no multilingual data is available
     */
    public static String currentLanguage(Page page) {
        return page.data().getString(LANG_PROPERTY);
    }

    /**
     * Helper method to get the language code from a page.
     */
    private static String getLanguageCode(Page page) {
        Object langValue = page.data().getValue(LANG_PROPERTY);
        if (langValue != null) {
            return langValue.toString().toLowerCase().trim();
        }
        return "en"; // Default language
    }

    /**
     * Returns the flag emoji for the given language code.
     *
     * @param languageCode the ISO 639-1 language code (e.g., "fr", "en", "es")
     * @return the Unicode flag emoji corresponding to the language, or a default globe emoji if not found
     */
    private static String languageFlag(String languageCode) {
        if (languageCode == null || languageCode.trim().isEmpty()) {
            return DEFAULT_FLAG;
        }

        // Normalize to lowercase for case-insensitive lookup
        String normalizedCode = languageCode.toLowerCase().trim();
        return LANGUAGE_FLAG_MAP.getOrDefault(normalizedCode, DEFAULT_FLAG);
    }

    /**
     * Helper method to extract multilingual data from a page.
     *
     * @param page the page to extract data from
     * @return the multilingual data object, or null if not available
     */
    private static Stream<Page> getMultilingualData(Page page) {
        String translationId = page.data().getString(TRANSLATIONS_PROPERTY, null);
        if (translationId == null) {
            return Stream.of();
        }
        return page.site().allPages()
                .stream()
                .filter(doc -> translationId.equals(doc.data(TRANSLATIONS_PROPERTY)))
                .filter(doc -> !doc.equals(page))
                .filter(distinctByKey(Page::id));
    }

    public static <T> Predicate<T> distinctByKey(Function<? super T, ?> keyExtractor) {
        Set<Object> seen = ConcurrentHashMap.newKeySet();
        return t -> seen.add(keyExtractor.apply(t));
    }

    public record Language(String code, String flag, RoqUrl url) {
    }
}
