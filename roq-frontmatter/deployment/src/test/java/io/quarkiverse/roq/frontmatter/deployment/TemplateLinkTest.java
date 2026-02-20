package io.quarkiverse.roq.frontmatter.deployment;

import static io.quarkiverse.roq.frontmatter.deployment.TemplateLink.PageLinkData;
import static io.quarkiverse.roq.frontmatter.deployment.TemplateLink.PaginateLinkData;
import static io.quarkiverse.roq.frontmatter.deployment.TemplateLink.pageLink;
import static io.quarkiverse.roq.frontmatter.deployment.TemplateLink.paginateLink;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;

import org.junit.jupiter.api.Test;

import io.quarkiverse.roq.frontmatter.deployment.exception.RoqTemplateLinkException;
import io.quarkiverse.roq.frontmatter.runtime.model.PageFiles;
import io.quarkiverse.roq.frontmatter.runtime.model.PageSource;
import io.quarkiverse.roq.frontmatter.runtime.model.SourceFile;
import io.quarkiverse.roq.frontmatter.runtime.model.TemplateSource;
import io.vertx.core.json.JsonObject;

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
