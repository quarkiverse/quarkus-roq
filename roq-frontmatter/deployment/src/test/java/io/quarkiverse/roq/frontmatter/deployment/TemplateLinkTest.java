package io.quarkiverse.roq.frontmatter.deployment;

import static io.quarkiverse.roq.frontmatter.deployment.TemplateLink.*;
import static org.junit.jupiter.api.Assertions.*;

import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

import org.junit.jupiter.api.Test;

import io.quarkiverse.roq.frontmatter.runtime.model.PageFiles;
import io.quarkiverse.roq.frontmatter.runtime.model.PageInfo;
import io.vertx.core.json.JsonObject;

class TemplateLinkTest {
    @Test
    void testLink() {
        JsonObject frontMatter = new JsonObject().put("title", "My First Blog Post");
        final PageInfo pageInfo = createPageInfo("posts/my-first-blog-post.md", "posts/my-first-blog-post.md", true, true);

        String generatedLink = pageLink("", ":year/:month/:day/:slug", new PageLinkData(pageInfo, null, frontMatter));
        assertEquals("2024/08/27/my-first-blog-post/", generatedLink);
    }

    @Test
    void testLinkExt() {
        JsonObject frontMatter = new JsonObject().put("title", "My First Blog Post");
        final PageInfo pageInfo = createPageInfo("posts/my-first-blog-post.md", "posts/my-first-blog-post.md", true, true);

        String generatedLink = pageLink("", ":year/:month/:day/:slug:ext!", new PageLinkData(pageInfo, null, frontMatter));
        assertEquals("2024/08/27/my-first-blog-post.html", generatedLink);

        String generatedLink2 = pageLink("", ":year/:month/:day/:slug:ext", new PageLinkData(pageInfo, null, frontMatter));
        assertEquals("2024/08/27/my-first-blog-post/", generatedLink2);
    }

    @Test
    void testLinkJson() {
        JsonObject frontMatter = new JsonObject();
        final PageInfo pageInfo = createPageInfo("foo.json", "bar/foo.json", false, false);

        String generatedLink = pageLink("", ":path:ext", new PageLinkData(pageInfo, null, frontMatter));
        assertEquals("bar/foo.json", generatedLink);
    }

    @Test
    void testPaginateLink() {
        JsonObject frontMatter = new JsonObject().put("title", "My First Blog Post");
        final PageInfo pageInfo = createPageInfo("posts/my-first-blog-post.html", "posts/my-first-blog-post.md", true, true);

        String generatedLink = paginateLink("foo", null, new PaginateLinkData(pageInfo, "posts", "3", frontMatter));
        assertEquals("foo/posts/page3/", generatedLink);
    }

    @Test
    void testSlugCase() {
        JsonObject frontMatter = new JsonObject().put("title", "My First Blog Post");
        final PageInfo pageInfo = createPageInfo("posts/my-first-blog-post.md", "posts/my-first-blog-post.md", true, true);

        PageLinkData data = new PageLinkData(pageInfo, null, frontMatter);
        assertEquals("2024/08/27/my-first-blog-post/", pageLink("", ":year/:month/:day/:slug", data));
        assertEquals("2024/08/27/My-First-Blog-Post/", pageLink("", ":year/:month/:day/:Slug", data));
    }

    private PageInfo createPageInfo(String sourcePath, String contentPath, boolean draft, boolean indexable) {
        return PageInfo.create(
                sourcePath,
                false,
                ZonedDateTime.parse("2024-08-27T10:15:30+01:00[Europe/Paris]").format(DateTimeFormatter.ISO_ZONED_DATE_TIME),
                "",
                contentPath,
                "",
                new PageFiles(List.of(), true),
                indexable,
                draft);
    }
}
