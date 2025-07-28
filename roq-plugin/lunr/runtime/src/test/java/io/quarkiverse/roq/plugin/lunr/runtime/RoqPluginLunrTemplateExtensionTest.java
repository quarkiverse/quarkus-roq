package io.quarkiverse.roq.plugin.lunr.runtime;

import static io.quarkiverse.roq.plugin.lunr.runtime.RoqPluginLunrTemplateExtension.extractAnchors;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.junit.jupiter.api.Test;

import io.quarkiverse.roq.plugin.lunr.runtime.RoqPluginLunrTemplateExtension.Anchor;

public class RoqPluginLunrTemplateExtensionTest {

    @Test
    void shouldExtractSingleSect1() {
        String html = """
                    <div class="sect1">
                      <h2 id="intro">Introduction</h2>
                      <div class="sectionbody">
                        <p>Welcome to the documentation.</p>
                      </div>
                    </div>
                """;

        Document doc = Jsoup.parse(html);
        List<Anchor> anchors = extractAnchors(doc);

        assertThat(anchors).hasSize(1);
        Anchor anchor = anchors.get(0);
        assertThat(anchor.id()).isEqualTo("intro");
        assertThat(anchor.title()).isEqualTo("Introduction");
        assertThat(anchor.content()).isEqualTo("Welcome to the documentation.");
        assertThat(anchor.boost()).isEqualTo(3); // h2 => 2 + 1
    }

    @Test
    void shouldExtractMultipleSections() {
        String html = """
                    <div class="sect1">
                      <h2 id="section1">Section 1</h2>
                      <div class="sectionbody">
                        <p>First part content.</p>
                      </div>
                    </div>
                    <div class="sect2">
                      <h3 id="section2">Section 2</h3>
                      <div class="sectionbody">
                        <p>Second part content.</p>
                      </div>
                    </div>
                """;

        Document doc = Jsoup.parse(html);
        List<Anchor> anchors = extractAnchors(doc);

        assertThat(anchors).hasSize(2);

        assertThat(anchors.get(0))
                .isEqualTo(new Anchor("section1", "Section 1", "First part content.", 3));

        assertThat(anchors.get(1))
                .isEqualTo(new Anchor("section2", "Section 2", "Second part content.", 4));
    }

    @Test
    void shouldIgnoreSectionWithoutHeading() {
        String html = """
                    <div class="sect1">
                      <div class="sectionbody">
                        <p>No heading here.</p>
                      </div>
                    </div>
                """;

        Document doc = Jsoup.parse(html);
        List<Anchor> anchors = extractAnchors(doc);

        assertThat(anchors).isEmpty();
    }

    @Test
    void shouldIgnoreHeadingWithoutId() {
        String html = """
                    <div class="sect1">
                      <h2>Heading without ID</h2>
                      <div class="sectionbody">
                        <p>Still has content.</p>
                      </div>
                    </div>
                """;

        Document doc = Jsoup.parse(html);
        List<Anchor> anchors = extractAnchors(doc);

        assertThat(anchors).isEmpty();
    }

    @Test
    void shouldExtractH2WithParagraphs() {
        String html = """
                    <h2 id="intro">Introduction</h2>
                    <p>This is the first paragraph.</p>
                    <p>This is the second paragraph.</p>
                """;

        Document doc = Jsoup.parse(html);
        List<Anchor> anchors = extractAnchors(doc);

        assertThat(anchors).hasSize(1);
        Anchor a = anchors.get(0);
        assertThat(a.id()).isEqualTo("intro");
        assertThat(a.title()).isEqualTo("Introduction");
        assertThat(a.content()).isEqualTo("This is the first paragraph. This is the second paragraph.");
        assertThat(a.boost()).isEqualTo(3); // h2 = level 2 + 1
    }

    @Test
    void shouldExtractH3WithList() {
        String html = """
                    <h3 id="features">Features</h3>
                    <ul>
                      <li>Fast</li>
                      <li>Reliable</li>
                    </ul>
                """;

        Document doc = Jsoup.parse(html);
        List<Anchor> anchors = extractAnchors(doc);

        assertThat(anchors).hasSize(1);
        Anchor a = anchors.get(0);
        assertThat(a.id()).isEqualTo("features");
        assertThat(a.title()).isEqualTo("Features");
        assertThat(a.content()).isEqualTo("Fast Reliable");
        assertThat(a.boost()).isEqualTo(4); // h3 = level 3 + 1
    }

    @Test
    void shouldHandleTwoConsecutiveHeadingsSeparately() {
        String html = """
                    <h2 id="a">A</h2>
                    <p>Text A1.</p>
                    <p>Text A2.</p>
                    <h2 id="b">B</h2>
                    <p>Text B1.</p>
                """;

        Document doc = Jsoup.parse(html);
        List<Anchor> anchors = extractAnchors(doc);

        assertThat(anchors).hasSize(2);

        Anchor a = anchors.get(0);
        assertThat(a.id()).isEqualTo("a");
        assertThat(a.title()).isEqualTo("A");
        assertThat(a.content()).isEqualTo("Text A1. Text A2.");
        assertThat(a.boost()).isEqualTo(3);

        Anchor b = anchors.get(1);
        assertThat(b.id()).isEqualTo("b");
        assertThat(b.title()).isEqualTo("B");
        assertThat(b.content()).isEqualTo("Text B1.");
        assertThat(b.boost()).isEqualTo(3);
    }

