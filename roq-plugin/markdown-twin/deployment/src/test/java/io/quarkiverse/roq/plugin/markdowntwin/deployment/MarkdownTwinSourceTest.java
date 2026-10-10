package io.quarkiverse.roq.plugin.markdowntwin.deployment;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/**
 * What a Markdown twin keeps of the Markdown source: the body of the generated template, with the front matter title as
 * its heading and without Qute's escape delimiters.
 */
class MarkdownTwinSourceTest {

    private final RoqPluginMarkdownTwinProcessor processor = new RoqPluginMarkdownTwinProcessor();

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
    void bodyKeepsItsIndentationAndEndsWithOneLineFeed() {
        // The generated template adds a line feed after {#markdown} and before {/markdown}; only those go.
        assertThat(processor.extractMarkdownSource("{#markdown}\n{|    indented code\n\ntext|}\n{/markdown}"))
                .isEqualTo("    indented code\n\ntext\n");
        assertThat(processor.extractMarkdownSource("{#markdown}\n{page.title}\n\ntext\n\n{/markdown}"))
                .isEqualTo("{page.title}\n\ntext\n");
    }

    @Test
    void quteEscapeDelimitersAreRemovedAsOnThePage() {
        assertThat(processor.extractMarkdownSource("{#markdown}\n{|Add {|`{page.tocHtml}`|} to a layout:\n\n{|\n```html\n"
                + "{#include fm/sitemap.xml /}\n```\n|}\n|}\n{/markdown}"))
                .isEqualTo("Add `{page.tocHtml}` to a layout:\n\n\n```html\n{#include fm/sitemap.xml /}\n```\n\n");
        assertThat(processor.unescape("an unclosed {| stays")).isEqualTo("an unclosed {| stays");
        assertThat(processor.unescape("nothing to do")).isEqualTo("nothing to do");
    }
}
