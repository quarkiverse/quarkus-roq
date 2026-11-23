package io.quarkiverse.roq;

import static org.hamcrest.Matchers.containsString;

import org.junit.jupiter.api.Test;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.RestAssured;

@QuarkusTest
public class RoqHybridTest {

    @Test
    public void testIndex() {
        RestAssured.when().get("/").then().statusCode(200).log().ifValidationFails()
                .body(containsString("Hybrid integration test site."));
    }

    @Test
    public void testStartupCachedPost() {
        RestAssured.when().get("/posts/startup-post/").then().statusCode(200).log().ifValidationFails()
                .body(containsString("Startup cached post."));
    }
}
