package io.quarkiverse.roq.plugin.l10n.asciidoc;

import java.util.Optional;

import io.quarkus.runtime.annotations.ConfigPhase;
import io.quarkus.runtime.annotations.ConfigRoot;
import io.smallrye.config.ConfigMapping;

@ConfigMapping(prefix = "quarkus.roq.l10n-adoc")
@ConfigRoot(phase = ConfigPhase.BUILD_AND_RUN_TIME_FIXED)
public interface L10nAdocConfig {

    /**
     * Base directory containing PO translation files.
     * When set, AsciiDoc content is translated at the AST level using PO files
     * resolved relative to this directory.
     */
    Optional<String> poBaseDir();

    /**
     * When true (default), translatable text is extracted to PO files during the build.
     * Set to false to only apply existing translations without updating PO files.
     */
    Optional<Boolean> extractOnBuild();

    /**
     * Target language code for generated PO file headers (e.g. "ja-JP").
     * Used when creating new PO files during extract-on-build.
     */
    Optional<String> targetLanguage();
}
