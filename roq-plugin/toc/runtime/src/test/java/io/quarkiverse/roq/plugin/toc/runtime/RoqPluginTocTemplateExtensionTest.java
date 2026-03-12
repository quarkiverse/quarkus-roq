package io.quarkiverse.roq.plugin.toc.runtime;

import static io.quarkiverse.roq.plugin.toc.runtime.RoqPluginTocTemplateExtension.buildHierarchy;
import static io.quarkiverse.roq.plugin.toc.runtime.RoqPluginTocTemplateExtension.extractHeadings;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.junit.jupiter.api.Test;

import io.quarkiverse.roq.plugin.toc.runtime.RoqPluginTocTemplateExtension.HeadingInfo;

public class RoqPluginTocTemplateExtensionTest {

    @Test
    void shouldExtractMarkdownHeadings() {
        String html = """
                <h2 id="intro">Introduction</h2>
                <p>Some text.</p>
                <h3 id="details">Details</h3>
                <p>More text.</p>
                """;

        Document doc = Jsoup.parse(html);
        List<HeadingInfo> headings = extractHeadings(doc);

        assertThat(headings).hasSize(2);
        assertThat(headings.get(0)).isEqualTo(new HeadingInfo("intro", "Introduction", 2));
        assertThat(headings.get(1)).isEqualTo(new HeadingInfo("details", "Details", 3));
    }

    @Test
    void shouldExtractAsciidocSections() {
        String html = """
                <div class="sect1">
                  <h2 id="chapter">Chapter One</h2>
                  <div class="sectionbody">
                    <p>Content.</p>
                    <div class="sect2">
                      <h3 id="subsection">Subsection</h3>
                      <div class="sectionbody">
                        <p>Sub content.</p>
                      </div>
                    </div>
                  </div>
                </div>
                """;

        Document doc = Jsoup.parse(html);
        List<HeadingInfo> headings = extractHeadings(doc);

        assertThat(headings).hasSize(2);
        assertThat(headings.get(0)).isEqualTo(new HeadingInfo("chapter", "Chapter One", 2));
        assertThat(headings.get(1)).isEqualTo(new HeadingInfo("subsection", "Subsection", 3));
    }

    @Test
    void shouldUseIdFromSectionWhenHeadingHasNone() {
        String html = """
                <div class="sect1" id="from-section">
                  <h2>No ID on heading</h2>
                  <div class="sectionbody">
                    <p>Content.</p>
                  </div>
                </div>
                """;

        Document doc = Jsoup.parse(html);
        List<HeadingInfo> headings = extractHeadings(doc);

        assertThat(headings).hasSize(1);
        assertThat(headings.get(0).id()).isEqualTo("from-section");
    }

    @Test
    void shouldSkipAsciidocSectionWithNoIdAnywhere() {
        String html = """
                <div class="sect1">
                  <h2>No ID anywhere</h2>
                  <div class="sectionbody">
                    <p>Content.</p>
                  </div>
                </div>
                """;

        Document doc = Jsoup.parse(html);
        List<HeadingInfo> headings = extractHeadings(doc);

        assertThat(headings).isEmpty();
    }

    @Test
    void shouldSkipMarkdownHeadingsWithoutId() {
        String html = """
                <h2>No ID</h2>
                <p>Text.</p>
                """;

        Document doc = Jsoup.parse(html);
        List<HeadingInfo> headings = extractHeadings(doc);

        assertThat(headings).isEmpty();
    }

    @Test
    void shouldNotDuplicateAsciidocHeadingsInMarkdownPass() {
        String html = """
                <div class="sect1">
                  <h2 id="asciidoc-heading">AsciiDoc Heading</h2>
                  <div class="sectionbody">
                    <p>Content.</p>
                  </div>
                </div>
                """;

        Document doc = Jsoup.parse(html);
        List<HeadingInfo> headings = extractHeadings(doc);

        // The h2[id] would also match the markdown selector, but the sect1 parent filter should skip it
        assertThat(headings).hasSize(1);
    }

    @Test
    void shouldNotSkipHeadingsInsideNonSectClassParent() {
        // A heading inside a "section" or "sector" class should NOT be skipped —
        // only "sect1" through "sect6" are AsciiDoc section wrappers.
        String html = """
                <div class="section">
                  <h2 id="markdown-heading">Markdown Heading</h2>
                  <p>Content.</p>
                </div>
                """;

        Document doc = Jsoup.parse(html);
        List<HeadingInfo> headings = extractHeadings(doc);

        assertThat(headings).hasSize(1);
        assertThat(headings.get(0)).isEqualTo(new HeadingInfo("markdown-heading", "Markdown Heading", 2));
    }

    @Test
    void shouldReturnEmptyForEmptyDocument() {
        Document doc = Jsoup.parse("");
        List<HeadingInfo> headings = extractHeadings(doc);
        assertThat(headings).isEmpty();
    }

    @Test
    void shouldReturnEmptyForNoHeadings() {
        String html = "<p>Just a paragraph.</p>";
        Document doc = Jsoup.parse(html);
        List<HeadingInfo> headings = extractHeadings(doc);
        assertThat(headings).isEmpty();
    }

