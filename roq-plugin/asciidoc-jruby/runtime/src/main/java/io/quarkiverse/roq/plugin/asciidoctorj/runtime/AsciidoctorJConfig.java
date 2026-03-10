package io.quarkiverse.roq.plugin.asciidoctorj.runtime;

import java.util.List;
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
     * File extensions that the custom include processor handles, in addition to {@code .adoc} and {@code .asciidoc}
     * which are always included.
     * <p>
     * This controls which {@code include::} directives are resolved by Roq's custom include processor
     * (which supports classpath resources and cross-directory resolution) versus the default AsciidoctorJ handler.
     * Co-located includes (same directory) typically work with the default handler; this processor is needed
     * for cross-directory includes or classpath resources.
     * <p>
     * <b>Note:</b> Tag extraction ({@code tag::name[]}/{@code end::name[]}) currently only recognizes
     * {@code //}-style comment markers (Java, Kotlin, JS, etc.). Files using other comment styles
     * ({@code #} for YAML/Python, {@code <!--} for XML) should use line ranges instead of tags,
     * or be excluded from this list so the default AsciidoctorJ handler processes them.
     */
    @WithDefault(".java,.kt,.js,.ts,.groovy,.scala")
    List<String> includeExtensions();

}
