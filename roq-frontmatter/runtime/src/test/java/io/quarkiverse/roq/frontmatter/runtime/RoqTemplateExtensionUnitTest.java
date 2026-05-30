package io.quarkiverse.roq.frontmatter.runtime;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import io.vertx.core.json.JsonArray;

/**
 * Pure unit tests (no Quarkus runtime).
 * <p>
 * Features tested: RoqTemplateExtension static utility methods — word counting,
 * HTML stripping, word limiting, slugification, MIME type detection,
 * and JsonArray-to-strings conversion.
 */
@DisplayName("Roq FrontMatter - Template extension utility methods")
public class RoqTemplateExtensionUnitTest {

    @Test
    @DisplayName("numberOfWords counts words correctly")
    public void testNumberOfWords() {
        assertEquals(3L, RoqTemplateExtension.numberOfWords("hello world foo"));
    }

    @Test
    @DisplayName("numberOfWords returns 0 for empty string")
    public void testNumberOfWordsEmpty() {
        assertEquals(0L, RoqTemplateExtension.numberOfWords(""));
    }

    @Test
    @DisplayName("stripHtml removes all HTML tags")
    public void testStripHtml() {
        assertEquals("Hello World", RoqTemplateExtension.stripHtml("<div>Hello <b>World</b></div>"));
    }

    @Test
    @DisplayName("stripHtml returns null for null input")
    public void testStripHtmlNull() {
        assertNull(RoqTemplateExtension.stripHtml(null));
    }

    @Test
    @DisplayName("wordLimit truncates with ellipsis")
    public void testWordLimitTruncates() {
        assertEquals("one two...", RoqTemplateExtension.wordLimit("one two three four", 2));
    }

    @Test
    @DisplayName("wordLimit does not truncate short text")
    public void testWordLimitNoTruncation() {
        assertEquals("one two", RoqTemplateExtension.wordLimit("one two", 5));
    }

    @Test
    @DisplayName("slugify converts to URL-safe format")
    public void testSlugify() {
        assertEquals("Hello-World", RoqTemplateExtension.slugify("Hello World!"));
    }

    @Test
    @DisplayName("slugify with lowerCase=true lowercases the result")
    public void testSlugifyLowerCase() {
        assertEquals("hello-world", RoqTemplateExtension.slugify("Hello World!", true));
    }

    @Test
    @DisplayName("slugify with lowerCase=false preserves case")
    public void testSlugifyPreserveCase() {
        assertEquals("Hello-World", RoqTemplateExtension.slugify("Hello World!", false));
    }

    @Test
    @DisplayName("slugify with preservePath=true preserves slashes")
    public void testSlugifyPreservePath() {
        assertEquals("Hello/World", RoqTemplateExtension.slugify("Hello/World", false, true));
    }

    @Test
    @DisplayName("slugify with preservePath=false replaces slashes with hyphens")
    public void testSlugifyReplacePath() {
        assertEquals("Hello-World", RoqTemplateExtension.slugify("Hello/World", false, false));
    }

    @Test
    @DisplayName("slugify with lowerCase and preservePath combines both options")
    public void testSlugifyLowerAndPreservePath() {
        assertEquals("hello/world", RoqTemplateExtension.slugify("Hello/World", true, true));
    }

    @Test
    @DisplayName("mimeType resolves PDF")
    public void testMimeTypePdf() {
        assertEquals("application/pdf", RoqTemplateExtension.mimeType("doc.pdf"));
    }

    @Test
    @DisplayName("mimeType resolves PNG")
    public void testMimeTypePng() {
        assertEquals("image/png", RoqTemplateExtension.mimeType("img.png"));
    }

    @Test
    @DisplayName("asStrings handles JsonArray")
    public void testAsStringsJsonArray() {
        JsonArray array = new JsonArray(List.of("a", "b"));
        assertEquals(List.of("a", "b"), RoqTemplateExtension.asStrings(array));
    }

    @Test
    @DisplayName("asStrings handles comma-separated string")
    public void testAsStringsCommaSeparated() {
        List<String> result = RoqTemplateExtension.asStrings("java, quarkus, roq");
        assertEquals(List.of("java", "quarkus", "roq"), result);
    }

    @Test
    @DisplayName("asStrings returns empty list for unsupported type")
    public void testAsStringsUnsupportedType() {
        assertEquals(List.of(), RoqTemplateExtension.asStrings(42));
    }
}
