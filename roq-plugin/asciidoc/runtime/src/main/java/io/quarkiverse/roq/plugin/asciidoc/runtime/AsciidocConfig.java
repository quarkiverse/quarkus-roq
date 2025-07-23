package io.quarkiverse.roq.plugin.asciidoc.runtime;

import io.quarkus.runtime.annotations.ConfigPhase;
import io.quarkus.runtime.annotations.ConfigRoot;
import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;

@ConfigMapping(prefix = "quarkus.asciidoc")
@ConfigRoot(phase = ConfigPhase.BUILD_AND_RUN_TIME_FIXED)
public interface AsciidocConfig {

    /**
     * Controls whether all AsciiDoc templates should be parsed by Qute.
     * <p>
     * When set to {@code true}, Qute will not parse AsciiDoc files,
     * except those that have the {@code :page-escape: false} attribute in their header.
     *
     * <p>
     * By default, Qute parsing is disabled for AsciiDoc templates.
     */
    @WithDefault("true")
    boolean escape();

}
