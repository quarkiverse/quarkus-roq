package io.quarkiverse.statiq.it;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.is;

import org.junit.jupiter.api.Test;

import io.quarkus.test.junit.QuarkusTest;

@QuarkusTest
public class StatiqResourceTest {

    @Test
    public void testHelloEndpoint() {
        given()
                .when().get("/statiq")
                .then()
                .statusCode(200)
                .body(is("Hello statiq"));
    }
}
