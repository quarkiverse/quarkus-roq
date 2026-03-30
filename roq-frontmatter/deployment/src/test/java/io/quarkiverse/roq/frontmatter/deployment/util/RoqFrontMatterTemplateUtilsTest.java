package io.quarkiverse.roq.frontmatter.deployment.util;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import io.quarkiverse.roq.frontmatter.deployment.items.scan.RoqFrontMatterQuteMarkupBuildItem.WrapperFilter;

/**
 * Pure unit tests for template utility methods in {@link RoqFrontMatterTemplateUtils}.
 */
@DisplayName("Roq FrontMatter - Template utility methods")
public class RoqFrontMatterTemplateUtilsTest {

    // ── resolveTemplateId ────────────────────────────────────────────────

    @Test
    @DisplayName("Page templates keep their file extension in the ID")
    void pageIdKeepsExtension() {
        assertEquals("pages/about.html",
                RoqFrontMatterTemplateUtils.resolveTemplateId("pages/about.html", false));
    }

    @Test
    @DisplayName("Document page templates keep their file extension in the ID")
    void documentPageIdKeepsExtension() {
        assertEquals("posts/my-post.html",
                RoqFrontMatterTemplateUtils.resolveTemplateId("posts/my-post.html", false));
    }

    @Test
    @DisplayName("Layout templates strip the file extension from the ID")
    void layoutIdStripsExtension() {
        String id = RoqFrontMatterTemplateUtils.resolveTemplateId("layouts/default.html", true);
        assertFalse(id.endsWith(".html"), "Layout ID should not contain file extension");
        assertEquals("layouts/default", id);
    }

    @Test
    @DisplayName("Theme layout templates strip the file extension from the ID")
    void themeLayoutIdStripsExtension() {
        String id = RoqFrontMatterTemplateUtils.resolveTemplateId("theme-layouts/my-theme/layouts/post.html", true);
        assertFalse(id.endsWith(".html"), "Theme layout ID should not contain file extension");
    }

    // ── isHtmlPartial ────────────────────────────────────────────────────

    @Test
    @DisplayName("HTML fragment without document markers is detected as partial")
    void htmlPartialDetected() {
        assertTrue(RoqFrontMatterTemplateUtils.isHtmlPartial("<div>hello</div>", true));
    }

    @Test
    @DisplayName("Full HTML document with DOCTYPE is not a partial")
    void fullHtmlDoctypeNotPartial() {
        assertFalse(RoqFrontMatterTemplateUtils.isHtmlPartial("<!DOCTYPE html><html><body></body></html>", true));
    }

    @Test
    @DisplayName("Full HTML document with <html> tag is not a partial")
    void fullHtmlTagNotPartial() {
        assertFalse(RoqFrontMatterTemplateUtils.isHtmlPartial("<html><body></body></html>", true));
    }

    @Test
    @DisplayName("Detection is case insensitive for document markers")
    void htmlPartialCaseInsensitive() {
        assertFalse(RoqFrontMatterTemplateUtils.isHtmlPartial("<HTML><BODY></BODY></HTML>", true));
    }

    @Test
    @DisplayName("Non-HTML content is never considered a partial")
    void nonHtmlNotPartial() {
        assertFalse(RoqFrontMatterTemplateUtils.isHtmlPartial("<div>hello</div>", false));
    }

    // ── resolveDefaultLayout ─────────────────────────────────────────────

    @Test
    @DisplayName("Full HTML document (not partial) has no default layout")
    void defaultLayoutNullWhenNotPartial() {
        assertNull(RoqFrontMatterTemplateUtils.resolveDefaultLayout(false, null, null));
    }

    // ── hasFrontMatter ──────────────────────────────────────────────────

    @Test
    @DisplayName("Content with YAML front matter block is recognized")
    void hasFrontMatterTrue() {
        assertTrue(RoqFrontMatterTemplateUtils.hasFrontMatter("---\ntitle: Hello\n---\nContent"));
    }

    @Test
    @DisplayName("Content without front matter delimiters is not recognized")
    void hasFrontMatterFalse() {
        assertFalse(RoqFrontMatterTemplateUtils.hasFrontMatter("<div>No front matter</div>"));
    }

