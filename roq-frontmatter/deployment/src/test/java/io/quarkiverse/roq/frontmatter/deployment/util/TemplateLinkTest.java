package io.quarkiverse.roq.frontmatter.deployment.util;

import static io.quarkiverse.roq.frontmatter.runtime.utils.TemplateLink.pageLink;
import static io.quarkiverse.roq.frontmatter.runtime.utils.TemplateLink.paginateLink;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import io.quarkiverse.roq.frontmatter.runtime.exception.RoqTemplateLinkException;
import io.quarkiverse.roq.frontmatter.runtime.model.PageFiles;
import io.quarkiverse.roq.frontmatter.runtime.model.PageSource;
import io.quarkiverse.roq.frontmatter.runtime.model.SourceFile;
import io.quarkiverse.roq.frontmatter.runtime.model.TemplateSource;
import io.quarkiverse.roq.frontmatter.runtime.utils.TemplateLink.PageLinkData;
import io.quarkiverse.roq.frontmatter.runtime.utils.TemplateLink.PaginateLinkData;
import io.vertx.core.json.JsonObject;

/**
 * Pure unit tests (no Quarkus runtime).
 * <p>
 * Features tested: link pattern resolution with date placeholders (:year, :month, :day),
 * slug/name generation, file extension handling, pagination links, case variants,
 * directory index pages, invalid placeholder detection.
 */
@DisplayName("Roq FrontMatter - Template link pattern resolution")
class TemplateLinkTest {
    @Test
    void testLink() {
        JsonObject frontMatter = new JsonObject().put("title", "My First Blog Post");
        final PageSource templateSource = createPageSource("posts/my-first-blog-post.md", true, false);

        String generatedLink = pageLink("", ":year/:month/:day/:slug", new PageLinkData(templateSource, null, frontMatter));
        assertEquals("2024/08/27/my-first-blog-post/", generatedLink);
    }

    @Test
    void testLinkExt() {
        JsonObject frontMatter = new JsonObject().put("title", "My First Blog Post");
        final PageSource templateSource = createPageSource("posts/my-first-blog-post.md", true, false);

        String generatedLink = pageLink("", ":year/:month/:day/:slug:ext!",
                new PageLinkData(templateSource, null, frontMatter));
        assertEquals("2024/08/27/my-first-blog-post.html", generatedLink);

        String generatedLink2 = pageLink("", ":year/:month/:day/:slug:ext",
                new PageLinkData(templateSource, null, frontMatter));
        assertEquals("2024/08/27/my-first-blog-post/", generatedLink2);
    }

    @Test
    void testLinkJson() {
        JsonObject frontMatter = new JsonObject();
        final PageSource templateSource = createPageSource("bar/foo.json", false, false);

        String generatedLink = pageLink("", ":path:ext", new PageLinkData(templateSource, null, frontMatter));
        assertEquals("bar/foo.json", generatedLink);
    }

    @Test
    void testPaginateLink() {
        JsonObject frontMatter = new JsonObject().put("title", "My First Blog Post");
        final PageSource templateSource = createPageSource("posts/my-first-blog-post.md",
                true, false);

        String generatedLink = paginateLink("foo", null, new PaginateLinkData(templateSource, "posts", "3", frontMatter));
        assertEquals("foo/posts/page3/", generatedLink);
    }

    @Test
    void testSlugCase() {
        JsonObject frontMatter = new JsonObject().put("title", "My First Blog Post");
        final PageSource templateSource = createPageSource("posts/my-first-blog-post.md", true, false);

        PageLinkData data = new PageLinkData(templateSource, null, frontMatter);
        assertEquals("2024/08/27/my-first-blog-post/", pageLink("", ":year/:month/:day/:slug", data));
        assertEquals("2024/08/27/My-First-Blog-Post/", pageLink("", ":year/:month/:day/:Slug", data));
    }

    @Test
    void testFileCase() {
        JsonObject frontMatter = new JsonObject().put("title", "This Is My Very First Blog Post");
        final PageSource templateSource = createPageSource("posts/My-First-Blog-Post.md", true, false);

        PageLinkData data = new PageLinkData(templateSource, null, frontMatter);
        assertEquals("2024/08/27/my-first-blog-post/", pageLink("", ":year/:month/:day/:name", data));
        assertEquals("2024/08/27/My-First-Blog-Post/", pageLink("", ":year/:month/:day/:Name", data));
    }

