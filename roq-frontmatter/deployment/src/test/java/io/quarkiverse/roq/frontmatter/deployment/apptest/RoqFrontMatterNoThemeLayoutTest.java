package io.quarkiverse.roq.frontmatter.deployment.apptest;

import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.containsString;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkus.test.QuarkusUnitTest;
import io.restassured.RestAssured;

/**
 * Site: {@code no-theme-site} (local)
 * <p>
 * No theme configured. Only local layouts available.
 * Verifies layout resolution works without a theme dependency.
 */
@DisplayName("Roq FrontMatter - No theme layout resolution")
public class RoqFrontMatterNoThemeLayoutTest {

    @RegisterExtension
    static final QuarkusUnitTest unitTest = new QuarkusUnitTest()
            .overrideConfigKey("quarkus.roq.dir", "src/test/no-theme-site");

    @Test
    @DisplayName("Local layout resolves without theme")
    public void testLocalLayoutNoTheme() {
        RestAssured.when().get("/pages/local-only").then().statusCode(200).log().ifValidationFails()
                .body(allOf(
                        containsString("no-theme-custom"),
                        containsString("Local layout only, no theme")));
    }
}
