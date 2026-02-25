package io.quarkiverse.roq.frontmatter.deployment;

import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasItems;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkus.test.QuarkusUnitTest;
import io.restassured.RestAssured;

public class RoqFrontMatterDraftEnabledTest {

    @RegisterExtension
    static final QuarkusUnitTest unitTest = new QuarkusUnitTest()
            .overrideConfigKey("quarkus.roq.resource-dir", "draft-site")
            .overrideConfigKey("site.draft", "true") // Enable showing drafts
            .withApplicationRoot((jar) -> jar
                    .addAsResource("draft-site"));

    @Test
    public void testDraftPostVisibleWhenEnabled() {
        // Post with draft: true should be visible when site.draft=true
        RestAssured.when().get("/posts/draft-post").then().statusCode(200).log().ifValidationFails()
                .body("html.head.title", equalTo("Draft Post"))
                .body("html.body.article.h1", equalTo("Draft Post"));
    }

    @Test
    public void testDraftDirectoryPostVisibleWhenEnabled() {
        // Post in drafts/ directory should be visible when site.draft=true
        RestAssured.when().get("/posts/auto-draft-post").then().statusCode(200).log().ifValidationFails()
                .body("html.head.title", equalTo("Auto Draft Post"))
                .body("html.body.article.h1", equalTo("Auto Draft Post"));
    }

    @Test
    public void testDraftDirectoryWithExplicitFalseVisibleWhenEnabled() {
        // Post in drafts/ directory should be visible when site.draft=true (even with explicit draft: false)
        RestAssured.when().get("/posts/override-draft-post").then().statusCode(200).log().ifValidationFails()
                .body("html.head.title", equalTo("Override Draft Post"))
                .body("html.body.article.h1", equalTo("Override Draft Post"));
    }

    @Test
    public void testIndexContainsAllPostsWhenDraftEnabled() {
        // Index page should show ALL posts when site.draft=true
        RestAssured.when().get("/").then().statusCode(200).log().ifValidationFails()
                .body("html.body.div.h1.size()", equalTo(5)) // All 5 posts visible
                .body("html.body.div.h1*.text()",
                        hasItems("Draft Post", "Published Post", "Normal Post", "Auto Draft Post", "Override Draft Post"));
    }

    @Test
    public void testPublishedPostStillVisible() {
        // Published posts should still be visible
        RestAssured.when().get("/posts/published-post").then().statusCode(200).log().ifValidationFails()
                .body("html.head.title", equalTo("Published Post"));
    }

    @Test
    public void testNormalPostStillVisible() {
        // Normal posts should still be visible
        RestAssured.when().get("/posts/normal-post").then().statusCode(200).log().ifValidationFails()
                .body("html.head.title", equalTo("Normal Post"));
    }
}
