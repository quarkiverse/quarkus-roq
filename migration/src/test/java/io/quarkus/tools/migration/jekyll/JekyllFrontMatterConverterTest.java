package io.quarkus.tools.migration.jekyll;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.dataformat.yaml.YAMLMapper;

class JekyllFrontMatterConverterTest {

    private JekyllFrontMatterConverter converter;
    private final YAMLMapper yamlMapper = new YAMLMapper();

    @BeforeEach
    void setUp() {
        converter = new JekyllFrontMatterConverter();
    }

    // --- Pagination tests ---

    @Test
    void testPaginationBasic(@TempDir Path tempDir) throws IOException {
        Path contentDir = tempDir.resolve("content");
        Files.createDirectories(contentDir);
        Files.writeString(contentDir.resolve("blog.md"), """
                ---
                layout: blog
                title: "Blog"
                pagination:
                  enabled: true
                ---
                """);

        converter.convertPagination(contentDir, null);

        String result = Files.readString(contentDir.resolve("blog.md"));
        assertTrue(result.contains("paginate:"));
        assertTrue(result.contains("collection: posts"));
        assertTrue(result.contains("size: 10"));
        assertTrue(result.contains("link: blog/page/:page"));
        assertFalse(result.contains("pagination:"));
        assertFalse(result.contains("enabled: true"));
    }

    @Test
    void testPaginationWithCustomCollection(@TempDir Path tempDir) throws IOException {
        Path contentDir = tempDir.resolve("content");
        Files.createDirectories(contentDir);
        Files.writeString(contentDir.resolve("articles.md"), """
                ---
                layout: list
                pagination:
                  enabled: true
                ---
                """);

        String configYaml = """
                pagination:
                  collection: articles
                  per_page: 5
                """;
        JsonNode config = yamlMapper.readTree(configYaml);

        converter.convertPagination(contentDir, config);

        String result = Files.readString(contentDir.resolve("articles.md"));
        assertTrue(result.contains("collection: articles"));
        assertTrue(result.contains("size: 5"));
        assertTrue(result.contains("link: articles/page/:page"));
    }

    @Test
    void testPaginationWithCustomPerPage(@TempDir Path tempDir) throws IOException {
        Path contentDir = tempDir.resolve("content");
        Files.createDirectories(contentDir);
        Files.writeString(contentDir.resolve("blog.md"), """
                ---
                pagination:
                  enabled: true
                ---
                """);

        String configYaml = """
                pagination:
                  per_page: 25
                """;
        JsonNode config = yamlMapper.readTree(configYaml);

        converter.convertPagination(contentDir, config);

        String result = Files.readString(contentDir.resolve("blog.md"));
        assertTrue(result.contains("size: 25"));
    }

    @Test
    void testPaginationMultipleFiles(@TempDir Path tempDir) throws IOException {
        Path contentDir = tempDir.resolve("content");
        Files.createDirectories(contentDir);
        Files.writeString(contentDir.resolve("blog.md"), """
                ---
                layout: blog
                pagination:
                  enabled: true
                ---
                """);
        Files.writeString(contentDir.resolve("author.md"), """
                ---
                layout: authors
                pagination:
                  enabled: true
                ---
                """);

        converter.convertPagination(contentDir, null);

        String blog = Files.readString(contentDir.resolve("blog.md"));
        assertTrue(blog.contains("link: blog/page/:page"));

        String author = Files.readString(contentDir.resolve("author.md"));
        assertTrue(author.contains("link: author/page/:page"));
    }

    @Test
    void testPaginationNoConfig(@TempDir Path tempDir) throws IOException {
        Path contentDir = tempDir.resolve("content");
        Files.createDirectories(contentDir);
        Files.writeString(contentDir.resolve("blog.md"), """
                ---
                pagination:
                  enabled: true
                ---
                """);

        converter.convertPagination(contentDir, null);

        String result = Files.readString(contentDir.resolve("blog.md"));
        assertTrue(result.contains("collection: posts"));
        assertTrue(result.contains("size: 10"));
    }

    @Test
    void testPaginationLeavesNonPaginatedFilesAlone(@TempDir Path tempDir) throws IOException {
        Path contentDir = tempDir.resolve("content");
        Files.createDirectories(contentDir);
        String original = """
                ---
                layout: page
                title: "About"
                ---
                Some content.
                """;
        Files.writeString(contentDir.resolve("about.md"), original);

        converter.convertPagination(contentDir, null);

        assertEquals(original, Files.readString(contentDir.resolve("about.md")));
    }

