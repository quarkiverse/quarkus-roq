package io.quarkiverse.roq.frontmatter.deployment.apptest;

import static org.hamcrest.Matchers.*;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkus.test.QuarkusUnitTest;
import io.restassured.RestAssured;

/**
 * Site: {@code timezone-site} (resource)
 * <p>
 * Config: custom date format (dd/MM/yyyy HH:mm), Europe/Paris timezone, site.future=false
 * <p>
 * Features tested: custom date format parsing, timezone applied to parsed dates,
 * future-date filtering works with custom format and timezone,
 * date string in rendered output reflects configured timezone.
 */
@DisplayName("Roq FrontMatter - Custom date format and timezone")
public class RoqFrontMatterTimezoneDateFormatTest {

    @RegisterExtension
    static final QuarkusUnitTest unitTest = new QuarkusUnitTest()
            .overrideConfigKey("quarkus.roq.resource-dir", "timezone-site")
            .overrideConfigKey("site.collections.posts", "true")
            .overrideConfigKey("site.date-format", "dd/MM/yyyy HH:mm")
            .overrideConfigKey("site.time-zone", "Europe/Paris")
            .withApplicationRoot((jar) -> jar
                    .addAsResource("timezone-site"));

    @Test
    @DisplayName("Past post with custom date format is visible")
    public void testPastPostVisible() {
        RestAssured.when().get("/posts/past-post").then().statusCode(200).log().ifValidationFails()
                .body("html.head.title", equalTo("Past Post"));
    }

    @Test
    @DisplayName("Future post with custom date format is filtered out")
    public void testFuturePostFiltered() {
        RestAssured.when().get("/posts/future-post").then().statusCode(404).log().ifValidationFails();
    }

    @Test
    @DisplayName("Index only shows past post")
    public void testIndexOnlyShowsPastPost() {
        RestAssured.when().get("/").then().statusCode(200).log().ifValidationFails()
                .body("html.body.span.find { it.@class == 'posts-count' }.text()", equalTo("1"))
                .body("html.body.span.find { it.@class == 'post-title' }.text()", equalTo("Past Post"));
    }

    @Test
    @DisplayName("Rendered date contains Europe/Paris timezone")
    public void testRenderedDateContainsTimezone() {
        RestAssured.when().get("/posts/past-post").then().statusCode(200).log().ifValidationFails()
                .body("html.body.article.span.find { it.@class == 'page-date' }.text()",
                        containsString("Europe/Paris"));
    }
}
