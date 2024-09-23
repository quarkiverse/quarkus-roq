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
                .body(containsString("I provide you with all the tools to generate static websites out of your Quarkus web application."))
                .body(containsString("Hello, world! I&#39;m Roq"))
                .body(containsString("minute read"))
                .body(containsString("Page 1 of"))
                .body(containsString("2024 &copy; ROQ"));
    }

    @Test
    public void testPosts() {
        given()
                .when().get("/posts/2024-08-29-welcome-to-roq")
                .then()
                .statusCode(200)
                .body(containsString("I provide you with all the tools to generate static websites out of your Quarkus web application."))
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
                .body(containsString("I provide you with all the tools to generate static websites out of your Quarkus web application."))
                .body(containsString("<h2 class=\"event-title\">Roq 1.0 Beta</h2>"))
                .body(containsString("2024 &copy; ROQ"));
    }
}
