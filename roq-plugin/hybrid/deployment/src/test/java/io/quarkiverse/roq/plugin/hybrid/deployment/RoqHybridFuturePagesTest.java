package io.quarkiverse.roq.plugin.hybrid.deployment;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.notNullValue;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkus.test.QuarkusExtensionTest;
import io.restassured.RestAssured;

public class RoqHybridFuturePagesTest {

    @RegisterExtension
    static final QuarkusExtensionTest unitTest = new QuarkusExtensionTest()
            .overrideConfigKey("quarkus.roq.resource-dir", "hybrid-test-site")
            .overrideConfigKey("site.hybrid.cache-mode", "lazy")
            .overrideConfigKey("site.hybrid.cache-store", "memory")
            .overrideConfigKey("site.hybrid.cache-in-dev-mode", "true")
            .withApplicationRoot((jar) -> jar
                    .addAsResource("hybrid-test-site"));

    @Test
    void testFuturePageBlockedByFilter() {
        RestAssured.when().get("/pages/future-page/").then()
                .statusCode(404)
                .header("X-Roq-Scheduled", notNullValue());
    }

    @Test
    void testPastPageRenders() {
        RestAssured.when().get("/pages/past-page/").then()
                .statusCode(200)
                .body(containsString("Past Page"));
    }
}
