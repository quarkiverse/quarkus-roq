package io.quarkiverse.roq.generator.runtime;

import io.smallrye.config.WithDefault;

public interface PathReplaceConfig {

    String DEFAULT_ALLOWED_REGEX = "[^a-zA-Z0-9_\\\\/.\\-]";
    String DEFAULT_REPLACE_WITH = "-";

    /**
     * Enable path character replace
     */
    @WithDefault("false")
    boolean enabled();

    /**
     * The regex of allowed characters for file names (other characters will be replaced), for example: `[^a-zA-Z0-9_\\\\/.\\-]`
     * <p>
     * By default, all characters are unchanged.
     */
    @WithDefault(DEFAULT_ALLOWED_REGEX)
    String allowedRegex();

    /**
     * The character to use to replace characters which doesn't match the 'allowed-regex'
     */
    @WithDefault(DEFAULT_REPLACE_WITH)
    String replaceWith();

    static PathReplaceConfig create(String allowedRegex, String replaceWith) {
        return new PathReplaceConfigImpl(true, allowedRegex, replaceWith);
    }

    static PathReplaceConfig replaceConfig() {
        return new PathReplaceConfigImpl(true, DEFAULT_ALLOWED_REGEX, DEFAULT_REPLACE_WITH);
    }

    static PathReplaceConfig createDefault() {
        return new PathReplaceConfigImpl(false, DEFAULT_ALLOWED_REGEX, DEFAULT_REPLACE_WITH);
    }

    record PathReplaceConfigImpl(boolean enabled, String allowedRegex, String replaceWith) implements PathReplaceConfig {
    }
}
