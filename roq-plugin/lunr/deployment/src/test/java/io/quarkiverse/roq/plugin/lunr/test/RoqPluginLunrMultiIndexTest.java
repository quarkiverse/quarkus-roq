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
 * Features tested: pages declare their indexes with {@code search-indexes} (directly or through a layout), and
 * each index file renders only the pages of the index named by its {@code search-index-name}.
 */
@DisplayName("Roq Plugin Lunr - multiple indexes")
public class RoqPluginLunrMultiIndexTest {

    @RegisterExtension
    static final QuarkusExtensionTest unitTest = new QuarkusExtensionTest()
            .withApplicationRoot((jar) -> jar
                    .addAsResource("application.properties")
                    .addAsResource("lunr-site"));

    @Test
    @DisplayName("Default index keeps pages without search-indexes")
    public void testDefaultIndexKeepsPagesWithoutKey() {
        RestAssured.when().get("/search-index.json").then().statusCode(200).log().ifValidationFails()
                .body(containsString("\"title\":\"Indexed Page\""));
    }

    @Test
    @DisplayName("Default index excludes pages that only belong to another index")
    public void testDefaultIndexExcludesOtherIndexPages() {
        RestAssured.when().get("/search-index.json").then().statusCode(200).log().ifValidationFails()
                .body(not(containsString("\"title\":\"Pancakes Recipe\"")));
    }

    @Test
    @DisplayName("Named index contains the pages of its layout's search-indexes")
    public void testNamedIndexContainsLayoutPages() {
        RestAssured.when().get("/recipes-search-index.json").then().statusCode(200).log().ifValidationFails()
                .body(containsString("\"title\":\"Pancakes Recipe\""));
    }

    @Test
    @DisplayName("Named index excludes pages of other indexes")
    public void testNamedIndexExcludesOtherPages() {
        RestAssured.when().get("/recipes-search-index.json").then().statusCode(200).log().ifValidationFails()
                .body(not(containsString("\"title\":\"Indexed Page\"")));
    }

    @Test
    @DisplayName("A page listing several indexes appears in each of them")
    public void testPageInSeveralIndexes() {
        RestAssured.when().get("/recipes-search-index.json").then().statusCode(200).log().ifValidationFails()
                .body(containsString("\"title\":\"Soup Recipe\""));
        RestAssured.when().get("/search-index.json").then().statusCode(200).log().ifValidationFails()
                .body(containsString("\"title\":\"Soup Recipe\""));
    }

    @Test
    @DisplayName("search: false keeps a page out of every index")
    public void testSearchFalseExcludedFromEveryIndex() {
        RestAssured.when().get("/recipes-search-index.json").then().statusCode(200).log().ifValidationFails()
                .body(not(containsString("\"title\":\"Secret Recipe\"")));
        RestAssured.when().get("/search-index.json").then().statusCode(200).log().ifValidationFails()
                .body(not(containsString("\"title\":\"Secret Recipe\"")));
    }

    @Test
    @DisplayName("Hand-written index files win: no index is generated for a declared name")
    public void testDeclaredIndexesNotGenerated() {
        RestAssured.when().get("/search-index/recipes.json").then().statusCode(404);
        RestAssured.when().get("/search-index/default.json").then().statusCode(404);
    }
}
