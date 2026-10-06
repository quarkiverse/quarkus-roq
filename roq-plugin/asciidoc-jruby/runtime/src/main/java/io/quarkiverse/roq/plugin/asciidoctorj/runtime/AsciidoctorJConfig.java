package io.quarkiverse.roq.plugin.asciidoctorj.runtime;

import java.util.Map;

import org.asciidoctor.SafeMode;

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
     * jail for every file access performed on the Ruby side. Paths that escape the jail are silently rewritten
     * back into it, which limits where files can be read from or written to.
     **/
    @WithDefault("page")
    BaseDir baseDir();

    enum BaseDir {
        /**
         * The directory of the page being rendered. File operations on the Ruby side are restricted to this directory.
         */
        PAGE,

        /**
         * The Roq site directory, which is the boundary Roq already enforces for {@code include::} directives.
         * This allows file operations anywhere below the site directory (for instance, writing generated content
         * to {@code public/}).
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

    /**
     * Safe mode for AsciidoctorJ processing.
     * <p>
     * Controls filesystem access and security restrictions:
     * <ul>
     * <li><code>UNSAFE</code> - Disables security features, allows access to any file (useful for static site generation)</li>
     * <li><code>SAFE</code> - Prevents access to files outside the parent directory of the source file</li>
     * <li><code>SERVER</code> - Disallows setting attributes that affect rendering</li>
     * <li><code>SECURE</code> - Disallows reading files from the filesystem</li>
     * </ul>
     * <p>
     * Default: <code>SAFE</code>
     * <p>
     * Note: For static site generation where content is trusted, <code>UNSAFE</code> is often more appropriate
     * as it allows includes from outside the document directory.
     *
     * See https://docs.asciidoctor.org/asciidoctor/latest/safe-modes/
     **/
    @WithDefault("SAFE")
    SafeMode safeMode();

}
