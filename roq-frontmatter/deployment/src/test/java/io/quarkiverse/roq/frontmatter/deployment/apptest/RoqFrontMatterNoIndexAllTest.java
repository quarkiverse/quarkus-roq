package io.quarkiverse.roq.frontmatter.deployment.apptest;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkus.test.QuarkusExtensionTest;
import io.restassured.RestAssured;

/**
 * Site: {@code no-index-all-site} (resource)
 * <p>
 * Config: defaults
 * <p>
 * Features tested: the {@code no-index-all} front matter key excludes a page from llms.txt and defaults its
 * {@code robots} meta tag to {@code noindex}; the {@code llmstxt} and {@code robots} keys win over it.
 */
@DisplayName("Roq FrontMatter - no-index-all")
public class RoqFrontMatterNoIndexAllTest {

    private static final String ROBOTS_NOINDEX = "<meta name=\"robots\" content=\"noindex\" />";

    @RegisterExtension
    static final QuarkusExtensionTest unitTest = new QuarkusExtensionTest()
            .overrideConfigKey("quarkus.roq.resource-dir", "no-index-all-site")
            .withApplicationRoot((jar) -> jar
                    .addAsResource("no-index-all-site"));

    @Test
    @DisplayName("no-index-all: true excludes the page from llms.txt")
    public void testNoIndexAllExcludesFromLlmsTxt() {
        RestAssured.when().get("/llms.txt").then().statusCode(200).log().ifValidationFails()
                .body(containsString("- [Indexed Page](/indexed/)"))
                .body(not(containsString("[No Index All Page]")));
    }

    @Test
    @DisplayName("llmstxt: true wins over no-index-all: true")
    public void testLlmstxtKeyWinsOverNoIndexAll() {
        RestAssured.when().get("/llms.txt").then().statusCode(200).log().ifValidationFails()
                .body(containsString("- [No Index All But Llmstxt Page](/no-index-all-llmstxt/)"));
    }

    @Test
    @DisplayName("llmstxt: false still excludes the page without no-index-all")
    public void testLlmstxtFalseStillExcludes() {
        RestAssured.when().get("/llms.txt").then().statusCode(200).log().ifValidationFails()
                .body(not(containsString("[Llmstxt False Page]")));
    }

    @Test
    @DisplayName("no-index-all: true defaults the robots meta tag to noindex")
    public void testNoIndexAllDefaultsRobotsToNoindex() {
        RestAssured.when().get("/no-index-all").then().statusCode(200).log().ifValidationFails()
                .body(containsString(ROBOTS_NOINDEX));
    }

    @Test
    @DisplayName("robots key wins over no-index-all: true")
    public void testRobotsKeyWinsOverNoIndexAll() {
        RestAssured.when().get("/no-index-all-robots").then().statusCode(200).log().ifValidationFails()
                .body(containsString("<meta name=\"robots\" content=\"index, follow\" />"))
                .body(not(containsString(ROBOTS_NOINDEX)));
    }

    @Test
    @DisplayName("No robots meta tag without no-index-all or robots")
    public void testNoRobotsMetaByDefault() {
        RestAssured.when().get("/indexed").then().statusCode(200).log().ifValidationFails()
                .body(not(containsString("name=\"robots\"")));
        RestAssured.when().get("/llmstxt-false").then().statusCode(200).log().ifValidationFails()
                .body(not(containsString("name=\"robots\"")));
    }
}
