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
 * Config: {@code site.ignored-files}, whose patterns match the whole path relative to {@code content/} and {@code public/}.
 */
@DisplayName("Roq FrontMatter - Ignored files")
public class RoqFrontMatterIgnoredFilesTest {

    @RegisterExtension
    static final QuarkusExtensionTest unitTest = new QuarkusExtensionTest()
            .overrideConfigKey("quarkus.roq.resource-dir", "ignored-files-site")
            .overrideConfigKey("site.ignored-files", "ignored.html,secret.txt,public/other.txt,wip/**,!_redirects")
            .withApplicationRoot((jar) -> jar
                    .addAsResource("ignored-files-site"));

    @Test
    @DisplayName("An ignored page is not published")
    public void testIgnoredPage() {
        RestAssured.when().get("/ignored").then().log().ifValidationFails().statusCode(404);
    }

    @Test
    @DisplayName("A page that no pattern matches is published")
    public void testOtherPageIsPublished() {
        RestAssured.when().get("/visible").then().log().ifValidationFails().statusCode(200).body(containsString("visible"));
    }

    @Test
    @DisplayName("A page in an ignored directory is not published")
    public void testIgnoredDirectory() {
        RestAssured.when().get("/wip/wip-page").then().log().ifValidationFails().statusCode(404);
    }

    @Test
    @DisplayName("A pattern matches the whole path, not a nested directory of the same name")
    public void testPatternMatchesTheWholePath() {
        RestAssured.when().get("/notes/wip/nested-wip-page").then().log().ifValidationFails().statusCode(200)
                .body(containsString("nested-wip-page"));
    }

    @Test
    @DisplayName("An ignored public file is not published")
    public void testIgnoredStaticFile() {
        RestAssured.when().get("/secret.txt").then().log().ifValidationFails().statusCode(404);
    }

    @Test
    @DisplayName("A pattern relative to the site directory does not match")
    public void testPatternRelativeToSiteDirectoryDoesNotMatch() {
        RestAssured.when().get("/other.txt").then().log().ifValidationFails().statusCode(200).body(containsString("other.txt"));
    }

    @Test
    @DisplayName("A pattern starting with ! does not publish an ignored file")
    public void testExclamationMarkDoesNotPublish() {
        RestAssured.when().get("/_redirects").then().log().ifValidationFails().statusCode(404);
    }

    @Test
    @DisplayName("The default ignored files are still ignored")
    public void testDefaultsStillApply() {
        RestAssured.when().get("/_hidden").then().log().ifValidationFails().statusCode(404);
    }

    @Test
    @DisplayName("A pattern applies to the public directory too")
    public void testPatternAppliesToEveryScannedDirectory() {
        RestAssured.when().get("/ignored.html").then().log().ifValidationFails().statusCode(404);
    }
}
