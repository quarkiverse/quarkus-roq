package io.quarkus.tools.migration.asciidoc;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

public class AsciiDocLinkToXrefConverterTest {

    private final AsciiDocLinkToXrefConverter converter = new AsciiDocLinkToXrefConverter();

    @Test
    public void testConvertBareLink() {
        String input = "See the link:getting-started-testing for details.";
        String expected = "See the xref:getting-started-testing.adoc for details.";
        assertEquals(expected, converter.convertLinks(input));
    }

    @Test
    public void testConvertLinkWithAnchor() {
        String input = "See link:getting-started-testing#quarkus-integration-test for details.";
        String expected = "See xref:getting-started-testing.adoc#quarkus-integration-test for details.";
        assertEquals(expected, converter.convertLinks(input));
    }

    @Test
    public void testConvertLinkWithText() {
        String input = "See link:getting-started-testing[Testing Guide] for details.";
        String expected = "See xref:getting-started-testing.adoc[Testing Guide] for details.";
        assertEquals(expected, converter.convertLinks(input));
    }

    @Test
    public void testConvertLinkWithAnchorAndText() {
        String input = "Details in the link:getting-started-testing#quarkus-integration-test[Testing Guide].";
        String expected = "Details in the xref:getting-started-testing.adoc#quarkus-integration-test[Testing Guide].";
        assertEquals(expected, converter.convertLinks(input));
    }

    @Test
    public void testPreserveHttpsUrls() {
        String input = "See link:https://example.com[example site].";
        String expected = "See link:https://example.com[example site].";
        assertEquals(expected, converter.convertLinks(input));
    }

    @Test
    public void testPreserveHttpUrls() {
        String input = "See link:http://test.com/path[test].";
        String expected = "See link:http://test.com/path[test].";
        assertEquals(expected, converter.convertLinks(input));
    }

    @Test
    public void testMixedLinks() {
        String input = "This guide explains testing. More details in the link:getting-started-testing#quarkus-integration-test[Testing Guide].\n\n"
                +
                "See also:\n" +
                "- link:building-native-image for native builds\n" +
                "- link:building-native-image#build-modes[different build modes]\n" +
                "- External link: link:https://example.com[example]\n" +
                "- Another URL: link:http://test.com/path[test]";

        String expected = "This guide explains testing. More details in the xref:getting-started-testing.adoc#quarkus-integration-test[Testing Guide].\n\n"
                +
                "See also:\n" +
                "- xref:building-native-image.adoc for native builds\n" +
                "- xref:building-native-image.adoc#build-modes[different build modes]\n" +
                "- External link: link:https://example.com[example]\n" +
                "- Another URL: link:http://test.com/path[test]";

        assertEquals(expected, converter.convertLinks(input));
    }

    @Test
    public void testRealWorldExample() {
        // This was the actual failing case from the migration
        String input = "Producing a native executable can lead to a few issues, and so it's also a good idea to run some tests against the application running in the native file. "
                +
                "The reasoning is explained in the link:getting-started-testing#quarkus-integration-test[Testing Guide].";

        String expected = "Producing a native executable can lead to a few issues, and so it's also a good idea to run some tests against the application running in the native file. "
                +
                "The reasoning is explained in the xref:getting-started-testing.adoc#quarkus-integration-test[Testing Guide].";

        assertEquals(expected, converter.convertLinks(input));
    }

    @Test
    public void testDoesNotConvertPathsWithSlashes() {
        // Static files or paths with / should not be converted
        String input = "Download link:assets/mallocstacks.py[mallocstacks.py] script.";
        String expected = "Download link:assets/mallocstacks.py[mallocstacks.py] script.";
        assertEquals(expected, converter.convertLinks(input));
    }

    @Test
    public void testDoesNotConvertFilesWithExtensions() {
        // Files that already have extensions should not be converted
        String input = "See link:example.pdf[PDF guide] for details.";
        String expected = "See link:example.pdf[PDF guide] for details.";
        assertEquals(expected, converter.convertLinks(input));
    }

    @Test
    public void testDoesNotConvertRelativePaths() {
        // Relative paths with ./ should not be converted
        String input = "Check link:./other-guide[other guide] here.";
        String expected = "Check link:./other-guide[other guide] here.";
        assertEquals(expected, converter.convertLinks(input));
    }
}
