package io.quarkiverse.roq.frontmatter.deployment.util;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import io.quarkiverse.roq.frontmatter.deployment.exception.RoqThemeConfigurationException;
import io.quarkiverse.roq.frontmatter.deployment.items.scan.RoqFrontMatterQuteMarkupBuildItem.WrapperFilter;

/**
 * Pure unit tests for layout utility methods in {@link RoqFrontMatterLayoutUtils}.
 */
@DisplayName("Roq FrontMatter - Layout utility methods")
public class RoqFrontMatterLayoutUtilsTest {

    // ── resolveDefaultLayout ─────────────────────────────────────────────

    @Test
    @DisplayName("Full HTML document (not partial) has no default layout")
    void defaultLayoutNullWhenNotPartial() {
        assertNull(RoqFrontMatterLayoutUtils.resolveDefaultLayout(false, null, null));
    }

    // ── getLayoutsDir ───────────────────────────────────────────────────

    @Test
    @DisplayName("Regular layouts use the standard layouts directory")
    void layoutsDirRegular() {
        assertEquals("layouts", RoqFrontMatterLayoutUtils.getLayoutsDir(false));
    }

    @Test
    @DisplayName("Theme layouts use a different directory than regular layouts")
    void layoutsDirThemeDiffersFromRegular() {
        String themeDir = RoqFrontMatterLayoutUtils.getLayoutsDir(true);
        String regularDir = RoqFrontMatterLayoutUtils.getLayoutsDir(false);
        assertNotEquals(themeDir, regularDir,
                "Theme layout directory should differ from regular layout directory");
    }

    // ── normalizedLayout: basic resolution ──────────────────────────────

    @Test
    @DisplayName("Returns null when both layout and default are unset")
    void normalizedLayoutNull() {
        assertNull(RoqFrontMatterLayoutUtils.normalizedLayout(Optional.empty(), null, null, null));
    }

    @Test
    @DisplayName("Returns null when default layout is 'none'")
    void normalizedLayoutNone() {
        assertNull(RoqFrontMatterLayoutUtils.normalizedLayout(Optional.empty(), null, null, "none"));
    }

    @Test
    @DisplayName("Short layout name is resolved to a full layout path")
    void normalizedLayoutResolvesShortName() {
        String result = RoqFrontMatterLayoutUtils.normalizedLayout(Optional.empty(), "default", null, null);
        assertEquals("layouts/default", result);
    }

    @Test
    @DisplayName("Already-qualified layout path is not double-prefixed")
    void normalizedLayoutNoDoublePrefix() {
        String result = RoqFrontMatterLayoutUtils.normalizedLayout(Optional.empty(), "layouts/default", null, null);
        assertFalse(result.contains("layouts/layouts/"),
                "Should not double-prefix an already-qualified path");
    }

    @Test
    @DisplayName("File extension is stripped from layout reference")
    void normalizedLayoutStripsExtension() {
        String result = RoqFrontMatterLayoutUtils.normalizedLayout(Optional.empty(), "default.html", null, null);
        assertFalse(result.endsWith(".html"), "Extension should be stripped");
    }

    @Test
    @DisplayName("Falls back to default layout when explicit layout is null")
    void normalizedLayoutUsesDefault() {
        String result = RoqFrontMatterLayoutUtils.normalizedLayout(Optional.empty(), null, null, "page");
        assertNotNull(result);
        assertEquals("layouts/page", result);
    }

    // ── normalizedLayout: new syntax (layout: foo) ──────────────────────

    @Test
    @DisplayName("Simple layout name resolves to layouts/ path")
    void simpleLayoutName() {
        String result = RoqFrontMatterLayoutUtils.normalizedLayout(Optional.of("my-theme"), "post", null, null);
        assertEquals("layouts/post", result);
    }

    @Test
    @DisplayName("Simple layout name works without theme configured")
    void simpleLayoutNameNoTheme() {
        String result = RoqFrontMatterLayoutUtils.normalizedLayout(Optional.empty(), "post", null, null);
        assertEquals("layouts/post", result);
    }

    // ── normalizedLayout: new syntax (theme-layout: foo) ────────────────

    @Test
    @DisplayName("theme-layout resolves to theme-layouts/{theme}/foo path")
    void themeLayoutResolvesToThemeLayoutPath() {
        String result = RoqFrontMatterLayoutUtils.normalizedLayout(Optional.of("my-theme"), null, "post", null);
        assertEquals("theme-layouts/my-theme/post", result);
    }

    @Test
    @DisplayName("theme-layout takes precedence over layout")
    void themeLayoutTakesPrecedence() {
        String result = RoqFrontMatterLayoutUtils.normalizedLayout(Optional.of("my-theme"), "default", "post", null);
        assertEquals("theme-layouts/my-theme/post", result);
    }

    @Test
    @DisplayName("theme-layout without theme configured throws exception")
    void themeLayoutWithoutThemeThrows() {
        assertThrows(RoqThemeConfigurationException.class,
                () -> RoqFrontMatterLayoutUtils.normalizedLayout(Optional.empty(), null, "post", null));
    }

    @Test
    @DisplayName("theme-layout strips file extension")
    void themeLayoutStripsExtension() {
        String result = RoqFrontMatterLayoutUtils.normalizedLayout(Optional.of("my-theme"), null, "post.html", null);
        assertEquals("theme-layouts/my-theme/post", result);
    }

    // ── normalizedLayout: legacy-theme backward compat (a) :theme/ ──────

