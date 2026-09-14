package io.quarkiverse.roq.plugin.asciidoctorj.runtime;

import java.util.Map;

import io.quarkus.runtime.annotations.ConfigPhase;
import io.quarkus.runtime.annotations.ConfigRoot;
import io.smallrye.config.ConfigMapping;

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
     * <p>
     * For boolean AsciiDoc attributes (flags that are either set or unset, such as
     * {@code sectanchors} or {@code sectnums}), use the value {@code "true"}. SmallRye
     * Config does not support empty-string map values, so {@code "true"} is converted to
     * an empty string before passing to AsciidoctorJ.
     * <p>
     * Examples:
     * <pre>
     * quarkus.asciidoc.attributes.sectanchors=true
     * quarkus.asciidoc.attributes.sectnums=true
     * quarkus.asciidoc.attributes.icons=font
     * </pre>
     **/
    Map<String, String> attributes();

}
