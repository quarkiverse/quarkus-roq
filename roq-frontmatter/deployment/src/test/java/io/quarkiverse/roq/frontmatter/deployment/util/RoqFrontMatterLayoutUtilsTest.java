package io.quarkiverse.roq.frontmatter.deployment.util;

import static org.junit.jupiter.api.Assertions.*;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import io.quarkiverse.roq.frontmatter.deployment.exception.RoqLayoutNotFoundException;
import io.quarkiverse.roq.frontmatter.deployment.exception.RoqThemeConfigurationException;
import io.quarkiverse.roq.frontmatter.deployment.items.scan.RoqFrontMatterAvailableLayoutsBuildItem;
import io.quarkiverse.roq.frontmatter.deployment.items.scan.RoqFrontMatterQuteMarkupBuildItem.WrapperFilter;
import io.quarkiverse.roq.frontmatter.deployment.util.RoqFrontMatterAssembleUtils.LayoutRef;
import io.quarkiverse.roq.frontmatter.runtime.model.SourceFile;
import io.vertx.core.json.JsonObject;

/**
 * Pure unit tests for layout resolution, layout ref picking, and utility methods.
 */
@DisplayName("Roq FrontMatter - Layout resolution")
public class RoqFrontMatterLayoutUtilsTest {

    private static SourceFile sourceFile(String path) {
        return new SourceFile(".", path);
    }

    private static RoqFrontMatterAvailableLayoutsBuildItem availableLayouts(String... ids) {
        Map<String, SourceFile> map = new LinkedHashMap<>();
        for (String id : ids) {
            map.put(id, sourceFile(id + ".html"));
        }
        return new RoqFrontMatterAvailableLayoutsBuildItem(map);
    }

    private static final Optional<String> MY_THEME = Optional.of("my-theme");
    private static final Optional<String> OTHER_THEME = Optional.of("other-theme");
    private static final Optional<String> NO_THEME = Optional.empty();
    private static final Optional<String> NO_SOURCE = Optional.empty();

    // ── resolveLayoutId ─────────────────────────────────────────────────

    @Nested
    @DisplayName("resolveLayoutId: layout: resolution")
    class LayoutResolution {

        @Test
        @DisplayName("#1 Content page, local layout exists")
        void contentPageLocalLayout() {
            var layouts = availableLayouts("layouts/post", "theme-layouts/my-theme/post");
            assertEquals("layouts/post",
                    layouts.resolveLayoutId(MY_THEME, NO_SOURCE, new LayoutRef("post", false)));
        }

        @Test
        @DisplayName("#2 Content page, no local, theme fallback")
        void contentPageThemeFallback() {
            var layouts = availableLayouts("theme-layouts/my-theme/about");
            assertEquals("theme-layouts/my-theme/about",
                    layouts.resolveLayoutId(MY_THEME, NO_SOURCE, new LayoutRef("about", false)));
        }

        @Test
        @DisplayName("#3a Content page, local override exists")
        void contentPageLocalOverrideExists() {
            var layouts = availableLayouts("layouts/custom", "layouts/my-theme/custom", "theme-layouts/my-theme/custom");
            assertEquals("layouts/custom",
                    layouts.resolveLayoutId(MY_THEME, NO_SOURCE, new LayoutRef("custom", false)));
        }

        @Test
        @DisplayName("#3b Content page, theme-dir override")
        void contentPageThemeDirOverride() {
            var layouts = availableLayouts("layouts/my-theme/custom", "theme-layouts/my-theme/custom");
            assertEquals("layouts/my-theme/custom",
                    layouts.resolveLayoutId(MY_THEME, NO_SOURCE, new LayoutRef("custom", false)));
        }

        @Test
        @DisplayName("#4 Cross-theme with /")
        void crossThemeWithSlash() {
            var layouts = availableLayouts("theme-layouts/other-theme/foo");
            assertEquals("theme-layouts/other-theme/foo",
                    layouts.resolveLayoutId(MY_THEME, NO_SOURCE, new LayoutRef("other-theme/foo", false)));
        }