    @Test
    @DisplayName("Legacy-theme: :theme/foo resolves to layouts/foo")
    void legacyThemeColonResolvesToSimplePath() {
        String result = RoqFrontMatterLayoutUtils.normalizedLayout(Optional.of("my-theme"), ":theme/post", null, null);
        assertEquals("layouts/post", result,
                ":theme/ should be stripped, resolving to simple layout path");
    }

    @Test
    @DisplayName("Legacy-theme: :theme/ in default layout is stripped when no theme")
    void legacyThemeColonDefaultStrippedNoTheme() {
        String result = RoqFrontMatterLayoutUtils.normalizedLayout(Optional.empty(), null, null, ":theme/page");
        assertEquals("layouts/page", result,
                ":theme/ should be stripped from defaults when no theme configured");
    }

    @Test
    @DisplayName("Legacy-theme: :theme/ in default layout is stripped when theme present")
    void legacyThemeColonDefaultStrippedWithTheme() {
        String result = RoqFrontMatterLayoutUtils.normalizedLayout(Optional.of("my-theme"), null, null, ":theme/page");
        assertEquals("layouts/page", result,
                ":theme/ should be stripped from defaults, resolving to simple path");
    }

    // ── normalizedLayout: legacy-theme backward compat (d) {theme-name}/foo ──

    @Test
    @DisplayName("Legacy-theme: {theme-name}/foo resolves to layouts/foo")
    void legacyThemeNamePrefixResolvesToSimplePath() {
        String result = RoqFrontMatterLayoutUtils.normalizedLayout(Optional.of("roq-default"), "roq-default/main", null,
                null);
        assertEquals("layouts/main", result,
                "{theme-name}/ prefix should be stripped, resolving to simple layout path");
    }

    @Test
    @DisplayName("Legacy-theme: {theme-name}/foo does not match when no theme configured")
    void themeNamePrefixIgnoredWhenNoTheme() {
        String result = RoqFrontMatterLayoutUtils.normalizedLayout(Optional.empty(), "roq-default/main", null, null);
        assertEquals("layouts/roq-default/main", result,
                "Without a theme, the value should be treated as a regular path");
    }

    // ── normalizedLayout: legacy-theme backward compat (b) full path ────

    @Test
    @DisplayName("Legacy-theme: Full theme-layouts/ path resolves as-is")
    void legacyThemeFullThemeLayoutsPath() {
        String result = RoqFrontMatterLayoutUtils.normalizedLayout(Optional.of("my-theme"),
                "theme-layouts/my-theme/default", null, null);
        assertEquals("theme-layouts/my-theme/default", result,
                "Full theme-layouts/ path should resolve to the actual theme layout ID");
    }

    // ── getLayoutKey ────────────────────────────────────────────────────

    @Test
    @DisplayName("Layout key strips the layouts directory prefix")
    void layoutKeyStripsPrefix() {
        String key = RoqFrontMatterLayoutUtils.getLayoutKey(Optional.empty(), "layouts/default");
        assertEquals("default", key);
    }

    @Test
    @DisplayName("Layout key for theme layout strips theme-layouts/{theme}/ prefix")
    void layoutKeyForThemeLayout() {
        String key = RoqFrontMatterLayoutUtils.getLayoutKey(Optional.of("my-theme"), "theme-layouts/my-theme/post");
        assertEquals("post", key);
    }

    @Test
    @DisplayName("Layout key for simple layout strips layouts/ prefix")
    void layoutKeyForSimpleLayout() {
        String key = RoqFrontMatterLayoutUtils.getLayoutKey(Optional.empty(), "layouts/post");
        assertEquals("post", key);
    }

    @Test
    @DisplayName("Layout key without layouts/ prefix is returned as-is")
    void layoutKeyNoPrefix() {
        assertEquals("custom/thing",
                RoqFrontMatterLayoutUtils.getLayoutKey(Optional.empty(), "custom/thing"));
    }

    // ── removeThemePrefix ───────────────────────────────────────────────

    @Test
    @DisplayName("removeThemePrefix strips both theme dir prefix AND theme name")
    void removesThemePrefixStripsThemeName() {
        String result = RoqFrontMatterLayoutUtils.removeThemePrefix("theme-layouts/my-theme/post");
        assertEquals("layouts/post", result,
                "Should strip theme-layouts/ prefix AND theme name segment");
    }

    @Test
    @DisplayName("Regular layout path is unchanged by removeThemePrefix")
    void removesThemePrefixNoOp() {
        String regularPath = "layouts/default";
        assertEquals(regularPath, RoqFrontMatterLayoutUtils.removeThemePrefix(regularPath));
    }

    // ── getIncludeFilter ────────────────────────────────────────────────

    @Test
    @DisplayName("Null layout produces an identity filter (no wrapping)")
    void includeFilterNull() {
        WrapperFilter filter = RoqFrontMatterLayoutUtils.getIncludeFilter(null);
        assertEquals("content", filter.apply("content"),
                "Null layout should not modify content");
    }

    @Test
    @DisplayName("Non-null layout wraps content with Qute include/end directives")
    void includeFilterWraps() {
        WrapperFilter filter = RoqFrontMatterLayoutUtils.getIncludeFilter("layouts/default");
        String result = filter.apply("body content");
        assertTrue(result.contains("body content"), "Wrapped content should preserve the body");
        assertTrue(result.contains("{#include"), "Should open a Qute include block");
        assertTrue(result.contains("{/include}"), "Should close the Qute include block");
        assertTrue(result.contains("layouts/default"), "Should reference the layout");
    }
}
