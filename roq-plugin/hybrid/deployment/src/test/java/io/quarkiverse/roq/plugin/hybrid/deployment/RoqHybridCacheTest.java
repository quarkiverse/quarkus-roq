package io.quarkiverse.roq.plugin.hybrid.deployment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.notNullValue;

import jakarta.inject.Inject;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkiverse.roq.plugin.hybrid.runtime.RoqCacheManager;
import io.quarkus.test.QuarkusExtensionTest;
import io.restassured.RestAssured;

public class RoqHybridCacheTest {

    @RegisterExtension
    static final QuarkusExtensionTest unitTest = new QuarkusExtensionTest()
            .overrideConfigKey("quarkus.roq.resource-dir", "hybrid-test-site")
            .overrideConfigKey("site.hybrid.cache-mode", "lazy")
            .overrideConfigKey("site.hybrid.cache-store", "memory")
            .overrideConfigKey("site.hybrid.cache-in-dev-mode", "true")
            .withApplicationRoot((jar) -> jar
                    .addAsResource("hybrid-test-site"));

    @Inject
    RoqCacheManager cacheManager;

    @Test
    void testLazyCachedPageRenders() {
        RestAssured.when().get("/").then()
                .statusCode(200)
                .body(containsString("Welcome to Roq Hybrid"));
    }

    @Test
    void testNoCachePageRenders() {
        RestAssured.when().get("/pages/no-cache/").then()
                .statusCode(200)
                .body(containsString("No Cache Page"));
    }

    @Test
    void testLastModifiedHeader() {
        RestAssured.when().get("/").then()
                .statusCode(200)
                .header("Last-Modified", notNullValue());
    }

    @Test
    void test304NotModified() {
        String lastModified = RestAssured.when().get("/").then()
                .statusCode(200)
                .extract().header("Last-Modified");

        RestAssured.given()
                .header("If-Modified-Since", lastModified)
                .when().get("/")
                .then()
                .statusCode(304);
    }

    @Test
    void testNoCachePageAlwaysReturns200() {
        RestAssured.given()
                .header("If-Modified-Since", "Mon, 01 Jan 2024 00:00:00 GMT")
                .when().get("/pages/no-cache/")
                .then()
                .statusCode(200);
    }

    @Test
    void testDifferentAcceptLanguageSharesCacheEntry() {
        RestAssured.given().header("Accept-Language", "en").when().get("/").then().statusCode(200);
        long sizeAfterEn = cacheManager.size();

        RestAssured.given().header("Accept-Language", "fr").when().get("/").then().statusCode(200);
        long sizeAfterFr = cacheManager.size();

        assertThat(sizeAfterFr).isEqualTo(sizeAfterEn);
    }

    @Test
    void testStartupPageCachedBeforeFirstRequest() {
        assertThat(cacheManager.size()).isGreaterThan(0);

        RestAssured.when().get("/pages/startup-page/").then()
                .statusCode(200)
                .body(containsString("Startup Cached"));
    }

    @Test
    void testStartupAndLazyCoexist() {
        RestAssured.when().get("/pages/startup-page/").then()
                .statusCode(200)
                .body(containsString("Startup Cached"));

        RestAssured.when().get("/").then()
                .statusCode(200)
                .body(containsString("Welcome to Roq Hybrid"));

        assertThat(cacheManager.size()).isGreaterThanOrEqualTo(2);
    }

    @Test
    void testFrontmatterLocaleCreatesSeparateCacheEntry() {
        RestAssured.when().get("/").then().statusCode(200);
        long sizeAfterIndex = cacheManager.size();

        RestAssured.when().get("/pages/french/").then()
                .statusCode(200)
                .body(containsString("Bonjour"));
        long sizeAfterFrench = cacheManager.size();

        assertThat(sizeAfterFrench).isGreaterThan(sizeAfterIndex);
    }
}
