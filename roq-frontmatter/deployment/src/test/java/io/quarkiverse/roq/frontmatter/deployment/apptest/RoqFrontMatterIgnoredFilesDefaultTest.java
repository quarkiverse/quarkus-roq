package io.quarkiverse.roq.frontmatter.deployment.apptest;

import static org.hamcrest.Matchers.containsString;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkus.test.QuarkusExtensionTest;
import io.restassured.RestAssured;

/**
 * Site: {@code ignored-files-site} (resource)
 * <p>
 * Config: defaults, so files whose name starts with an underscore are ignored, and the files the other tests ignore are
 * published.
 */
@DisplayName("Roq FrontMatter - Default ignored files")
public class RoqFrontMatterIgnoredFilesDefaultTest {

    @RegisterExtension
    static final QuarkusExtensionTest unitTest = new QuarkusExtensionTest()
            .overrideConfigKey("quarkus.roq.resource-dir", "ignored-files-site")
            .withApplicationRoot((jar) -> jar
                    .addAsResource("ignored-files-site"));

    @Test
    @DisplayName("A page is published")
    public void testPageIsPublished() {
        RestAssured.when().get("/visible").then().log().ifValidationFails().statusCode(200).body(containsString("visible"));
    }

    @Test
    @DisplayName("The page that another test ignores is published")
    public void testPageIgnoredElsewhereIsPublished() {
        RestAssured.when().get("/ignored").then().log().ifValidationFails().statusCode(200).body(containsString("ignored"));
    }

    @Test
    @DisplayName("A page in a nested directory is published")
    public void testNestedPageIsPublished() {
        RestAssured.when().get("/wip/wip-page").then().log().ifValidationFails().statusCode(200)
                .body(containsString("wip-page"));
    }

    @Test
    @DisplayName("A page whose name starts with an underscore is not published")
    public void testUnderscorePageIsIgnored() {
        RestAssured.when().get("/_hidden").then().log().ifValidationFails().statusCode(404);
    }

    @Test
    @DisplayName("A nested page whose name starts with an underscore is not published")
    public void testNestedUnderscorePageIsIgnored() {
        RestAssured.when().get("/notes/_nested").then().log().ifValidationFails().statusCode(404);
    }

    @Test
    @DisplayName("A public file is published")
    public void testStaticFileIsPublished() {
        RestAssured.when().get("/visible.txt").then().log().ifValidationFails().statusCode(200)
                .body(containsString("visible.txt"));
    }

    @Test
    @DisplayName("The public file that another test ignores is published")
    public void testStaticFileIgnoredElsewhereIsPublished() {
        RestAssured.when().get("/secret.txt").then().log().ifValidationFails().statusCode(200)
                .body(containsString("secret.txt"));
    }

    @Test
    @DisplayName("A public file whose name starts with an underscore is not published")
    public void testUnderscoreStaticFileIsIgnored() {
        RestAssured.when().get("/_redirects").then().log().ifValidationFails().statusCode(404);
    }

    @Test
    @DisplayName("The public file named like the page that another test ignores is published")
    public void testPublicFileNamedLikeAPageIsPublished() {
        RestAssured.when().get("/ignored.html").then().log().ifValidationFails().statusCode(200)
                .body(containsString("ignored.html in public"));
    }
}
