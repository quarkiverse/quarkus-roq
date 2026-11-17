package io.quarkiverse.roq.plugin.markdown.deployment;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;

import org.junit.jupiter.api.Test;

import io.quarkiverse.roq.frontmatter.deployment.items.scan.RoqFrontMatterQuteMarkupBuildItem;
import io.quarkiverse.roq.frontmatter.deployment.items.scan.RoqFrontMatterQuteMarkupBuildItem.QuteMarkupSection;
import io.quarkiverse.roq.frontmatter.deployment.items.scan.RoqMarkdownConverterBuildItem.MarkdownConversion;
import io.quarkiverse.roq.frontmatter.deployment.util.RoqFrontMatterTemplateUtils;
import io.vertx.core.json.JsonObject;

/**
 * What the Markdown of a Markdown page keeps of the source: the body of the generated template, with the front matter
 * title as its heading, without the wrapper Roq puts around an escaped page, and without the author's Qute escape
 * delimiters when the page is a Qute template.
 */
class MarkdownSourceConverterTest {

    private final QuteMarkupSection section = new QuteMarkupSection("{#markdown}", "{/markdown}");
    private final MarkdownSourceConverter converter = new MarkdownSourceConverter(section);

    private String extract(String generatedTemplate) {
        return converter.extractMarkdownSource(generatedTemplate);
    }

    /** The template Roq generates for a Markdown page, so the test sees the escape wrapper Roq really writes. */
    private String template(String source, boolean escaped) {
        return RoqFrontMatterTemplateUtils.applyContentTransforms(source, escaped,
                new RoqFrontMatterQuteMarkupBuildItem("markdown", file -> true, section), "layouts/page", true)
                .generatedTemplate();
    }

    @Test
    void theBodyOfTheGeneratedTemplateWithTheTitle() {
        // The source is not read from disk: a page from a dependency jar has none, and the scan already decoded it.
        final JsonObject data = new JsonObject().put("title", "Welcome to Roq!");
        final String template = template("Hello folks,\n", true);
        assertThat(converter.convert(new MarkdownConversion(Path.of("absent/welcome.md"), template, null, null, null, data,
                null))).hasValue("# Welcome to Roq!\n\nHello folks,\n");
        // Without a title in the data the body stands alone.
        assertThat(converter.convert(new MarkdownConversion(null, template, null, null, null, new JsonObject(), null)))
                .hasValue("Hello folks,\n");
        // No markdown section, no Markdown.
        assertThat(converter.convert(new MarkdownConversion(null, "{#asciidoc}\nText.\n{/asciidoc}", null, null, null, data,
                null))).isEmpty();
        assertThat(converter.convert(new MarkdownConversion(null, null, null, null, null, data, null))).isEmpty();
    }

    @Test
    void titleComesFromTheFrontMatterUnlessTheSourceHasOne() {
        assertThat(converter.withTitle("Hello folks,\n", "Welcome to Roq!")).isEqualTo("# Welcome to Roq!\n\nHello folks,\n");
        assertThat(converter.withTitle("\n\nHello folks,\n", "Welcome to Roq!"))
                .isEqualTo("# Welcome to Roq!\n\nHello folks,\n");
        assertThat(converter.withTitle("# Own heading\n\nText.\n", "Welcome to Roq!")).isEqualTo("# Own heading\n\nText.\n");
        // A heading after a first line, or underlined with =, counts too.
        assertThat(converter.withTitle("[View source](x)\n\n# Heading 1\nText.\n", "Markdown Markup Test"))
                .isEqualTo("[View source](x)\n\n# Heading 1\nText.\n");
        assertThat(converter.withTitle("Heading 1\n=========\nText.\n", "Title")).isEqualTo("Heading 1\n=========\nText.\n");
        assertThat(converter.withTitle("Text.\n", null)).isEqualTo("Text.\n");
    }

    @Test
    void aCommentInAFencedCodeBlockIsNotTheTitle() {
        assertThat(converter.withTitle("Text.\n\n```yaml\n# content/posts/a.md\n---\n```\n", "Aliases"))
                .isEqualTo("# Aliases\n\nText.\n\n```yaml\n# content/posts/a.md\n---\n```\n");
        // A fence closes only on the same character, at least as long; a heading after the block still counts.
        assertThat(converter.withTitle("~~~~\n# a\n```\n# b\n~~~~\n# Real\n", "Title"))
                .isEqualTo("~~~~\n# a\n```\n# b\n~~~~\n# Real\n");
        assertThat(converter.withTitle("````\n# a\n```\n# b\n````\nText.\n", "Title"))
                .isEqualTo("# Title\n\n````\n# a\n```\n# b\n````\nText.\n");
        // Backticks followed by text holding a backtick are inline code, not a fence.
        assertThat(converter.withTitle("```js` is inline code\n\n# Real\n", "Title"))
                .isEqualTo("```js` is inline code\n\n# Real\n");
    }

