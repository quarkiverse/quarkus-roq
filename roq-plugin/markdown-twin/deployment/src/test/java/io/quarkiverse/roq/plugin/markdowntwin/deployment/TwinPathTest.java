package io.quarkiverse.roq.plugin.markdowntwin.deployment;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Where twins live and how twins link to each other: inside the page's directory for a directory-style URL, beside the
 * file otherwise.
 */
class TwinPathTest {

    private final RoqPluginMarkdownTwinProcessor processor = new RoqPluginMarkdownTwinProcessor();

    @Test
    void directoryStyleUrlGetsIndexMdInsideTheDirectory() {
        assertThat(processor.twinPath("docs/basics/")).isEqualTo("docs/basics/index.md");
        assertThat(processor.twinPath("posts/welcome-to-roq/")).isEqualTo("posts/welcome-to-roq/index.md");
    }

    @Test
    void fileStyleUrlGetsMdAppended() {
        assertThat(processor.twinPath("404.html")).isEqualTo("404.html.md");
    }

    @Test
    void siteRootHasNoTwin() {
        assertThat(processor.twinPath("")).isNull();
        assertThat(processor.twinPath("/")).isNull();
        assertThat(processor.twinPath(null)).isNull();
    }

    @Test
    void crossPageLinksPointAtTheOtherPagesTwin(@TempDir Path tmp) throws IOException {
        final Path page = tmp.resolve("basics.adoc");
        Files.writeString(page, """
                xref:advanced.adoc[A] xref:./advanced.adoc[B] xref:guides/advanced.adoc[C] xref:/docs/advanced.adoc[D]
                """);
        final AsciidocMarkdownConverter converter = new AsciidocMarkdownConverter(Map.of(),
                AsciidocMarkdownConverter.Includes.SITE);
        // Without a page URL the links are relative, as the AsciidoctorJ plugin falls back to `../`.
        assertThat(converter.convert(page, tmp, Map.of(), null)).hasValue("[A](../advanced/index.md) "
                + "[B](../advanced/index.md) [C](../guides/advanced/index.md) [D](/docs/advanced/index.md)\n");
        // With the page URL /docs/basics/ the processor passes its parent path, so the links are site-absolute as on
        // the HTML page.
        assertThat(converter.convert(page, tmp, Map.of("relfileprefix", "/docs/"), null))
                .hasValue("[A](/docs/advanced/index.md) "
                        + "[B](/docs/advanced/index.md) [C](/docs/guides/advanced/index.md) [D](/docs/advanced/index.md)\n");
    }
}
