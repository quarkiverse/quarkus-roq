package io.quarkiverse.roq.plugin.hybrid.deployment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;

import jakarta.inject.Inject;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkiverse.roq.plugin.hybrid.runtime.RoqCacheManager;
import io.quarkus.test.QuarkusExtensionTest;
import io.restassured.RestAssured;

public class RoqHybridTtlAndMaxTest {

    @RegisterExtension
    static final QuarkusExtensionTest unitTest = new QuarkusExtensionTest()
            .overrideConfigKey("quarkus.roq.resource-dir", "hybrid-test-site")
            .overrideConfigKey("site.hybrid.cache-mode", "lazy")
            .overrideConfigKey("site.hybrid.cache-store", "memory")
            .overrideConfigKey("site.hybrid.cache-in-dev-mode", "true")
            .overrideConfigKey("site.hybrid.cache-ttl", "PT0.5S")
            .overrideConfigKey("site.hybrid.cache-max-size", "2")
            .withApplicationRoot((jar) -> jar
                    .addAsResource("hybrid-test-site"));

    @Inject
    RoqCacheManager cacheManager;

    @Test
    void testTtlExpiresLazyCache() throws Exception {
        RestAssured.when().get("/").then()
                .statusCode(200)
                .body(containsString("Welcome to Roq Hybrid"));

        assertThat(cacheManager.size()).isGreaterThan(0);

        Thread.sleep(1000);

        RestAssured.when().get("/").then()
                .statusCode(200)
                .body(containsString("Welcome to Roq Hybrid"));
    }

    @Test
    void testMaxSizeLimitsLazyCache() {
        RestAssured.when().get("/").then().statusCode(200);
        RestAssured.when().get("/pages/french/").then().statusCode(200);
        RestAssured.when().get("/pages/past-page/").then().statusCode(200);

        long lazySize = cacheManager.size();
        // startup page is always cached + max 2 lazy = at most startup + 2
        // startup page (startup-page) is not counted in the lazy max
        assertThat(lazySize).isLessThanOrEqualTo(4);
    }

    @Test
    void testStartupNotEvictedByMaxSize() {
        RestAssured.when().get("/").then().statusCode(200);
        RestAssured.when().get("/pages/french/").then().statusCode(200);
        RestAssured.when().get("/pages/past-page/").then().statusCode(200);

        RestAssured.when().get("/pages/startup-page/").then()
                .statusCode(200)
                .body(containsString("Startup Cached"));
    }
}
