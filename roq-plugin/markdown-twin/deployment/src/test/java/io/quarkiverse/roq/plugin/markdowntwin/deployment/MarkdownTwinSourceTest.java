package io.quarkiverse.roq.plugin.markdowntwin.deployment;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import io.quarkiverse.roq.frontmatter.deployment.items.scan.RoqFrontMatterQuteMarkupBuildItem.QuteMarkupSection;

/**
 * What a Markdown twin keeps of the Markdown source: the body of the generated template, with the front matter title as
 * its heading and without Qute's escape delimiters.
 */
class MarkdownTwinSourceTest {

    private final RoqPluginMarkdownTwinProcessor processor = new RoqPluginMarkdownTwinProcessor();
    private final QuteMarkupSection section = new QuteMarkupSection("{#markdown}", "{/markdown}");

    private String extract(String generatedTemplate) {
        return processor.extractMarkdownSource(generatedTemplate, section);
    }

    @Test
    void titleComesFromTheFrontMatterUnlessTheSourceHasOne() {
        assertThat(processor.withTitle("Hello folks,\n", "Welcome to Roq!")).isEqualTo("# Welcome to Roq!\n\nHello folks,\n");
        assertThat(processor.withTitle("\n\nHello folks,\n", "Welcome to Roq!"))
                .isEqualTo("# Welcome to Roq!\n\nHello folks,\n");
        assertThat(processor.withTitle("# Own heading\n\nText.\n", "Welcome to Roq!")).isEqualTo("# Own heading\n\nText.\n");
        // A heading after a first line, or underlined with =, counts too.
        assertThat(processor.withTitle("[View source](x)\n\n# Heading 1\nText.\n", "Markdown Markup Test"))
                .isEqualTo("[View source](x)\n\n# Heading 1\nText.\n");
        assertThat(processor.withTitle("Heading 1\n=========\nText.\n", "Title")).isEqualTo("Heading 1\n=========\nText.\n");
        assertThat(processor.withTitle("Text.\n", null)).isEqualTo("Text.\n");
    }

    @Test
    void aCommentInAFencedCodeBlockIsNotTheTitle() {
        assertThat(processor.withTitle("Text.\n\n```yaml\n# content/posts/a.md\n---\n```\n", "Aliases"))
                .isEqualTo("# Aliases\n\nText.\n\n```yaml\n# content/posts/a.md\n---\n```\n");
        // A fence closes only on the same character, at least as long; a heading after the block still counts.
        assertThat(processor.withTitle("~~~~\n# a\n```\n# b\n~~~~\n# Real\n", "Title"))
                .isEqualTo("~~~~\n# a\n```\n# b\n~~~~\n# Real\n");
        assertThat(processor.withTitle("````\n# a\n```\n# b\n````\nText.\n", "Title"))
                .isEqualTo("# Title\n\n````\n# a\n```\n# b\n````\nText.\n");
        // Backticks followed by text holding a backtick are inline code, not a fence.
        assertThat(processor.withTitle("```js` is inline code\n\n# Real\n", "Title"))
                .isEqualTo("```js` is inline code\n\n# Real\n");
    }

    @Test
    void aHeadingIndentedByUpToThreeSpacesCountsAndCodeDoesNot() {
        assertThat(processor.withTitle("   # Indented heading\n", "Title")).isEqualTo("   # Indented heading\n");
        assertThat(processor.withTitle("#\tTabbed heading\n", "Title")).isEqualTo("#\tTabbed heading\n");
        assertThat(processor.withTitle("Heading 1\n=== \n", "Title")).isEqualTo("Heading 1\n=== \n");
        // Four spaces, or a tab, make an indented code block.
        assertThat(processor.withTitle("    # code\n", "Title")).isEqualTo("# Title\n\n    # code\n");
        assertThat(processor.withTitle("\t# TODO\n\nText.\n", "Title")).isEqualTo("# Title\n\n\t# TODO\n\nText.\n");
        assertThat(processor.withTitle("  \t# TODO\n", "Title")).isEqualTo("# Title\n\n  \t# TODO\n");
    }

    @Test
    void aSetextUnderlineCountsOnlyAfterAParagraphLine() {
        assertThat(processor.withTitle("- item\n=\n", "Title")).isEqualTo("# Title\n\n- item\n=\n");
        assertThat(processor.withTitle("1. item\n===\n", "Title")).isEqualTo("# Title\n\n1. item\n===\n");
        assertThat(processor.withTitle("    code\n===\n", "Title")).isEqualTo("# Title\n\n    code\n===\n");
        assertThat(processor.withTitle("> quote\n===\n", "Title")).isEqualTo("# Title\n\n> quote\n===\n");
        assertThat(processor.withTitle("-dash word\n===\n", "Title")).isEqualTo("-dash word\n===\n");
        assertThat(processor.withTitle("2025 was a year\n===\n", "Title")).isEqualTo("2025 was a year\n===\n");
    }

    @Test
    void crlfLineEndingsBecomeLineFeeds() {
        assertThat(processor.withTitle(
                extract("{#markdown}\n{|Heading 1\r\n=========\r\nText.\r\n|}\n{/markdown}"),
                "Title")).isEqualTo("Heading 1\n=========\nText.\n");
        assertThat(processor.withTitle(extract("{#markdown}\n{|\r\nText.\r\n|}\n{/markdown}"), "Title"))
                .isEqualTo("# Title\n\nText.\n");
    }

    @Test
    void bodyKeepsItsIndentationAndEndsWithOneLineFeed() {
        // The generated template adds a line feed after {#markdown} and before {/markdown}; only those go.
        assertThat(extract("{#markdown}\n{|    indented code\n\ntext|}\n{/markdown}"))
                .isEqualTo("    indented code\n\ntext\n");
        // A Markdown page is a Qute template unless it sets escape: true; its expressions stay as written.
        assertThat(extract("{#markdown}\n{page.title}\n\ntext\n\n{/markdown}")).isEqualTo("{page.title}\n\ntext\n");
        assertThat(extract("{#asciidoc}\n{|text|}\n{/asciidoc}")).isNull();
        assertThat(extract(null)).isNull();
        assertThat(processor.extractMarkdownSource("{#markdown}\n{|text|}\n{/markdown}", null)).isNull();
        // The section's name comes from the markup build item, so a renamed section is still found, with or without
        // the line feeds around the body.
        assertThat(processor.extractMarkdownSource("{#md}{|text|}{/md}", new QuteMarkupSection("{#md}", "{/md}")))
                .isEqualTo("text\n");
    }

    @Test
    void quteEscapeDelimitersAreRemovedAsOnThePage() {
        assertThat(extract("{#markdown}\n{|Add {|`{page.tocHtml}`|} to a layout:\n\n{|\n```html\n"
                + "{#include fm/sitemap.xml /}\n```\n|}\n|}\n{/markdown}"))
                .isEqualTo("Add `{page.tocHtml}` to a layout:\n\n\n```html\n{#include fm/sitemap.xml /}\n```\n\n");
        assertThat(processor.unescape("an unclosed {| stays")).isEqualTo("an unclosed {| stays");
        assertThat(processor.unescape("nothing to do")).isEqualTo("nothing to do");
    }
}
