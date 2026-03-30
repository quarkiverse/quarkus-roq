package io.quarkiverse.roq.frontmatter.deployment.util;

import static org.junit.jupiter.api.Assertions.*;

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
