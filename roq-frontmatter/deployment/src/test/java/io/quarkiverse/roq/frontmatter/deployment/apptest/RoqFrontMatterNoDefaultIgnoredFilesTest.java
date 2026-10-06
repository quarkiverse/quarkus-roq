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
 * Config: {@code site.default-ignored-files} set to a pattern that matches nothing, so the files whose name starts with an
 * underscore are published.
 */
@DisplayName("Roq FrontMatter - No default ignored files")
public class RoqFrontMatterNoDefaultIgnoredFilesTest {

    @RegisterExtension
    static final QuarkusExtensionTest unitTest = new QuarkusExtensionTest()
            .overrideConfigKey("quarkus.roq.resource-dir", "ignored-files-site")
            .overrideConfigKey("site.default-ignored-files", "nomatch")
            .withApplicationRoot((jar) -> jar
                    .addAsResource("ignored-files-site"));

    @Test
    @DisplayName("A page whose name starts with an underscore is published")
    public void testUnderscorePageIsPublished() {
        RestAssured.when().get("/_hidden").then().log().ifValidationFails().statusCode(200).body(containsString("_hidden"));
    }

    @Test
    @DisplayName("A nested page whose name starts with an underscore is published")
    public void testNestedUnderscorePageIsPublished() {
        RestAssured.when().get("/notes/_nested").then().log().ifValidationFails().statusCode(200)
                .body(containsString("_nested"));
    }

    @Test
    @DisplayName("The file named _redirects is published")
    public void testRedirectsIsPublished() {
        RestAssured.when().get("/_redirects").then().log().ifValidationFails().statusCode(200)
                .body(containsString("_redirects"));
    }

    @Test
    @DisplayName("The file named _redirects.old is published")
    public void testRedirectsWithASuffixIsPublished() {
        RestAssured.when().get("/_redirects.old").then().log().ifValidationFails().statusCode(200)
                .body(containsString("_redirects.old"));
    }
}
