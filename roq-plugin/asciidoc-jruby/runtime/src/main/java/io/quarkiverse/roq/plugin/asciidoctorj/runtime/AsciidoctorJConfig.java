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
     * Which directory to use as AsciidoctorJ's {@code base_dir}.
     * <p>
     * In {@link org.asciidoctor.SafeMode#SAFE} mode (the mode Roq renders with), {@code base_dir} is also the
     * jail for every file access performed on the Ruby side, in particular by {@code asciidoctor-diagram}.
     * Paths that escape the jail are silently rewritten back into it, which means diagram sources can only be
     * read from, and diagram images can only be written to, the jail.
     **/
    @WithDefault("page")
    BaseDir baseDir();

    enum BaseDir {
        /**
         * The directory of the page being rendered. Relative paths in {@code plantuml::...[]} and friends are
         * resolved against it, and generated images are written next to the page.
         */
        PAGE,

        /**
         * The Roq site directory, which is the boundary Roq already enforces for {@code include::} directives.
         * Relative paths on the Ruby side are then resolved against the site directory, and {@code imagesoutdir}
         * can point anywhere below it (for instance at {@code public/}, so generated images are published).
         */
        SITE
    }

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

}
