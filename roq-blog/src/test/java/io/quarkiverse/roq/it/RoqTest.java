package io.quarkiverse.roq.it;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.containsString;

import org.junit.jupiter.api.Test;

import io.quarkus.test.junit.QuarkusTest;

@QuarkusTest
public class RoqTest {

    @Test
    public void testIndex() {
        given()
                .when().get("/")
                .then()
                .statusCode(200)
                .body(containsString("All the tools to generate static websites out of your Quarkus web application."))
                .body(containsString("Welcome to Roq!"))
                .body(containsString("2 minute read"))
                .body(containsString("2024, Aug 29"))
                .body(containsString("2024 &copy; ROQ"));
    }

    @Test
    public void testPosts() {
        given()
                .when().get("/posts/2024-08-29-welcome-to-roq")
                .then()
                .statusCode(200)
                .body(containsString("All the tools to generate static websites out of your Quarkus web application."))
                .body(containsString("<p>Hello folks,</p>"))
                .body(containsString("<h1 class=\"page-title\">Welcome to Roq!</h1>"))
                .body(containsString("2024 &copy; ROQ"));
    }

    @Test
    public void testPage() {
        given()
                .when().get("/events")
                .then()
                .statusCode(200)
                .body(containsString("All the tools to generate static websites out of your Quarkus web application."))
                .body(containsString("<h2 class=\"event-title\">Roq 1.0 Beta</h2>"))
                .body(containsString("2024 &copy; ROQ"));
    }
}
