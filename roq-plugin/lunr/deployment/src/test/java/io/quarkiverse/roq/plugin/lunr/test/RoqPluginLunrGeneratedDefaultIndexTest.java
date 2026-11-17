package io.quarkiverse.roq.plugin.lunr.test;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkus.test.QuarkusExtensionTest;
import io.restassured.RestAssured;

/**
 * Site: {@code lunr-site} (resource) without {@code content/search-index.json}.
 * <p>
 * Features tested: the {@code default} index is generated when its file is missing, while the index declared by
 * the hand-written {@code recipes-search-index.json} is not generated.
 */
@DisplayName("Roq Plugin Lunr - generated default index")
public class RoqPluginLunrGeneratedDefaultIndexTest {

    @RegisterExtension
    static final QuarkusExtensionTest unitTest = new QuarkusExtensionTest()
            .withApplicationRoot((jar) -> jar
                    .addAsResource("application.properties")
                    .addAsResource("lunr-site")
                    .delete("/lunr-site/content/search-index.json"));

    @Test
    @DisplayName("The default index is generated")
    public void testDefaultIndexGenerated() {
        RestAssured.when().get("/search-index.json").then().statusCode(200).log().ifValidationFails()
                .body(containsString("\"title\":\"Indexed Page\""))
                .body(not(containsString("\"title\":\"Pancakes Recipe\"")));
    }

    @Test
    @DisplayName("An index declared by a file is not generated")
    public void testDeclaredIndexNotGenerated() {
        RestAssured.when().get("/recipes-search-index.json").then().statusCode(200).log().ifValidationFails()
                .body(containsString("\"title\":\"Pancakes Recipe\""));
        RestAssured.when().get("/search-index/recipes.json").then().statusCode(404);
    }

    @Test
    @DisplayName("search-script maps the generated default and the file index")
    public void testSearchScriptIndexMap() {
        RestAssured.when().get("/").then().statusCode(200).log().ifValidationFails()
                .body(containsString(
                        "indexes: {\"default\":\"/search-index.json\",\"recipes\":\"/recipes-search-index.json\"}"));
    }
}
