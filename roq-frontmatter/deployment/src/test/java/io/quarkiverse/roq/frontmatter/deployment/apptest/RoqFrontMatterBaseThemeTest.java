package io.quarkiverse.roq.frontmatter.deployment.apptest;

import static org.hamcrest.Matchers.containsString;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkus.test.QuarkusUnitTest;
import io.restassured.RestAssured;

/**
 * Site: {@code base-theme-site} (resource)
 * <p>
 * Verifies that roq-base theme layouts work as fallback when no local layouts are provided.
 */
@DisplayName("Roq FrontMatter - Base theme layouts")
public class RoqFrontMatterBaseThemeTest {

    @RegisterExtension
    static final QuarkusUnitTest unitTest = new QuarkusUnitTest()
            .overrideConfigKey("quarkus.roq.resource-dir", "base-theme-site")
            .overrideConfigKey("quarkus.default-locale", "en")
            .overrideConfigKey("site.time-zone", "UTC")
            .withApplicationRoot((jar) -> jar
                    .addAsResource("base-theme-site"));

    @Test
    @DisplayName("Page renders with roq-base page layout")
    public void testPage() {
        RestAssured.when().get("/about").then().statusCode(200).log().ifValidationFails()
                .body(containsString("<h1 class=\"page-title\">About</h1>"))
                .body(containsString("About page using roq-base page layout"));
    }

    @Test
    @DisplayName("Post renders with roq-base post layout")
    public void testPost() {
        RestAssured.when().get("/posts/hello-post").then().statusCode(200).log().ifValidationFails()
                .body(containsString("<h1 class=\"page-title\">Hello Post</h1>"))
                .body(containsString("A post using roq-base post layout."));
    }
}