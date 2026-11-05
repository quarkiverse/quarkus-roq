package io.quarkiverse.roq.plugin.markdowntwin.deployment;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import io.quarkiverse.roq.frontmatter.deployment.items.assemble.RoqFrontMatterRawPageBuildItem;
import io.quarkiverse.roq.frontmatter.deployment.items.data.RoqFrontMatterPageTemplateBuildItem;
import io.quarkiverse.roq.frontmatter.deployment.items.data.RoqFrontMatterRootUrlBuildItem;
import io.quarkiverse.roq.frontmatter.deployment.items.data.RoqFrontMatterStaticFileBuildItem;
import io.quarkiverse.roq.frontmatter.deployment.items.data.RoqPageMarkdownBuildItem;
import io.quarkiverse.roq.frontmatter.deployment.items.scan.RoqMarkdownConverterBuildItem;
import io.quarkiverse.roq.frontmatter.deployment.items.scan.RoqMarkdownConverterBuildItem.MarkdownConversion;
import io.quarkiverse.roq.frontmatter.runtime.model.PageFiles;
import io.quarkiverse.roq.frontmatter.runtime.model.PageSource;
import io.quarkiverse.roq.frontmatter.runtime.model.RootUrl;
import io.quarkiverse.roq.frontmatter.runtime.model.RoqUrl;
import io.quarkiverse.roq.frontmatter.runtime.model.SourceFile;
import io.quarkiverse.roq.frontmatter.runtime.model.TemplateSource;
import io.vertx.core.json.JsonObject;

/**
 * Where a twin is published, and which pages get one: the twin step hands each eligible page to the converter of its
 * markup, once, and publishes what comes back.
 */
class TwinPathTest {

    private final RoqPluginMarkdownTwinProcessor processor = new RoqPluginMarkdownTwinProcessor();

