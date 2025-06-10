package io.quarkiverse.roq.frontmatter.deployment;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;

import org.junit.jupiter.api.Test;

import io.quarkiverse.roq.frontmatter.runtime.RoqTemplateExtension;
import io.quarkiverse.roq.frontmatter.runtime.model.NormalPage;
import io.quarkiverse.roq.frontmatter.runtime.model.PageInfo;
import io.quarkiverse.roq.frontmatter.runtime.model.RootUrl;
import io.quarkiverse.roq.frontmatter.runtime.model.RoqCollections;
import io.quarkiverse.roq.frontmatter.runtime.model.RoqUrl;
import io.quarkiverse.roq.frontmatter.runtime.model.Site;
import io.vertx.core.json.JsonObject;

public class RoqTemplateExtensionTest {

    private NormalPage createMockIndexPage() {
        RootUrl rootUrl = new RootUrl("https://example.com", "/");
        RoqUrl pageUrl = new RoqUrl(rootUrl, "/");
        PageInfo pageInfo = PageInfo.create("index", false, null, "", "index.html", "index", null, true, true);
        JsonObject pageData = new JsonObject();
        return new NormalPage(pageUrl, pageInfo, pageData, null);
    }

    @Test
    public void testAssetPathWithBasePath() {
        // Create a site with base path
        RootUrl rootUrl = new RootUrl("https://example.com", "/");
        RoqUrl siteUrl = new RoqUrl(rootUrl, "/");
        JsonObject data = new JsonObject();
        NormalPage indexPage = createMockIndexPage();
        Site site = new Site(siteUrl, "images/", "/my-project", data, List.of(indexPage),
                new RoqCollections(java.util.Map.of()));

        // Test asset path generation
        String result = RoqTemplateExtension.assetPath(site, "/css/style.css");
        assertEquals("/my-project/css/style.css", result);

        // Test with path not starting with /
        result = RoqTemplateExtension.assetPath(site, "js/app.js");
        assertEquals("/my-project/js/app.js", result);
    }

    @Test
    public void testAssetPathWithoutBasePath() {
        // Create a site without base path
        RootUrl rootUrl = new RootUrl("https://example.com", "/");
        RoqUrl siteUrl = new RoqUrl(rootUrl, "/");
        JsonObject data = new JsonObject();
        NormalPage indexPage = createMockIndexPage();
        Site site = new Site(siteUrl, "images/", "", data, List.of(indexPage), new RoqCollections(java.util.Map.of()));

        // Test asset path generation
        String result = RoqTemplateExtension.assetPath(site, "/css/style.css");
        assertEquals("/css/style.css", result);
    }

    @Test
    public void testAssetPathWithBasePathNotStartingWithSlash() {
        // Create a site with base path not starting with /
        RootUrl rootUrl = new RootUrl("https://example.com", "/");
        RoqUrl siteUrl = new RoqUrl(rootUrl, "/");
        JsonObject data = new JsonObject();
        NormalPage indexPage = createMockIndexPage();
        Site site = new Site(siteUrl, "images/", "my-project", data, List.of(indexPage),
                new RoqCollections(java.util.Map.of()));

        // Test asset path generation
        String result = RoqTemplateExtension.assetPath(site, "/css/style.css");
        assertEquals("/my-project/css/style.css", result);
    }

}
