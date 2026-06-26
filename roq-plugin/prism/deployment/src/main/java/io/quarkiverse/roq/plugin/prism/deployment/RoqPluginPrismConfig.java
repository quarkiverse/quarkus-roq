package io.quarkiverse.roq.plugin.prism.deployment;

import java.util.List;

import io.quarkus.runtime.annotations.ConfigPhase;
import io.quarkus.runtime.annotations.ConfigRoot;
import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;

@ConfigMapping(prefix = "quarkus.roq.prism")
@ConfigRoot(phase = ConfigPhase.BUILD_TIME)
public interface RoqPluginPrismConfig {

    /**
     * Prism languages to bundle, given by their canonical name in
     * <a href="https://github.com/PrismJS/prism/blob/master/components.json">prism's
     * components.json</a> (e.g. {@code java}, {@code bash}, {@code yaml},
     * {@code markup-templating}).
     * <p>
     * See <a href="https://prismjs.com/#supported-languages">prismjs.com &mdash; Supported
     * languages</a> for the full list.
     * <p>
     * Required &mdash; an empty list fails the build. Transitive {@code require} prerequisites are
     * added automatically (e.g. {@code java} pulls in {@code clike}).
     */
    List<String> languages();

    /**
     * Prism theme to bundle. One of the themes shipped in the {@code prismjs} package:
     * {@code default}, {@code coy}, {@code dark}, {@code funky}, {@code okaidia},
     * {@code solarizedlight}, {@code tomorrow}, {@code twilight}.
     * <p>
     * To preview a theme, pick one under <a href="https://prismjs.com/index.html#theme">Themes</a>
     * on prismjs.com and scroll down to the
     * <a href="https://prismjs.com/index.html#examples">Examples</a> section.
     * <p>
     * Defaults to {@code default}.
     */
    @WithDefault("default")
    String theme();
}
