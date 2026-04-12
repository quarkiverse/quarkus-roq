package io.quarkiverse.roq.frontmatter.deployment.apptest;

import static org.hamcrest.Matchers.*;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkus.test.QuarkusUnitTest;
import io.restassured.RestAssured;

/**
 * Site: {@code future-site} (resource)
 * <p>
 * Config: site.future=true
 * <p>
 * Features tested: future-dated posts ARE visible when site.future=true,
 * past-dated and no-date posts remain visible.
 */
@DisplayName("Roq FrontMatter - Future date filtering (future visible)")
public class RoqFrontMatterFutureDateEnabledTest {

    @RegisterExtension
    static final QuarkusUnitTest unitTest = new QuarkusUnitTest()
            .overrideConfigKey("quarkus.roq.resource-dir", "future-site")
            .overrideConfigKey("site.future", "true")
            .overrideConfigKey("site.collections.posts", "true")
            .withApplicationRoot((jar) -> jar
                    .addAsResource("future-site"));

    @Test
    @DisplayName("Future filename post is visible when future enabled")
    public void testFutureFilenamePostVisible() {
        RestAssured.when().get("/posts/future-filename-post").then().statusCode(200).log().ifValidationFails()
                .body("html.head.title", equalTo("Future Filename Post"));
    }

    @Test
    @DisplayName("Future FM date post is visible when future enabled")
    public void testFutureFmPostVisible() {
        RestAssured.when().get("/posts/future-fm-post").then().statusCode(200).log().ifValidationFails()
                .body("html.head.title", equalTo("Future FM Post"));
    }

    @Test
    @DisplayName("Past posts remain visible when future enabled")
    public void testPastPostsStillVisible() {
        RestAssured.when().get("/posts/past-post").then().statusCode(200).log().ifValidationFails()
                .body("html.head.title", equalTo("Past Post"));
        RestAssured.when().get("/posts/past-fm-date-post").then().statusCode(200).log().ifValidationFails()
                .body("html.head.title", equalTo("Past FM Date Post"));
    }

    @Test
    @DisplayName("No-date post remains visible when future enabled")
    public void testNoDatePostStillVisible() {
        RestAssured.when().get("/posts/no-date-post").then().statusCode(200).log().ifValidationFails()
                .body("html.head.title", equalTo("No Date Post"));
    }

    @Test
    @DisplayName("Index shows all 5 posts when future enabled")
    public void testIndexShowsAllPosts() {
        RestAssured.when().get("/").then().statusCode(200).log().ifValidationFails()
                .body("html.body.span.find { it.@class == 'posts-count' }.text()", equalTo("5"))
                .body("html.body.span.findAll { it.@class == 'post-title' }*.text()",
                        hasItems("Past Post", "Past FM Date Post", "No Date Post",
                                "Future Filename Post", "Future FM Post"));
    }
}
