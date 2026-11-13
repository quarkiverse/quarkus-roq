package io.quarkiverse.roq.frontmatter.deployment.apptest;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkus.test.QuarkusExtensionTest;
import io.restassured.RestAssured;

/**
 * Verifies the app starts when there are no layouts and no pages at all
 * (e.g. all files are ignored, to only use the data).
 * <p>
 * Unlike {@link RoqFrontMatterNoPagesTest}, where base layouts still come from the classpath,
 * nothing is left here, so the {@code Sources} bean is not produced.
 */
@DisplayName("Roq FrontMatter - No layouts and no pages")
public class RoqFrontMatterNoLayoutsNoPagesTest {

    @RegisterExtension
    static final QuarkusExtensionTest unitTest = new QuarkusExtensionTest()
            .overrideConfigKey("quarkus.roq.resource-dir", "ignored-files-site")
            .overrideConfigKey("site.ignored-files", "**")
            .withApplicationRoot((jar) -> jar
                    .addAsResource("ignored-files-site"));

    @Test
    @DisplayName("App starts and serves no pages")
    public void testStartsWithoutLayoutsAndPages() {
        RestAssured.when().get("/").then().statusCode(404).log().ifValidationFails();
        RestAssured.when().get("/visible").then().statusCode(404).log().ifValidationFails();
    }
}