    // --- Permalink tests ---

    @Test
    void testPermalinkMatchingFilename(@TempDir Path tempDir) throws IOException {
        Path contentDir = tempDir.resolve("content");
        Files.createDirectories(contentDir);
        Files.writeString(contentDir.resolve("about.md"), """
                ---
                layout: page
                permalink: /about/
                title: "About"
                ---
                """);

        converter.convertPermalinks(contentDir);

        String result = Files.readString(contentDir.resolve("about.md"));
        assertFalse(result.contains("permalink:"));
        assertFalse(result.contains("link:"));
        assertTrue(result.contains("layout: page"));
        assertTrue(result.contains("title: \"About\""));
    }

    @Test
    void testPermalinkInSubdirNotStrippedWhenDifferentFromRelativePath(@TempDir Path tempDir) throws IOException {
        Path contentDir = tempDir.resolve("content");
        Path guidesDir = contentDir.resolve("guides");
        Files.createDirectories(guidesDir);
        Files.writeString(guidesDir.resolve("guides.md"), """
                ---
                layout: documentation
                permalink: /guides/
                ---
                """);

        converter.convertPermalinks(contentDir);

        String result = Files.readString(guidesDir.resolve("guides.md"));
        assertTrue(result.contains("link: /guides/"),
                "permalink /guides/ should become link because relative path is guides/guides, not guides");
        assertFalse(result.contains("permalink:"));
    }

    @Test
    void testPermalinkRemovalLeavesNoBlankLine(@TempDir Path tempDir) throws IOException {
        Path contentDir = tempDir.resolve("content");
        Files.createDirectories(contentDir);
        Files.writeString(contentDir.resolve("about.md"), """
                ---
                layout: page
                permalink: /about/
                title: "About"
                ---
                """);

        converter.convertPermalinks(contentDir);

        String result = Files.readString(contentDir.resolve("about.md"));
        assertFalse(result.contains("\n\n"), "Should not have blank lines in frontmatter");
    }

    @Test
    void testPermalinkDifferentFromFilename(@TempDir Path tempDir) throws IOException {
        Path contentDir = tempDir.resolve("content");
        Files.createDirectories(contentDir);
        Files.writeString(contentDir.resolve("events.md"), """
                ---
                layout: page
                permalink: /community/events/
                ---
                """);

        converter.convertPermalinks(contentDir);

        String result = Files.readString(contentDir.resolve("events.md"));
        assertTrue(result.contains("link: /community/events/"));
        assertFalse(result.contains("permalink:"));
    }

    @Test
    void testPermalinkAbsent(@TempDir Path tempDir) throws IOException {
        Path contentDir = tempDir.resolve("content");
        Files.createDirectories(contentDir);
        String original = """
                ---
                layout: page
                title: "About"
                ---
                """;
        Files.writeString(contentDir.resolve("about.md"), original);

        converter.convertPermalinks(contentDir);

        assertEquals(original, Files.readString(contentDir.resolve("about.md")));
    }

    @Test
    void testPermalinkWithoutSlashes(@TempDir Path tempDir) throws IOException {
        Path contentDir = tempDir.resolve("content");
        Files.createDirectories(contentDir);
        Files.writeString(contentDir.resolve("about.md"), """
                ---
                permalink: about
                ---
                """);

        converter.convertPermalinks(contentDir);

        String result = Files.readString(contentDir.resolve("about.md"));
        assertFalse(result.contains("permalink:"));
        assertFalse(result.contains("link:"));
    }

    // --- Redirect deduplication tests ---

    @Test
    void testMergeRedirectDuplicates(@TempDir Path tempDir) throws IOException {
        Path contentDir = tempDir.resolve("content");
        Path redirectsDir = contentDir.resolve("redirects/guides");
        Files.createDirectories(redirectsDir);

        Files.writeString(redirectsDir.resolve("foo-guide.html"), """
                ---
                permalink: /guides/foo-guide.html
                newUrl: /guides/foo
                ---
                """);
        Files.writeString(redirectsDir.resolve("foo-guide.md"), """
                ---
                permalink: /guides/foo-guide/index.html
                newUrl: /guides/foo
                ---
                """);

        converter.mergeRedirectDuplicates(contentDir);

        assertTrue(Files.exists(redirectsDir.resolve("foo-guide.html")));
        assertFalse(Files.exists(redirectsDir.resolve("foo-guide.md")));

        String result = Files.readString(redirectsDir.resolve("foo-guide.html"));
        assertTrue(result.contains("/guides/foo-guide.html"));
        assertTrue(result.contains("/guides/foo-guide/index.html"));
        assertTrue(result.contains("newUrl: /guides/foo"));
    }