    @Test
    void testFileCaseDate() {
        JsonObject frontMatter = new JsonObject().put("title", "This Is My Very First Blog Post");
        final PageSource templateSource = createPageSource("posts/2024-03-02-My-First-Blog-Post.md", true, false);

        PageLinkData data = new PageLinkData(templateSource, null, frontMatter);
        assertEquals("2024/08/27/my-first-blog-post/", pageLink("", ":year/:month/:day/:name", data));
        assertEquals("2024/08/27/My-First-Blog-Post/", pageLink("", ":year/:month/:day/:Name", data));
    }

    @Test
    void testFileIndexCaseDate() {
        JsonObject frontMatter = new JsonObject().put("title", "This Is My Very First Blog Post");
        final PageSource templateSource = createPageSource("posts/2024-03-02-My-First-Blog-Post/index.md", true, true);

        PageLinkData data = new PageLinkData(templateSource, null, frontMatter);
        assertEquals("2024/08/27/my-first-blog-post/", pageLink("", ":year/:month/:day/:name", data));
        assertEquals("2024/08/27/My-First-Blog-Post/", pageLink("", ":year/:month/:day/:Name", data));
    }

    @Test
    void testInvalidPlaceholder() {
        JsonObject frontMatter = new JsonObject().put("title", "My First Blog Post");
        final PageSource templateSource = createPageSource("posts/my-first-blog-post.md", true, false);

        PageLinkData data = new PageLinkData(templateSource, null, frontMatter);

        RoqTemplateLinkException withSingle = assertThrows(RoqTemplateLinkException.class, () -> {
            pageLink("", "/post/:foo", data);
        });

        assertTrue(withSingle.getMessage().contains(":foo"),
                "Exception message should mention the invalid placeholder :foo");

        RoqTemplateLinkException withMultiple = assertThrows(RoqTemplateLinkException.class, () -> {
            pageLink("", "/post/:foo/:bar/:baz", data);
        });

        assertTrue(withMultiple.getMessage().contains(":foo"));
        assertTrue(withMultiple.getMessage().contains(":bar"));
        assertTrue(withMultiple.getMessage().contains(":baz"));

        RoqTemplateLinkException withHyphen = assertThrows(RoqTemplateLinkException.class, () -> {
            pageLink("", "/post/:invalid-placeholder", data);
        });
        assertTrue(withHyphen.getMessage().contains(":invalid-placeholder"),
                "Exception message should mention the invalid placeholder with hyphen");
    }

    @Test
    void testValidPlaceholdersNoException() {
        JsonObject frontMatter = new JsonObject().put("title", "My First Blog Post");
        final PageSource templateSource = createPageSource("posts/my-first-blog-post.md", true, false);

        PageLinkData data = new PageLinkData(templateSource, null, frontMatter);

        // Test that valid placeholders don't throw exceptions
        assertDoesNotThrow(() -> pageLink("", "/:year/:month/:day/:slug", data));
        assertDoesNotThrow(() -> pageLink("", "/:path:ext", data));
        assertDoesNotThrow(() -> pageLink("", "/:name/:Slug", data));
        assertDoesNotThrow(() -> pageLink("", "/:dir/:slug", data));
        assertDoesNotThrow(() -> pageLink("", "/:dir[1]/:slug", data));
    }

    @Test
    void testSlugTruncation() {
        JsonObject frontMatter = new JsonObject().put("title", "My First Blog Post About Quarkus");
        final PageSource templateSource = createPageSource("posts/my-first-blog-post-about-quarkus.md", true, false);
        PageLinkData data = new PageLinkData(templateSource, null, frontMatter);

        assertEquals("2024/08/27/my-first-blog/", pageLink("", ":year/:month/:day/:slug~3", data));
    }

    @Test
    void testSlugTruncationCasePreserving() {
        JsonObject frontMatter = new JsonObject().put("title", "My First Blog Post");
        final PageSource templateSource = createPageSource("posts/my-first-blog-post.md", true, false);
        PageLinkData data = new PageLinkData(templateSource, null, frontMatter);

        assertEquals("2024/08/27/My-First/", pageLink("", ":year/:month/:day/:Slug~2", data));
    }

