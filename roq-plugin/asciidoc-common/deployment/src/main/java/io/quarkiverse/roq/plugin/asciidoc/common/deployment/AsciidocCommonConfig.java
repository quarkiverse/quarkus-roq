package io.quarkiverse.roq.plugin.asciidoc.common.deployment;

import java.util.Map;

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

    /**
     * The AsciiDoc attributes applied when a page is rendered, and when a page is converted to Markdown at build time
     * (the Markdown twin plugin). The same properties configure the AsciidoctorJ plugin, see its {@code attributes}.
     * <p>
     * For a boolean attribute (a flag that is set or unset, such as {@code sectanchors}), use the value {@code "true"},
     * which is converted to an empty value before it reaches the parser.
     */
    Map<String, String> attributes();

    /**
     * The safe mode that bounds what a build-time conversion to Markdown may include: {@code UNSAFE} allows any
     * file, {@code SAFE} and {@code SERVER} the files inside the site directory, {@code SECURE} none. The
     * AsciidoctorJ plugin also renders its pages with this property, see its {@code safe-mode}.
     */
    @WithDefault("SAFE")
    String safeMode();

}
