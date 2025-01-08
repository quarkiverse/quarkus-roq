package io.quarkiverse.roq.it;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.response.ValidatableResponse;
import org.junit.jupiter.api.Test;

@QuarkusTest
public class RoqBlogTest {

    @Test
    public void testIndex() {
        given().when().get("/").then().statusCode(200).body(containsString(
                        "A Static Site Generator to easily create a static website or blog using Quarkus super-powers."))
                .body(containsString("Hello, world! I&#39;m Roq")).body(containsString("minute(s) read"))
                .body(containsString("Page 1 of")).body(containsString("&copy; ROQ"));
    }

    @Test
    public void testTag() {
        given().when().get("/posts/tag/cool-stuff").then().statusCode(200).body(containsString("cool-stuff"));
    }

    @Test
    public void testPosts() {
        given().when().get("/posts/welcome-to-roq").then().statusCode(200).body(containsString(
                        "A Static Site Generator to easily create a static website or blog using Quarkus super-powers."))
                .body(containsString("<p>Hello folks,</p>"))
                .body(containsString("<h1 class=\"page-title\">Welcome to Roq!</h1>"))
                .body(containsString("&copy; ROQ"));
    }

    @Test
    public void testPostsAsciidoc() {
        ValidatableResponse body = given().when().get("/posts/write-your-blog-posts-in-asciidoc").then().statusCode(200).body(containsString(
                        "Writing content is AsciiDoc format is an absolut no brainer"))
                .body(containsString("<pre class=\"highlightjs highlight\"><code class=\"language-shell hljs\" data-lang=\"shell\">quarkus extension add io.quarkiverse.roq:quarkus-roq-plugin-asciidoc</code></pre>"))
                .body(containsString("&copy; ROQ"));
        System.out.println(body.extract().body().asString());
    }

    @Test
    public void testPage() {
        given().when().get("/events").then().statusCode(200).body(containsString(
                        "A Static Site Generator to easily create a static website or blog using Quarkus super-powers."))
                .body(containsString("<h2 class=\"event-title\">Roq 1.0 Beta</h2>"))
                .body(containsString("&copy; ROQ"));
    }

    @Test
    public void testAlias() {
        given().when().get("/first-roq-article-ever/").then().statusCode(200)
                .body(containsString("url=\"/posts/welcome-to-roq/\""));
    }

    @Test
    public void testRss() {
        given().when().get("/rss.xml").then().statusCode(200)
                .body(startsWith("<rss xmlns:dc="))
                .body(endsWith("</rss>\n"));
    }

    @Test
    public void testSitemap() {
        given().when().get("/sitemap.xml").then().statusCode(200)
                .body(containsString("<urlset"))
                .body(containsString("<loc>/</loc>"))
                .body(containsString("<loc>/posts/page2/</loc>"))
                .body(containsString("<loc>/posts/tag/plugin/</loc>"))
                .body(not(containsString("<loc>/404.html</loc>")))
                .body(containsString("</urlset>"));
    }
}
