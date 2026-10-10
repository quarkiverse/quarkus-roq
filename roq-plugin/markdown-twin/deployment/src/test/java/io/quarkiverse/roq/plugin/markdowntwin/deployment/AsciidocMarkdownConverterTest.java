package io.quarkiverse.roq.plugin.markdowntwin.deployment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import io.quarkiverse.roq.plugin.markdowntwin.deployment.AsciidocMarkdownConverter.Includes;

/**
 * Unit tests for the converter: what the plugin adds around Yupiik's {@code GithubFlavoredMarkdownRenderer} (includes,
 * site attributes, fallback title, links between twins, callouts the parser rejects, failures). Plain JUnit, no Quarkus.
 */
class AsciidocMarkdownConverterTest {

    @TempDir
    Path tmp;

    private final AsciidocMarkdownConverter converter = new AsciidocMarkdownConverter(Map.of(), Includes.SITE);

    private String convert(String asciidoc, Map<String, String> pageAttributes, String fallbackTitle) throws IOException {
        final Path page = tmp.resolve("page.adoc");
        Files.writeString(page, asciidoc);
        final Optional<String> result = converter.convert(page, tmp, pageAttributes, fallbackTitle);
        assertThat(result).isPresent();
        return result.get();
    }

    @Test
    void fallbackTitleWhenTheSourceHasNoTitleLine() throws IOException {
        assertThat(convert("Just a paragraph.", Map.of(), "From Front Matter"))
                .isEqualTo("# From Front Matter\n\nJust a paragraph.\n");
        assertThat(convert("= Own Title\n\nJust a paragraph.", Map.of(), "From Front Matter"))
                .isEqualTo("# Own Title\n\nJust a paragraph.\n");
    }

    @Test
    void noTableOfContentsWhateverTheTocAttribute() throws IOException {
        final String expected = "# Doc\n\nIntro.\n\n## First\n\nText.\n\n## Second\n\nMore.\n";
        for (String toc : List.of(":toc:", ":toc: preamble", ":toc: left")) {
            assertThat(convert("= Doc\n" + toc + "\n\nIntro.\n\n== First\n\nText.\n\n== Second\n\nMore.\n", Map.of(), null))
                    .as(toc)
                    .isEqualTo(expected);
        }
        assertThat(convert("= Doc\n:toc: macro\n\nIntro.\n\ntoc::[]\n\n== First\n\nText.\n\n== Second\n\nMore.\n", Map.of(),
                null))
                .isEqualTo(expected);
        assertThat(new AsciidocMarkdownConverter(Map.of("toc", "preamble"), Includes.SITE)
                .convert(tmp.resolve("page.adoc"), tmp, Map.of(), null))
                .hasValue(expected);
    }

    @Test
    void crossReferencesPointAtSiblingTwins() throws IOException {
        final String md = convert("""
                = Doc

                [#pages]
                == Pages

                See xref:advanced.adoc#editor[the editor], <<pages,the pages section>> and <<pages>>.
                """, Map.of(), null);
        assertThat(md).contains("[the editor](../advanced/index.md#editor)");
        assertThat(md).contains("[the pages section](#pages)");
        assertThat(md).contains("[Pages](#pages)");
    }

    @Test
    void imagesUseImagesDir() throws IOException {
        final String md = convert("""
                = Doc
                :imagesdir: {site-path}images/docs

                image::layouts.webp[Roq layouts]

                Inline image:icon.svg[] here.
                """, Map.of("site-path", "/"), null);
        assertThat(md).contains("![Roq layouts](/images/docs/layouts.webp)");
        assertThat(md).contains("![icon](/images/docs/icon.svg)");
    }

    @Test
    void attributesResolveOrStayLiteral() throws IOException {
        final String md = convert("""
                = Doc
                :product: Roq

                {product} lives at {site-url}; {missing} stays and so does {page.title}.
                """, Map.of("site-url", "https://roq.dev"), null);
        assertThat(md).contains("Roq lives at <https://roq.dev>; {missing} stays and so does {page.title}.");
    }

    @Test
    void sourceBlockCalloutsKeepTheirMarkers() throws IOException {
        final String md = convert("""
                .A title
                [source,yaml]
                ----
                site:
                  title: Roq <1>
                  url: https://roq.dev <2>
                ----
                <1> The site title.
                <2> The site URL.
                """, Map.of(), null);
        assertThat(md).contains("**A title**\n\n```yaml\n");
        assertThat(md).contains("title: Roq <1>\n");
        assertThat(md).contains("url: https://roq.dev <2>\n```\n\n1. The site title.\n2. The site URL.\n");
    }

    @Test
    void unknownMacroIsWrittenAsText() throws IOException {
        assertThat(convert("Add io.quarkus:quarkus-arc[optional] to the build.", Map.of(), null))
                .isEqualTo("Add io.quarkus:quarkus-arc[optional] to the build.\n");
    }

    @Test
    void siteThatMakesUnknownMacrosFailGetsNoTwin() throws IOException {
        final Path page = tmp.resolve("gav.adoc");
        Files.writeString(page, "Add io.quarkus:quarkus-arc[optional] to the build.");
        assertThat(new AsciidocMarkdownConverter(Map.of("yupiik-renderer-githubflavoredmarkdown-unknownMacro", "fail"),
                Includes.SITE).convert(page, tmp, Map.of(), null)).isEmpty();
    }

