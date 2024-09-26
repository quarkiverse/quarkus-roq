package io.quarkiverse.roq.frontmatter.deployment;

import static io.quarkiverse.roq.frontmatter.deployment.Link.*;
import static org.junit.jupiter.api.Assertions.*;

import java.time.ZonedDateTime;

import org.junit.jupiter.api.Test;

import io.vertx.core.json.JsonObject;

class LinkTest {

    @Test
    void testLink() {

        JsonObject frontMatter = new JsonObject()
                .put("title", "My First Blog Post");

        String generatedLink = link("/", ":year/:month/:day/:title", new LinkData("my-first-blog-post",
                ZonedDateTime.parse("2024-08-27T10:15:30+01:00[Europe/Paris]"), null, null, frontMatter));
        assertEquals("2024/08/27/my-first-blog-post", generatedLink);
    }

    @Test
    void testPaginateLink() {
        JsonObject frontMatter = new JsonObject()
                .put("title", "My First Blog Post");

        String generatedLink = link("/", DEFAULT_PAGINATE_LINK_TEMPLATE, new LinkData("my-first-blog-post",
                ZonedDateTime.parse("2024-08-27T10:15:30+01:00[Europe/Paris]"), "posts", "3", frontMatter));
        assertEquals("posts/page3", generatedLink);
    }
}
