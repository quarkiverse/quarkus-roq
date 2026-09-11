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
     * Use the special value {@code BLANK} to set an attribute to the empty string,
     * since SmallRye Config does not support empty map values.
     * For example, {@code quarkus.asciidoc.attributes.idprefix=BLANK} sets {@code idprefix}
     * to {@code ""}.
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

}
