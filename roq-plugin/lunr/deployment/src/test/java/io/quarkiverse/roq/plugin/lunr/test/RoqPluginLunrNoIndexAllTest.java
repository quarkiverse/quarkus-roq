package io.quarkiverse.roq.plugin.lunr.test;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkus.test.QuarkusExtensionTest;
import io.restassured.RestAssured;

/**
 * Site: {@code lunr-site} (resource)
 * <p>
 * Features tested: the {@code no-index-all} front matter key excludes a page from the search index, and the
 * {@code search} key wins over it in both directions.
 */
@DisplayName("Roq Plugin Lunr - no-index-all")
public class RoqPluginLunrNoIndexAllTest {

    @RegisterExtension
    static final QuarkusExtensionTest unitTest = new QuarkusExtensionTest()
            .withApplicationRoot((jar) -> jar
                    .addAsResource("application.properties")
                    .addAsResource("lunr-site"));

    @Test
    @DisplayName("no-index-all: true excludes the page from the search index")
    public void testNoIndexAllExcludesFromSearchIndex() {
        RestAssured.when().get("/search-index.json").then().statusCode(200).log().ifValidationFails()
                .body(containsString("\"title\":\"Indexed Page\""))
                .body(not(containsString("\"title\":\"No Index All Page\"")));
    }

    @Test
    @DisplayName("search: true wins over no-index-all: true")
    public void testSearchKeyWinsOverNoIndexAll() {
        RestAssured.when().get("/search-index.json").then().statusCode(200).log().ifValidationFails()
                .body(containsString("\"title\":\"No Index All But Search Page\""));
    }

    @Test
    @DisplayName("search: false still excludes the page without no-index-all")
    public void testSearchFalseStillExcludes() {
        RestAssured.when().get("/search-index.json").then().statusCode(200).log().ifValidationFails()
                .body(not(containsString("\"title\":\"Search False Page\"")));
    }
}
