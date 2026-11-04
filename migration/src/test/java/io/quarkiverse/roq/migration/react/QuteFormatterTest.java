package io.quarkiverse.roq.migration.react;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class QuteFormatterTest {

    @Test
    void putsBlocksOnTheirOwnLinesAndKeepsInlineContentTogether() {
        assertThat(QuteFormatter.format("<footer class=\"bar\"><div>Sponsored by<a href=\"/\"><img src=\"x\"></a></div>"
                + "<div><i class=\"fa\"></i>Edit this Page</div></footer>")).isEqualTo("""
                        <footer class="bar">
                          <div>Sponsored by<a href="/"><img src="x"></a></div>
                          <div><i class="fa"></i>Edit this Page</div>
                        </footer>""");
    }

    @Test
    void laysOutWhatContainsABlockLikeABlock() {
        assertThat(QuteFormatter.format("<header><a href=\"/\"><h1>{=siteTitle??}</h1></a><span>x</span></header>"))
                .isEqualTo("""
                        <header>
                          <a href="/">
                            <h1>{=siteTitle??}</h1>
                          </a>
                          <span>x</span>
                        </header>""");
    }

    @Test
    void laysOutQuteSectionsAroundBlocks() {
        assertThat(QuteFormatter.format("<div>{#if a??}<p>A</p>{#else}<p>B</p>{/if}{#include partials/x /}</div>"))
                .isEqualTo("""
                        <div>
                          {#if a??}
                            <p>A</p>
                          {#else}
                            <p>B</p>
                          {/if}
                          {#include partials/x /}
                        </div>""");
    }

    @Test
    void leavesInlineSectionsOnTheirLine() {
        assertThat(QuteFormatter.format("<p>{#if a??}<b>A</b>{#else}B{/if} and {=c}</p>"))
                .isEqualTo("<p>{#if a??}<b>A</b>{#else}B{/if} and {=c}</p>");
    }

    @Test
    void leavesPreformattedTextAsItIs() {
        assertThat(QuteFormatter.format("<div><pre>  a\n<b>b</b></pre><p>c</p></div>")).isEqualTo("""
                <div>
                  <pre>  a
                <b>b</b></pre>
                  <p>c</p>
                </div>""");
    }

    @Test
    void leavesWhatItCannotReadAsItIs() {
        assertThat(QuteFormatter.format("<div><p>unclosed</div>")).isEqualTo("<div><p>unclosed</div>");
    }

    @Test
    void keepsGatsbyConstrainedImagesInline() {
        // Their wrapper is an inline block, so a line break before it would show as a space
        assertThat(QuteFormatter.format("<p>A<div class=\"gatsby-image-wrapper gatsby-image-wrapper-constrained\">"
                + "<div><img></div><img></div> logo</p>"))
                .isEqualTo("<p>A<div class=\"gatsby-image-wrapper gatsby-image-wrapper-constrained\"><div><img></div><img>"
                        + "</div> logo</p>");
    }
}