    @Test
    @DisplayName("Empty front matter block is still recognized")
    void hasFrontMatterEmpty() {
        assertTrue(RoqFrontMatterTemplateUtils.hasFrontMatter("---\n---\nContent"));
    }

    // ── getFrontMatter ──────────────────────────────────────────────────

    @Test
    @DisplayName("Extracts YAML content between front matter delimiters")
    void getFrontMatterExtract() {
        assertEquals("title: Hello",
                RoqFrontMatterTemplateUtils.getFrontMatter("---\ntitle: Hello\n---\nContent"));
    }

    @Test
    @DisplayName("Returns empty string when closing delimiter is missing")
    void getFrontMatterNoClose() {
        assertEquals("", RoqFrontMatterTemplateUtils.getFrontMatter("---\ntitle: Hello"));
    }

    // ── stripFrontMatter ────────────────────────────────────────────────

    @Test
    @DisplayName("Removes front matter block, leaving only content")
    void stripFrontMatterRemoves() {
        assertEquals("Content",
                RoqFrontMatterTemplateUtils.stripFrontMatter("---\ntitle: Hello\n---\nContent"));
    }

    @Test
    @DisplayName("Content without front matter is returned unchanged")
    void stripFrontMatterNoOp() {
        assertEquals("<div>Hello</div>",
                RoqFrontMatterTemplateUtils.stripFrontMatter("<div>Hello</div>"));
    }

    // ── getLayoutsDir ───────────────────────────────────────────────────

    @Test
    @DisplayName("Regular layouts use the standard layouts directory")
    void layoutsDirRegular() {
        assertEquals("layouts", RoqFrontMatterTemplateUtils.getLayoutsDir(false));
    }

    @Test
    @DisplayName("Theme layouts use a different directory than regular layouts")
    void layoutsDirThemeDiffersFromRegular() {
        String themeDir = RoqFrontMatterTemplateUtils.getLayoutsDir(true);
        String regularDir = RoqFrontMatterTemplateUtils.getLayoutsDir(false);
        assertNotEquals(themeDir, regularDir,
                "Theme layout directory should differ from regular layout directory");
    }

    // ── normalizedLayout ────────────────────────────────────────────────

    @Test
    @DisplayName("Returns null when both layout and default are unset")
    void normalizedLayoutNull() {
        assertNull(RoqFrontMatterTemplateUtils.normalizedLayout(Optional.empty(), null, null));
    }

    @Test
    @DisplayName("Returns null when default layout is 'none'")
    void normalizedLayoutNone() {
        assertNull(RoqFrontMatterTemplateUtils.normalizedLayout(Optional.empty(), null, "none"));
    }

    @Test
    @DisplayName("Short layout name is resolved to a full layout path")
    void normalizedLayoutResolvesShortName() {
        String result = RoqFrontMatterTemplateUtils.normalizedLayout(Optional.empty(), "default", null);
        assertTrue(result.contains("default"), "Result should contain the layout name");
        assertTrue(result.contains("layouts/"), "Result should be under the layouts directory");
    }

    @Test
    @DisplayName("Already-qualified layout path is not double-prefixed")
    void normalizedLayoutNoDoublePrefix() {
        String result = RoqFrontMatterTemplateUtils.normalizedLayout(Optional.empty(), "layouts/default", null);
        assertFalse(result.contains("layouts/layouts/"),
                "Should not double-prefix an already-qualified path");
    }

    @Test
    @DisplayName("File extension is stripped from layout reference")
    void normalizedLayoutStripsExtension() {
        String result = RoqFrontMatterTemplateUtils.normalizedLayout(Optional.empty(), "default.html", null);
        assertFalse(result.endsWith(".html"), "Extension should be stripped");
    }

    @Test
    @DisplayName("Falls back to default layout when explicit layout is null")
    void normalizedLayoutUsesDefault() {
        String result = RoqFrontMatterTemplateUtils.normalizedLayout(Optional.empty(), null, "page");
        assertNotNull(result);
        assertTrue(result.contains("page"), "Should resolve default layout name");
    }

