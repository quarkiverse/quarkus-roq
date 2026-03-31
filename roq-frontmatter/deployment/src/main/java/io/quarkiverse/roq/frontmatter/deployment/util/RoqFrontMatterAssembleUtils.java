package io.quarkiverse.roq.frontmatter.deployment.util;

import static io.quarkiverse.roq.frontmatter.deployment.util.RoqFrontMatterLayoutUtils.*;
import static io.quarkiverse.roq.frontmatter.deployment.util.RoqFrontMatterTemplateUtils.*;
import static io.quarkiverse.roq.frontmatter.runtime.RoqFrontMatterKeys.ESCAPE;
import static io.quarkiverse.roq.frontmatter.runtime.RoqFrontMatterKeys.LAYOUT;
import static io.quarkiverse.roq.frontmatter.runtime.RoqFrontMatterKeys.THEME_LAYOUT;

import java.util.List;

import io.quarkiverse.roq.frontmatter.deployment.items.data.RoqFrontMatterDataModificationBuildItem;
import io.quarkiverse.roq.frontmatter.deployment.items.scan.FrontMatterTemplateMetadata;
import io.quarkiverse.roq.frontmatter.deployment.util.RoqFrontMatterTemplateUtils.TransformedContent;
import io.quarkiverse.roq.frontmatter.runtime.config.ConfiguredCollection;
import io.quarkiverse.roq.frontmatter.runtime.config.RoqSiteConfig;
import io.quarkiverse.roq.frontmatter.runtime.model.TemplateSource;
import io.vertx.core.json.JsonObject;

public final class RoqFrontMatterAssembleUtils {

    private RoqFrontMatterAssembleUtils() {
    }

    // ── Template processing ──────────────────────────────────────────────

    /**
     * Plain data record holding processed template information.
     * Callers add page-specific or layout-specific fields when constructing build items.
     */
    public record ProcessedTemplate(
            TemplateSource templateSource,
            String layout,
            JsonObject data,
            String generatedTemplate,
            String generatedContentTemplate) {

        public String id() {
            return templateSource.id();
        }
    }

    /**
     * Process a scanned template using its pre-collected metadata: resolve layout,
     * apply data modifications, apply content transforms, and create TemplateSource.
     */
    public static ProcessedTemplate processTemplate(
            FrontMatterTemplateMetadata metadata, boolean isPage, boolean isThemeLayout,
            ConfiguredCollection collection, boolean isIndex, boolean isSiteIndex,
            RoqSiteConfig config,
            List<RoqFrontMatterDataModificationBuildItem> dataModifications) {

        JsonObject data = metadata.parsedHeaders().data().copy();
        String content = metadata.parsedHeaders().content();

        String defaultLayout = isPage
                ? resolveDefaultLayout(metadata.isPartial(), collection, config)
                : null;

        String layoutId = normalizedLayout(config.theme(),
                data.getString(LAYOUT),
                data.getString(THEME_LAYOUT),
                defaultLayout);

        for (RoqFrontMatterDataModificationBuildItem modification : dataModifications) {
            data = modification.modifier()
                    .modify(new RoqFrontMatterDataModificationBuildItem.SourceData(
                            metadata.filePath(), metadata.referencePath(), collection, isPage, data));
        }

        boolean escaped = Boolean.parseBoolean(data.getString(ESCAPE, "false"));
        TransformedContent transformed = applyContentTransforms(content, escaped, metadata.markup(), layoutId);

        // Legacy-theme backward compat (c): remap layouts/{theme-name}/foo → layouts/foo
        String templateId = metadata.templateId();
        String outputPath = metadata.outputPath();
        if (!isPage && !isThemeLayout) {
            templateId = remapLegacyThemeLayoutOverride(config.theme(), templateId);
            outputPath = removeLegacyThemeOverridePath(config.theme(), outputPath);
        }

        TemplateSource source = TemplateSource.create(
                templateId,
                getMarkup(metadata.isHtml(), metadata.markup()),
                metadata.sourceFile(),
                metadata.referencePath(),
                outputPath,
                !isPage,
                metadata.isHtml(),
                isIndex,
                isSiteIndex);

        return new ProcessedTemplate(source, layoutId, data,
                transformed.generatedTemplate(),
                transformed.contentWithMarkup());
    }
}
