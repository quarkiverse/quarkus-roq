package io.quarkiverse.roq.frontmatter.deployment.utils;

import static io.quarkiverse.roq.frontmatter.deployment.utils.AsciidocHeaderParser.parseHeader;
import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class AsciidocHeaderParserTest {

    @Test
    void testBasicTitleAuthorAttributes() throws Exception {
        String doc = """
                = My Title
                John Doe
                :attr1: value1
                :attr2: value2

                == Section 1
                Content...
                """;

        var header = parseHeader(doc);

        assertEquals("My Title", header.title());
        assertEquals("John Doe", header.author());
        assertEquals(2, header.attributes().size());
        assertEquals("value1", header.attributes().get("attr1"));
        assertEquals("value2", header.attributes().get("attr2"));
    }

    @Test
    void testNoAuthor() throws Exception {
        String doc = """
                = My Title
                :foo: bar
                """;

        var header = parseHeader(doc);

        assertEquals("My Title", header.title());
        assertNull(header.author());
        assertEquals("bar", header.attributes().get("foo"));
    }

    @Test
    void testNoTitle() throws Exception {
        String doc = """
                John Doe
                :foo: bar
                """;

        var header = parseHeader(doc);

        assertNull(header.title());
        assertNull(header.author());
        assertTrue(header.attributes().isEmpty());
    }

    @Test
    void testBooleanAttribute() throws Exception {
        String doc = """
                = Title
                Author Name
                :flag:
                :nono: false
                :empty-value:
                """;

        var header = parseHeader(doc);
        assertFalse((Boolean) header.attributes().get("nono"));
        assertTrue((Boolean) header.attributes().get("flag"));
        assertTrue((Boolean) header.attributes().get("empty-value"));
    }

    @Test
    void testUnsetAttribute() throws Exception {
        String doc = """
                = Title
                Author Name
                :unset-attr: foo
                :!unset-attr:
                """;

        var header = parseHeader(doc);

        assertTrue(header.attributes().containsKey("unset-attr"));
        assertNull(header.attributes().get("unset-attr"));
    }

    @Test
    void testAttributesWithQuotes() throws Exception {
        String doc = """
                = Title
                Author Name
                :quoted-attr: "a quoted value"
                :single-quote: 'single quoted'
                """;

        var header = parseHeader(doc);

        assertEquals("a quoted value", header.attributes().get("quoted-attr"));
        assertEquals("single quoted", header.attributes().get("single-quote"));
    }

    @Test
    void testBlockCommentsNotClosed() throws Exception {
        String doc = """
                ////
                block comment start
                more comment

                = Real Title
                John Doe

                // another comment
                :attr: val

                :another: value
                """;

        var header = parseHeader(doc);

        assertNull(header.title());
        assertNull(header.author());
        assertTrue(header.attributes().isEmpty());
    }

    @Test
    void testBlockCommentsAreIgnored() throws Exception {
        String doc = """
                ////
                block comment start
                more comment
                ////

                = Real Title
                John Doe

                // another comment
                :attr: val

                ////
                block comment start
                more comment
                ////
                :another: value
                """;

        var header = parseHeader(doc);

        assertEquals("Real Title", header.title());
        assertEquals("John Doe", header.author());
        assertEquals("val", header.attributes().get("attr"));
        assertEquals("value", header.attributes().get("another"));
    }

    @Test
    void testCommentsAreIgnored() throws Exception {
        String doc = """
                // This is a line comment
                //= Not a title

                = Real Title
                John Doe

                // another comment
                :attr: val

                ////
                block comment start
                more comment
                ////
                :another: value
                """;

        var header = parseHeader(doc);

        assertEquals("Real Title", header.title());
        assertEquals("John Doe", header.author());
        assertEquals("val", header.attributes().get("attr"));
        assertEquals("value", header.attributes().get("another"));
    }

    @Test
    void testEarlyBreakOnContentLine() throws Exception {
        String doc = """
                = Title
                Author Name
                :attr: value

                == Section 1
                Content starts here
                :attr-after: ignored
                """;

        var header = parseHeader(doc);

        assertEquals("Title", header.title());
        assertEquals("Author Name", header.author());
        assertEquals("value", header.attributes().get("attr"));
        assertFalse(header.attributes().containsKey("attr-after"));
    }

    @Test
    void testEmptyFile() throws Exception {
        var header = parseHeader("");
        assertNull(header.title());
        assertNull(header.author());
        assertTrue(header.attributes().isEmpty());
    }

    @Test
    void testOnlyCommentsAndEmptyLines() throws Exception {
        String doc = """
                //
                // Comment lines
                // Another comment
                ////

                ////
                block comment
                ////
                """;

        var header = parseHeader(doc);

        assertNull(header.title());
        assertNull(header.author());
        assertTrue(header.attributes().isEmpty());
    }

}
