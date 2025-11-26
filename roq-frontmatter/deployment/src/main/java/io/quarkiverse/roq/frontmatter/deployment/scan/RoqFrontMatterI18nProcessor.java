package io.quarkiverse.roq.frontmatter.deployment.scan;

import io.quarkiverse.roq.frontmatter.deployment.data.RoqFrontMatterDataModificationBuildItem;
import io.quarkiverse.roq.frontmatter.deployment.data.RoqFrontMatterDataProcessor;
import io.quarkiverse.roq.frontmatter.runtime.config.RoqSiteConfig;
import io.quarkus.deployment.annotations.BuildStep;

public class RoqFrontMatterI18nProcessor {

    private static final String LOCALE_KEY = "locale";

    @BuildStep
    public RoqFrontMatterScanModificationBuildItem processMultilingualPage(RoqSiteConfig config) {
        return new RoqFrontMatterScanModificationBuildItem(data -> {
            if (data.fm().getString(LOCALE_KEY) != null &&
                    !config.defaultLanguage().equals(data.fm().getString(LOCALE_KEY)) &&
                    RoqFrontMatterRawTemplateBuildItem.TemplateType.DOCUMENT_PAGE == data.type()) {
                var fm = data.fm().put(RoqFrontMatterDataProcessor.LINK_KEY,
                        "/%s/%s/%s".formatted(data.collection().id(), ":slug", data.fm().getString(LOCALE_KEY)));
                return new RoqFrontMatterScanModificationBuildItem.SourceScanData(data.path(), data.relativePath(), null, RoqFrontMatterRawTemplateBuildItem.TemplateType.NORMAL_PAGE,fm);
            }
            return data;
        });
    }
}
