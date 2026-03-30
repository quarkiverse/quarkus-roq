package io.quarkiverse.roq.frontmatter.deployment.apptest;

import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasItems;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkus.test.QuarkusUnitTest;
import io.restassured.RestAssured;

/**
 * Site: {@code draft-site} (resource)
 * <p>
 * Config: defaults (site.draft=false)
 * <p>
 * Features tested: draft document filtering when drafts are disabled,
 * FM draft flag, drafts/ directory convention, FM override of directory convention.
 */
@DisplayName("Roq FrontMatter - Draft filtering (drafts hidden)")
public class RoqFrontMatterDraftTest {

    @RegisterExtension
    static final QuarkusUnitTest unitTest = new QuarkusUnitTest()
            .overrideConfigKey("quarkus.roq.resource-dir", "draft-site")
            .withApplicationRoot((jar) -> jar
                    .addAsResource("draft-site"));

    @Test
    @DisplayName("Post with draft:true returns 404")
    public void testDraftPostNotVisible() {
        // Post with draft: true in frontmatter should return 404
        RestAssured.when().get("/posts/draft-post").then().statusCode(404).log().ifValidationFails();
    }

    @Test
    @DisplayName("Post with draft:false is visible")
    public void testPublishedPostVisible() {
        // Post with draft: false in frontmatter should be visible
        RestAssured.when().get("/posts/published-post").then().statusCode(200).log().ifValidationFails()
                .body("html.head.title", equalTo("Published Post"))
                .body("html.body.article.h1", equalTo("Published Post"));
    }

    @Test
    @DisplayName("Post without draft flag defaults to visible")
    public void testNoDraftFlagPostVisible() {
        // Post without draft flag in frontmatter should be visible (default is false)
        RestAssured.when().get("/posts/normal-post").then().statusCode(200).log().ifValidationFails()
                .body("html.head.title", equalTo("Normal Post"))
                .body("html.body.article.h1", equalTo("Normal Post"));
    }

    @Test
    @DisplayName("Index excludes draft posts")
    public void testIndexDoesNotContainDraftPosts() {
        // Index page should only show published posts (draft: true and auto-draft are excluded)
        RestAssured.when().get("/").then().statusCode(200).log().ifValidationFails()
                .body("html.body.div.h1.size()", equalTo(3))
                .body("html.body.div.h1*.text()", hasItems("Published Post", "Normal Post", "Override Draft Post"));
    }

    @Test
    @DisplayName("Draft property is accessible from template")
    public void testPageDraftProperty() {
        // Verify that page.draft property is accessible and correct
        RestAssured.when().get("/posts/published-post").then().statusCode(200).log().ifValidationFails()
                .body("html.body.article.span.find { it.@class == 'draft-status' }.text()", equalTo("false"));
    }

    @Test
    @DisplayName("Post in drafts/ directory is hidden")
    public void testDraftDirectoryPostNotVisible() {
        // Post in drafts/ directory without explicit draft flag should be hidden
        RestAssured.when().get("/posts/auto-draft-post").then().statusCode(404).log().ifValidationFails();
    }

    @Test
    @DisplayName("Explicit draft:false in drafts/ directory overrides convention")
    public void testDraftDirectoryWithExplicitFalseVisible() {
        // Frontmatter takes precedence: explicit draft: false in drafts/ should be visible
        RestAssured.when().get("/posts/override-draft-post").then().statusCode(200).log().ifValidationFails()
                .body("html.head.title", equalTo("Override Draft Post"))
                .body("html.body.article.h1", equalTo("Override Draft Post"))
                .body("html.body.article.span.find { it.@class == 'draft-status' }.text()", equalTo("false"));
    }

    @Test
    @DisplayName("Index includes drafts/ post with explicit draft:false")
    public void testIndexIncludesDraftDirectoryPostWithExplicitFalse() {
        // Explicit draft: false in drafts/ behaves as published content
        RestAssured.when().get("/").then().statusCode(200).log().ifValidationFails()
                .body("html.body.div.h1.size()", equalTo(3))
                .body("html.body.div.h1*.text()", hasItems("Published Post", "Normal Post", "Override Draft Post"));
    }
}
