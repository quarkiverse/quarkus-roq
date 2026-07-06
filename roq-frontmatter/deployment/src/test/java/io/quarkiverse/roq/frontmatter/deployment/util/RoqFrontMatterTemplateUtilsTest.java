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

    // ── isPartialHtmlDocument ───────────────────────────────────────────

    @Test
    @DisplayName("HTML fragment without document markers is detected as partial")
    void htmlPartialDetected() {
        assertTrue(RoqFrontMatterTemplateUtils.isPartialHtmlDocument("<div>hello</div>", true));
    }

    @Test
    @DisplayName("Full HTML document with DOCTYPE is not a partial")
    void fullHtmlDoctypeNotPartial() {
        assertFalse(RoqFrontMatterTemplateUtils.isPartialHtmlDocument("<!DOCTYPE html><html><body></body></html>", true));
    }

    @Test
    @DisplayName("Full HTML document with <html> tag is not a partial")
    void fullHtmlTagNotPartial() {
        assertFalse(RoqFrontMatterTemplateUtils.isPartialHtmlDocument("<html><body></body></html>", true));
    }

    @Test
    @DisplayName("Detection is case insensitive for document markers")
    void htmlPartialCaseInsensitive() {
        assertFalse(RoqFrontMatterTemplateUtils.isPartialHtmlDocument("<HTML><BODY></BODY></HTML>", true));
    }

    @Test
    @DisplayName("Non-HTML content is never considered a partial")
    void nonHtmlNotPartial() {
        assertFalse(RoqFrontMatterTemplateUtils.isPartialHtmlDocument("<div>hello</div>", false));
    }

    @Test
    @DisplayName("Complete HTML with leading whitespace is not a partial")
    void completeHtmlWithLeadingWhitespace() {
        assertFalse(RoqFrontMatterTemplateUtils.isPartialHtmlDocument("   \n\t<!DOCTYPE html><html>", true));
    }

    @Test
    @DisplayName("Complete HTML with leading comment is not a partial")
    void completeHtmlWithLeadingComment() {
        assertFalse(RoqFrontMatterTemplateUtils.isPartialHtmlDocument("<!-- Comment -->\n<!DOCTYPE html><html>", true));
    }

    @Test
    @DisplayName("HTML in code example is still detected as partial")
    void htmlInCodeExampleIsPartial() {
        String content = """
                = AsciiDoc Document

                Example:
                [source,html]
                ----
                <!DOCTYPE html>
                <html><body>Example</body></html>
                ----
                """;
        assertTrue(RoqFrontMatterTemplateUtils.isPartialHtmlDocument(content, true));
    }

    @Test
    @DisplayName("Empty content is detected as partial")
    void emptyContentIsPartial() {
        assertTrue(RoqFrontMatterTemplateUtils.isPartialHtmlDocument("", true));
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

    // ── hasMisplacedFrontMatter ──────────────────────────────────────────

    @Test
    @DisplayName("Front matter at start of file is not misplaced")
    void hasMisplacedFrontMatterFalseWhenAtStart() {
        assertFalse(RoqFrontMatterTemplateUtils.hasMisplacedFrontMatter("---\ntitle: Hello\n---\nContent"));
    }

    @Test
    @DisplayName("Content without front matter is not misplaced")
    void hasMisplacedFrontMatterFalseWhenNone() {
        assertFalse(RoqFrontMatterTemplateUtils.hasMisplacedFrontMatter("<div>No front matter</div>"));
    }

    @Test
    @DisplayName("Front matter preceded by an HTML comment is detected as misplaced")
    void hasMisplacedFrontMatterAfterHtmlComment() {
        assertTrue(RoqFrontMatterTemplateUtils
                .hasMisplacedFrontMatter("<!-- a comment -->\n---\ntitle: Hello\n---\nContent"));
    }

    @Test
    @DisplayName("Front matter preceded by a Qute comment is detected as misplaced")
    void hasMisplacedFrontMatterAfterQuteComment() {
        assertTrue(RoqFrontMatterTemplateUtils
                .hasMisplacedFrontMatter("{!-- a comment --}\n---\ntitle: Hello\n---\nContent"));
    }

    @Test
    @DisplayName("Multiple HTML comments before front matter are detected as misplaced")
    void hasMisplacedFrontMatterAfterMultipleComments() {
        assertTrue(RoqFrontMatterTemplateUtils
                .hasMisplacedFrontMatter("<!-- one -->\n<!-- two -->\n---\ntitle: Hello\n---\nContent"));
    }

    @Test
    @DisplayName("AsciiDoc code block showing frontmatter example is not flagged")
    void hasMisplacedFrontMatterFalseForAsciiDocCodeBlock() {
        // A --- ... --- block inside an AsciiDoc ---- code block is legitimate
        // content (e.g. documentation showing frontmatter examples) and must
        // not be flagged as misplaced frontmatter.
        assertFalse(RoqFrontMatterTemplateUtils.hasMisplacedFrontMatter(
                "= Title\n\n[source,html]\n----\n---\ntheme-layout: main\n---\n\nContent\n----\n"));
    }

    @Test
    @DisplayName("A single AsciiDoc thematic break (---) alone is not flagged as misplaced frontmatter")
    void hasMisplacedFrontMatterFalseForThematicBreak() {
        // A single --- used as an AsciiDoc thematic break (horizontal rule)
        // does not form a --- ... --- block, so it must not be flagged.
        assertFalse(RoqFrontMatterTemplateUtils
                .hasMisplacedFrontMatter("= Title\n\n---\n\nSome text after the rule."));
    }

    @Test
    @DisplayName("Front matter preceded by any text is detected as misplaced")
    void hasMisplacedFrontMatterAfterText() {
        // The opening --- must be on line 1: any preceding line is misplaced.
        // This reproduces the example from the bug report (a stray line "tutu"
        // before the frontmatter block).
        assertTrue(RoqFrontMatterTemplateUtils.hasMisplacedFrontMatter("""
                tutu
                ---
                title: "Welcome to Roq!"
                date: 2024-08-29 13:32:20 +0200
                description: This is the first article ever made with Quarkus Roq
                tags: blogging
                author: ia3andy
                redirect_from: [first-roq-article-ever]
                ---

                Content here"""));
    }

    @Test
    @DisplayName("Front matter preceded by a blank line is detected as misplaced")
    void hasMisplacedFrontMatterAfterBlankLine() {
        // The opening --- must be on line 1: even a blank line before it is misplaced.
        assertTrue(RoqFrontMatterTemplateUtils.hasMisplacedFrontMatter("\n---\ntitle: Hello\n---\nContent"));
    }

    @Test
    @DisplayName("Markdown code block showing frontmatter example is not flagged")
    void hasMisplacedFrontMatterFalseForMarkdownCodeBlock() {
        // A --- ... --- block inside a Markdown ``` fence is legitimate content
        // (e.g. documentation showing frontmatter examples) and must not be
        // flagged as misplaced frontmatter.
        assertFalse(RoqFrontMatterTemplateUtils.hasMisplacedFrontMatter(
                "# Title\n\n```yaml\n---\ntitle: x\n---\n```\nText"));
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