        @Test
        @DisplayName("#4 Cross-theme with / prefers layouts/ over theme-layouts/")
        void crossThemeLocalFirst() {
            var layouts = availableLayouts("layouts/other-theme/foo", "theme-layouts/other-theme/foo");
            assertEquals("layouts/other-theme/foo",
                    layouts.resolveLayoutId(MY_THEME, NO_SOURCE, new LayoutRef("other-theme/foo", false)));
        }

        @Test
        @DisplayName("#5 Theme layout, own == active, user override wins")
        void themeLayoutOwnEqualsActiveUserOverride() {
            var layouts = availableLayouts("layouts/page", "theme-layouts/my-theme/page");
            assertEquals("layouts/page",
                    layouts.resolveLayoutId(MY_THEME, MY_THEME, new LayoutRef("page", false)));
        }

        @Test
        @DisplayName("#6 Theme layout, own == active, no override")
        void themeLayoutOwnEqualsActiveNoOverride() {
            var layouts = availableLayouts("theme-layouts/my-theme/about");
            assertEquals("theme-layouts/my-theme/about",
                    layouts.resolveLayoutId(MY_THEME, MY_THEME, new LayoutRef("about", false)));
        }

        @Test
        @DisplayName("#7 Theme layout, own != active")
        void themeLayoutOwnNotActive() {
            var layouts = availableLayouts("theme-layouts/other-theme/foo");
            assertEquals("theme-layouts/other-theme/foo",
                    layouts.resolveLayoutId(MY_THEME, OTHER_THEME, new LayoutRef("foo", false)));
        }

        @Test
        @DisplayName("#7 Theme layout, own != active, user override in layouts/{own}/")
        void themeLayoutOwnNotActiveUserOverride() {
            var layouts = availableLayouts("layouts/other-theme/foo", "theme-layouts/other-theme/foo");
            assertEquals("layouts/other-theme/foo",
                    layouts.resolveLayoutId(MY_THEME, OTHER_THEME, new LayoutRef("foo", false)));
        }

        @Test
        @DisplayName("Throws when layout not found")
        void throwsWhenNotFound() {
            var layouts = availableLayouts();
            assertThrows(RoqLayoutNotFoundException.class,
                    () -> layouts.resolveLayoutId(MY_THEME, NO_SOURCE, new LayoutRef("nonexistent", false)));
        }

        @Test
        @DisplayName("Returns null for null value")
        void nullValueReturnsNull() {
            var layouts = availableLayouts();
            assertNull(layouts.resolveLayoutId(MY_THEME, NO_SOURCE, new LayoutRef(null, false)));
        }

        @Test
        @DisplayName("Returns null for blank value")
        void blankValueReturnsNull() {
            var layouts = availableLayouts();
            assertNull(layouts.resolveLayoutId(MY_THEME, NO_SOURCE, new LayoutRef("  ", false)));
        }

        @Test
        @DisplayName("File extension is stripped")
        void extensionStripped() {
            var layouts = availableLayouts("layouts/post");
            assertEquals("layouts/post",
                    layouts.resolveLayoutId(MY_THEME, NO_SOURCE, new LayoutRef("post.html", false)));
        }
    }

    @Nested
    @DisplayName("resolveLayoutId: theme-layout: resolution")
    class ThemeLayoutResolution {

        @Test
        @DisplayName("#11 theme-layout: with / direct match")
        void themeLayoutWithSlash() {
            var layouts = availableLayouts("theme-layouts/my-theme/page");
            assertEquals("theme-layouts/my-theme/page",
                    layouts.resolveLayoutId(MY_THEME, NO_SOURCE, new LayoutRef("my-theme/page", true)));
        }

        @Test
        @DisplayName("#12 theme-layout: no / from content page")
        void themeLayoutNoSlashFromContent() {
            var layouts = availableLayouts("theme-layouts/my-theme/page");
            assertEquals("theme-layouts/my-theme/page",
                    layouts.resolveLayoutId(MY_THEME, NO_SOURCE, new LayoutRef("page", true)));
        }

        @Test
        @DisplayName("#13 theme-layout: no / from theme layout resolves to own theme")
        void themeLayoutNoSlashFromThemeLayoutResolvesToOwn() {
            var layouts = availableLayouts("theme-layouts/my-theme/page");
            assertEquals("theme-layouts/my-theme/page",
                    layouts.resolveLayoutId(MY_THEME, MY_THEME, new LayoutRef("page", true)));
        }

