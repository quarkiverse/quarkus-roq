package io.quarkiverse.roq.frontmatter.deployment.apptest;

import static org.hamcrest.Matchers.containsString;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkus.test.QuarkusExtensionTest;
import io.restassured.RestAssured;

/**
 * Site: {@code base-starter-site} (resource)
 * <p>
 * Mirrors the files provided by the {@code roq-base-theme-codestart} (local default layout, about page and blog listing)
 * and verifies they render on top of the roq-base theme layouts.
 */
@DisplayName("Roq FrontMatter - Base theme starter files")
public class RoqFrontMatterBaseStarterTest {

    @RegisterExtension
    static final QuarkusExtensionTest unitTest = new QuarkusExtensionTest()
            .overrideConfigKey("quarkus.roq.resource-dir", "base-starter-site")
            .overrideConfigKey("quarkus.default-locale", "en")
            .overrideConfigKey("site.time-zone", "UTC")
            .withApplicationRoot((jar) -> jar
                    .addAsResource("base-starter-site"));

    @Test
    @DisplayName("Index renders with the local default layout (header/footer)")
    public void testIndex() {
        RestAssured.when().get("/").then().statusCode(200).log().ifValidationFails()
                .body(containsString("class=\"site-header\""))
                .body(containsString("<h1>Base Starter Site</h1>"))
                .body(containsString("class=\"site-footer\""));
    }

    @Test
    @DisplayName("About page renders with roq-base page layout wrapped in the local default layout")
    public void testAbout() {
        RestAssured.when().get("/about").then().statusCode(200).log().ifValidationFails()
                .body(containsString("class=\"site-header\""))
                .body(containsString("<h1 class=\"page-title\">About</h1>"))
                .body(containsString("make this page your own"));
    }

    @Test
    @DisplayName("Blog page lists posts")
    public void testBlog() {
        RestAssured.when().get("/blog").then().statusCode(200).log().ifValidationFails()
                .body(containsString("class=\"post-list\""))
                .body(containsString("Hello Post"))
                .body(containsString("The first post of this starter blog."));
    }
}
