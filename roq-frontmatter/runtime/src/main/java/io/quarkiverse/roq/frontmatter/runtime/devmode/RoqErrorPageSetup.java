package io.quarkiverse.roq.frontmatter.runtime.devmode;

import io.quarkus.dev.ErrorPageGenerators;
import io.quarkus.dev.spi.HotReplacementContext;
import io.quarkus.dev.spi.HotReplacementSetup;

/**
 * Registers Roq's custom error page generator for all RoqException subclasses
 * in dev mode. Discovered via META-INF/services/io.quarkus.dev.spi.HotReplacementSetup.
 */
public class RoqErrorPageSetup implements HotReplacementSetup {

    private static final String[] ROQ_EXCEPTION_CLASSES = {
            "io.quarkiverse.roq.frontmatter.runtime.exception.RoqException",
            "io.quarkiverse.roq.frontmatter.runtime.exception.RoqStaticFileException",
            "io.quarkiverse.roq.frontmatter.deployment.exception.RoqLayoutNotFoundException",
            "io.quarkiverse.roq.frontmatter.deployment.exception.RoqFrontMatterReadingException",
            "io.quarkiverse.roq.frontmatter.deployment.exception.RoqPathConflictException",
            "io.quarkiverse.roq.frontmatter.deployment.exception.RoqTemplateLinkException",
            "io.quarkiverse.roq.frontmatter.deployment.exception.RoqSiteIndexNotFoundException",
            "io.quarkiverse.roq.frontmatter.deployment.exception.RoqSiteScanningException",
            "io.quarkiverse.roq.frontmatter.deployment.exception.RoqPluginException",
            "io.quarkiverse.roq.frontmatter.deployment.exception.RoqThemeConfigurationException",
    };

    @Override
    public void setupHotDeployment(HotReplacementContext context) {
        for (String className : ROQ_EXCEPTION_CLASSES) {
            ErrorPageGenerators.register(className, RoqErrorPage::generatePage);
        }
    }
}