        @Test
        @DisplayName("theme-layout: without theme configured throws")
        void themeLayoutNoThemeThrows() {
            var layouts = availableLayouts("theme-layouts/my-theme/page");
            assertThrows(RoqThemeConfigurationException.class,
                    () -> layouts.resolveLayoutId(NO_THEME, NO_SOURCE, new LayoutRef("page", true)));
        }

        @Test
        @DisplayName("theme-layout: with / not found throws")
        void themeLayoutWithSlashNotFound() {
            var layouts = availableLayouts();
            assertThrows(RoqLayoutNotFoundException.class,
                    () -> layouts.resolveLayoutId(MY_THEME, NO_SOURCE, new LayoutRef("my-theme/nonexistent", true)));
        }
    }

    @Nested
    @DisplayName("resolveLayoutId: legacy support")
    class LegacyResolution {

        @Test
        @DisplayName("#8 Legacy :theme/ stripped, resolves normally")
        void legacyColonTheme() {
            var layouts = availableLayouts("layouts/post");
            assertEquals("layouts/post",
                    layouts.resolveLayoutId(MY_THEME, NO_SOURCE, new LayoutRef(":theme/post", false)));
        }

        @Test
        @DisplayName("#9 Legacy full theme-layouts/ path, direct match")
        void legacyFullThemeLayoutsPath() {
            var layouts = availableLayouts("theme-layouts/my-theme/page");
            assertEquals("theme-layouts/my-theme/page",
                    layouts.resolveLayoutId(MY_THEME, NO_SOURCE, new LayoutRef("theme-layouts/my-theme/page", false)));
        }

        @Test
        @DisplayName("#10 Legacy layouts/ prefix, direct match")
        void legacyLayoutsPrefix() {
            var layouts = availableLayouts("layouts/post");
            assertEquals("layouts/post",
                    layouts.resolveLayoutId(MY_THEME, NO_SOURCE, new LayoutRef("layouts/post", false)));
        }

        @Test
        @DisplayName("Legacy full path not found throws")
        void legacyFullPathNotFound() {
            var layouts = availableLayouts();
            assertThrows(RoqLayoutNotFoundException.class,
                    () -> layouts.resolveLayoutId(MY_THEME, NO_SOURCE, new LayoutRef("theme-layouts/my-theme/gone", false)));
        }
    }

    // ── extractSourceTheme ──────────────────────────────────────────────

    @Nested
    @DisplayName("extractSourceTheme")
    class ExtractSourceTheme {

        @Test
        @DisplayName("Theme layout returns theme name")
        void themeLayout() {
            assertEquals(Optional.of("roq-default"),
                    RoqFrontMatterAssembleUtils.extractSourceTheme(true, "theme-layouts/roq-default/page"));
        }

        @Test
        @DisplayName("Non-theme layout returns empty")
        void nonThemeLayout() {
            assertEquals(Optional.empty(),
                    RoqFrontMatterAssembleUtils.extractSourceTheme(false, "layouts/page"));
        }

        @Test
        @DisplayName("Content page returns empty")
        void contentPage() {
            assertEquals(Optional.empty(),
                    RoqFrontMatterAssembleUtils.extractSourceTheme(false, "content/pages/about"));
        }

        @Test
        @DisplayName("Theme layout without slash after theme name returns empty")
        void themeLayoutNoSlash() {
            assertEquals(Optional.empty(),
                    RoqFrontMatterAssembleUtils.extractSourceTheme(true, "theme-layouts/orphan"));
        }
    }

    // ── resolveLayoutRef ────────────────────────────────────────────────

    @Nested
    @DisplayName("resolveLayoutRef")
    class ResolveLayoutRef {

        @Test
        @DisplayName("theme-layout: takes precedence over layout:")
        void themeLayoutPrecedence() {
            JsonObject data = new JsonObject().put("layout", "page").put("theme-layout", "custom");
            LayoutRef ref = RoqFrontMatterAssembleUtils.resolveLayoutRef(data, true, true, null, null);
            assertEquals("custom", ref.value());
            assertTrue(ref.scopeToTheme());
        }