    @Test
    void converterResolvesIncludesLikeTheBlog() throws IOException {
        final Path includes = Files.createDirectories(tmp.resolve("includes"));
        final Path docs = Files.createDirectories(tmp.resolve("content/docs"));
        Files.writeString(includes.resolve("attributes.adoc"), """
                :notitle:
                :imagesdir: {site-path}images/docs
                """);
        Files.writeString(includes.resolve("edit-contrib.adoc"),
                """
                        NOTE: If you find an issue, link:https://github.com/x/edit/main/blog/content/docs/{doc-name}.adoc[edit this document].
                        """);
        Files.writeString(includes.resolve("directory-structure.adoc"), """
                == Directory Structure

                [source,yaml]
                ----
                content: pages <1>
                ----
                <1> The pages.
                """);
        final Path page = docs.resolve("basics.adoc");
        Files.writeString(page, """
                ---
                layout: doc
                ---
                = Roq the basics
                include::../../includes/attributes.adoc[]
                :doc-name: basics

                include::../../includes/edit-contrib.adoc[]

                By default, your site files live in the project root.

                include::../../includes/directory-structure.adoc[]
                """);

        final Optional<String> result = converter.convert(page, tmp, Map.of("site-path", "/"), null);

        assertThat(result).isPresent();
        final String md = result.get();
        assertThat(md).startsWith("# Roq the basics\n\n");
        assertThat(md).contains("> [!NOTE]");
        assertThat(md).contains("[edit this document](https://github.com/x/edit/main/blog/content/docs/basics.adoc)");
        assertThat(md).contains("## Directory Structure\n\n```yaml\ncontent: pages <1>\n```\n\n1. The pages.\n");
        assertThat(md).doesNotContain("include::").doesNotContain(":imagesdir:").doesNotContain("{doc-name}")
                .doesNotContain("layout: doc");
    }

    @Test
    void calloutsTheParserRejectsKeepTheTwin() throws IOException {
        // Asciidoctor accepts a callout list that does not match the markers one to one, with a warning; the parser
        // fails on it unless `callout-mismatch` is `ignore`, which the converter sets so the page keeps its twin.
        final Path page = tmp.resolve("callouts.adoc");
        Files.writeString(page, """
                = Callouts

                [source,java]
                ----
                first(); <1>
                second(); <2>
                ----
                <1> Only the first marker has an item.

                After the block.
                """);
        final Optional<String> result = converter.convert(page, tmp, Map.of(), null);
        assertThat(result).isPresent();
        assertThat(result.get()).contains("```java\nfirst(); <1>\nsecond();\n```\n\n1. Only the first marker has an item.\n")
                .contains("After the block.");
        assertThat(new AsciidocMarkdownConverter(Map.of("callout-mismatch", "fail"), Includes.SITE)
                .convert(page, tmp, Map.of(), null)).isEmpty();
    }

    @Test
    void converterSurvivesAMissingInclude() throws IOException {
        final Path page = tmp.resolve("missing.adoc");
        Files.writeString(page, """
                = Missing include

                include::nowhere.adoc[]

                Still here.
                """);
        final Optional<String> result = converter.convert(page, tmp, Map.of(), null);
        assertThat(result).isPresent();
        assertThat(result.get()).contains("# Missing include").contains("Still here.");
    }

    @Test
    void includeOutsideTheSiteIsSkippedUnlessUnsafe() throws IOException {
        // The AsciidoctorJ plugin refuses such an include in SAFE mode, the default, so the twin must not show what
        // the page does not.
        final Path site = Files.createDirectories(tmp.resolve("site"));
        final Path secret = tmp.resolve("secret.adoc");
        Files.writeString(secret, "The secret.\n");
        final Path page = site.resolve("page.adoc");
        Files.writeString(page, "= Page\n\ninclude::../secret.adoc[]\n\nStill here.\n");

        assertThat(converter.convert(page, site, Map.of(), null))
                .hasValueSatisfying(md -> assertThat(md).doesNotContain("The secret.").contains("Still here."));
        assertThat(new AsciidocMarkdownConverter(Map.of(), Includes.ANY).convert(page, site, Map.of(), null))
                .hasValueSatisfying(md -> assertThat(md).contains("The secret.").contains("Still here."));
        // SECURE mode refuses every include, inside the site too.
        Files.writeString(site.resolve("inside.adoc"), "Inside.\n");
        Files.writeString(page, "= Page\n\ninclude::inside.adoc[]\n\nStill here.\n");
        assertThat(converter.convert(page, site, Map.of(), null))
                .hasValueSatisfying(md -> assertThat(md).contains("Inside."));
        assertThat(new AsciidocMarkdownConverter(Map.of(), Includes.NONE).convert(page, site, Map.of(), null))
                .hasValueSatisfying(md -> assertThat(md).doesNotContain("Inside.").contains("Still here."));
    }

    @Test
    void configuredAttributesWinOverThePageAttributes() throws IOException {
        // As on the HTML page, where the AsciidoctorJ plugin applies quarkus.asciidoc.attributes.* last.
        final Path page = tmp.resolve("page.adoc");
        Files.writeString(page, "Site: {site-url}, name: {docname}.\n");
        assertThat(new AsciidocMarkdownConverter(Map.of("site-url", "https://configured.example"), Includes.SITE)
                .convert(page, tmp, Map.of("site-url", "https://page.example", "docname", "page"), null))
                .hasValue("Site: <https://configured.example>, name: page.\n");
    }

    @Test
    void converterReturnsEmptyForAMissingFile() {
        assertThat(converter.convert(tmp.resolve("absent.adoc"), tmp, Map.of(), null)).isEmpty();
    }

    @Test
    void converterSurvivesAnAttributeThatReferencesItself() throws IOException {
        // Older parsers overflowed the stack on a value naming its own attribute; whatever the parser does with such a
        // page, the converter must not take the build down with it.
        final Path page = tmp.resolve("self-reference.adoc");
        Files.writeString(page, """
                = Self reference
                :a: {a}

                See {a} here.
                """);
        assertThatCode(() -> converter.convert(page, tmp, Map.of(), null)).doesNotThrowAnyException();
    }
}
