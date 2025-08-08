package io.quarkiverse.roq.frontmatter.deployment;

import static io.quarkiverse.roq.frontmatter.deployment.TemplateLink.*;
import static org.junit.jupiter.api.Assertions.*;

import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;

import org.junit.jupiter.api.Test;

import io.quarkiverse.roq.frontmatter.runtime.model.PageFiles;
import io.quarkiverse.roq.frontmatter.runtime.model.PageSource;
import io.quarkiverse.roq.frontmatter.runtime.model.SourceFile;
import io.quarkiverse.roq.frontmatter.runtime.model.TemplateSource;
import io.vertx.core.json.JsonObject;

class TemplateLinkTest {
    @Test
    void testLink() {
        JsonObject frontMatter = new JsonObject().put("title", "My First Blog Post");
        final PageSource templateSource = createPageSource("posts/my-first-blog-post.md", true);

        String generatedLink = pageLink("", ":year/:month/:day/:slug", new PageLinkData(templateSource, null, frontMatter));
        assertEquals("2024/08/27/my-first-blog-post/", generatedLink);
    }

    @Test
    void testLinkExt() {
        JsonObject frontMatter = new JsonObject().put("title", "My First Blog Post");
        final PageSource templateSource = createPageSource("posts/my-first-blog-post.md", true);

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
        final PageSource templateSource = createPageSource("bar/foo.json", false);

        String generatedLink = pageLink("", ":path:ext", new PageLinkData(templateSource, null, frontMatter));
        assertEquals("bar/foo.json", generatedLink);
    }

    @Test
    void testPaginateLink() {
        JsonObject frontMatter = new JsonObject().put("title", "My First Blog Post");
        final PageSource templateSource = createPageSource("posts/my-first-blog-post.md",
                true);

        String generatedLink = paginateLink("foo", null, new PaginateLinkData(templateSource, "posts", "3", frontMatter));
        assertEquals("foo/posts/page3/", generatedLink);
    }

    @Test
    void testSlugCase() {
        JsonObject frontMatter = new JsonObject().put("title", "My First Blog Post");
        final PageSource templateSource = createPageSource("posts/my-first-blog-post.md", true);

        PageLinkData data = new PageLinkData(templateSource, null, frontMatter);
        assertEquals("2024/08/27/my-first-blog-post/", pageLink("", ":year/:month/:day/:slug", data));
        assertEquals("2024/08/27/My-First-Blog-Post/", pageLink("", ":year/:month/:day/:Slug", data));
    }

    private PageSource createPageSource(String path, boolean isTargetHtml) {
        TemplateSource templateSource = TemplateSource.create(
                path,
                "markdown",
                "",
                new SourceFile("/bla", path),
                path,
                "",
                false,
                isTargetHtml,
                false,
                false);

        return new PageSource(templateSource, false,
                ZonedDateTime.parse("2024-08-27T10:15:30+01:00[Europe/Paris]").format(DateTimeFormatter.ISO_ZONED_DATE_TIME),
                PageFiles.empty());
    }
}
