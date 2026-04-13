package io.quarkiverse.roq.it;

import io.quarkiverse.roq.testing.RoqAndRoll;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.QuarkusTestProfile;
import io.quarkus.test.junit.TestProfile;
import io.restassured.RestAssured;
import io.restassured.response.ValidatableResponse;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.hamcrest.Matchers.*;

@QuarkusTest
@RoqAndRoll
@TestProfile(RoqBlogSlugifiedFilesTest.SlugifyFilesConfig.class)
public class RoqBlogSlugifiedFilesTest {
    public static final String TITLE = "Hello, world! I’m Roq";
    public static final String DESCRIPTION = "A static site generator (SSG) that makes it fun and easy to build websites and blogs.";

    @Test
    public void testIndex() {
        RestAssured.when().get("/").then().statusCode(200)
                .log()
                .everything().body(containsString(
                        DESCRIPTION))
                .body(containsString(TITLE)).body(containsString("minute(s) read"))
                .body(containsString("Page 1 of")).body(containsString("&copy; ROQ"));
    }

    @Test
    public void testTag() {
        RestAssured.when().get("/posts/tag/cool-stuff").then().statusCode(200).body(containsString("cool-stuff"));
    }

    @Test
    public void testSlugifyFile() {
        RestAssured.when().get("/posts/roq-n-roll-your-tests/c-est-de-la-poussi-re-d-toile.jpg").then().statusCode(200);
        RestAssured.when().get("/posts/do-you-want-to-publish-a-blog-post-series/series.foo.png").then().statusCode(200);
    }

    @Test
    public void testPosts() {
        RestAssured.when().get("/posts/welcome-to-roq").then().statusCode(200).body(containsString(
                        DESCRIPTION))
                .body(containsString("<p>Hello folks,</p>"))
                .body(containsString("<h1 class=\"page-title\">Welcome to Roq!</h1>"))
                .body(containsString("&copy; ROQ"));
    }

    @Test
    public void testPostsAsciidoc() {
        ValidatableResponse body = RestAssured.when().get("/posts/write-your-blog-posts-in-asciidoc").then().statusCode(200).body(containsString(
                        "Writing content is AsciiDoc format is an absolut no brainer"))
                .body(containsString("<pre class=\"highlightjs highlight\"><code class=\"language-shell hljs\" data-lang=\"shell\">quarkus extension add io.quarkiverse.roq:quarkus-roq-plugin-asciidoc</code></pre>"))
                .body(containsString("&copy; ROQ"));
        System.out.println(body.extract().body().asString());
    }

    @Test
    public void testPage() {
        RestAssured.when().get("/events").then().statusCode(200).body(containsString(
                        DESCRIPTION))
                .body(containsString("<h2 class=\"event-title\">Roq 1.0 Beta</h2>"))
                .body(containsString("&copy; ROQ"));
    }

    @Test
    public void testAlias() {
        RestAssured.when().get("/first-roq-article-ever/").then().statusCode(200)
                .body(containsString("url=\"/posts/welcome-to-roq/\""));
    }

    @Test
    public void testRss() {
        RestAssured.when().get("/rss.xml").then().statusCode(200)
                .body(startsWith("<rss xmlns:dc="))
                .body(endsWith("</rss>\n"));
    }

    @Test
    public void testSitemap() {
        RestAssured.when().get("/sitemap.xml").then().statusCode(200)
                .body(containsString("<urlset"))
                .body(containsString("<loc>/</loc>"))
                .body(containsString("<loc>/posts/page2/</loc>"))
                .body(containsString("<loc>/posts/tag/plugin/</loc>"))
                .body(not(containsString("<loc>/404.html</loc>")))
                .body(containsString("</urlset>"));
    }


    public static class SlugifyFilesConfig implements QuarkusTestProfile {

        @Override
        public Map<String, String> getConfigOverrides() {
            return Map.of("site.slugify-files", "true");
        }
    }



}
