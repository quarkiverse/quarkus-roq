package io.quarkiverse.roq.frontmatter.deployment;

import static org.hamcrest.Matchers.equalTo;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkus.test.QuarkusUnitTest;
import io.restassured.RestAssured;

public class RoqFrontMatterRuntimeCacheTest {

    @RegisterExtension
    static final QuarkusUnitTest unitTest = new QuarkusUnitTest()
            .overrideConfigKey("quarkus.roq.resource-dir", "cache-test-site")
            .overrideConfigKey("site.runtime-cache", "lazy")
            .withApplicationRoot((jar) -> jar
                    .addAsResource("cache-test-site"));

    @Test
    public void testOnDemandCache() {
        // First request should render and cache
        RestAssured.when().get("/").then().statusCode(200)
                .header("X-Roq-Cache-Mode", "lazy")
                .header("X-Roq-Cache-Hit", "false")
                .log().ifValidationFails()
                .body("html.head.title", equalTo("Cache Test Site"));

        // Second request should serve from cache
        RestAssured.when().get("/").then()
                .header("X-Roq-Cache-Mode", "lazy")
                .header("X-Roq-Cache-Hit", "true")
                .statusCode(200).log().ifValidationFails()
                .body("html.head.title", equalTo("Cache Test Site"));
    }

    @Test
    public void testPageWithCachedFalse() {
        // Page with cached: false should not be cached
        RestAssured.when().get("/page/no-cache").then()
                .header("X-Roq-Cache-Mode", "false")
                .header("X-Roq-Cache-Hit", "false")
                .statusCode(200).log().ifValidationFails()
                .body("html.body.article.h1", equalTo("No Cache Page"));
    }

    @Test
    public void testPageWithCachedStartup() {
        // Page with cached: startup should be pre-cached
        RestAssured.when().get("/page/startup-cache").then()
                .header("X-Roq-Cache-Mode", "startup")
                .header("X-Roq-Cache-Hit", "true")
                .statusCode(200).log().ifValidationFails()
                .body("html.head.title", equalTo("Startup Cache Page - Cache Test Site"))
                .body("html.body.article.h1", equalTo("Startup Cache Page"));
    }

    @Test
    public void testLocaleAwareCacheKey() {
        // First EN request should render and cache for EN locale key
        RestAssured.given().header("Accept-Language", "en-US").when().get("/page/locale-cache").then()
                .header("X-Roq-Cache-Mode", "lazy")
                .header("X-Roq-Cache-Hit", "false")
                .statusCode(200).log().ifValidationFails()
                .body("html.body.article.h1", equalTo("Locale Cache Page"));

        // Different locale should not reuse EN cache entry
        RestAssured.given().header("Accept-Language", "pt-BR").when().get("/page/locale-cache").then()
                .header("X-Roq-Cache-Mode", "lazy")
                .header("X-Roq-Cache-Hit", "false")
                .statusCode(200).log().ifValidationFails()
                .body("html.body.article.h1", equalTo("Locale Cache Page"));

        // Subsequent PT request should hit PT cache entry
        RestAssured.given().header("Accept-Language", "pt-BR").when().get("/page/locale-cache").then()
                .header("X-Roq-Cache-Mode", "lazy")
                .header("X-Roq-Cache-Hit", "true")
                .statusCode(200).log().ifValidationFails()
                .body("html.body.article.h1", equalTo("Locale Cache Page"));
    }
}
