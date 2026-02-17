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
            .overrideConfigKey("site.runtime-cache", "on-demand")
            .withApplicationRoot((jar) -> jar
                    .addAsResource("cache-test-site"));

    @Test
    public void testOnDemandCache() {
        // First request should render and cache
        RestAssured.when().get("/").then().statusCode(200)
                .header("X-Roq-Cache-Mode", "on-demand")
                .log().ifValidationFails()
                .body("html.head.title", equalTo("Cache Test Site"));

        // Second request should serve from cache
        RestAssured.when().get("/").then()
                .header("X-Roq-Cache-Mode", "on-demand")
                .statusCode(200).log().ifValidationFails()
                .body("html.head.title", equalTo("Cache Test Site"));
    }

    @Test
    public void testPageWithCachedFalse() {
        // Page with cached: false should not be cached
        RestAssured.when().get("/page/no-cache").then()
                .header("X-Roq-Cache-Mode", "false")
                .statusCode(200).log().ifValidationFails()
                .body("html.body.article.h1", equalTo("No Cache Page"));
    }

    @Test
    public void testPageWithCachedStartup() {
        // Page with cached: startup should be pre-cached
        RestAssured.when().get("/page/startup-cache").then()
                .header("X-Roq-Cache-Mode", "startup")
                .statusCode(200).log().ifValidationFails()
                .body("html.body.article.h1", equalTo("Startup Cache Page"));
    }
}