    @Test
    void testSlugTruncationExceedsWordCount() {
        JsonObject frontMatter = new JsonObject().put("title", "Short");
        final PageSource templateSource = createPageSource("posts/short.md", true, false);
        PageLinkData data = new PageLinkData(templateSource, null, frontMatter);

        assertEquals("2024/08/27/short/", pageLink("", ":year/:month/:day/:slug~100", data));
    }

    @Test
    void testSlugNoTruncationBackwardsCompatible() {
        JsonObject frontMatter = new JsonObject().put("title", "My First Blog Post");
        final PageSource templateSource = createPageSource("posts/my-first-blog-post.md", true, false);
        PageLinkData data = new PageLinkData(templateSource, null, frontMatter);

        assertEquals("2024/08/27/my-first-blog-post/", pageLink("", ":year/:month/:day/:slug", data));
    }

    @Test
    void testTruncationOnUnsupportedPlaceholder() {
        JsonObject frontMatter = new JsonObject().put("title", "My Post");
        final PageSource templateSource = createPageSource("posts/my-post.md", true, false);
        PageLinkData data = new PageLinkData(templateSource, null, frontMatter);

        assertThrows(RoqTemplateLinkException.class, () -> pageLink("", ":year~2/:slug", data));
    }

    @Test
    void testNameTruncation() {
        JsonObject frontMatter = new JsonObject().put("title", "Something Else Entirely");
        final PageSource templateSource = createPageSource("posts/2024-03-02-My-First-Blog-Post.md", true, false);
        PageLinkData data = new PageLinkData(templateSource, null, frontMatter);

        assertEquals("2024/08/27/my-first/", pageLink("", ":year/:month/:day/:name~2", data));
    }

    // --- :dir placeholder tests ---

    @Test
    void testDirPlaceholderSimple() {
        JsonObject frontMatter = new JsonObject().put("title", "My Post");
        final PageSource templateSource = createPageSource("posts/my-post.md", true, false);
        PageLinkData data = new PageLinkData(templateSource, null, frontMatter);

        assertEquals("posts/my-post/", pageLink("", ":dir/:slug", data));
    }

    @Test
    void testDirPlaceholderNested() {
        JsonObject frontMatter = new JsonObject().put("title", "AMQP Dev Services");
        final PageSource templateSource = createPageSource("posts/v2/guides/amqp.md", true, false);
        PageLinkData data = new PageLinkData(templateSource, "posts", frontMatter);

        assertEquals("posts/v2/guides/amqp-dev-services/", pageLink("", ":dir/:slug", data));
    }

    @Test
    void testDirPlaceholderNoDirectory() {
        JsonObject frontMatter = new JsonObject().put("title", "Root File");
        final PageSource templateSource = createPageSource("myfile.md", true, false);
        PageLinkData data = new PageLinkData(templateSource, null, frontMatter);

        assertEquals("root-file/", pageLink("", ":dir/:slug", data));
    }

    @Test
    void testDirPlaceholderIndex() {
        JsonObject frontMatter = new JsonObject().put("title", "My Dir");
        final PageSource templateSource = createPageSource("posts/my-dir/index.md", true, true);
        PageLinkData data = new PageLinkData(templateSource, "posts", frontMatter);

        assertEquals("posts/my-dir/", pageLink("", ":dir/:slug", data));
    }

    // --- :dir[N] slice tests ---

    @Test
    void testDirSliceFromIndex1() {
        JsonObject frontMatter = new JsonObject().put("title", "Getting Started");
        final PageSource templateSource = createPageSource("versions/main/guides/getting-started.md", true, false);
        PageLinkData data = new PageLinkData(templateSource, "versions", frontMatter);

        assertEquals("version/main/guides/getting-started/", pageLink("", "/version/:dir[1]/:slug", data));
    }

    @Test
    void testDirSliceFromIndex0SameAsDir() {
        JsonObject frontMatter = new JsonObject().put("title", "AMQP Dev Services");
        final PageSource templateSource = createPageSource("posts/v2/guides/amqp.md", true, false);
        PageLinkData data = new PageLinkData(templateSource, "posts", frontMatter);

        assertEquals("posts/v2/guides/amqp-dev-services/", pageLink("", ":dir[0]/:slug", data));
    }

