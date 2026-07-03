package io.quarkiverse.roq;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.containsString;

import org.junit.jupiter.api.Test;

import io.quarkus.test.junit.QuarkusTest;

@QuarkusTest
public class RoqThemeLinktreeTest {

    @Test
    void testHomePage() {
        given()
                .when().get("/")
                .then()
                .statusCode(200)
                .body(containsString("My Roq Site"));
    }
}