    @Test
    void testMergeRedirectDuplicatesNoMatch(@TempDir Path tempDir) throws IOException {
        Path contentDir = tempDir.resolve("content");
        Path redirectsDir = contentDir.resolve("redirects/guides");
        Files.createDirectories(redirectsDir);

        Files.writeString(redirectsDir.resolve("only-md.md"), """
                ---
                permalink: /guides/only-md/index.html
                newUrl: /guides/something
                ---
                """);
        Files.writeString(redirectsDir.resolve("only-html.html"), """
                ---
                permalink: /guides/only-html.html
                newUrl: /guides/other
                ---
                """);

        converter.mergeRedirectDuplicates(contentDir);

        assertTrue(Files.exists(redirectsDir.resolve("only-md.md")));
        assertTrue(Files.exists(redirectsDir.resolve("only-html.html")));
    }

    @Test
    void testConvertProjectMergesRedirectsInCollectionDir(@TempDir Path tempDir) throws IOException {
        Files.createDirectories(tempDir.resolve("content"));
        Files.writeString(tempDir.resolve("_config.yml"), "title: Test\n");

        Path redirectsDir = tempDir.resolve("_redirects/guides");
        Files.createDirectories(redirectsDir);
        Files.writeString(redirectsDir.resolve("bar-guide.html"), """
                ---
                permalink: /guides/bar-guide.html
                newUrl: /guides/bar
                ---
                """);
        Files.writeString(redirectsDir.resolve("bar-guide.md"), """
                ---
                permalink: /guides/bar-guide/index.html
                newUrl: /guides/bar
                ---
                """);

        converter.convertProject(tempDir);

        assertTrue(Files.exists(redirectsDir.resolve("bar-guide.html")));
        assertFalse(Files.exists(redirectsDir.resolve("bar-guide.md")));

        String result = Files.readString(redirectsDir.resolve("bar-guide.html"));
        assertTrue(result.contains("/guides/bar-guide.html"));
        assertTrue(result.contains("/guides/bar-guide/index.html"));
    }

    @Test
    void testConvertProjectConvertsPermalinksInCollectionDirs(@TempDir Path tempDir) throws IOException {
        Files.createDirectories(tempDir.resolve("content"));
        Files.writeString(tempDir.resolve("_config.yml"), "title: Test\n");

        Path guidesDir = tempDir.resolve("_guides");
        Files.createDirectories(guidesDir);
        Files.writeString(guidesDir.resolve("guides.md"), """
                ---
                layout: documentation
                permalink: /guides/
                ---
                """);

        converter.convertProject(tempDir);

        String result = Files.readString(guidesDir.resolve("guides.md"));
        assertTrue(result.contains("link: /guides/"),
                "permalink /guides/ in _guides/guides.md should become link because " +
                        "post-move path guides/guides != guides");
        assertFalse(result.contains("permalink:"));
    }

    @Test
    void testConvertProjectStripsRedundantPermalinkInCollectionDir(@TempDir Path tempDir) throws IOException {
        Files.createDirectories(tempDir.resolve("content"));
        Files.writeString(tempDir.resolve("_config.yml"), "title: Test\n");

        Path guidesDir = tempDir.resolve("_guides");
        Files.createDirectories(guidesDir);
        Files.writeString(guidesDir.resolve("foo.md"), """
                ---
                permalink: /guides/foo/
                ---
                """);

        converter.convertProject(tempDir);

        String result = Files.readString(guidesDir.resolve("foo.md"));
        assertFalse(result.contains("permalink:"), "redundant permalink should be stripped");
        assertFalse(result.contains("link:"), "redundant permalink should not become link");
    }

    // --- Include target prefix tests ---

