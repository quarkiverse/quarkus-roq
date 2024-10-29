package io.quarkiverse.roq.frontmatter.deployment;

import static io.quarkiverse.roq.frontmatter.deployment.TemplateLink.*;
import static org.junit.jupiter.api.Assertions.*;

import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;

import org.junit.jupiter.api.Test;

import io.quarkiverse.roq.frontmatter.runtime.model.PageInfo;
import io.vertx.core.json.JsonObject;

class TemplateLinkTest {

    @Test
    void testLink() {

        JsonObject frontMatter = new JsonObject()
                .put("title", "My First Blog Post");
        final PageInfo pageInfo = PageInfo.create("_posts/my-first-blog-post.md", false, "images",
                ZonedDateTime.parse("2024-08-27T10:15:30+01:00[Europe/Paris]").format(DateTimeFormatter.ISO_ZONED_DATE_TIME),
                "", "_posts/my-first-blog-post.md", "");
        String generatedLink = pageLink("/", ":year/:month/:day/:slug", new PageLinkData(pageInfo, null, frontMatter));
        assertEquals("2024/08/27/my-first-blog-post", generatedLink);
    }

    @Test
    void testLinkExt() {

        JsonObject frontMatter = new JsonObject()
                .put("title", "My First Blog Post");
        final PageInfo pageInfo = PageInfo.create("posts/my-first-blog-post.md", false, "images",
                ZonedDateTime.parse("2024-08-27T10:15:30+01:00[Europe/Paris]").format(DateTimeFormatter.ISO_ZONED_DATE_TIME),
                "", "_posts/my-first-blog-post.md", "");
        String generatedLink = pageLink("/", ":year/:month/:day/:slug:ext!", new PageLinkData(pageInfo, null, frontMatter));
        assertEquals("2024/08/27/my-first-blog-post.html", generatedLink);
        String generatedLink2 = pageLink("/", ":year/:month/:day/:slug:ext", new PageLinkData(pageInfo, null, frontMatter));
        assertEquals("2024/08/27/my-first-blog-post", generatedLink2);
    }

    @Test
    void testLinkJson() {

        JsonObject frontMatter = new JsonObject();
        final PageInfo pageInfo = PageInfo.create("foo.json", false, "images",
                ZonedDateTime.parse("2024-08-27T10:15:30+01:00[Europe/Paris]").format(DateTimeFormatter.ISO_ZONED_DATE_TIME),
                "", "bar/foo.json", "");
        String generatedLink = pageLink("/", ":path:ext", new PageLinkData(pageInfo, null, frontMatter));
        assertEquals("bar/foo.json", generatedLink);
    }

    @Test
    void testPaginateLink() {
        JsonObject frontMatter = new JsonObject()
                .put("title", "My First Blog Post");

        final PageInfo pageInfo = PageInfo.create("posts/my-first-blog-post.html", false, "images",
                ZonedDateTime.parse("2024-08-27T10:15:30+01:00[Europe/Paris]").format(DateTimeFormatter.ISO_ZONED_DATE_TIME),
                "", "", "_posts/my-first-blog-post.md");
        String generatedLink = paginateLink("/", null,
                new PaginateLinkData(pageInfo, "posts", "3", frontMatter));
        assertEquals("posts/page3", generatedLink);
    }
}
