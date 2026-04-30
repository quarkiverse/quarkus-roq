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
            "io.quarkiverse.roq.exception.RoqException",
            "io.quarkiverse.roq.frontmatter.runtime.exception.RoqStaticFileException",
            "io.quarkiverse.roq.frontmatter.runtime.config.RoqFrontMatterConfigException",
            "io.quarkiverse.roq.frontmatter.deployment.exception.RoqLayoutNotFoundException",
            "io.quarkiverse.roq.frontmatter.deployment.exception.RoqFrontMatterReadingException",
            "io.quarkiverse.roq.frontmatter.deployment.exception.RoqPathConflictException",
            "io.quarkiverse.roq.frontmatter.deployment.exception.RoqTemplateLinkException",
            "io.quarkiverse.roq.frontmatter.deployment.exception.RoqSiteIndexNotFoundException",
            "io.quarkiverse.roq.frontmatter.deployment.exception.RoqSiteScanningException",
            "io.quarkiverse.roq.frontmatter.deployment.exception.RoqPluginException",
            "io.quarkiverse.roq.frontmatter.deployment.exception.RoqThemeConfigurationException",
            "io.quarkiverse.roq.data.deployment.exception.DataConflictException",
            "io.quarkiverse.roq.data.deployment.exception.DataConversionException",
            "io.quarkiverse.roq.data.deployment.exception.DataListBindingException",
            "io.quarkiverse.roq.data.deployment.exception.DataMappingMismatchException",
            "io.quarkiverse.roq.data.deployment.exception.DataMappingRequiredFileException",
            "io.quarkiverse.roq.data.deployment.exception.DataReadingException",
            "io.quarkiverse.roq.data.deployment.exception.DataScanningException",
    };

    @Override
    public void setupHotDeployment(HotReplacementContext context) {
        for (String className : ROQ_EXCEPTION_CLASSES) {
            ErrorPageGenerators.register(className, RoqErrorPage::generatePage);
        }
    }
}
