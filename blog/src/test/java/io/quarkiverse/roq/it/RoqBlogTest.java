
package io.quarkiverse.roq.it;

import io.quarkiverse.roq.testing.RoqAndRoll;
import io.quarkiverse.roq.testing.RoqLinks;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.RestAssured;
import io.restassured.response.ValidatableResponse;
import org.junit.jupiter.api.Test;

import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;

@QuarkusTest
@RoqAndRoll
public class RoqBlogTest {

    @Test
    public void testIndex() {
        RestAssured.when().get("/").then().statusCode(200)
                .log()
                .everything()
                .body(containsString("Static</span> Sites"))
                .body(containsString("Imagined for AI"))
                .body(containsString("&copy; ROQ"))
                .body(containsString("<a href=\"https://central.sonatype.com/artifact/io.quarkiverse.roq/quarkus-roq\""));
    }

    @Test
    public void testTag() {
        RestAssured.when().get("/posts/tag/cool-stuff").then().statusCode(200).body(containsString("cool-stuff"));
    }

    @Test
    public void testSpecialNameFile() {
        RestAssured.when().get("/posts/roq-n-roll-your-tests/c'est de la poussière d'étoile.jpg").then().statusCode(200);
        RestAssured.when().get("/posts/do-you-want-to-publish-a-blog-post-series/series.foo.png").then().statusCode(200);
    }

    @Test
    public void testPosts() {
        RestAssured.when().get("/posts/welcome-to-roq").then().statusCode(200)
                .body(containsString("<p>Hello folks,</p>"))
                .body(containsString("<h1 class=\"page-title\">Welcome to Roq!</h1>"))
                .body(containsString("&copy; ROQ"));
    }

    @Test
    public void testPostsAsciidoc() {
        ValidatableResponse body = RestAssured.when().get("/posts/write-your-blog-posts-in-asciidoc").then().statusCode(200).body(containsString(
                        "Writing content is AsciiDoc format is an absolut no brainer"))
                .body(containsString("<code class=\"language-shell hljs\" data-lang=\"shell\">roq add plugin:asciidoc</code>"))
                .body(containsString("&copy; ROQ"));
        System.out.println(body.extract().body().asString());
    }

    @Test
    public void testPage() {
        RestAssured.when().get("/events").then().statusCode(200)
                .body(containsString("<h2 class=\"event-title\">Roq 2.0</h2>"))
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
    public void testLlmsTxt() {
        RestAssured.when().get("/llms.txt").then().statusCode(200)
                .contentType(containsString("text/plain"))
                .body(containsString("# "))
                .body(containsString("## Posts"))
                .body(containsString("## Pages"))
                .body(containsString("[Welcome to Roq!]"))
                .body(not(containsString("<")));
    }

    @Test
    public void testLlmsFullTxt() {
        RestAssured.when().get("/llms-full.txt").then().statusCode(200)
                .contentType(containsString("text/plain"))
                .body(containsString("# "))
                .body(containsString("## Posts"))
                .body(containsString("### [Welcome to Roq!]"))
                .body(not(containsString("<div")))
                .body(not(containsString("<p>")));
    }

    @Test
    public void testSitemap() {
        RestAssured.when().get("/sitemap.xml").then().statusCode(200)
                .body(containsString("<urlset"))
                .body(containsString("<loc>/</loc>"))
                .body(containsString("<loc>/posts/tag/plugin/</loc>"))
                .body(not(containsString("<loc>/404.html</loc>")))
                .body(containsString("</urlset>"));
    }

    @Test
    public void testLinks() {
        assertFalse(RoqLinks.collect().isEmpty(), "Should collect links from the generated site");
        assertTrue(RoqLinks.checkInternal().isEmpty(), "Should have no broken internal links");
    }
}
