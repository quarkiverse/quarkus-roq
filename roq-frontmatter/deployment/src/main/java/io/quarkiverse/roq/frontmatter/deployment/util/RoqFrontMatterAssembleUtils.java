package io.quarkiverse.roq.frontmatter.deployment.util;

import static io.quarkiverse.roq.frontmatter.deployment.util.RoqFrontMatterLayoutUtils.*;
import static io.quarkiverse.roq.frontmatter.deployment.util.RoqFrontMatterTemplateUtils.*;
import static io.quarkiverse.roq.frontmatter.runtime.RoqFrontMatterKeys.ESCAPE;
import static io.quarkiverse.roq.frontmatter.runtime.RoqFrontMatterKeys.LAYOUT;
import static io.quarkiverse.roq.frontmatter.runtime.RoqFrontMatterKeys.THEME_LAYOUT;
import static io.quarkiverse.roq.frontmatter.runtime.RoqTemplates.THEME_LAYOUTS_DIR;

import java.util.List;
import java.util.Optional;

import io.quarkiverse.roq.frontmatter.deployment.items.data.RoqFrontMatterDataModificationBuildItem;
import io.quarkiverse.roq.frontmatter.deployment.items.scan.FrontMatterTemplateMetadata;
import io.quarkiverse.roq.frontmatter.deployment.items.scan.RoqFrontMatterAvailableLayoutsBuildItem;
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
            RoqSiteConfig config, RoqFrontMatterAvailableLayoutsBuildItem availableLayouts,
            List<RoqFrontMatterDataModificationBuildItem> dataModifications) {

        JsonObject data = metadata.parsedHeaders().data().copy();
        String content = metadata.parsedHeaders().content();

        LayoutRef layoutRef = resolveLayoutRef(data, isPage, metadata.isPartial(), collection, config);
        Optional<String> sourceTheme = extractSourceTheme(isThemeLayout, metadata.templateId());
        String layoutId = availableLayouts.resolveLayoutId(
                config.theme(), sourceTheme, layoutRef.value(), layoutRef.scopeToTheme());

        for (RoqFrontMatterDataModificationBuildItem modification : dataModifications) {
            data = modification.modifier()
                    .modify(new RoqFrontMatterDataModificationBuildItem.SourceData(
                            metadata.filePath(), metadata.referencePath(), collection, isPage, data));
        }

        boolean escaped = Boolean.parseBoolean(data.getString(ESCAPE, "false"));
        TransformedContent transformed = applyContentTransforms(content, escaped, metadata.markup(), layoutId);

        TemplateSource source = TemplateSource.create(
                metadata.templateId(),
                getMarkup(metadata.isHtml(), metadata.markup()),
                metadata.sourceFile(),
                metadata.referencePath(),
                metadata.outputPath(),
                !isPage,
                metadata.isHtml(),
                isIndex,
                isSiteIndex);

        return new ProcessedTemplate(source, layoutId, data,
                transformed.generatedTemplate(),
                transformed.contentWithMarkup());
    }

    // ── Layout helpers ──────────────────────────────────────────────────

    public record LayoutRef(String value, boolean scopeToTheme) {
    }

    /**
     * Pick the layout value from front matter: {@code theme-layout:} takes precedence,
     * then {@code layout:}, then the default for pages.
     */
    public static LayoutRef resolveLayoutRef(JsonObject data, boolean isPage, boolean isPartial,
            ConfiguredCollection collection, RoqSiteConfig config) {
        String themeLayout = data.getString(THEME_LAYOUT);
        if (themeLayout != null && !themeLayout.isBlank()) {
            return new LayoutRef(themeLayout, true);
        }
        String layout = data.getString(LAYOUT);
        if (layout != null) {
            return new LayoutRef(layout, false);
        }
        if (isPage) {
            return new LayoutRef(resolveDefaultLayout(isPartial, collection, config), false);
        }
        return new LayoutRef(null, false);
    }

    /**
     * Extract the theme name that a layout belongs to from its template ID.
     * Returns empty for content pages and user layouts.
     */
    public static Optional<String> extractSourceTheme(boolean isThemeLayout, String templateId) {
        if (!isThemeLayout) {
            return Optional.empty();
        }
        String afterDir = templateId.substring(THEME_LAYOUTS_DIR.length());
        int slash = afterDir.indexOf('/');
        if (slash >= 0) {
            return Optional.of(afterDir.substring(0, slash));
        }
        return Optional.empty();
    }
}
