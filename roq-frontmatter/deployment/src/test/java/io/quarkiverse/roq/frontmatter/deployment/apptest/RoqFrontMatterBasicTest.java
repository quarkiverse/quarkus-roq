package io.quarkiverse.roq.frontmatter.deployment.apptest;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkus.test.QuarkusUnitTest;
import io.restassured.RestAssured;

/**
 * Site: {@code basic-site} (resource)
 * <p>
 * Config: defaults
 * <p>
 * Features tested: basic page/post/index rendering with default config,
 * titles, content, layout inheritance, static file serving.
 */
@DisplayName("Roq FrontMatter - Basic rendering")
public class RoqFrontMatterBasicTest {

    @RegisterExtension
    static final QuarkusUnitTest unitTest = new QuarkusUnitTest()
            .overrideConfigKey("quarkus.roq.resource-dir", "basic-site")
            .overrideConfigKey("quarkus.default-locale", "en")
            .overrideConfigKey("site.time-zone", "UTC")
            .withApplicationRoot((jar) -> jar
                    .addAsResource("basic-site"));

    @Test
    @DisplayName("Page renders with title, heading, and data expression")
    public void testPage() {
        RestAssured.when().get("/page/some-page").then().statusCode(200).log().ifValidationFails()
                .body("html.head.title", equalTo("Some page - Simple Site"))
                .body("html.body.article.h1", equalTo("Some page"))
                .body("html.body.article.p", equalTo("We can also use data"));
    }

    @Test
    @DisplayName("Post renders with title, heading, and content")
    public void testPost() {
        RestAssured.when().get("/the-posts/new-post").then().statusCode(200).log().ifValidationFails()
                .body("html.head.title", equalTo("New Post - Simple Site"))
                .body("html.body.article.h1", equalTo("New post with html"))
                .body("html.body.article.p", equalTo("This is a new post."));
    }

    @Test
    @DisplayName("Index page lists posts in order")
    public void testIndex() {
        RestAssured.when().get("/").then().statusCode(200).log().ifValidationFails()
                .body("html.head.title", equalTo("Simple Site"))
                .body("html.body.div.h1[0]", containsString("Override Post"))
                .body("html.body.div.h1[1]", containsString("New Post"));
    }

    @Test
    @DisplayName("Post renders date format extensions")
    public void testDateFormats() {
        RestAssured.when().get("/the-posts/new-post").then().statusCode(200).log().ifValidationFails()
                .body("html.body.article.span.find { it.@class == 'date-iso' }.text()", equalTo("2024-10-09T00:00:00Z"))
                .body("html.body.article.span.find { it.@class == 'date-iso-date' }.text()", equalTo("2024-10-09"))
                .body("html.body.article.span.find { it.@class == 'date-short-date' }.text()", equalTo("Oct 9, 2024"))
                .body("html.body.article.span.find { it.@class == 'date-long-date' }.text()", equalTo("October 9, 2024"))
                .body("html.body.article.span.find { it.@class == 'date-short-time' }.text()", containsString("12:00"))
                .body("html.body.article.span.find { it.@class == 'date-short-time' }.text()", containsString("AM"))
                .body("html.body.article.span.find { it.@class == 'date-long-time' }.text()", containsString("12:00:00"))
                .body("html.body.article.span.find { it.@class == 'date-long-time' }.text()", containsString("AM"))
                .body("html.body.article.span.find { it.@class == 'date-long-time' }.text()", containsString("UTC"))
                .body("html.body.article.span.find { it.@class == 'date-short' }.text()", containsString("Oct 9, 2024"))
                .body("html.body.article.span.find { it.@class == 'date-short' }.text()", containsString("12:00"))
                .body("html.body.article.span.find { it.@class == 'date-long' }.text()", containsString("October 9, 2024"))
                .body("html.body.article.span.find { it.@class == 'date-long' }.text()", containsString("12:00:00"))
                .body("html.body.article.span.find { it.@class == 'date-long' }.text()", containsString("UTC"))
                .body("html.body.article.span.find { it.@class == 'date-rfc822' }.text()",
                        equalTo("Wed, 09 Oct 2024 00:00:00 +0000"));
    }

    @Test
    @DisplayName("Static image file is served")
    public void testStatic() {
        RestAssured.when().get("/images/iamroq.png").then().statusCode(200).log().ifValidationFails();
    }

    @Test
    @DisplayName("Page can render another page's content and contentAbstract")
    public void testPageContent() {
        String body = RestAssured.when().get("/page/content-test").then().statusCode(200).log().ifValidationFails()
                .extract().body().asString();
        // Verify page.content renders the post's inner content (without layout)
        assertTrue(body.contains("New post with html"), "Should contain post heading from content");
        assertTrue(body.contains("This is a new post."), "Should contain post paragraph from content");
        // Verify contentAbstract strips HTML and limits words
        assertTrue(body.contains("post-abstract-"), "Should contain abstract div");
        // Verify content of a post with insert overrides (override-post)
        assertTrue(body.contains("Override post content"), "Should contain override post's heading from content");
        assertTrue(body.contains("This post uses an insert override."),
                "Should contain override post's paragraph from content");
    }

    @Test
    @DisplayName("Post with insert override renders content")
    public void testInsertOverride() {
        // TODO: insert overrides inside fragments not supported yet (https://github.com/quarkusio/quarkus/issues/53518)
        String body = RestAssured.when().get("/the-posts/override-post").then().statusCode(200).log().ifValidationFails()
                .extract().body().asString();
        assertTrue(body.contains("Override post content"), "Should contain the main post content");
    }

    @Test
    @DisplayName("Normal page without date has null date")
    public void testNormalPageNullDate() {
        RestAssured.when().get("/page/some-page").then().statusCode(200).log().ifValidationFails()
                .body("html.body.article.span.find { it.@class == 'has-date' }.text()", equalTo("no"));
    }

    @Test
    @DisplayName("{=expr} is literal text when alt-expr-syntax is disabled")
    public void testAltSyntaxNotActive() {
        RestAssured.when().get("/page/some-page").then().statusCode(200).log().ifValidationFails()
                .body("html.body.article.span.find { it.@class == 'literal' }.text()", equalTo("{=not-alt-syntax}"));
    }

}
