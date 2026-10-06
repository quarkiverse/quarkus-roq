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
 * Config: {@code site.default-ignored-files} replaced so that only the top-level {@code _redirects} of the underscore files is
 * published.
 */
@DisplayName("Roq FrontMatter - Replaced default ignored files")
public class RoqFrontMatterReplacedDefaultIgnoredFilesTest {

    @RegisterExtension
    static final QuarkusExtensionTest unitTest = new QuarkusExtensionTest()
            .overrideConfigKey("quarkus.roq.resource-dir", "ignored-files-site")
            .overrideConfigKey("site.default-ignored-files", "**/_**,regex:_(?!redirects$).*")
            .withApplicationRoot((jar) -> jar
                    .addAsResource("ignored-files-site"));

    @Test
    @DisplayName("The file named _redirects is published")
    public void testRedirectsIsPublished() {
        RestAssured.when().get("/_redirects").then().log().ifValidationFails().statusCode(200)
                .body(containsString("_redirects"));
    }

    @Test
    @DisplayName("Another page whose name starts with an underscore is still ignored")
    public void testOtherUnderscorePageIsIgnored() {
        RestAssured.when().get("/_hidden").then().log().ifValidationFails().statusCode(404);
    }

    @Test
    @DisplayName("A nested page whose name starts with an underscore is still ignored")
    public void testNestedUnderscorePageIsIgnored() {
        RestAssured.when().get("/notes/_nested").then().log().ifValidationFails().statusCode(404);
    }

    @Test
    @DisplayName("A file whose name only starts with _redirects is still ignored")
    public void testRedirectsWithASuffixIsIgnored() {
        RestAssured.when().get("/_redirects.old").then().log().ifValidationFails().statusCode(404);
    }
}