        @Test
        @DisplayName("layout: used when no theme-layout:")
        void layoutUsed() {
            JsonObject data = new JsonObject().put("layout", "page");
            LayoutRef ref = RoqFrontMatterAssembleUtils.resolveLayoutRef(data, true, true, null, null);
            assertEquals("page", ref.value());
            assertFalse(ref.scopeToTheme());
        }

        @Test
        @DisplayName("Non-page with no layout returns null value")
        void nonPageNoLayout() {
            JsonObject data = new JsonObject();
            LayoutRef ref = RoqFrontMatterAssembleUtils.resolveLayoutRef(data, false, true, null, null);
            assertNull(ref.value());
            assertFalse(ref.scopeToTheme());
        }

        @Test
        @DisplayName("Blank theme-layout: is ignored")
        void blankThemeLayoutIgnored() {
            JsonObject data = new JsonObject().put("theme-layout", "  ").put("layout", "page");
            LayoutRef ref = RoqFrontMatterAssembleUtils.resolveLayoutRef(data, true, true, null, null);
            assertEquals("page", ref.value());
            assertFalse(ref.scopeToTheme());
        }
    }

    // ── getLayoutKey ────────────────────────────────────────────────────

    @Nested
    @DisplayName("getLayoutKey")
    class GetLayoutKey {

        @Test
        @DisplayName("Strips layouts/ prefix")
        void stripsLayoutsPrefix() {
            assertEquals("default",
                    RoqFrontMatterLayoutUtils.getLayoutKey(NO_THEME, "layouts/default"));
        }

        @Test
        @DisplayName("Strips theme-layouts/{theme}/ prefix")
        void stripsThemeLayoutsPrefix() {
            assertEquals("post",
                    RoqFrontMatterLayoutUtils.getLayoutKey(MY_THEME, "theme-layouts/my-theme/post"));
        }

        @Test
        @DisplayName("No prefix returns as-is")
        void noPrefix() {
            assertEquals("custom/thing",
                    RoqFrontMatterLayoutUtils.getLayoutKey(NO_THEME, "custom/thing"));
        }
    }

    // ── removeThemePrefix ───────────────────────────────────────────────

    @Nested
    @DisplayName("removeThemePrefix")
    class RemoveThemePrefix {

        @Test
        @DisplayName("Strips theme-layouts/ and theme name")
        void stripsThemePrefix() {
            assertEquals("layouts/post",
                    RoqFrontMatterLayoutUtils.removeThemePrefix("theme-layouts/my-theme/post"));
        }

        @Test
        @DisplayName("Non-theme path unchanged")
        void nonThemePath() {
            assertEquals("layouts/default",
                    RoqFrontMatterLayoutUtils.removeThemePrefix("layouts/default"));
        }
    }

    // ── getIncludeFilter ────────────────────────────────────────────────

    @Nested
    @DisplayName("getIncludeFilter")
    class GetIncludeFilter {

        @Test
        @DisplayName("Null layout produces identity filter")
        void nullLayout() {
            WrapperFilter filter = RoqFrontMatterLayoutUtils.getIncludeFilter(null, true);
            assertEquals("content", filter.apply("content"));
        }

        @Test
        @DisplayName("Page with layout wraps with Qute include and fragment")
        void pageWrapsContentWithFragment() {
            WrapperFilter filter = RoqFrontMatterLayoutUtils.getIncludeFilter("layouts/default", true);
            String result = filter.apply("body");
            assertTrue(result.contains("{#include"));
            assertTrue(result.contains("{/include}"));
            assertTrue(result.contains("layouts/default"));
            assertTrue(result.contains("{#fragment RoqPageContent}"));
            assertTrue(result.contains("{/fragment}"));
        }

        @Test
        @DisplayName("Layout-to-layout wraps with Qute include without fragment")
        void layoutWrapsContentWithoutFragment() {
            WrapperFilter filter = RoqFrontMatterLayoutUtils.getIncludeFilter("layouts/default", false);
            String result = filter.apply("body");
            assertTrue(result.contains("{#include"));
            assertTrue(result.contains("{/include}"));
            assertTrue(result.contains("layouts/default"));
            assertFalse(result.contains("{#fragment"));
        }
    }
}