    // --- Hierarchy building tests ---

    @Test
    void shouldBuildFlatHierarchyForSameLevel() {
        List<HeadingInfo> headings = List.of(
                new HeadingInfo("a", "A", 2),
                new HeadingInfo("b", "B", 2),
                new HeadingInfo("c", "C", 2));

        List<TocEntry> roots = buildHierarchy(headings);

        assertThat(roots).hasSize(3);
        assertThat(roots.get(0).children()).isEmpty();
        assertThat(roots.get(1).children()).isEmpty();
        assertThat(roots.get(2).children()).isEmpty();
    }

    @Test
    void shouldNestChildrenUnderParent() {
        List<HeadingInfo> headings = List.of(
                new HeadingInfo("parent", "Parent", 2),
                new HeadingInfo("child1", "Child 1", 3),
                new HeadingInfo("child2", "Child 2", 3));

        List<TocEntry> roots = buildHierarchy(headings);

        assertThat(roots).hasSize(1);
        TocEntry parent = roots.get(0);
        assertThat(parent.id()).isEqualTo("parent");
        assertThat(parent.children()).hasSize(2);
        assertThat(parent.children().get(0).id()).isEqualTo("child1");
        assertThat(parent.children().get(1).id()).isEqualTo("child2");
    }

    @Test
    void shouldBuildDeeplyNestedHierarchy() {
        List<HeadingInfo> headings = List.of(
                new HeadingInfo("h2", "H2", 2),
                new HeadingInfo("h3", "H3", 3),
                new HeadingInfo("h4", "H4", 4));

        List<TocEntry> roots = buildHierarchy(headings);

        assertThat(roots).hasSize(1);
        assertThat(roots.get(0).children()).hasSize(1);
        assertThat(roots.get(0).children().get(0).children()).hasSize(1);
        assertThat(roots.get(0).children().get(0).children().get(0).id()).isEqualTo("h4");
    }

    @Test
    void shouldHandleSkippedLevels() {
        // h2 -> h4 (skipping h3) should still nest h4 under h2
        List<HeadingInfo> headings = List.of(
                new HeadingInfo("h2", "H2", 2),
                new HeadingInfo("h4", "H4", 4));

        List<TocEntry> roots = buildHierarchy(headings);

        assertThat(roots).hasSize(1);
        assertThat(roots.get(0).children()).hasSize(1);
        assertThat(roots.get(0).children().get(0).id()).isEqualTo("h4");
    }

    @Test
    void shouldPopBackToCorrectParent() {
        List<HeadingInfo> headings = List.of(
                new HeadingInfo("h2a", "H2 A", 2),
                new HeadingInfo("h3", "H3", 3),
                new HeadingInfo("h2b", "H2 B", 2));

        List<TocEntry> roots = buildHierarchy(headings);

        assertThat(roots).hasSize(2);
        assertThat(roots.get(0).id()).isEqualTo("h2a");
        assertThat(roots.get(0).children()).hasSize(1);
        assertThat(roots.get(1).id()).isEqualTo("h2b");
        assertThat(roots.get(1).children()).isEmpty();
    }

    @Test
    void shouldBuildHierarchyForEmptyList() {
        List<TocEntry> roots = buildHierarchy(List.of());
        assertThat(roots).isEmpty();
    }

    // --- HTML rendering tests ---

    @Test
    void shouldRenderTocHtml() {
        List<HeadingInfo> headings = List.of(
                new HeadingInfo("intro", "Introduction", 2),
                new HeadingInfo("details", "Details", 3));

        List<TocEntry> entries = buildHierarchy(headings);
        StringBuilder sb = new StringBuilder();
        sb.append("<nav class=\"roq-toc\">\n");
        renderEntriesViaReflection(sb, entries);
        sb.append("</nav>\n");

        String html = sb.toString();
        assertThat(html).contains("<a href=\"#intro\">Introduction</a>");
        assertThat(html).contains("<a href=\"#details\">Details</a>");
        assertThat(html).contains("<nav class=\"roq-toc\">");
    }

    @Test
    void shouldEscapeSpecialCharactersInTocHtml() {
        List<HeadingInfo> headings = List.of(
                new HeadingInfo("q&a", "Q&A: <Questions> \"Answered\"", 2));

        List<TocEntry> entries = buildHierarchy(headings);
        StringBuilder sb = new StringBuilder();
        renderEntriesViaReflection(sb, entries);

        String html = sb.toString();
        assertThat(html).contains("&amp;A:");
        assertThat(html).contains("&lt;Questions&gt;");
        assertThat(html).contains("&quot;Answered&quot;");
        assertThat(html).contains("q&amp;a");
        assertThat(html).doesNotContain("<Questions>");
    }

    /**
     * Helper to call the private renderEntries method for testing.
     */
    private void renderEntriesViaReflection(StringBuilder sb, List<TocEntry> entries) {
        try {
            var method = RoqPluginTocTemplateExtension.class.getDeclaredMethod("renderEntries",
                    StringBuilder.class, List.class);
            method.setAccessible(true);
            method.invoke(null, sb, entries);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