    @Test
    void testDirSliceBeyondBounds() {
        JsonObject frontMatter = new JsonObject().put("title", "Getting Started");
        final PageSource templateSource = createPageSource("versions/main/guides/getting-started.md", true, false);
        PageLinkData data = new PageLinkData(templateSource, "versions", frontMatter);

        assertEquals("getting-started/", pageLink("", ":dir[3]/:slug", data));
    }

    // --- :path with explicit slug tests ---

    @Test
    void testPathHonoursExplicitSlug() {
        JsonObject frontMatter = new JsonObject().put("title", "Some Title").put("slug", "custom-slug");
        final PageSource templateSource = createPageSource("posts/my-post.md", true, false);
        PageLinkData data = new PageLinkData(templateSource, "posts", frontMatter);

        assertEquals("posts/custom-slug/", pageLink("", ":path", data));
    }

    @Test
    void testPathIgnoresTitle() {
        JsonObject frontMatter = new JsonObject().put("title", "A Different Title");
        final PageSource templateSource = createPageSource("posts/my-post.md", true, false);
        PageLinkData data = new PageLinkData(templateSource, "posts", frontMatter);

        assertEquals("posts/my-post/", pageLink("", ":path", data));
    }

    @Test
    void testPathExplicitSlugNoDirectory() {
        JsonObject frontMatter = new JsonObject().put("slug", "custom-slug");
        final PageSource templateSource = createPageSource("myfile.md", true, false);
        PageLinkData data = new PageLinkData(templateSource, null, frontMatter);

        assertEquals("custom-slug/", pageLink("", ":path", data));
    }

    @Test
    void testPathExplicitSlugNestedDirectory() {
        JsonObject frontMatter = new JsonObject().put("slug", "my-guide");
        final PageSource templateSource = createPageSource("posts/v2/guides/getting-started.md", true, false);
        PageLinkData data = new PageLinkData(templateSource, "posts", frontMatter);

        assertEquals("posts/v2/guides/my-guide/", pageLink("", ":path", data));
    }

    @Test
    void testPathWithoutSlugUnchanged() {
        JsonObject frontMatter = new JsonObject().put("title", "Whatever");
        final PageSource templateSource = createPageSource("posts/2024-03-02-My-First-Blog-Post.md", true, false);
        PageLinkData data = new PageLinkData(templateSource, "posts", frontMatter);

        assertEquals("posts/2024-03-02-my-first-blog-post/", pageLink("", ":path", data));
    }

    @Test
    void testPathExplicitSlugIsSlugified() {
        JsonObject frontMatter = new JsonObject().put("slug", "My Custom Slug!");
        final PageSource templateSource = createPageSource("posts/my-post.md", true, false);
        PageLinkData data = new PageLinkData(templateSource, "posts", frontMatter);

        assertEquals("posts/my-custom-slug/", pageLink("", ":path", data));
    }

    @Test
    void testPathExplicitSlugIndexPage() {
        JsonObject frontMatter = new JsonObject().put("slug", "custom-slug");
        final PageSource templateSource = createPageSource("posts/my-dir/index.md", true, true);
        PageLinkData data = new PageLinkData(templateSource, "posts", frontMatter);

        assertEquals("posts/custom-slug/", pageLink("", ":path", data));
    }

    @Test
    void testNoColonNoException() {
        JsonObject frontMatter = new JsonObject().put("title", "My First Blog Post");
        final PageSource templateSource = createPageSource("posts/my-first-blog-post.md", true, false);

        PageLinkData data = new PageLinkData(templateSource, null, frontMatter);

        // Test that links without colons don't throw exceptions (efficient early exit)
        assertDoesNotThrow(() -> pageLink("", "/static/path/", data));
        assertDoesNotThrow(() -> pageLink("", "/posts/blog.html", data));
    }

    private PageSource createPageSource(String path, boolean isTargetHtml, boolean isIndex) {
        TemplateSource templateSource = TemplateSource.create(
                path,
                "markdown",
                new SourceFile("/bla", path),
                path,
                "",
                false,
                isTargetHtml,
                isIndex,
                false);

        return new PageSource(templateSource, false,
                ZonedDateTime.parse("2024-08-27T10:15:30+01:00[Europe/Paris]").format(DateTimeFormatter.ISO_ZONED_DATE_TIME),
                PageFiles.empty(), false);
    }
}
