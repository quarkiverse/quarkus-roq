package io.quarkiverse.roq.plugin.lunr.test;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkus.test.QuarkusExtensionTest;
import io.restassured.RestAssured;

/**
 * Site: {@code lunr-site} (resource) without {@code content/recipes-search-index.json}.
 * <p>
 * Features tested: the hand-written {@code content/search-index.json} stays the {@code default} index (the build
 * would fail on a path conflict if it were generated too), and the missing {@code recipes} index is generated.
 */
@DisplayName("Roq Plugin Lunr - generated named index")
public class RoqPluginLunrGeneratedNamedIndexTest {

    @RegisterExtension
    static final QuarkusExtensionTest unitTest = new QuarkusExtensionTest()
            .withApplicationRoot((jar) -> jar
                    .addAsResource("application.properties")
                    .addAsResource("lunr-site")
                    .delete("/lunr-site/content/recipes-search-index.json"));

    @Test
    @DisplayName("The missing named index is generated")
    public void testNamedIndexGenerated() {
        RestAssured.when().get("/search-index/recipes.json").then().statusCode(200).log().ifValidationFails()
                .body(containsString("\"title\":\"Pancakes Recipe\""))
                .body(not(containsString("\"title\":\"Indexed Page\"")));
    }

    @Test
    @DisplayName("The default index file keeps serving the default index")
    public void testDefaultIndexFromFile() {
        RestAssured.when().get("/search-index.json").then().statusCode(200).log().ifValidationFails()
                .body(containsString("\"title\":\"Indexed Page\""));
    }

    @Test
    @DisplayName("search-script maps the file default and the generated named index")
    public void testSearchScriptIndexMap() {
        RestAssured.when().get("/").then().statusCode(200).log().ifValidationFails()
                .body(containsString(
                        "indexes: {\"default\":\"/search-index.json\",\"recipes\":\"/search-index/recipes.json\"}"));
    }
}
