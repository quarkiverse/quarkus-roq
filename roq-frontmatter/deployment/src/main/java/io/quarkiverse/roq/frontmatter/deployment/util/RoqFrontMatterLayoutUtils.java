package io.quarkiverse.roq.frontmatter.deployment.util;

import static io.quarkiverse.roq.frontmatter.deployment.util.RoqFrontMatterConstants.LAYOUTS_DIR_PREFIX;
import static io.quarkiverse.roq.frontmatter.runtime.RoqTemplates.LAYOUTS_DIR;
import static io.quarkiverse.roq.frontmatter.runtime.RoqTemplates.ROQ_GENERATED_QUTE_PREFIX;
import static io.quarkiverse.roq.frontmatter.runtime.RoqTemplates.THEME_LAYOUTS_DIR_PREFIX;
import static io.quarkiverse.tools.stringpaths.StringPaths.removeExtension;

import java.util.Optional;

import org.jboss.logging.Logger;

import io.quarkiverse.roq.frontmatter.deployment.exception.RoqThemeConfigurationException;
import io.quarkiverse.roq.frontmatter.deployment.items.scan.RoqFrontMatterQuteMarkupBuildItem.WrapperFilter;
import io.quarkiverse.roq.frontmatter.runtime.config.ConfiguredCollection;
import io.quarkiverse.roq.frontmatter.runtime.config.RoqSiteConfig;
import io.quarkiverse.tools.stringpaths.StringPaths;

public final class RoqFrontMatterLayoutUtils {

    private static final Logger LOGGER = Logger.getLogger(RoqFrontMatterLayoutUtils.class);

    private RoqFrontMatterLayoutUtils() {
    }

    // ── Layout directory ────────────────────────────────────────────────

    public static String getLayoutsDir(boolean isThemeLayout) {
        if (isThemeLayout) {
            return THEME_LAYOUTS_DIR_PREFIX + LAYOUTS_DIR;
        }
        return LAYOUTS_DIR;
    }

    // ── Default layout ──────────────────────────────────────────────────

    /**
     * Resolve the default layout for a page.
     * Returns null for layouts or when content is a full HTML document.
     */
    public static String resolveDefaultLayout(boolean isHtmlPartial, ConfiguredCollection collection,
            RoqSiteConfig config) {
        if (!isHtmlPartial) {
            return null;
        }
        return collection != null ? collection.layout() : config.pageLayout().orElse(null);
    }

    // ── Layout resolution ───────────────────────────────────────────────

    /**
     * Resolve layout references from front matter into a canonical layout ID.
     * <p>
     * New syntax:
     * <ul>
     * <li>{@code layout: foo} → resolves local first ({@code layouts/foo}), theme fallback via dedup</li>
     * <li>{@code theme-layout: foo} → explicitly targets theme layout ({@code theme-layouts/{theme}/foo})</li>
     * </ul>
     * Legacy-theme backward compat (deprecated, will be errors in future versions):
     * <ul>
     * <li>(a) {@code layout: :theme/foo} → strips {@code :theme/}, resolves as {@code layouts/foo}</li>
     * <li>(b) {@code layout: theme-layouts/{theme}/foo} → resolves as-is to that theme layout ID</li>
     * <li>(c) Layout override files at {@code layouts/{theme-name}/foo.html} — handled in
     * {@link RoqFrontMatterAssembleUtils#processTemplate}</li>
     * <li>(d) {@code layout: {theme-name}/foo} → strips theme name prefix, resolves as {@code layouts/foo}</li>
     * </ul>
     */
    public static String normalizedLayout(Optional<String> theme, String layout, String themeLayout,
            String defaultLayout) {
        // theme-layout takes precedence — explicitly target a theme layout
        if (themeLayout != null && !themeLayout.isBlank()) {
            if (theme.isEmpty()) {
                throw new RoqThemeConfigurationException(
                        "No theme detected! Using 'theme-layout: %s' is only possible with a theme installed as a dependency."
                                .formatted(themeLayout));
            }
            String normalized = StringPaths.join(StringPaths.join(getLayoutsDir(true), theme.get()), themeLayout);
            return removeExtension(normalized);
        }

        String normalized = layout;

        if (normalized == null) {
            normalized = defaultLayout;
            if (normalized == null || normalized.isBlank() || "none".equalsIgnoreCase(normalized)) {
                return null;
            }
        }

        // Legacy-theme backward compat (a): :theme/ syntax is deprecated — strip and warn
        if (normalized.contains(":theme/")) {
            String simple = normalized.replace(":theme/", "").replace(":theme", "");
            LOGGER.warnf(
                    "DEPRECATED (legacy-theme): ':theme' in layout '%s' is deprecated. Use 'layout: %s' instead (resolves local first, theme fallback). "
                            + "For explicit theme targeting, use 'theme-layout: %s'.",
                    normalized, simple, simple);
            normalized = simple;
        }

        // Legacy-theme backward compat (d): {theme-name}/foo syntax is deprecated — strip theme name prefix
        if (theme.isPresent() && normalized.startsWith(theme.get() + "/")) {
            String simple = normalized.substring(theme.get().length() + 1);
            LOGGER.warnf(
                    "DEPRECATED (legacy-theme): '%s' in layout uses theme name prefix. "
                            + "Use 'layout: %s' instead (resolves local first, theme fallback). "
                            + "For explicit theme targeting, use 'theme-layout: %s'.",
                    normalized, simple, simple);
            normalized = simple;
        }

        // Legacy-theme backward compat (b): full theme-layouts/ path is deprecated — warn but resolve as-is
        if (normalized.startsWith(getLayoutsDir(true) + "/")) {
            String simple = removeThemePrefix(normalized);
            if (simple.startsWith(LAYOUTS_DIR_PREFIX)) {
                simple = simple.substring(LAYOUTS_DIR_PREFIX.length());
            }
            LOGGER.warnf(
                    "DEPRECATED (legacy-theme): Using 'theme-layouts/' in layout is deprecated. "
                            + "Use 'theme-layout: %s' instead.",
                    simple);
            return removeExtension(normalized);
        }

        if (!normalized.contains(LAYOUTS_DIR_PREFIX)) {
            normalized = StringPaths.join(LAYOUTS_DIR_PREFIX, normalized);
        }
        return removeExtension(normalized);
    }