    @Test
    void directoryStyleUrlGetsIndexMdInside() {
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
    void eachEligiblePageIsConvertedOnceByTheConverterOfItsMarkup() {
        final List<MarkdownConversion> conversions = new ArrayList<>();
        final RoqMarkdownConverterBuildItem markdown = new RoqMarkdownConverterBuildItem("markdown", conversion -> {
            conversions.add(conversion);
            return Optional.of("# " + conversion.title() + "\n");
        });
        final RoqMarkdownConverterBuildItem asciidoc = new RoqMarkdownConverterBuildItem("asciidoc",
                conversion -> Optional.empty());
        final RootUrl root = new RootUrl("https://example.org", "/");
        final List<RoqFrontMatterPageTemplateBuildItem> templates = List.of(
                page("markdown", "docs/basics/", new JsonObject().put("title", "Basics"), root),
                page("markdown", "docs/opt-out/", new JsonObject().put("title", "Out").put("mdtwin", false), root),
                page("markdown", "docs/hidden/", new JsonObject().put("title", "Hidden").put("no-index-all", true), root),
                page("markdown", "docs/kept/", new JsonObject().put("title", "Kept").put("no-index-all", true)
                        .put("mdtwin", true), root),
                page("asciidoc", "docs/broken/", new JsonObject().put("title", "Broken"), root),
                page("html", "docs/raw/", new JsonObject().put("title", "Raw"), root));
        final List<RoqPageMarkdownBuildItem> pages = new ArrayList<>();

        processor.convertPages(config(true), null, new RoqFrontMatterRootUrlBuildItem(root), templates,
                List.of(markdown, asciidoc),
                pages::add);

        // Basics and Kept are converted; Out and Hidden opt out; Broken's converter gives nothing; Raw has no converter.
        assertThat(pages).extracting(RoqPageMarkdownBuildItem::path).containsExactly("docs/basics/index.md",
                "docs/kept/index.md");
        assertThat(pages).extracting(RoqPageMarkdownBuildItem::markdown).containsExactly("# Basics\n", "# Kept\n");
        assertThat(conversions).hasSize(2);
        final MarkdownConversion first = conversions.get(0);
        assertThat(first.source()).endsWithRaw(Path.of("content", "docs", "basics.md"));
        assertThat(first.generatedTemplate()).isEqualTo("{#markdown}\nBasics\n{/markdown}");
        assertThat(first.siteDir().toString()).endsWith("site");
        assertThat(first.rootUrl()).isEqualTo(root);
        assertThat(first.url().path()).isEqualTo("/docs/basics/");
        assertThat(first.crossReferenceSuffix()).isEqualTo("/index.md");

        final List<RoqFrontMatterStaticFileBuildItem> files = new ArrayList<>();
        processor.publishTwins(pages, files::add);
        assertThat(files).extracting(RoqFrontMatterStaticFileBuildItem::link).containsExactly("docs/basics/index.md",
                "docs/kept/index.md");
        assertThat(new String(files.get(0).content(), StandardCharsets.UTF_8)).isEqualTo("# Basics\n");
    }

    @Test
    void withoutAnyConverterNoPageIsConverted() {
        final RootUrl root = new RootUrl("https://example.org", "/");
        final List<RoqPageMarkdownBuildItem> pages = new ArrayList<>();
        processor.convertPages(config(true), null, new RoqFrontMatterRootUrlBuildItem(root),
                List.of(page("markdown", "docs/basics/", new JsonObject().put("title", "Basics"), root)), List.of(),
                pages::add);
        assertThat(pages).isEmpty();
    }

    @Test
    void disabledConvertsNoPage() {
        final RootUrl root = new RootUrl("https://example.org", "/");
        final List<RoqPageMarkdownBuildItem> pages = new ArrayList<>();
        processor.convertPages(config(false), null, new RoqFrontMatterRootUrlBuildItem(root),
                List.of(page("markdown", "docs/basics/", new JsonObject().put("title", "Basics"), root)),
                List.of(markdownConverter()), pages::add);
        assertThat(pages).isEmpty();
    }

    @Test
    void anExcludedPageIsNotConverted() {
        final RootUrl root = new RootUrl("https://example.org", "/");
        final List<RoqPageMarkdownBuildItem> pages = new ArrayList<>();
        processor.convertPages(config(true, "/version/**"), null, new RoqFrontMatterRootUrlBuildItem(root),
                List.of(page("markdown", "docs/basics/", new JsonObject().put("title", "Basics"), root),
                        page("markdown", "version/old/", new JsonObject().put("title", "Old"), root)),
                List.of(markdownConverter()), pages::add);
        assertThat(pages).extracting(RoqPageMarkdownBuildItem::path).containsExactly("docs/basics/index.md");
    }

    private static RoqMarkdownConverterBuildItem markdownConverter() {
        return new RoqMarkdownConverterBuildItem("markdown",
                conversion -> Optional.of("# " + conversion.title() + "\n"));
    }

    private static MarkdownTwinConfig config(boolean enabled, String... exclude) {
        return new MarkdownTwinConfig() {
            @Override
            public boolean enabled() {
                return enabled;
            }

            @Override
            public Optional<List<String>> include() {
                return Optional.empty();
            }

            @Override
            public Optional<List<String>> exclude() {
                return exclude.length == 0 ? Optional.empty() : Optional.of(List.of(exclude));
            }
        };
    }

    private RoqFrontMatterPageTemplateBuildItem page(String markup, String resourcePath, JsonObject data, RootUrl root) {
        final String name = resourcePath.replaceAll("/$", "");
        final String extension = "asciidoc".equals(markup) ? "adoc" : "html".equals(markup) ? "html" : "md";
        final TemplateSource template = TemplateSource.create(name, markup,
                new SourceFile("/tmp/site", "content/" + name + "." + extension), name + "." + extension,
                name + ".html", false, true, false, false);
        final PageSource source = new PageSource(template, false, null, PageFiles.EMPTY, false);
        final RoqFrontMatterRawPageBuildItem raw = new RoqFrontMatterRawPageBuildItem(template, null, data, null,
                "{#" + markup + "}\n" + data.getString("title") + "\n{/" + markup + "}", List.of());
        return new RoqFrontMatterPageTemplateBuildItem(raw, data, source, new RoqUrl(root, resourcePath));
    }
}