    @Test
    void shouldStopMarkdownContentAtSameOrHigherLevelHeading() {
        String html = """
                    <h2 id="parent">Parent</h2>
                    <p>Parent content.</p>
                    <h4 id="child">Child</h4>
                    <p>Child content.</p>
                    <h2 id="next">Next</h2>
                    <p>Next content.</p>
                """;

        Document doc = Jsoup.parse(html);
        List<Anchor> anchors = extractAnchors(doc);

        assertThat(anchors).hasSize(3);

        assertThat(anchors.get(0).id()).isEqualTo("parent");
        assertThat(anchors.get(0).content()).isEqualTo("Parent content. Child Child content.");
        assertThat(anchors.get(0).boost()).isEqualTo(3);

        assertThat(anchors.get(1).id()).isEqualTo("child");
        assertThat(anchors.get(1).content()).isEqualTo("Child content.");
        assertThat(anchors.get(1).boost()).isEqualTo(5); // h4 = 4 + 1

        assertThat(anchors.get(2).id()).isEqualTo("next");
        assertThat(anchors.get(2).content()).isEqualTo("Next content.");
        assertThat(anchors.get(2).boost()).isEqualTo(3);
    }

    @Test
    void shouldAggregateTextFromVariousSiblings() {
        String html = """
                    <h3 id="info">Info</h3>
                    <div>Line 1</div>
                    <span>Line 2</span>
                    <p>Line 3</p>
                """;

        Document doc = Jsoup.parse(html);
        List<Anchor> anchors = extractAnchors(doc);

        assertThat(anchors).hasSize(1);
        Anchor a = anchors.get(0);
        assertThat(a.id()).isEqualTo("info");
        assertThat(a.title()).isEqualTo("Info");
        assertThat(a.content()).isEqualTo("Line 1 Line 2 Line 3");
        assertThat(a.boost()).isEqualTo(4);
    }

    @Test
    void shouldIgnoreHeadingsWithoutId() {
        String html = """
                    <h2>Untitled</h2>
                    <p>Some text</p>
                """;

        Document doc = Jsoup.parse(html);
        List<Anchor> anchors = extractAnchors(doc);

        assertThat(anchors).isEmpty();
    }

    @Test
    void shouldHandleHeadingWithNoSiblings() {
        String html = """
                    <h2 id="empty">Lonely Heading</h2>
                """;

        Document doc = Jsoup.parse(html);
        List<Anchor> anchors = extractAnchors(doc);

        assertThat(anchors).isEmpty();
    }

    @Test
    void shouldIgnoreNestedHeadingsInsideContentBlocks() {
        String html = """
                    <h2 id="main">Main</h2>
                    <div>
                      <p>Some intro</p>
                      <h3 id="sub">Sub inside div</h3>
                      <p>Sub content</p>
                    </div>
                """;

        Document doc = Jsoup.parse(html);
        List<Anchor> anchors = extractAnchors(doc);

        assertThat(anchors).hasSize(2); // Should stop at <div>
        Anchor anchor = anchors.get(0);
        assertThat(anchor.id()).isEqualTo("main");
        assertThat(anchor.title()).isEqualTo("Main");
        assertThat(anchor.content()).isEqualTo("Some intro Sub inside div Sub content");
    }

    @Test
    void shouldSkipEmptyDocument() {
        String html = "";

        Document doc = Jsoup.parse(html);
        List<Anchor> anchors = extractAnchors(doc);

        assertThat(anchors).isEmpty();
    }

    @Test
    void shouldHandleMalformedHtml() {
        String html = """
                    <h2 id="oops">Oops
                    <p>Still works?</p>
                """;

        Document doc = Jsoup.parse(html);
        List<Anchor> anchors = extractAnchors(doc);

        assertThat(anchors).isEmpty();
    }

    @Test
    void shouldStopAtSameLevelHeadingEvenWithoutContent() {
        String html = """
                    <h3 id="a">A</h3>
                    <h3 id="b">B</h3>
                    <p>Paragraph for B</p>
                """;

        Document doc = Jsoup.parse(html);
        List<Anchor> anchors = extractAnchors(doc);

        assertThat(anchors).hasSize(1);

        assertThat(anchors.get(0).id()).isEqualTo("b");
        assertThat(anchors.get(0).content()).isEqualTo("Paragraph for B");
    }

    @Test
    void shouldNotBreakOnUnsupportedHeadingLevels() {
        String html = """
                    <h7 id="deep">Too Deep</h7>
                    <p>Ignored?</p>
                """;

        Document doc = Jsoup.parse(html);
        List<Anchor> anchors = extractAnchors(doc);

        // h7 is not a valid HTML tag, but Jsoup accepts it
        assertThat(anchors).isEmpty();
    }

    @Test
    void shouldHandleMixedContentTypes() {
        String html = """
                    <h2 id="mixed">Mixed</h2>
                    <p>Text</p>
                    <pre><code>Code block</code></pre>
                    <blockquote>Quote</blockquote>
                """;

        Document doc = Jsoup.parse(html);
        List<Anchor> anchors = extractAnchors(doc);

        assertThat(anchors).hasSize(1);
        assertThat(anchors.get(0).content()).isEqualTo("Text Code block Quote");
    }
}
