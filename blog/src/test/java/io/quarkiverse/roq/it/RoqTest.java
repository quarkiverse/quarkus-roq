package io.quarkiverse.roq.it;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.containsString;

import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

@QuarkusTest
public class RoqTest {

    @Test
    public void testIndex() {
        given().when().get("/").then().statusCode(200).body(containsString(
                        "I provide you with all the tools to generate static websites out of your Quarkus web application."))
                .body(containsString("Hello, world! I&#39;m Roq")).body(containsString("minute read"))
                .body(containsString("Page 1 of")).body(containsString("2024 &copy; ROQ"));
    }

    @Test
    public void testTag() {
        given().when().get("/posts/tag/cool-stuff").then().statusCode(200).body(containsString("cool-stuff"));
    }

    @Test
    public void testPosts() {
        given().when().get("/posts/2024-08-29-welcome-to-roq").then().statusCode(200).body(containsString(
                        "I provide you with all the tools to generate static websites out of your Quarkus web application."))
                .body(containsString("<p>Hello folks,</p>"))
                .body(containsString("<h1 class=\"page-title\">Welcome to Roq!</h1>"))
                .body(containsString("2024 &copy; ROQ"));
    }

    @Test
    public void testPostsAsciidoc() {
        given().when().get("/posts/2024-10-22-asciidoc").then().statusCode(200).body(containsString(
                        "Writing content is AsciiDoc format is an absolut no brainer"))
                .body(containsString("<pre class=\"highlightjs highlight\"><code class=\"language-shell hljs\" data-lang=\"shell\">quarkus extension add io.quarkiverse.roq:quarkus-roq-plugin-asciidoc</code></pre>"))
                .body(containsString("2024 &copy; ROQ"));
    }

    @Test
    public void testPage() {
        given().when().get("/events").then().statusCode(200).body(containsString(
                        "I provide you with all the tools to generate static websites out of your Quarkus web application."))
                .body(containsString("<h2 class=\"event-title\">Roq 1.0 Beta</h2>"))
                .body(containsString("2024 &copy; ROQ"));
    }

    @Test
    public void testAlias() {
        given().when().get("/first-roq-article-ever").then().statusCode(200)
                .body(containsString("url=\"/posts/2024-08-29-welcome-to-roq\""));
    }
}
