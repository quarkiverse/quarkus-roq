package io.quarkiverse.roq.plugin.asciidoc.common.deployment;

import io.quarkus.runtime.annotations.ConfigPhase;
import io.quarkus.runtime.annotations.ConfigRoot;
import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;

@ConfigMapping(prefix = "quarkus.asciidoc")
@ConfigRoot(phase = ConfigPhase.BUILD_TIME)
public interface AsciidocCommonConfig {

    /**
     * Controls whether all AsciiDoc templates should be parsed by Qute.
     * <p>
     * When set to {@code true}, Qute will parse AsciiDoc files.
     * Files with the {@code :qute:} attribute in their header will override this config.
     *
     * <p>
     * By default, Qute parsing is disabled for AsciiDoc templates.
     */
    @WithDefault("false")
    boolean qute();

}
