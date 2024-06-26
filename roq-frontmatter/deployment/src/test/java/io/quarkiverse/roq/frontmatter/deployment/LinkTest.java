package io.quarkiverse.roq.frontmatter.deployment;

import static io.quarkiverse.roq.frontmatter.deployment.Link.link;
import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

import io.vertx.core.json.JsonObject;

class LinkTest {

    @Test
    void testLink() {

        JsonObject frontMatter = new JsonObject()
                .put("title", "My First Blog Post")
                .put("year", "2024")
                .put("month", "08")
                .put("day", "27")
                .put("base-filename", "my-first-blog-post")
                .put("link", "/:year/:month/:day/:title/");

        String generatedLink = link(frontMatter);
        assertEquals("/2024/08/27/my-first-blog-post/", generatedLink);
    }
}
