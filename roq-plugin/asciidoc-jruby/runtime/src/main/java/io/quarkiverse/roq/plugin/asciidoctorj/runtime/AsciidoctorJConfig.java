package io.quarkiverse.roq.plugin.asciidoctorj.runtime;

import java.util.Map;

import io.quarkus.runtime.annotations.ConfigPhase;
import io.quarkus.runtime.annotations.ConfigRoot;
import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;

@ConfigMapping(prefix = "quarkus.asciidoc")
@ConfigRoot(phase = ConfigPhase.BUILD_AND_RUN_TIME_FIXED)
public interface AsciidoctorJConfig {

    /**
     * Defines the AsciidoctorJ attributes to be applied during rendering.
     * <p>
     * Default values:
     * <ul>
     * <li><code>relfileprefix=../</code></li>
     * <li><code>relfilesuffix=/</code></li>
     * <li><code>noheader=true</code></li>
     * <li><code>showtitle=true</code></li>
     * </ul>
     **/
    Map<String, String> attributes();

    /**
     * Controls whether all AsciiDoc templates should be parsed by Qute.
     * <p>
     * When set to {@code true}, Qute will parse AsciiDoc files,
     * except those that have the {@code :page-qute: false} attribute in their header.
     *
     * <p>
     * By default, Qute parsing is disabled for AsciiDoc templates.
     */
    @WithDefault("false")
    boolean qute();

}
