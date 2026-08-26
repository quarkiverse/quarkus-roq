package io.quarkiverse.roq.plugin.lunr.test;

import static org.hamcrest.Matchers.containsString;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkus.test.QuarkusExtensionTest;
import io.restassured.RestAssured;

/**
 * Site: {@code lunr-site} (resource)
 * <p>
 * Features tested: accessibility attributes rendered by the search-overlay,
 * search-button and search-script tags.
 */
@DisplayName("Roq Plugin Lunr - search widgets accessibility")
public class RoqPluginLunrTagsAccessibilityTest {

    @RegisterExtension
    static final QuarkusExtensionTest unitTest = new QuarkusExtensionTest()
            .withApplicationRoot((jar) -> jar
                    .addAsResource("application.properties")
                    .addAsResource("lunr-site"));

    @Test
    @DisplayName("Search overlay exposes dialog semantics and a live results region")
    public void testSearchOverlayAccessibility() {
        RestAssured.when().get("/").then().statusCode(200)
                .body(containsString(
                        "id=\"search-overlay\" role=\"dialog\" aria-modal=\"true\" aria-label=\"Search\" aria-hidden=\"true\""))
                .body(containsString("id=\"search-close\" aria-label=\"Close search\""))
                .body(containsString(
                        "id=\"search-results\" role=\"region\" aria-live=\"polite\" aria-label=\"Search results\""));
    }

    @Test
    @DisplayName("Search trigger is a keyboard-operable button")
    public void testSearchButtonAccessibility() {
        RestAssured.when().get("/").then().statusCode(200)
                .body(containsString(
                        "<button type=\"button\" id=\"search-button\" class=\"search-button\" aria-label=\"Search\">"));
    }
}
