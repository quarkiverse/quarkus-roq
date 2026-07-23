package io.quarkiverse.roq.plugin.l10n.asciidoc;

import java.util.Optional;

import io.quarkus.runtime.annotations.ConfigPhase;
import io.quarkus.runtime.annotations.ConfigRoot;
import io.smallrye.config.ConfigMapping;

@ConfigMapping(prefix = "l10n")
@ConfigRoot(phase = ConfigPhase.BUILD_AND_RUN_TIME_FIXED)
public interface L10nAdocConfig {

    /**
     * Base directory containing PO translation files.
     * When set, AsciiDoc content is translated at the AST level using PO files
     * resolved relative to this directory.
     */
    Optional<String> poBaseDir();
}
