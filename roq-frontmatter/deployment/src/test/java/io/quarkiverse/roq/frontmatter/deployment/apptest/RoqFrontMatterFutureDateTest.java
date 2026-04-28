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
 * Config: defaults (site.future=false)
 * <p>
 * Features tested: future-dated post filtering (filename date, FM date),
 * past-dated posts remain visible, no-date posts use today's date (visible).
 */
@DisplayName("Roq FrontMatter - Future date filtering")
public class RoqFrontMatterFutureDateTest {

    @RegisterExtension
    static final QuarkusUnitTest unitTest = new QuarkusUnitTest()
            .overrideConfigKey("quarkus.roq.resource-dir", "future-site")
            .overrideConfigKey("site.collections.posts", "true")
            .overrideConfigKey("smallrye.config.mapping.validate-unknown", "false")
            .withApplicationRoot((jar) -> jar
                    .addAsResource("future-site"));

    @Test
    @DisplayName("Past post with date in filename is visible")
    public void testPastFilenamePostVisible() {
        RestAssured.when().get("/posts/past-post").then().statusCode(200).log().ifValidationFails()
                .body("html.head.title", equalTo("Past Post"));
    }

    @Test
    @DisplayName("Past post with date in frontmatter is visible")
    public void testPastFmDatePostVisible() {
        RestAssured.when().get("/posts/past-fm-date-post").then().statusCode(200).log().ifValidationFails()
                .body("html.head.title", equalTo("Past FM Date Post"));
    }

    @Test
    @DisplayName("Post with no date uses today (visible)")
    public void testNoDatePostVisible() {
        RestAssured.when().get("/posts/no-date-post").then().statusCode(200).log().ifValidationFails()
                .body("html.head.title", equalTo("No Date Post"));
    }

    @Test
    @DisplayName("Future post with date in filename returns 404")
    public void testFutureFilenamePostNotVisible() {
        RestAssured.when().get("/posts/future-filename-post").then().statusCode(404).log().ifValidationFails();
    }

    @Test
    @DisplayName("Future post with date in frontmatter returns 404")
    public void testFutureFmPostNotVisible() {
        RestAssured.when().get("/posts/future-fm-post").then().statusCode(404).log().ifValidationFails();
    }

    @Test
    @DisplayName("Index only shows past and no-date posts (3 visible)")
    public void testIndexExcludesFuturePosts() {
        RestAssured.when().get("/").then().statusCode(200).log().ifValidationFails()
                .body("html.body.span.find { it.@class == 'posts-count' }.text()", equalTo("3"));
    }

    @Test
    @DisplayName("Index lists correct post titles")
    public void testIndexPostTitles() {
        RestAssured.when().get("/").then().statusCode(200).log().ifValidationFails()
                .body("html.body.span.findAll { it.@class == 'post-title' }*.text()",
                        hasItems("Past Post", "Past FM Date Post", "No Date Post"))
                .body("html.body.span.findAll { it.@class == 'post-title' }*.text()",
                        not(hasItems("Future Filename Post", "Future FM Post")));
    }

    @Test
    @DisplayName("Collection post with no date still has a date (fallback to now)")
    public void testNoDatePostStillHasDate() {
        RestAssured.when().get("/posts/no-date-post").then().statusCode(200).log().ifValidationFails()
                .body("html.body.article.span.find { it.@class == 'has-date' }.text()", equalTo("yes"));
    }
}