    @Test
    @DisplayName(":theme placeholder is replaced with actual theme name")
    void normalizedLayoutThemeReplacement() {
        String result = RoqFrontMatterTemplateUtils.normalizedLayout(Optional.of("my-theme"), ":theme/post", null);
        assertTrue(result.contains("my-theme"), "Should contain the resolved theme name");
        assertFalse(result.contains(":theme"), "Placeholder should be fully resolved");
    }

    // ── getLayoutKey ────────────────────────────────────────────────────

    @Test
    @DisplayName("Layout key strips the layouts directory prefix")
    void layoutKeyStripsPrefix() {
        String key = RoqFrontMatterTemplateUtils.getLayoutKey(Optional.empty(), "layouts/default");
        assertFalse(key.startsWith("layouts/"), "Key should not start with layouts/");
        assertEquals("default", key);
    }

    @Test
    @DisplayName("Layout key replaces theme name with :theme placeholder")
    void layoutKeyThemeReplacement() {
        String key = RoqFrontMatterTemplateUtils.getLayoutKey(Optional.of("my-theme"), "layouts/my-theme/post");
        assertTrue(key.contains(":theme"), "Theme name should be replaced with :theme");
        assertFalse(key.contains("my-theme"), "Actual theme name should not appear");
    }

    @Test
    @DisplayName("Layout key without layouts/ prefix is returned as-is")
    void layoutKeyNoPrefix() {
        assertEquals("custom/thing",
                RoqFrontMatterTemplateUtils.getLayoutKey(Optional.empty(), "custom/thing"));
    }

    // ── removeThemePrefix ───────────────────────────────────────────────

    @Test
    @DisplayName("Theme layout path is converted to regular layout path")
    void removesThemePrefix() {
        String themeDir = RoqFrontMatterTemplateUtils.getLayoutsDir(true);
        String regularDir = RoqFrontMatterTemplateUtils.getLayoutsDir(false);
        String themePath = themeDir + "/my-theme/post";
        String result = RoqFrontMatterTemplateUtils.removeThemePrefix(themePath);
        assertTrue(result.startsWith(regularDir + "/"),
                "Should start with regular layout directory");
    }

    @Test
    @DisplayName("Regular layout path is unchanged by removeThemePrefix")
    void removesThemePrefixNoOp() {
        String regularPath = "layouts/default";
        assertEquals(regularPath, RoqFrontMatterTemplateUtils.removeThemePrefix(regularPath));
    }

    // ── getIncludeFilter ────────────────────────────────────────────────

    @Test
    @DisplayName("Null layout produces an identity filter (no wrapping)")
    void includeFilterNull() {
        WrapperFilter filter = RoqFrontMatterTemplateUtils.getIncludeFilter(null);
        assertEquals("content", filter.apply("content"),
                "Null layout should not modify content");
    }

    @Test
    @DisplayName("Non-null layout wraps content with Qute include/end directives")
    void includeFilterWraps() {
        WrapperFilter filter = RoqFrontMatterTemplateUtils.getIncludeFilter("layouts/default");
        String result = filter.apply("body content");
        assertTrue(result.contains("body content"), "Wrapped content should preserve the body");
        assertTrue(result.contains("{#include"), "Should open a Qute include block");
        assertTrue(result.contains("{/include}"), "Should close the Qute include block");
        assertTrue(result.contains("layouts/default"), "Should reference the layout");
    }

    // ── getEscapeFilter ─────────────────────────────────────────────────

    @Test
    @DisplayName("Escaped content is wrapped with Qute escape delimiters")
    void escapeFilterWraps() {
        WrapperFilter filter = RoqFrontMatterTemplateUtils.getEscapeFilter(true);
        String result = filter.apply("content");
        assertNotEquals("content", result, "Escaped content should be wrapped");
    }

    @Test
    @DisplayName("Non-escaped content is returned unchanged")
    void escapeFilterNoOp() {
        WrapperFilter filter = RoqFrontMatterTemplateUtils.getEscapeFilter(false);
        assertEquals("content", filter.apply("content"),
                "Non-escaped content should not be modified");
    }
}
