package io.quarkiverse.roq.frontmatter.runtime.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import io.vertx.core.json.JsonObject;

/**
 * Pages bound to a site resolve their images through it, without a CDI container (build-time evaluation).
 */
class PageBindSiteTest {

    private static final RootUrl ROOT = new RootUrl("https://example.com", "/");

    private static PageSource source(String id, boolean siteIndex, List<String> files) {
        return new PageSource(
                TemplateSource.create(id, "html", new SourceFile("/site", "content/" + id), id, "roq-gen/" + id,
                        false, true, true, siteIndex),
                false, null, new PageFiles(files, false), false);
    }

    @Test
    void imageResolvesThroughBoundSite() {
        NormalPage index = new NormalPage(new RoqUrl(ROOT, ""),
                source("index.html", true, List.of("images/logo.png")), new JsonObject(), null);
        NormalPage post = new NormalPage(new RoqUrl(ROOT, "posts/hello/"),
                source("posts/hello/index.html", false, List.of("cover.png")),
                new JsonObject().put("image", "cover.png"), null);
        Site site = new Site(index.url(), "images/", index.data(), List.of(index, post),
                new RoqCollections(Map.of()));

        index.bindSite(site);
        post.bindSite(site);

        assertSame(site, post.site());
        assertEquals("/posts/hello/cover.png", post.image().toString());
        assertEquals("/images/logo.png", index.image("logo.png").toString());
        assertEquals("/images/logo.png", post.image("/logo.png").toString());
    }
}
