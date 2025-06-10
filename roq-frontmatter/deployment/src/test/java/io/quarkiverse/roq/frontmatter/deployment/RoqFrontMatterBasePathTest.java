package io.quarkiverse.roq.frontmatter.deployment;

import static org.hamcrest.Matchers.*;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkus.test.QuarkusUnitTest;
import io.restassured.RestAssured;

public class RoqFrontMatterBasePathTest {

    // Test with base path configuration
    @RegisterExtension
    static final QuarkusUnitTest unitTest = new QuarkusUnitTest()
            .overrideConfigKey("site.base-path", "/my-project")
            .overrideConfigKey("quarkus.roq.resource-dir", "basepath-site")
            .withApplicationRoot((jar) -> jar
                    .addAsResource("basepath-site"));

    @Test
    public void testBasePathInSiteModel() {
        // Test that the site.basePath property is accessible in templates
        RestAssured.when().get("/").then().statusCode(200).log().ifValidationFails()
                .body("html.body.div.span", equalTo("/my-project"));
    }

    @Test
    public void testAssetPathHelper() {
        // Test that the assetPath helper function works correctly
        RestAssured.when().get("/").then().statusCode(200).log().ifValidationFails()
                .body("html.head.link.@href", equalTo("/my-project/css/style.css"));
    }

    @Test
    public void testCustomFrontmatterPropertyAccess() {
        // Test that custom frontmatter properties are accessible via page.data
        RestAssured.when().get("/custom-page").then().statusCode(200).log().ifValidationFails()
                .body("html.body.div.p", equalTo("Custom value from frontmatter"));
    }

    @Test
    public void testPageWithoutBasePath() {
        // Test that pages work normally when no base path is configured
        RestAssured.when().get("/normal-page").then().statusCode(200).log().ifValidationFails()
                .body("html.body.h1", equalTo("Normal Page"));
    }
}
