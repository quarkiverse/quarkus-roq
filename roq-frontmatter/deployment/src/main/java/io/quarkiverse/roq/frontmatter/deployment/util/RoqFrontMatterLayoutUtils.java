package io.quarkiverse.roq.frontmatter.deployment.util;

import static io.quarkiverse.roq.frontmatter.runtime.RoqTemplates.LAYOUTS_DIR;
import static io.quarkiverse.roq.frontmatter.runtime.RoqTemplates.ROQ_GENERATED_QUTE_PREFIX;
import static io.quarkiverse.roq.frontmatter.runtime.RoqTemplates.THEME_LAYOUTS_DIR;

import java.util.Optional;

import io.quarkiverse.roq.frontmatter.deployment.items.scan.RoqFrontMatterQuteMarkupBuildItem.WrapperFilter;
import io.quarkiverse.roq.frontmatter.runtime.config.ConfiguredCollection;
import io.quarkiverse.roq.frontmatter.runtime.config.RoqSiteConfig;

public final class RoqFrontMatterLayoutUtils {

    private RoqFrontMatterLayoutUtils() {
    }

    // ── Default layout ──────────────────────────────────────────────────

    /**
     * Resolve the default layout for a page.
     * Returns null for layouts or when content is a full HTML document.
     */
    public static String resolveDefaultLayout(boolean isPartialHtmlDocument, ConfiguredCollection collection,
            RoqSiteConfig config) {
        if (!isPartialHtmlDocument) {
            return null;
        }
        return collection != null ? collection.layout() : config.pageLayout().orElse(null);
    }

    // ── Layout key extraction ───────────────────────────────────────────

    public static String getLayoutKey(Optional<String> theme, String resolvedLayout) {
        String result = resolvedLayout;
        // Strip theme-layouts/{theme}/ prefix for theme layout IDs
        if (theme.isPresent()) {
            String themePrefix = THEME_LAYOUTS_DIR + theme.get() + "/";
            if (result.startsWith(themePrefix)) {
                return result.substring(themePrefix.length());
            }
        }
        // Strip layouts/ prefix for regular layout IDs
        if (result.startsWith(LAYOUTS_DIR)) {
            result = result.substring(LAYOUTS_DIR.length());
        }
        return result;
    }

    // ── Theme prefix utilities ──────────────────────────────────────────

    public static String removeThemePrefix(String id) {
        if (!id.startsWith(THEME_LAYOUTS_DIR)) {
            return id;
        }
        // Strip "theme-layouts/" prefix and the theme name segment
        // e.g. "theme-layouts/roq-default/post" → "layouts/post"
        String afterDir = id.substring(THEME_LAYOUTS_DIR.length());
        int slashIndex = afterDir.indexOf('/');
        if (slashIndex >= 0) {
            return LAYOUTS_DIR + afterDir.substring(slashIndex + 1);
        }
        return LAYOUTS_DIR + afterDir;
    }

    // ── Include filter ──────────────────────────────────────────────────

    public static WrapperFilter getIncludeFilter(String layout) {
        if (layout == null) {
            return WrapperFilter.EMPTY;
        }
        String prefix = "{#include %s%s}\n".formatted(ROQ_GENERATED_QUTE_PREFIX, layout);
        return new WrapperFilter(prefix, "\n{/include}");
    }
}
