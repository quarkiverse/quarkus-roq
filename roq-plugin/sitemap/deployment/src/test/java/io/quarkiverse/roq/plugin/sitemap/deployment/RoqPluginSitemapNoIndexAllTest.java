package io.quarkiverse.roq.plugin.sitemap.deployment;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkus.test.QuarkusExtensionTest;
import io.restassured.RestAssured;

/**
 * Site: {@code sitemap-site} (resource)
 * <p>
 * Features tested: the {@code no-index-all} front matter key excludes a page from sitemap.xml, and the
 * {@code sitemap} key wins over it in both directions.
 */
@DisplayName("Roq Plugin Sitemap - no-index-all")
public class RoqPluginSitemapNoIndexAllTest {

    @RegisterExtension
    static final QuarkusExtensionTest unitTest = new QuarkusExtensionTest()
            .overrideConfigKey("quarkus.roq.resource-dir", "sitemap-site")
            .withApplicationRoot((jar) -> jar
                    .addAsResource("sitemap-site"));

    @Test
    @DisplayName("no-index-all: true excludes the page from sitemap.xml")
    public void testNoIndexAllExcludesFromSitemap() {
        RestAssured.when().get("/sitemap.xml").then().statusCode(200).log().ifValidationFails()
                .body(containsString("<loc>/indexed/</loc>"))
                .body(not(containsString("<loc>/no-index-all/</loc>")));
    }

    @Test
    @DisplayName("sitemap: true wins over no-index-all: true")
    public void testSitemapKeyWinsOverNoIndexAll() {
        RestAssured.when().get("/sitemap.xml").then().statusCode(200).log().ifValidationFails()
                .body(containsString("<loc>/no-index-all-sitemap/</loc>"));
    }

    @Test
    @DisplayName("sitemap: false still excludes the page without no-index-all")
    public void testSitemapFalseStillExcludes() {
        RestAssured.when().get("/sitemap.xml").then().statusCode(200).log().ifValidationFails()
                .body(not(containsString("<loc>/sitemap-false/</loc>")));
    }
}