    @Test
    void testPrefixIncludeTargetsRenamesFileAndUpdatesInclude(@TempDir Path tempDir) throws IOException {
        Path postsDir = tempDir.resolve("content/posts");
        Files.createDirectories(postsDir);

        Files.writeString(postsDir.resolve("transcription.adoc"),
                "Some content with no frontmatter\n");
        Files.writeString(postsDir.resolve("2020-04-30-insights.adoc"), """
                ---
                title: Insights
                ---
                Here is the transcript:
                include::transcription.adoc[]
                """);

        converter.prefixIncludeTargets(tempDir.resolve("content"));

        assertFalse(Files.exists(postsDir.resolve("transcription.adoc")),
                "Original file should be renamed");
        assertTrue(Files.exists(postsDir.resolve("_transcription.adoc")),
                "File should be prefixed with _");

        String parentContent = Files.readString(postsDir.resolve("2020-04-30-insights.adoc"));
        assertTrue(parentContent.contains("include::_transcription.adoc[]"),
                "Include directive should reference the renamed file");
        assertFalse(parentContent.contains("include::transcription.adoc[]"),
                "Old include reference should be gone");
    }

    @Test
    void testPrefixIncludeTargetsSkipsFilesWithFrontmatter(@TempDir Path tempDir) throws IOException {
        Path postsDir = tempDir.resolve("content/posts");
        Files.createDirectories(postsDir);

        Files.writeString(postsDir.resolve("real-post.adoc"), """
                ---
                title: A real post
                ---
                Post content
                """);

        converter.prefixIncludeTargets(tempDir.resolve("content"));

        assertTrue(Files.exists(postsDir.resolve("real-post.adoc")),
                "File with frontmatter should not be renamed");
        assertFalse(Files.exists(postsDir.resolve("_real-post.adoc")));
    }

    @Test
    void testPrefixIncludeTargetsMultipleFiles(@TempDir Path tempDir) throws IOException {
        Path postsDir = tempDir.resolve("content/posts");
        Files.createDirectories(postsDir);

        Files.writeString(postsDir.resolve("transcription_0.adoc"),
                "First transcript\n");
        Files.writeString(postsDir.resolve("transcription_1.adoc"),
                "Second transcript\n");
        Files.writeString(postsDir.resolve("2020-04-30-insights.adoc"), """
                ---
                title: Insights
                ---
                include::transcription_0.adoc[]
                include::transcription_1.adoc[]
                """);

        converter.prefixIncludeTargets(tempDir.resolve("content"));

        assertFalse(Files.exists(postsDir.resolve("transcription_0.adoc")));
        assertFalse(Files.exists(postsDir.resolve("transcription_1.adoc")));
        assertTrue(Files.exists(postsDir.resolve("_transcription_0.adoc")));
        assertTrue(Files.exists(postsDir.resolve("_transcription_1.adoc")));

        String parentContent = Files.readString(postsDir.resolve("2020-04-30-insights.adoc"));
        assertTrue(parentContent.contains("include::_transcription_0.adoc[]"));
        assertTrue(parentContent.contains("include::_transcription_1.adoc[]"));
    }

    @Test
    void testPrefixIncludeTargetsSkipsAlreadyPrefixed(@TempDir Path tempDir) throws IOException {
        Path postsDir = tempDir.resolve("content/posts");
        Files.createDirectories(postsDir);

        Files.writeString(postsDir.resolve("_already-prefixed.adoc"),
                "Some content\n");

        converter.prefixIncludeTargets(tempDir.resolve("content"));

        assertTrue(Files.exists(postsDir.resolve("_already-prefixed.adoc")),
                "Already-prefixed file should stay as-is");
    }

    // --- Integration test ---

    @Test
    void testConvertProjectRunsBothTransforms(@TempDir Path tempDir) throws IOException {
        Files.createDirectories(tempDir.resolve("content"));
        Files.writeString(tempDir.resolve("_config.yml"), """
                pagination:
                  collection: posts
                  per_page: 10
                """);
        Files.writeString(tempDir.resolve("content/blog.md"), """
                ---
                layout: blog
                permalink: /blog/
                pagination:
                  enabled: true
                ---
                """);

        converter.convertProject(tempDir);

        String result = Files.readString(tempDir.resolve("content/blog.md"));
        assertFalse(result.contains("permalink:"));
        assertTrue(result.contains("paginate:"));
        assertTrue(result.contains("link: blog/page/:page"));
    }
}