    @Test
    void aHeadingIndentedByUpToThreeSpacesCountsAndCodeDoesNot() {
        assertThat(converter.withTitle("   # Indented heading\n", "Title")).isEqualTo("   # Indented heading\n");
        assertThat(converter.withTitle("#\tTabbed heading\n", "Title")).isEqualTo("#\tTabbed heading\n");
        assertThat(converter.withTitle("Heading 1\n=== \n", "Title")).isEqualTo("Heading 1\n=== \n");
        // Four spaces, or a tab, make an indented code block.
        assertThat(converter.withTitle("    # code\n", "Title")).isEqualTo("# Title\n\n    # code\n");
        assertThat(converter.withTitle("\t# TODO\n\nText.\n", "Title")).isEqualTo("# Title\n\n\t# TODO\n\nText.\n");
        assertThat(converter.withTitle("  \t# TODO\n", "Title")).isEqualTo("# Title\n\n  \t# TODO\n");
    }

    @Test
    void aSetextUnderlineCountsOnlyAfterAParagraphLine() {
        assertThat(converter.withTitle("- item\n=\n", "Title")).isEqualTo("# Title\n\n- item\n=\n");
        assertThat(converter.withTitle("1. item\n===\n", "Title")).isEqualTo("# Title\n\n1. item\n===\n");
        assertThat(converter.withTitle("    code\n===\n", "Title")).isEqualTo("# Title\n\n    code\n===\n");
        assertThat(converter.withTitle("> quote\n===\n", "Title")).isEqualTo("# Title\n\n> quote\n===\n");
        assertThat(converter.withTitle("-dash word\n===\n", "Title")).isEqualTo("-dash word\n===\n");
        assertThat(converter.withTitle("2025 was a year\n===\n", "Title")).isEqualTo("2025 was a year\n===\n");
    }

    @Test
    void crlfLineEndingsBecomeLineFeeds() {
        assertThat(converter.withTitle(extract(template("Heading 1\r\n=========\r\nText.\r\n", true)), "Title"))
                .isEqualTo("Heading 1\n=========\nText.\n");
        assertThat(converter.withTitle(extract(template("\r\nText.\r\n", true)), "Title"))
                .isEqualTo("# Title\n\nText.\n");
    }

    @Test
    void bodyKeepsItsIndentationAndEndsWithOneLineFeed() {
        // The generated template adds a line feed after {#markdown} and before {/markdown}; only those go.
        assertThat(extract(template("    indented code\n\ntext", true))).isEqualTo("    indented code\n\ntext\n");
        // A Markdown page is a Qute template unless it sets escape: true; its expressions stay as written.
        assertThat(extract("{#markdown}\n{page.title}\n\ntext\n\n{/markdown}")).isEqualTo("{page.title}\n\ntext\n");
        assertThat(extract("{#asciidoc}\n{|text|}\n{/asciidoc}")).isNull();
        assertThat(extract("no section")).isNull();
        assertThat(extract(null)).isNull();
        assertThat(new MarkdownSourceConverter(null).extractMarkdownSource("{#markdown}\n{|text|}\n{/markdown}")).isNull();
        // The section comes from the markup this plugin registers, so a renamed section is still found, with or without
        // the line feeds around the body.
        assertThat(new MarkdownSourceConverter(new QuteMarkupSection("{#md}", "{/md}")).extractMarkdownSource(
                "{#md}{|text|}{/md}")).isEqualTo("text\n");
    }

    @Test
    void quteEscapeDelimitersAreRemovedAsOnThePage() {
        final String source = "Add {|`{page.tocHtml}`|} to a layout:\n\n{|\n```html\n{#include fm/sitemap.xml /}\n```\n|}\n";
        assertThat(extract(template(source, false)))
                .isEqualTo("Add `{page.tocHtml}` to a layout:\n\n\n```html\n{#include fm/sitemap.xml /}\n```\n\n");
        // A body that starts with {| and ends with |} is still the author's, not a wrapper.
        assertThat(extract(template("{|A|} middle {|B|}", false))).isEqualTo("A middle B\n");
        assertThat(converter.unescape("an unclosed {| stays")).isEqualTo("an unclosed {| stays");
        assertThat(converter.unescape("nothing to do")).isEqualTo("nothing to do");
        // Qute matches the pipe count, so a {|| ... ||} section may hold |} as text; what Qute renders, the twin keeps.
        assertThat(converter.unescape("{||a |} b||}")).isEqualTo("a |} b");
        assertThat(converter.unescape("x {||||||y {|z|} w||||||} v")).isEqualTo("x y {|z|} w v");
        assertThat(converter.unescape("{||a|}")).isEqualTo("{||a|}");
    }

    @Test
    void anEscapedPageKeepsItsTextAndItsOwnHeading() {
        // An escaped page shows its text as written, {| ... |} included, so its twin keeps it too.
        final String source = "Add {|`{page.tocHtml}`|} to a layout.\n";
        assertThat(extract(template(source, true))).isEqualTo(source);
        assertThat(extract(template("Qute escapes look like `{|` and `|}`, e.g. {| x |}\n", true)))
                .isEqualTo("Qute escapes look like `{|` and `|}`, e.g. {| x |}\n");
        assertThat(converter.withTitle(extract(template("Use {name} in a template.\n", true)), "Title"))
                .isEqualTo("# Title\n\nUse {name} in a template.\n");
        assertThat(converter.withTitle(extract(template("# Own heading\n\nText.\n", true)), "Title"))
                .isEqualTo("# Own heading\n\nText.\n");
        // Pipes at the edges of the body, or a |} inside it, do not move the wrapper, which has a fixed width.
        assertThat(extract(template("|a| and |}\n", true))).isEqualTo("|a| and |}\n");
        assertThat(extract(template("|", true))).isEqualTo("|\n");
    }
}
