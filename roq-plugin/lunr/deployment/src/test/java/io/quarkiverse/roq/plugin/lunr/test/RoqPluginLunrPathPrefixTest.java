package io.quarkiverse.roq.plugin.lunr.test;

import static org.hamcrest.Matchers.containsString;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkus.test.QuarkusExtensionTest;
import io.restassured.RestAssured;

/**
 * Site: {@code lunr-site} (resource) without its index files, served under {@code site.path-prefix=/blog}.
 * <p>
 * Features tested: generated indexes are published under the site path prefix and mapped with it.
 */
@DisplayName("Roq Plugin Lunr - generated indexes with a path prefix")
public class RoqPluginLunrPathPrefixTest {

    @RegisterExtension
    static final QuarkusExtensionTest unitTest = new QuarkusExtensionTest()
            .overrideConfigKey("site.path-prefix", "/blog/")
            .withApplicationRoot((jar) -> {
                jar.addAsResource("application.properties")
                        .addAsResource("lunr-site");
                jar.delete("/lunr-site/content/search-index.json");
                jar.delete("/lunr-site/content/recipes-search-index.json");
            });

    @Test
    @DisplayName("Generated indexes are served under the path prefix")
    public void testIndexesUnderPrefix() {
        RestAssured.when().get("/blog/search-index.json").then().statusCode(200).log().ifValidationFails()
                .body(containsString("\"title\":\"Indexed Page\""));
        RestAssured.when().get("/blog/search-index/recipes.json").then().statusCode(200).log().ifValidationFails()
                .body(containsString("\"title\":\"Pancakes Recipe\""));
        RestAssured.when().get("/search-index.json").then().statusCode(404);
    }

    @Test
    @DisplayName("search-script maps the generated indexes with the path prefix")
    public void testSearchScriptIndexMap() {
        RestAssured.when().get("/blog/").then().statusCode(200).log().ifValidationFails()
                .body(containsString(
                        "indexes: {\"default\":\"/blog/search-index.json\",\"recipes\":\"/blog/search-index/recipes.json\"}"));
    }
}