    // ── Layout key extraction ───────────────────────────────────────────

    public static String getLayoutKey(Optional<String> theme, String resolvedLayout) {
        String result = resolvedLayout;
        // Strip theme-layouts/{theme}/ prefix for theme layout IDs
        if (theme.isPresent()) {
            String themePrefix = getLayoutsDir(true) + "/" + theme.get() + "/";
            if (result.startsWith(themePrefix)) {
                return result.substring(themePrefix.length());
            }
        }
        // Strip layouts/ prefix for regular layout IDs
        if (result.startsWith(LAYOUTS_DIR_PREFIX)) {
            result = result.substring(LAYOUTS_DIR_PREFIX.length());
        }
        return result;
    }

    // ── Theme prefix utilities ──────────────────────────────────────────

    public static String removeThemePrefix(String id) {
        String themeDir = getLayoutsDir(true); // "theme-layouts"
        if (!id.startsWith(themeDir + "/")) {
            return id;
        }
        // Strip "theme-layouts/" prefix and the theme name segment
        // e.g. "theme-layouts/roq-default/post" → "layouts/post"
        String afterThemeDir = id.substring(themeDir.length() + 1);
        int slashIndex = afterThemeDir.indexOf('/');
        if (slashIndex >= 0) {
            return getLayoutsDir(false) + "/" + afterThemeDir.substring(slashIndex + 1);
        }
        return getLayoutsDir(false) + "/" + afterThemeDir;
    }

    /**
     * Remap a legacy layout override path and log a deprecation warning if changed.
     * e.g. "layouts/roq-default/post" with theme "roq-default" → "layouts/post"
     * Returns the id unchanged if it doesn't match the pattern.
     */
    public static String remapLegacyThemeLayoutOverride(Optional<String> theme, String id) {
        String simplified = removeLegacyThemeOverridePath(theme, id);
        if (!simplified.equals(id)) {
            LOGGER.warnf(
                    "DEPRECATED (legacy-theme): Layout override at '%s' uses old path with theme name. "
                            + "Move to '%s' and use 'theme-layout: %s' in front matter instead.",
                    id, simplified,
                    simplified.substring(LAYOUTS_DIR_PREFIX.length()));
        }
        return simplified;
    }

    static String removeLegacyThemeOverridePath(Optional<String> theme, String id) {
        if (theme.isEmpty()) {
            return id;
        }
        String themeSubPrefix = LAYOUTS_DIR_PREFIX + theme.get() + "/";
        if (!id.startsWith(themeSubPrefix)) {
            return id;
        }
        return LAYOUTS_DIR_PREFIX + id.substring(themeSubPrefix.length());
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
