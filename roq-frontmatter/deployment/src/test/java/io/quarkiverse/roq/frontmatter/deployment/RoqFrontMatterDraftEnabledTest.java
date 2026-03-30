package io.quarkiverse.roq.frontmatter.deployment;

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
 * Config: site.draft=true
 * <p>
 * Features tested: draft document visibility when drafts are enabled,
 * backward compatibility with published and normal posts.
 */
@DisplayName("Roq FrontMatter - Draft filtering (drafts visible)")
public class RoqFrontMatterDraftEnabledTest {

    @RegisterExtension
    static final QuarkusUnitTest unitTest = new QuarkusUnitTest()
            .overrideConfigKey("quarkus.roq.resource-dir", "draft-site")
            .overrideConfigKey("site.draft", "true") // Enable showing drafts
            .withApplicationRoot((jar) -> jar
                    .addAsResource("draft-site"));

    @Test
    @DisplayName("Draft post is visible when drafts enabled")
    public void testDraftPostVisibleWhenEnabled() {
        // Post with draft: true should be visible when site.draft=true
        RestAssured.when().get("/posts/draft-post").then().statusCode(200).log().ifValidationFails()
                .body("html.head.title", equalTo("Draft Post"))
                .body("html.body.article.h1", equalTo("Draft Post"))
                .body("html.body.article.span.find { it.@class == 'draft-status' }.text()", equalTo("true"));
    }

    @Test
    @DisplayName("Drafts/ directory post is visible when drafts enabled")
    public void testDraftDirectoryPostVisibleWhenEnabled() {
        // Post in drafts/ directory should be visible when site.draft=true
        RestAssured.when().get("/posts/auto-draft-post").then().statusCode(200).log().ifValidationFails()
                .body("html.head.title", equalTo("Auto Draft Post"))
                .body("html.body.article.h1", equalTo("Auto Draft Post"))
                .body("html.body.article.span.find { it.@class == 'draft-status' }.text()", equalTo("true"));
    }

    @Test
    @DisplayName("Explicit draft:false in drafts/ remains false when drafts enabled")
    public void testDraftDirectoryWithExplicitFalseVisibleWhenEnabled() {
        // Frontmatter takes precedence over drafts/: explicit draft: false remains false
        RestAssured.when().get("/posts/override-draft-post").then().statusCode(200).log().ifValidationFails()
                .body("html.head.title", equalTo("Override Draft Post"))
                .body("html.body.article.h1", equalTo("Override Draft Post"))
                .body("html.body.article.span.find { it.@class == 'draft-status' }.text()", equalTo("false"));
    }

    @Test
    @DisplayName("Index shows all 5 posts when drafts enabled")
    public void testIndexContainsAllPostsWhenDraftEnabled() {
        // Index page should show ALL posts when site.draft=true
        RestAssured.when().get("/").then().statusCode(200).log().ifValidationFails()
                .body("html.body.div.h1.size()", equalTo(5)) // All 5 posts visible
                .body("html.body.div.h1*.text()",
                        hasItems("Draft Post", "Published Post", "Normal Post", "Auto Draft Post", "Override Draft Post"));
    }

    @Test
    @DisplayName("Published post remains visible when drafts enabled")
    public void testPublishedPostStillVisible() {
        // Published posts should still be visible
        RestAssured.when().get("/posts/published-post").then().statusCode(200).log().ifValidationFails()
                .body("html.head.title", equalTo("Published Post"));
    }

    @Test
    @DisplayName("Normal post remains visible when drafts enabled")
    public void testNormalPostStillVisible() {
        // Normal posts should still be visible
        RestAssured.when().get("/posts/normal-post").then().statusCode(200).log().ifValidationFails()
                .body("html.head.title", equalTo("Normal Post"));
    }
}
