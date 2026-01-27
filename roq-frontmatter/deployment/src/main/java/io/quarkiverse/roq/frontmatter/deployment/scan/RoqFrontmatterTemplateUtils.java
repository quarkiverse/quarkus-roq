package io.quarkiverse.roq.frontmatter.deployment.scan;

import static io.quarkiverse.roq.frontmatter.deployment.scan.RoqFrontMatterQuteMarkupBuildItem.QuteMarkupSection.find;
import static io.quarkiverse.roq.frontmatter.deployment.scan.RoqFrontMatterScanProcessor.LAYOUTS_DIR_PREFIX;
import static io.quarkiverse.roq.frontmatter.runtime.RoqTemplates.ROQ_GENERATED_QUTE_PREFIX;
import static io.quarkiverse.roq.util.PathUtils.removeExtension;

import java.util.Map;
import java.util.Optional;

import io.quarkiverse.roq.frontmatter.deployment.exception.RoqThemeConfigurationException;
import io.quarkiverse.roq.util.PathUtils;
import io.vertx.core.http.impl.MimeMapping;

public final class RoqFrontmatterTemplateUtils {

    private RoqFrontmatterTemplateUtils() {
    }

    public static RoqFrontMatterQuteMarkupBuildItem.WrapperFilter getIncludeFilter(String layout) {
        if (layout == null) {
            return RoqFrontMatterQuteMarkupBuildItem.WrapperFilter.EMPTY;
        }
        String prefix = "{#include %s%s}\n".formatted(ROQ_GENERATED_QUTE_PREFIX, layout);
        return new RoqFrontMatterQuteMarkupBuildItem.WrapperFilter(prefix, "\n{/include}");
    }

    public static RoqFrontMatterQuteMarkupBuildItem.WrapperFilter getMarkupFilter(
            Map<String, RoqFrontMatterQuteMarkupBuildItem.WrapperFilter> markups, String fileName) {
        return find(markups, fileName,
                RoqFrontMatterQuteMarkupBuildItem.WrapperFilter.EMPTY);
    }

    public static String normalizedLayout(Optional<String> theme, String layout, String defaultLayout) {
        String normalized = layout;

        if (normalized == null) {
            // no layout specified => use default
            normalized = defaultLayout;
            if (normalized == null || normalized.isBlank() || "none".equalsIgnoreCase(normalized)) {
                // no default layout or none
                return null;
            }
            if (normalized.contains(":theme/") && theme.isEmpty()) {
                // the default use the theme layout but there is no theme, we just remove the theme prefix
                normalized = normalized.replace(":theme/", "");
            }
        }

        if (normalized.contains(":theme")) {
            if (theme.isPresent()) {
                normalized = normalized.replace(":theme", theme.get());
            } else {
                // We don't allow to specify the theme in the template directly when there is no theme
                // I suppose we could allow it in the future
                throw new RoqThemeConfigurationException(
                        "No theme detected! Using ':theme' in 'layout: %s' is only possible with a theme installed as a dependency."
                                .formatted(layout));
            }
        }

        // normalized layout looks like this `layouts/foo`
        // we also keep theme layout `theme-layouts/roq-theme/foo` (contains and not startWith)
        if (!normalized.contains(LAYOUTS_DIR_PREFIX)) {
            normalized = PathUtils.join(LAYOUTS_DIR_PREFIX, normalized);
        }
        return removeExtension(normalized);
    }

    public static String getLayoutKey(Optional<String> theme, String resolvedLayout) {
        String result = resolvedLayout;
        if (result.startsWith(LAYOUTS_DIR_PREFIX)) {
            result = result.substring(LAYOUTS_DIR_PREFIX.length());

            if (theme.isPresent() && result.contains(theme.get())) {
                result = result.replace(theme.get(), ":theme");
            }
        }
        return result;
    }

    public static String resolveOutputExtension(boolean markupFound,
            TemplateContext templateContext) {
        if (markupFound) {
            return ".html";
        }
        final String extension = templateContext.getExtension();
        if (extension == null) {
            return "";
        }
        final String mimeTypeForExtension = MimeMapping.getMimeTypeForExtension(extension);
        if (mimeTypeForExtension != null) {
            return "." + extension;
        }
        return ".html";
    }

    public static String stripFrontMatter(String content) {
        return RoqFrontMatterScanProcessor.FRONTMATTER_PATTERN.matcher(content).replaceFirst("");
    }
}
