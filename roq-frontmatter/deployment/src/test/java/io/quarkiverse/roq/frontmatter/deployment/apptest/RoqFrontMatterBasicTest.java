package io.quarkiverse.roq.frontmatter.deployment.apptest;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;

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
                .body("html.body.div.h1[0]", containsString("New Post"))
                .body("html.body.div.h1[1]", containsString("Some Post"));
    }

    @Test
    @DisplayName("Static image file is served")
    public void testStatic() {
        RestAssured.when().get("/images/iamroq.png").then().statusCode(200).log().ifValidationFails();
    }

    @Test
    @DisplayName("Normal page without date has null date")
    public void testNormalPageNullDate() {
        RestAssured.when().get("/page/some-page").then().statusCode(200).log().ifValidationFails()
                .body("html.body.article.span.find { it.@class == 'has-date' }.text()", equalTo("no"));
    }

}
