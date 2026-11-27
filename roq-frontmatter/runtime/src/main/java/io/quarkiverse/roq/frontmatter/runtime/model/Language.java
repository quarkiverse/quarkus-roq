package io.quarkiverse.roq.frontmatter.runtime.model;

import java.util.Arrays;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * The built-in language catalogue: ISO 639-1 two-letter codes with a flag emoji.
 * <p>
 * A content file {@code index.<code>.<html-ext>} is a translation when {@code <code>} is in this catalogue and is not
 * {@code site.default-locale}.
 */
public enum Language {

    EN("en", "🇬🇧"),
    FR("fr", "🇫🇷"),
    DE("de", "🇩🇪"),
    ES("es", "🇪🇸"),
    IT("it", "🇮🇹"),
    PT("pt", "🇵🇹"),
    NL("nl", "🇳🇱"),
    SV("sv", "🇸🇪"),
    DA("da", "🇩🇰"),
    NO("no", "🇳🇴"),
    FI("fi", "🇫🇮"),
    PL("pl", "🇵🇱"),
    CS("cs", "🇨🇿"),
    RU("ru", "🇷🇺"),
    UK("uk", "🇺🇦"),
    EL("el", "🇬🇷"),
    TR("tr", "🇹🇷"),
    AR("ar", "🇸🇦"),
    HE("he", "🇮🇱"),
    HI("hi", "🇮🇳"),
    JA("ja", "🇯🇵"),
    KO("ko", "🇰🇷"),
    ZH("zh", "🇨🇳");

    private static final Set<String> CODES = Arrays.stream(values()).map(Language::code)
            .collect(Collectors.toUnmodifiableSet());

    private final String code;
    private final String flag;

    Language(String code, String flag) {
        this.code = code;
        this.flag = flag;
    }

    /**
     * @return the ISO 639-1 code, e.g. {@code fr}
     */
    public String code() {
        return code;
    }

    /**
     * @return the flag emoji, e.g. 🇫🇷
     */
    public String flag() {
        return flag;
    }

    /**
     * @param code the language code, matched exactly (no trimming, no case folding)
     * @return the language, or empty when the code is null or not in the catalogue
     */
    public static Optional<Language> fromCode(String code) {
        if (code == null) {
            return Optional.empty();
        }
        for (Language language : values()) {
            if (language.code.equals(code)) {
                return Optional.of(language);
            }
        }
        return Optional.empty();
    }

    /**
     * @return all the catalogue codes
     */
    public static Set<String> codes() {
        return CODES;
    }
}
