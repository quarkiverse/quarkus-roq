package io.quarkiverse.roq.frontmatter.deployment;

import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasItems;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkus.test.QuarkusUnitTest;
import io.restassured.RestAssured;

public class RoqFrontMatterDraftTest {

    @RegisterExtension
    static final QuarkusUnitTest unitTest = new QuarkusUnitTest()
            .overrideConfigKey("quarkus.roq.resource-dir", "draft-site")
            .withApplicationRoot((jar) -> jar
                    .addAsResource("draft-site"));

    @Test
    public void testDraftPostNotVisible() {
        // Post with draft: true in frontmatter should return 404
        RestAssured.when().get("/posts/draft-post").then().statusCode(404).log().ifValidationFails();
    }

    @Test
    public void testPublishedPostVisible() {
        // Post with draft: false in frontmatter should be visible
        RestAssured.when().get("/posts/published-post").then().statusCode(200).log().ifValidationFails()
                .body("html.head.title", equalTo("Published Post"))
                .body("html.body.article.h1", equalTo("Published Post"));
    }

    @Test
    public void testNoDraftFlagPostVisible() {
        // Post without draft flag in frontmatter should be visible (default is false)
        RestAssured.when().get("/posts/normal-post").then().statusCode(200).log().ifValidationFails()
                .body("html.head.title", equalTo("Normal Post"))
                .body("html.body.article.h1", equalTo("Normal Post"));
    }

    @Test
    public void testIndexDoesNotContainDraftPosts() {
        // Index page should only show published posts (not those with draft: true or in drafts/)
        RestAssured.when().get("/").then().statusCode(200).log().ifValidationFails()
                .body("html.body.div.h1.size()", equalTo(2)) // Only 2 published posts (not in drafts/)
                .body("html.body.div.h1*.text()", hasItems("Published Post", "Normal Post"));
    }

    @Test
    public void testPageDraftProperty() {
        // Verify that page.draft property is accessible and correct
        RestAssured.when().get("/posts/published-post").then().statusCode(200).log().ifValidationFails()
                .body("html.body.article.span.find { it.@class == 'draft-status' }.text()", equalTo("false"));
    }

    @Test
    public void testDraftDirectoryPostNotVisible() {
        // Post in drafts/ directory without explicit draft flag should be hidden
        RestAssured.when().get("/posts/auto-draft-post").then().statusCode(404).log().ifValidationFails();
    }

    @Test
    public void testDraftDirectoryWithExplicitFalseNotVisible() {
        // Post in drafts/ directory with explicit draft: false should STILL be hidden (directory takes precedence)
        RestAssured.when().get("/posts/override-draft-post").then().statusCode(404).log().ifValidationFails();
    }

    @Test
    public void testIndexExcludesDraftDirectoryPosts() {
        // Index page should exclude all posts from drafts/ directory (directory takes precedence)
        RestAssured.when().get("/").then().statusCode(200).log().ifValidationFails()
                .body("html.body.div.h1.size()", equalTo(2)) // 2 published posts only
                .body("html.body.div.h1*.text()", hasItems("Published Post", "Normal Post"));
    }
}
